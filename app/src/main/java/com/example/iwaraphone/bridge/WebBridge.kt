package com.example.iwaraphone.bridge

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 通道超时等错误。注意不能用 withTimeout 抛出的 TimeoutCancellationException，它会被协程当作"取消"而静默吞掉 */
class BridgeException(message: String) : Exception(message)

data class BridgeResponse(val status: Int, val body: String) {
    val isChallenge: Boolean
        get() = body.contains("Just a moment", true) ||
            body.contains("__cf_chl", true) ||
            body.contains("Enable JavaScript and cookies to continue", true)
}

/**
 * 浏览器通道：在真实 WebView 中打开 iwara.tv，由浏览器完成 Cloudflare 验证，
 * 接口请求通过页面内的 fetch() 发出，结果经 JavascriptInterface 回传。
 */
object WebBridge {
    const val HOME = "https://www.iwara.tv/"
    private const val TAG = "WebBridge"
    private const val READY_TIMEOUT_MS = 120_000L
    private const val FETCH_TIMEOUT_MS = 30_000L
    private const val EVAL_TIMEOUT_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<BridgeResponse>>()
    private var readySignal = CompletableDeferred<Unit>()
    private var probeStartedAt = 0L

    var webView: WebView? = null
        private set
    var userAgent: String = ""
        private set

    /** 浏览器已通过验证，可以发请求 */
    var ready by mutableStateOf(false)
        private set

    /** 验证需要用户手动操作，界面应把 WebView 显示出来 */
    var needsUser by mutableStateOf(false)
        private set

    var status by mutableStateOf("未启动")
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        webView = view
        userAgent = view.settings.userAgentString
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(JsCallback, "AndroidBridge")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                probe()
            }
        }
        load(HOME)
    }

    fun detach() {
        webView?.destroy()
        webView = null
    }

    fun reload() = load(HOME)

    private fun load(url: String) {
        if (readySignal.isCompleted) readySignal = CompletableDeferred()
        ready = false
        probeStartedAt = System.currentTimeMillis()
        status = "正在打开 iwara.tv…"
        main.post { webView?.loadUrl(url) }
    }

    private fun probe() {
        webView?.evaluateJavascript("document.title + '|' + (document.body ? document.body.innerText.slice(0, 300) : '')") { raw ->
            val text = raw.orEmpty()
            val challenged = text.contains("Just a moment", true) ||
                text.contains("Enable JavaScript and cookies", true) ||
                text.contains("Verify you are human", true)
            if (challenged) {
                val waited = System.currentTimeMillis() - probeStartedAt
                needsUser = waited > 6_000
                status = if (needsUser) "需要手动完成人机验证" else "浏览器验证中…"
                main.postDelayed({ probe() }, 1_500)
            } else {
                needsUser = false
                ready = true
                status = "已连接"
                readySignal.complete(Unit)
            }
        }
    }

    private suspend fun awaitReady() {
        withTimeoutOrNull(READY_TIMEOUT_MS) { readySignal.await() } ?: throw BridgeException("连接 iwara 超时，请检查网络或在网页版完成验证")
    }

    /** 在页面上下文中执行 fetch，遇到验证页会重新验证并重试一次 */
    suspend fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): BridgeResponse {
        awaitReady()
        val first = runFetch(url, method, headers, body)
        if (!first.isChallenge) return first
        Log.w(TAG, "challenge on $url, re-verifying")
        load(url)
        awaitReady()
        load(HOME)
        delay(300)
        awaitReady()
        return runFetch(url, method, headers, body)
    }

    private suspend fun runFetch(url: String, method: String, headers: Map<String, String>, body: String?): BridgeResponse {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BridgeResponse>()
        pending[id] = deferred
        val opts = JSONObject().put("method", method).put("headers", JSONObject(headers)).put("credentials", "include")
        if (body != null) opts.put("body", body)
        val js = """
            fetch(${JSONObject.quote(url)}, $opts)
              .then(r => r.text().then(t => AndroidBridge.onResult(${JSONObject.quote(id)}, r.status, t)))
              .catch(e => AndroidBridge.onResult(${JSONObject.quote(id)}, 0, String(e)));
        """.trimIndent()
        main.post { webView?.evaluateJavascript(js, null) }
        return try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { deferred.await() } ?: throw BridgeException("请求超时：${url.substringBefore('?')}")
        } finally {
            pending.remove(id)
        }
    }

    suspend fun eval(js: String) {
        awaitReady()
        main.post { webView?.evaluateJavascript(js, null) }
    }

    /** 读取页面 localStorage（用于获取网页端登录后保存的令牌） */
    suspend fun localStorage(): Map<String, String> {
        awaitReady()
        val deferred = CompletableDeferred<String>()
        main.post {
            webView?.evaluateJavascript("JSON.stringify(Object.assign({}, window.localStorage))") { deferred.complete(it ?: "") }
        }
        val raw = withTimeoutOrNull(EVAL_TIMEOUT_MS) { deferred.await() } ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(JSONObject("{\"v\":$raw}").getString("v"))
            obj.keys().asSequence().associateWith { obj.optString(it) }
        }.getOrDefault(emptyMap())
    }

    private object JsCallback {
        @JavascriptInterface
        fun onResult(id: String, status: Int, body: String) {
            pending[id]?.complete(BridgeResponse(status, body))
        }
    }
}
