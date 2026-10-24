package com.example.iwaraphone.data.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 人机验证协调：请求被 Cloudflare 拦截时调用 [solve]，界面（CloudflareDialogHost）弹出 WebView 让用户完成验证。
 * 并发的请求共用同一次验证。
 */
object CloudflareGate {
    private val _request = MutableStateFlow<String?>(null)
    /** 需要验证的页面地址，非 null 时界面显示验证弹窗 */
    val request = _request.asStateFlow()

    private var pending: CompletableDeferred<Boolean>? = null

    /** 返回 true 表示验证通过，false 表示用户取消 */
    suspend fun solve(url: String): Boolean {
        val deferred = synchronized(this) {
            pending ?: CompletableDeferred<Boolean>().also {
                pending = it
                _request.value = url
            }
        }
        return deferred.await()
    }

    fun finish(success: Boolean) = synchronized(this) {
        pending?.complete(success)
        pending = null
        _request.value = null
    }
}
