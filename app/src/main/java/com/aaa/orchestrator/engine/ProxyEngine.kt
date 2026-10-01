package com.aaa.orchestrator.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Manages proxy network routing and validates low-latency egress.
 *
 * Supports:
 * - 🇩🇪 Germany Route (DE): Bypasses Twitter/X Face Verification & Bot flags, enabling up to 6 OTPs per 2nr number
 *   (Method verified via @EHR_QUICKSMS_BACKUP).
 * - 🇵🇱 Poland Route (PL): Native carrier IP alignment for +48 Polish virtual numbers.
 */
class ProxyEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
) {

    data class ProxyEndpoint(
        val host: String,
        val port: Int,
        val user: String,
        val pass: String,
        val country: String = "DE"
    )

    private val germanyPool = listOf(
        ProxyEndpoint("de.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "DE"),
        ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "DE"),
        ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "DE")
    )

    private val polandPool = listOf(
        ProxyEndpoint("pl.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "PL"),
        ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "PL"),
        ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "PL")
    )

    private val _currentCountry = MutableStateFlow("DE") // Default to Germany (Face Verification bypass)
    val currentCountry: StateFlow<String> = _currentCountry.asStateFlow()

    private var currentProxyIndex = 0

    fun setCountry(countryCode: String) {
        if (countryCode == "DE" || countryCode == "PL") {
            _currentCountry.value = countryCode
            currentProxyIndex = 0
            Timber.i("Proxy country routed to: $countryCode")
        }
    }

    fun toggleCountry(): String {
        val next = if (_currentCountry.value == "DE") "PL" else "DE"
        setCountry(next)
        return next
    }

    fun getActiveProxy(): ProxyEndpoint {
        val pool = if (_currentCountry.value == "DE") germanyPool else polandPool
        return pool[currentProxyIndex % pool.size]
    }

    suspend fun verifyProxyHealth(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val endpoint = getActiveProxy()
        try {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(endpoint.host, endpoint.port))
            val proxyClient = client.newBuilder()
                .proxy(proxy)
                .proxyAuthenticator { _, response ->
                    val credential = okhttp3.Credentials.basic(endpoint.user, endpoint.pass)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
                .build()

            val request = Request.Builder()
                .url("https://ipv4.webshare.io/")
                .build()

            proxyClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    Timber.i("Proxy health verified via ${endpoint.host}:${endpoint.port} (${endpoint.country}) in ${latency}ms")
                    return@withContext Pair(true, latency)
                } else {
                    Timber.w("Proxy responded with HTTP ${response.code}. Rotating.")
                    rotateProxy()
                    return@withContext Pair(false, latency)
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            Timber.w("Proxy ping failed (${latency}ms): ${e.message}. Rotating to next proxy endpoint.")
            rotateProxy()
            return@withContext Pair(false, latency)
        }
    }

    fun rotateProxy() {
        val pool = if (_currentCountry.value == "DE") germanyPool else polandPool
        currentProxyIndex = (currentProxyIndex + 1) % pool.size
        Timber.i("Rotated to proxy #${currentProxyIndex + 1}: ${getActiveProxy().host} (${getActiveProxy().country})")
    }
}
