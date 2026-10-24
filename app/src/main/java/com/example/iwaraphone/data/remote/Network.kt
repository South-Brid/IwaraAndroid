package com.example.iwaraphone.data.remote

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
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

/** status 0 表示网络层失败（连不上、超时等） */
data class ApiResponse(val status: Int, val body: String, val cfMitigated: Boolean = false) {
    /** 被 Cloudflare 拦截（返回的是人机验证页而不是接口数据） */
    val isChallenge: Boolean
        get() = cfMitigated || (status in CHALLENGE_STATUS && CHALLENGE_MARKERS.any { body.contains(it, true) })

    private companion object {
        val CHALLENGE_STATUS = setOf(403, 429, 503)
        val CHALLENGE_MARKERS = listOf("Just a moment", "_cf_chl_opt", "cf-turnstile", "challenge-platform")
    }
}

/**
 * OkHttp 客户端：与 WebView 共用 Cookie（人机验证后的 cf_clearance）和 User-Agent，
 * 用户在验证弹窗里通过一次验证后，原生请求即可继续访问接口。
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

    suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?): ApiResponse =
        withContext(Dispatchers.IO) {
            val requestBody = body?.toRequestBody(JSON) ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody(JSON) else null
            val request = Request.Builder().url(url).method(method, requestBody).headers(headers.toHeaders()).build()
            try {
                client.newCall(request).execute().use { r ->
                    ApiResponse(r.code, r.body.string(), cfMitigated = r.header("cf-mitigated") == "challenge")
                }
            } catch (e: IOException) {
                ApiResponse(0, e.toString())
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
