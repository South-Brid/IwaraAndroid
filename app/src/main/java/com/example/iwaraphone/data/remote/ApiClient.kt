package com.example.iwaraphone.data.remote

import android.net.Uri
import com.example.iwaraphone.bridge.BridgeResponse
import com.example.iwaraphone.bridge.WebBridge
import org.json.JSONObject

class ApiException(message: String, val status: Int) : Exception(message)

/** 由登录模块实现，ApiClient 只依赖这个接口，不直接依赖登录逻辑 */
fun interface TokenProvider {
    suspend fun accessToken(): String?
}

/** iwara 接口的请求入口：所有请求经 WebBridge 在浏览器中发出，统一处理请求头、令牌和错误 */
object ApiClient {
    const val BASE = "https://apiq.iwara.tv"
    const val REFERER = "https://www.iwara.tv/"

    var tokenProvider = TokenProvider { null }

    private val baseHeaders = mapOf("Accept" to "application/json", "Content-Type" to "application/json")

    /** path 可以是 /videos 这样的相对路径，也可以是完整地址 */
    fun url(path: String, params: Map<String, Any?> = emptyMap()): String {
        val b = Uri.parse(if (path.startsWith("http")) path else BASE + path).buildUpon()
        params.forEach { (k, v) -> if (v != null) b.appendQueryParameter(k, v.toString()) }
        return b.build().toString()
    }

    suspend fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        auth: Boolean = true,
    ): BridgeResponse {
        val token = if (auth) tokenProvider.accessToken() else null
        val authHeader = token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        return WebBridge.fetch(url, method, baseHeaders + authHeader + headers, body)
    }

    suspend fun get(path: String, params: Map<String, Any?> = emptyMap(), headers: Map<String, String> = emptyMap()): String =
        checked(request(url(path, params), headers = headers))

    suspend fun post(path: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap(), auth: Boolean = true): String =
        checked(request(url(path), "POST", body?.toString(), headers, auth))

    fun checked(r: BridgeResponse): String {
        if (r.status in 200..299) return r.body
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
