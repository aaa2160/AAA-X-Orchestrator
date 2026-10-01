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
import kotlin.random.Random

/**
 * Manages dynamic proxy network routing with multi-region rotation,
 * latency health checks, and random location dispatch.
 *
 * Supported regions:
 * - Germany (DE): Recommended bypass route for Twitter automated checks
 * - United Kingdom (GB)
 * - United States (US)
 * - Netherlands (NL)
 * - France (FR)
 * - Poland (PL): Align with +48 mobile prefixes
 * - Canada (CA)
 * - RANDOM: Dynamically cycles through all available regions
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

    private val proxyPool = mapOf(
        "DE" to listOf(
            ProxyEndpoint("de.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "DE"),
            ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "DE"),
            ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "DE")
        ),
        "GB" to listOf(
            ProxyEndpoint("uk.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "GB"),
            ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "GB")
        ),
        "US" to listOf(
            ProxyEndpoint("us.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "US"),
            ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "US")
        ),
        "NL" to listOf(
            ProxyEndpoint("nl.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "NL"),
            ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "NL")
        ),
        "FR" to listOf(
            ProxyEndpoint("fr.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "FR"),
            ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "FR")
        ),
        "PL" to listOf(
            ProxyEndpoint("pl.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "PL"),
            ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "PL")
        ),
        "CA" to listOf(
            ProxyEndpoint("ca.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "CA"),
            ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "CA")
        )
    )

    private val availableCountries = listOf("DE", "GB", "US", "NL", "FR", "PL", "CA", "RANDOM")

    private val _currentCountry = MutableStateFlow("RANDOM")
    val currentCountry: StateFlow<String> = _currentCountry.asStateFlow()

    private var currentProxyIndex = 0

    fun setCountry(countryCode: String) {
        if (countryCode in availableCountries) {
            _currentCountry.value = countryCode
            currentProxyIndex = 0
            Timber.i("Proxy country set to: $countryCode")
        }
    }

    fun toggleCountry(): String {
        val currentIndex = availableCountries.indexOf(_currentCountry.value)
        val nextIndex = (currentIndex + 1) % availableCountries.size
        val next = availableCountries[nextIndex]
        setCountry(next)
        return next
    }

    fun randomizeLocation(): String {
        val concreteCountries = listOf("DE", "GB", "US", "NL", "FR", "PL", "CA")
        val randomCountry = concreteCountries.random()
        setCountry(randomCountry)
        return randomCountry
    }

    fun getActiveProxy(): ProxyEndpoint {
        val targetCountry = if (_currentCountry.value == "RANDOM") {
            val concreteCountries = listOf("DE", "GB", "US", "NL", "FR", "PL", "CA")
            concreteCountries[Random.nextInt(concreteCountries.size)]
        } else {
            _currentCountry.value
        }
        val pool = proxyPool[targetCountry] ?: proxyPool["DE"]!!
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
        val pool = proxyPool[_currentCountry.value] ?: proxyPool["DE"]!!
        currentProxyIndex = (currentProxyIndex + 1) % pool.size
        Timber.i("Rotated to proxy #${currentProxyIndex + 1}: ${getActiveProxy().host} (${getActiveProxy().country})")
    }
}
