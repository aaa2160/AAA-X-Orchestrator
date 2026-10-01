package com.aaa.orchestrator.engine

import android.content.Context
import android.webkit.CookieManager
import com.aaa.orchestrator.data.model.AccountRecord
import com.aaa.orchestrator.data.model.DashboardMetrics
import com.aaa.orchestrator.data.model.OrchestratorState
import com.aaa.orchestrator.data.model.SyncStatus
import com.aaa.orchestrator.data.repository.AccountRepository
import com.aaa.orchestrator.data.repository.CloudSyncRepository
import com.aaa.orchestrator.data.repository.TelephonyPoolRepository
import com.aaa.orchestrator.service.SmsNotificationListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Master Execution Engine for AAA X-Orchestrator.
 * Connects to live WebView, real on-device SMS notification listener,
 * real Polish number pool, and actual Twitter session cookie extraction.
 * No fake accounts or mock delays.
 */
class OrchestratorEngine(
    private val context: Context,
    private val accountRepository: AccountRepository,
    private val telephonyRepo: TelephonyPoolRepository = TelephonyPoolRepository(),
    private val cloudSyncRepo: CloudSyncRepository = CloudSyncRepository(),
    private val proxyEngine: ProxyEngine = ProxyEngine(),
    private val hardwareGuard: HardwareGuard = HardwareGuard(context)
) {

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeJob: Job? = null

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()

    private val _activePhoneNumber = MutableStateFlow("")
    val activePhoneNumber: StateFlow<String> = _activePhoneNumber.asStateFlow()

    private val _activePassword = MutableStateFlow(PasswordSynthesizer.generatePassword())
    val activePassword: StateFlow<String> = _activePassword.asStateFlow()

    private val _latestOtp = MutableStateFlow<String?>(null)
    val latestOtp: StateFlow<String?> = _latestOtp.asStateFlow()

    private val isHalted = AtomicBoolean(false)

    val proxyCountry: StateFlow<String> = proxyEngine.currentCountry

    init {
        val initialProxy = proxyEngine.getActiveProxy()
        // Load persistent stats from database
        engineScope.launch {
            val total = accountRepository.getTotalAccountCount()
            val pending = accountRepository.getPendingCount()
            val slot = telephonyRepo.getActiveSlot()
            if (slot != null && slot.phoneNumber.isNotBlank()) {
                _activePhoneNumber.value = slot.phoneNumber
            }
            _metrics.value = _metrics.value.copy(
                totalCreated = total,
                pendingSyncCount = pending,
                currentSlotInfo = telephonyRepo.getSlotSummary(),
                activeProxyIp = "${initialProxy.host}:${initialProxy.port} (${initialProxy.country})"
            )
        }

        // Connect real SMS / Telegram notification listener
        SmsNotificationListener.onOtpReceived = { code ->
            _latestOtp.value = code
            Timber.i("OrchestratorEngine received real SMS / Telegram OTP: $code")
        }

        // Connect real Accessibility Service phone detection (Telegram & Cloud SMS)
        com.aaa.orchestrator.service.OrchestratorAccessibilityService.onPhoneDetected = { detectedPhone ->
            if (detectedPhone.isNotBlank()) {
                _activePhoneNumber.value = detectedPhone
                val slotIdx = telephonyRepo.getActiveSlot()?.slotIndex ?: 1
                telephonyRepo.setSlotPhoneNumber(slotIdx, detectedPhone)
                _metrics.value = _metrics.value.copy(currentSlotInfo = telephonyRepo.getSlotSummary())
                Timber.i("OrchestratorEngine received real Telegram Bot phone: $detectedPhone")
            }
        }

        // Poll Render Cloud for real phone number and inbound OTPs from Telegram worker
        engineScope.launch {
            while (isActive) {
                try {
                    // Check cloud phone number
                    val cloudPhone = cloudSyncRepo.fetchCloudActivePhone()
                    if (!cloudPhone.isNullOrBlank() && cloudPhone != _activePhoneNumber.value) {
                        _activePhoneNumber.value = cloudPhone
                        val slotIdx = telephonyRepo.getActiveSlot()?.slotIndex ?: 1
                        telephonyRepo.setSlotPhoneNumber(slotIdx, cloudPhone)
                        _metrics.value = _metrics.value.copy(currentSlotInfo = telephonyRepo.getSlotSummary())
                        Timber.i("Cloud phone synced to Orchestrator from Telegram: $cloudPhone")
                    }

                    // Check cloud latest OTP
                    val cloudOtp = cloudSyncRepo.fetchCloudLatestOtp()
                    if (!cloudOtp.isNullOrBlank() && cloudOtp != _latestOtp.value) {
                        _latestOtp.value = cloudOtp
                        Timber.i("Cloud OTP intercepted and synced to Orchestrator: $cloudOtp")
                    }
                } catch (e: Exception) {
                    // Silent background retry
                }
                delay(2500)
            }
        }
    }

    /**
     * Signals the Telegram bot worker via Render cloud to rent a brand new phone number.
     */
    fun requestNewPhoneNumberFromTelegramBot() {
        engineScope.launch {
            val sent = cloudSyncRepo.requestNewPhoneFromBot()
            if (sent) {
                Timber.i("Requested new phone number from @EHR_QUICKINCOME_BOT via Render Cloud")
            }
        }
    }

    fun toggleProxyCountry(): String {
        val next = proxyEngine.toggleCountry()
        val proxy = proxyEngine.getActiveProxy()
        _metrics.value = _metrics.value.copy(
            activeProxyIp = "${proxy.host}:${proxy.port} (${proxy.country})"
        )
        return next
    }

    fun startAutomation() {
        if (activeJob?.isActive == true) {
            Timber.w("Automation engine is already running.")
            return
        }

        isHalted.set(false)
        activeJob = engineScope.launch {
            Timber.i("Starting real autonomous workflow session...")
            _metrics.value = _metrics.value.copy(isRunning = true)

            // Step 1: Pre-Flight Safety & Hardware Check
            _state.value = OrchestratorState.PreflightCheck
            val batterySnapshot = hardwareGuard.checkHardwareStatus()
            _metrics.value = _metrics.value.copy(
                batteryPercent = batterySnapshot.percent,
                batteryTempCelsius = batterySnapshot.temperatureCelsius
            )

            val (isSafe, reason) = hardwareGuard.isSafeToOperate()
            if (!isSafe) {
                _state.value = OrchestratorState.PausedThrottled(reason ?: "Hardware Guard Active")
                _metrics.value = _metrics.value.copy(isRunning = false)
                return@launch
            }

            // Step 2: Stealth Proxy & Telephony Slot Allocation
            val proxy = proxyEngine.getActiveProxy()
            _metrics.value = _metrics.value.copy(
                activeProxyIp = "${proxy.host}:${proxy.port} (${proxy.country})"
            )

            val activeSlot = telephonyRepo.getActiveSlot()
            val phone = activeSlot?.phoneNumber ?: _activePhoneNumber.value
            if (phone.isBlank()) {
                _state.value = OrchestratorState.PausedThrottled("Please acquire a real phone number from Telegram Bot (@EHR_QUICKINCOME_BOT) before starting.")
                _metrics.value = _metrics.value.copy(isRunning = false)
                return@launch
            }
            _activePhoneNumber.value = phone
            _activePassword.value = PasswordSynthesizer.generatePassword()
            _latestOtp.value = null

            // Step 3: Target Dispatch - Direct real browser to X.com Signup
            _state.value = OrchestratorState.TargetDispatch("https://x.com/i/flow/signup")
            _state.value = OrchestratorState.TelephonyLoop(
                slotNumber = activeSlot?.slotIndex ?: 1,
                phoneNumber = phone
            )

            // Step 4: Real Session Detection Loop
            // Listens for real authenticated cookies in CookieManager without creating fake data
            while (isActive && !isHalted.get()) {
                delay(3000)
                try {
                    withContext(Dispatchers.Main) {
                        val cookies = CookieManager.getInstance().getCookie("https://x.com") ?: ""
                        if (CookieParser.hasValidTwitterSession(cookies)) {
                            Timber.i("Real Twitter authenticated session detected in CookieManager!")
                            captureRealSession(cookies)
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Timber.e(e, "Error checking live session cookies")
                }
            }

            _metrics.value = _metrics.value.copy(isRunning = false)
            if (!isHalted.get()) {
                _state.value = OrchestratorState.Idle
            }
        }
    }

    /**
     * Graceful stop triggered intentionally by user.
     */
    fun stopAutomation() {
        activeJob?.cancel()
        _state.value = OrchestratorState.Idle
        _metrics.value = _metrics.value.copy(isRunning = false)
        Timber.i("Automation stopped gracefully by user.")
    }

    /**
     * Emergency Kill Switch trigger (< 1 millisecond response).
     */
    fun triggerEmergencyKillSwitch() {
        isHalted.set(true)
        activeJob?.cancel()
        _state.value = OrchestratorState.HaltedKillSwitch(System.currentTimeMillis())
        _metrics.value = _metrics.value.copy(isRunning = false)
        Timber.e("EMERGENCY KILL SWITCH TRIGGERED: All active operations terminated immediately.")
    }

    /**
     * Captures real session cookies from the live WebView CookieManager,
     * stores the account securely in local SQLite DB, and syncs to Telegram.
     */
    suspend fun captureRealSession(
        cookieString: String,
        customUsername: String? = null,
        customSecret: String? = null
    ): Result<AccountRecord> = withContext(Dispatchers.IO) {
        if (!CookieParser.hasValidTwitterSession(cookieString)) {
            return@withContext Result.failure(
                IllegalStateException("No valid Twitter session found. Both auth_token and ct0 are required.")
            )
        }

        _state.value = OrchestratorState.SessionExtraction

        val username = customUsername ?: run {
            val twidMatch = Regex("twid=u%3D(\\d+)").find(cookieString)?.groupValues?.get(1)
            val digits = _activePhoneNumber.value.filter { it.isDigit() }
            if (twidMatch != null) "x_user_$twidMatch"
            else if (digits.isNotEmpty()) "x_acc_${digits.takeLast(6)}"
            else "x_account"
        }

        val password = _activePassword.value
        val twoFactorSecret = customSecret ?: ""
        val phoneNumber = _activePhoneNumber.value

        val record = AccountRecord(
            username = username,
            password = password,
            twoFactorSecret = twoFactorSecret,
            cookies = CookieParser.sanitizeForExport(cookieString),
            phoneNumberUsed = phoneNumber
        )

        // Save real account to SQLite DB
        val savedId = accountRepository.saveAccount(record)

        // Cloud sync to Telegram channel (-1003932377927) and Sheets
        val syncStatus = cloudSyncRepo.syncAccount(record.copy(id = savedId))
        accountRepository.updateSyncStatus(savedId, syncStatus)

        // Increment telephony usage
        telephonyRepo.incrementActiveSlotUsage()

        // Update metrics
        val updatedCount = accountRepository.getTotalAccountCount()
        val pendingCount = accountRepository.getPendingCount()
        _metrics.value = _metrics.value.copy(
            totalCreated = updatedCount,
            pendingSyncCount = pendingCount,
            currentSlotInfo = telephonyRepo.getSlotSummary()
        )

        _state.value = OrchestratorState.MultiCloudSync(updatedCount)
        Timber.i("REAL ACCOUNT CAPTURED & SAVED: $username (${record.phoneNumberUsed})")

        // Prepare next slot and password for the next run
        val nextSlot = telephonyRepo.getActiveSlot()
        _activePhoneNumber.value = nextSlot?.phoneNumber ?: ""
        _activePassword.value = PasswordSynthesizer.generatePassword()
        _latestOtp.value = null

        return@withContext Result.success(record.copy(id = savedId))
    }

    /**
     * Allows the user to specify their real cloud / virtual phone number.
     */
    fun updateActivePhoneNumber(newPhone: String) {
        _activePhoneNumber.value = newPhone
        val slotIndex = telephonyRepo.getActiveSlot()?.slotIndex ?: 1
        telephonyRepo.setSlotPhoneNumber(slotIndex, newPhone)
        _metrics.value = _metrics.value.copy(currentSlotInfo = telephonyRepo.getSlotSummary())
        Timber.i("Active phone number manually set to: $newPhone")
    }

    /**
     * Allows the user to specify a custom password or regenerate one.
     */
    fun updateActivePassword(newPass: String) {
        _activePassword.value = newPass
    }

    fun regeneratePassword(): String {
        val newPass = PasswordSynthesizer.generatePassword()
        _activePassword.value = newPass
        return newPass
    }
}
