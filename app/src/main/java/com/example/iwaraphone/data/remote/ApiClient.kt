package com.example.iwaraphone.data.remote

import android.net.Uri
import com.example.iwaraphone.bridge.BridgeResponse
import com.example.iwaraphone.bridge.WebBridge
import kotlinx.coroutines.delay
import org.json.JSONObject

class ApiException(message: String, val status: Int) : Exception(message)

/** 由登录模块实现，ApiClient 只依赖这个接口，不直接依赖登录逻辑 */
fun interface TokenProvider {
    suspend fun accessToken(): String?
}

/**
 * iwara 接口的请求入口：统一处理请求头、令牌和错误。
 * 默认走原生通道（[Network]）；遇到 Cloudflare 验证时让用户在 WebView 里验证一次再重试，
 * 验证后原生请求仍被拦截，则本次运行改走浏览器通道（[WebBridge.fetch]）。
 */
object ApiClient {
    const val BASE = "https://apiq.iwara.tv"
    const val REFERER = "https://www.iwara.tv/"
    private const val NETWORK_RETRIES = 4
    private const val RETRY_BASE_DELAY_MS = 1_000L

    var tokenProvider = TokenProvider { null }

    /** 原生请求在验证后仍被拦截时置为 true，之后全部走浏览器通道 */
    @Volatile
    private var useBrowser = false

    private val baseHeaders = mapOf("Accept" to "application/json", "Content-Type" to "application/json")

    /** path 可以是 /videos 这样的相对路径，也可以是完整地址 */
    fun url(path: String, params: Map<String, Any?> = emptyMap()): String {
        val b = Uri.parse(if (path.startsWith("http")) path else BASE + path).buildUpon()
        params.forEach { (k, v) -> if (v != null) b.appendQueryParameter(k, v.toString()) }
        return b.build().toString()
    }

    /**
     * 遇到网络层失败（status 0，如代理瞬断）时自动重试。
     * 默认只重试 GET；登录、换令牌这类重复提交无副作用的请求可传 [retry] = true。
     */
    suspend fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        auth: Boolean = true,
        retry: Boolean = method == "GET",
    ): BridgeResponse {
        val token = if (auth) tokenProvider.accessToken() else null
        val authHeader = token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        val all = baseHeaders + authHeader + headers
        var r = send(url, method, all, body, retry)
        if (r.isChallenge && !useBrowser && WebBridge.verify()) {
            r = send(url, method, all, body, retry)
            if (r.isChallenge) {
                useBrowser = true
                r = send(url, method, all, body, retry)
            }
        }
        return r
    }

    private suspend fun send(url: String, method: String, headers: Map<String, String>, body: String?, retry: Boolean): BridgeResponse {
        val attempts = if (retry) NETWORK_RETRIES + 1 else 1
        var r = fetch(url, method, headers, body)
        // 指数退避（1s、2s、4s、8s）：握手失败等错误会瞬间返回，间隔太短的重试扛不住几秒的网络抖动
        for (i in 1 until attempts) {
            if (r.status != 0) break
            delay(RETRY_BASE_DELAY_MS shl (i - 1))
            r = fetch(url, method, headers, body)
        }
        return r
    }

    private suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?) =
        if (useBrowser) WebBridge.fetch(url, method, headers, body) else Network.fetch(url, method, headers, body)

    suspend fun get(path: String, params: Map<String, Any?> = emptyMap(), headers: Map<String, String> = emptyMap()): String =
        checked(request(url(path, params), headers = headers))

    suspend fun post(
        path: String,
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
        auth: Boolean = true,
        retry: Boolean = false,
    ): String = checked(request(url(path), "POST", body?.toString(), headers, auth, retry))

    suspend fun delete(path: String): String = checked(request(url(path), "DELETE", retry = true))

    fun checked(r: BridgeResponse): String {
        if (r.status in 200..299) return r.body
        if (r.status == 0) throw ApiException("网络连接失败，请检查网络或代理后重试", 0)
        if (r.isChallenge) throw ApiException("需要完成人机验证，请重试", r.status)
        val key = runCatching { JSONObject(r.body).optString("message") }.getOrNull().orEmpty()
        val msg = when (key) {
            "errors.invalidLogin" -> "邮箱或密码错误"
            "errors.serverError" -> "服务器错误"
            "errors.notFound" -> "内容不存在或已删除"
            "errors.forbidden" -> "没有权限（可能需要登录）"
            "" -> r.body.take(120)
            else -> key
        }
        throw ApiException("HTTP ${r.status}：$msg", r.status)
    }
}
