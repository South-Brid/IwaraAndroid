package com.example.iwaraphone.data.remote

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.example.iwaraphone.bridge.BridgeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 原生请求通道：OkHttp 与 WebView 共用 Cookie（含 Cloudflare 的 cf_clearance）和 User-Agent，
 * 用户在 WebView 里通过一次人机验证后，原生请求即可直接访问接口。
 */
object Network {
    private const val ORIGIN = "https://www.iwara.tv"
    private val JSON = "application/json".toMediaType()

    var userAgent: String = ""
        private set

    fun init(context: Context) {
        userAgent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("")
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .header("Referer", ApiClient.REFERER)
                        .header("Origin", ORIGIN)
                        .build()
                )
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 网络层失败返回 status 0（与 WebBridge 一致，由 ApiClient 统一重试） */
    suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?): BridgeResponse =
        withContext(Dispatchers.IO) {
            val requestBody = body?.toRequestBody(JSON) ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(JSON) else null
            val request = Request.Builder().url(url).method(method, requestBody).headers(headers.toHeaders()).build()
            try {
                client.newCall(request).execute().use { r ->
                    BridgeResponse(r.code, r.body.string(), cfMitigated = r.header("cf-mitigated") == "challenge")
                }
            } catch (e: IOException) {
                BridgeResponse(0, e.toString())
            }
        }
}

private object WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        CookieManager.getInstance().getCookie(url.toString())
            ?.split(';')
            ?.mapNotNull { Cookie.parse(url, it.trim()) }
            .orEmpty()
}
