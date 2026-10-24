package com.example.iwaraphone.data.remote

import android.net.Uri
import kotlinx.coroutines.delay
import org.json.JSONObject

class ApiException(message: String, val status: Int) : Exception(message)

/** 由登录模块实现，ApiClient 只依赖这个接口，不直接依赖登录逻辑 */
fun interface TokenProvider {
    suspend fun accessToken(): String?
}

/** iwara 接口的请求入口：统一处理请求头、令牌、人机验证和错误 */
object ApiClient {
    const val BASE = "https://apiq.iwara.tv"
    const val REFERER = "https://www.iwara.tv/"
    private const val NETWORK_RETRIES = 2
    private const val RETRY_DELAY_MS = 1_000L

    var tokenProvider = TokenProvider { null }

    private val baseHeaders = mapOf("Accept" to "application/json", "Content-Type" to "application/json")

    /** path 可以是 /videos 这样的相对路径，也可以是完整地址 */
    fun url(path: String, params: Map<String, Any?> = emptyMap()): String {
        val b = Uri.parse(if (path.startsWith("http")) path else BASE + path).buildUpon()
        params.forEach { (k, v) -> if (v != null) b.appendQueryParameter(k, v.toString()) }
        return b.build().toString()
    }

    /**
     * 网络层失败（status 0）时自动重试，默认只重试 GET；重复提交无副作用的请求可传 [retry] = true。
     * 被 Cloudflare 拦截时弹出验证，用户通过后重试一次。
     */
    suspend fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        auth: Boolean = true,
        retry: Boolean = method == "GET",
    ): ApiResponse {
        val token = if (auth) tokenProvider.accessToken() else null
        val all = baseHeaders + token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty() + headers
        val r = send(url, method, all, body, retry)
        return if (r.isChallenge && CloudflareGate.solve(REFERER)) send(url, method, all, body, retry) else r
    }

    private suspend fun send(url: String, method: String, headers: Map<String, String>, body: String?, retry: Boolean): ApiResponse {
        var r = Network.fetch(url, method, headers, body)
        repeat(if (retry) NETWORK_RETRIES else 0) { i ->
            if (r.status != 0) return r
            delay(RETRY_DELAY_MS * (i + 1))
            r = Network.fetch(url, method, headers, body)
        }
        return r
    }

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

    fun checked(r: ApiResponse): String {
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
