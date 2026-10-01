package com.aaa.orchestrator.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aaa.orchestrator.MainActivity
import timber.log.Timber

/**
 * Macroify-style Floating Companion Overlay that stays on top of the screen
 * when interacting with external standalone applications like 2nr (Drugi Numer).
 *
 * Features:
 * - Draggable anywhere on the screen
 * - Real-time display of auto-detected Polish phone number
 * - 1-tap "Copy & Return to AAA-X"
 * - Live OTP banner when SMS verification code arrives from 2nr
 * - Eliminates tedious manual app switching and typing
 */
class FloatingAssistantService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var badgeTextView: TextView? = null
    private var phoneTextView: TextView? = null
    private var otpContainer: LinearLayout? = null
    private var otpTextView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (canDrawOverlays()) {
            initFloatingOverlay()
        } else {
            Timber.w("SYSTEM_ALERT_WINDOW permission not granted for FloatingAssistantService")
            stopSelf()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val dpToPx = { dp: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        // Root container
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#38BDF8")) // Sky blue border
            }
            elevation = dpToPx(8).toFloat()
        }

        // Header Row (Badge + Title + Close Button)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val badgeView = TextView(this).apply {
            text = if (currentPhone.startsWith("+48")) "2NR" else "TG BOT"
            textSize = 9f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7")) // Accent Blue
                cornerRadius = dpToPx(4).toFloat()
            }
        }
        badgeTextView = badgeView

        val titleView = TextView(this).apply {
            text = " Companion"
            textSize = 12f
            setTextColor(Color.parseColor("#F8FAFC"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            setOnClickListener {
                stopSelf()
            }
        }

        headerRow.addView(badgeView)
        headerRow.addView(titleView)
        headerRow.addView(closeButton)
        rootLayout.addView(headerRow)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                topMargin = dpToPx(6)
                bottomMargin = dpToPx(6)
            }
            setBackgroundColor(Color.parseColor("#334155"))
        }
        rootLayout.addView(divider)

        // Phone Number Row
        val phoneRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        phoneTextView = TextView(this).apply {
            text = currentPhone
            textSize = 12f
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val returnButton = Button(this).apply {
            text = "Return ↗"
            textSize = 11f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dpToPx(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(32)
            )
            setOnClickListener {
                copyPhoneToClipboard()
                bringOrchestratorToFront(this@FloatingAssistantService)
            }
        }

        phoneRow.addView(phoneTextView)
        phoneRow.addView(returnButton)
        rootLayout.addView(phoneRow)

        // OTP Banner (Hidden until OTP is received)
        otpContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#065F46")) // Dark Emerald
                cornerRadius = dpToPx(6).toFloat()
            }
            visibility = if (currentOtp != null) View.VISIBLE else View.GONE
        }

        otpTextView = TextView(this).apply {
            text = "OTP: ${currentOtp ?: ""}"
            textSize = 11f
            setTextColor(Color.parseColor("#34D399"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val fillOtpButton = Button(this).apply {
            text = "Fill OTP ↗"
            textSize = 10f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669"))
                cornerRadius = dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(28)
            )
            setOnClickListener {
                copyOtpToClipboard()
                bringOrchestratorToFront(this@FloatingAssistantService)
            }
        }

        otpContainer?.addView(otpTextView)
        otpContainer?.addView(fillOtpButton)
        rootLayout.addView(otpContainer)

        // Window Layout Parameters
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dpToPx(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(120)
        }

        // Draggable Touch Handling
        rootLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(rootLayout, params)
                        return true
                    }
                }
                return false
            }
        })

        floatingView = rootLayout
        try {
            windowManager?.addView(rootLayout, params)
            Timber.i("FloatingAssistantService overlay attached successfully.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach floating overlay")
        }
    }

    private fun copyPhoneToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("2nr Phone", currentPhone)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied $currentPhone", Toast.LENGTH_SHORT).show()
    }

    private fun copyOtpToClipboard() {
        val otp = currentOtp ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("2nr OTP", otp)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied OTP $otp", Toast.LENGTH_SHORT).show()
    }

    private fun onPhoneChanged(phone: String) {
        phoneTextView?.post {
            phoneTextView?.text = phone
            badgeTextView?.text = if (phone.startsWith("+48")) "2NR" else "TG BOT"
        }
    }

    private fun onOtpChanged(otp: String?) {
        otpTextView?.post {
            if (otp != null) {
                otpTextView?.text = "OTP: $otp"
                otpContainer?.visibility = View.VISIBLE
            } else {
                otpContainer?.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Timber.w(e, "Error removing floating overlay view")
            }
        }
        instance = null
        Timber.i("FloatingAssistantService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private var instance: FloatingAssistantService? = null
        var currentPhone: String = "+48459074091"
            set(value) {
                field = value
                instance?.onPhoneChanged(value)
            }

        var currentOtp: String? = null
            set(value) {
                field = value
                instance?.onOtpChanged(value)
            }

        fun start(context: Context) {
            try {
                val intent = Intent(context, FloatingAssistantService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Could not start FloatingAssistantService")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingAssistantService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Could not stop FloatingAssistantService")
            }
        }

        fun updatePhone(phone: String) {
            currentPhone = phone
        }

        fun updateOtp(otp: String) {
            currentOtp = otp
        }

        fun bringOrchestratorToFront(context: Context) {
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "Could not bring MainActivity to front")
            }
        }
    }
}
