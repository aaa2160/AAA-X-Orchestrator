package com.aaa.orchestrator.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Manages proxy network routing and validates low-latency egress.
 * Ensures strict Poland country-matching to pair seamlessly with Polish (+48) numbers from 2nr.
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
        val country: String = "PL"
    )

    private val proxyPool = listOf(
        ProxyEndpoint("p.webshare.io", 80, "lebvkslv", "7zqkmd5k0rca", "PL"),
        ProxyEndpoint("p.webshare.io", 80, "acdyvomx", "xquzdqsbaqne", "PL")
    )

    private var currentProxyIndex = 0

    fun getActiveProxy(): ProxyEndpoint {
        return proxyPool[currentProxyIndex % proxyPool.size]
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
        currentProxyIndex = (currentProxyIndex + 1) % proxyPool.size
        Timber.i("Rotated to proxy #${currentProxyIndex + 1}: ${getActiveProxy().host}")
    }
}
