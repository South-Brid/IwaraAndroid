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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** 通道超时等错误。注意不能用 withTimeout 抛出的 TimeoutCancellationException，它会被协程当作"取消"而静默吞掉 */
class BridgeException(message: String) : Exception(message)

data class BridgeResponse(val status: Int, val body: String, val cfMitigated: Boolean = false) {
    val isChallenge: Boolean
        get() = cfMitigated || (status in CHALLENGE_STATUS && CHALLENGE_MARKERS.any { body.contains(it, true) })

    private companion object {
        val CHALLENGE_STATUS = setOf(403, 429, 503)
        val CHALLENGE_MARKERS = listOf("Just a moment", "__cf_chl", "_cf_chl_opt", "cf-turnstile", "challenge-platform", "Enable JavaScript and cookies to continue")
    }
}

/**
 * 浏览器通道。平时不加载网页，只在两种情况下使用：
 * 1. [verify]：原生请求遇到 Cloudflare 验证时，显示 WebView 让用户完成人机验证（拿到 cf_clearance 后原生请求即可通过）
 * 2. [fetch]：原生请求验证后仍被拦截时的后备通道，在页面内用 fetch() 发请求
 * 另外"网页版"入口（网页登录）也复用这个 WebView。
 */
object WebBridge {
    const val HOME = "https://www.iwara.tv/"
    private const val TAG = "WebBridge"
    private const val FETCH_TIMEOUT_MS = 30_000L
    private const val JS_ABORT_MS = 15_000L
    private const val EVAL_TIMEOUT_MS = 10_000L
    private const val PROBE_INTERVAL_MS = 1_500L

    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<String, CompletableDeferred<BridgeResponse>>()
    private var verifySignal: CompletableDeferred<Boolean>? = null
    private var loaded = false
    /** 页面就绪后要执行的脚本（如退出登录时清除网页端令牌，而页面还没加载） */
    private var pendingJs: String? = null

    var webView: WebView? = null
        private set

    /** 页面已加载并通过验证 */
    var ready by mutableStateOf(false)
        private set

    /** 正在等待用户完成人机验证，界面应把 WebView 显示出来 */
    var needsUser by mutableStateOf(false)
        private set

    var status by mutableStateOf("未启动")
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        webView = view
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
    }

    fun detach() {
        webView?.destroy()
        webView = null
    }

    /** 打开网页版时调用：页面没加载过才加载 */
    fun ensureLoaded() {
        if (!loaded) load(HOME)
    }

    fun reload() = load(HOME)

    private fun load(url: String) {
        loaded = true
        main.post {
            ready = false
            status = "正在打开 iwara.tv…"
            webView?.loadUrl(url)
        }
    }

    /**
     * 打开 iwara 首页，出现验证页时显示 WebView 让用户完成人机验证（自动通过则不打扰用户）；并发调用共用同一次验证。
     * 返回 true 表示已通过（页面不再是验证页），false 表示用户取消。
     */
    suspend fun verify(): Boolean {
        val signal = synchronized(this) {
            verifySignal ?: CompletableDeferred<Boolean>().also {
                verifySignal = it
                load(HOME)
            }
        }
        return signal.await()
    }

    fun cancelVerify() = finishVerify(false)

    private fun finishVerify(success: Boolean) {
        synchronized(this) {
            verifySignal?.complete(success)
            verifySignal = null
        }
        main.post { needsUser = false }
    }

    private fun probe() {
        webView?.evaluateJavascript("document.title + '|' + (document.body ? document.body.innerText.slice(0, 300) : '')") { raw ->
            val text = raw.orEmpty()
            val challenged = text.contains("Just a moment", true) ||
                text.contains("Enable JavaScript and cookies", true) ||
                text.contains("Verify you are human", true)
            if (challenged) {
                status = "需要人机验证"
                // 只有在有请求等待验证时才弹出 WebView 并持续检测；用户取消后不再打扰
                if (verifySignal != null) {
                    needsUser = true
                    main.postDelayed({ probe() }, PROBE_INTERVAL_MS)
                }
            } else {
                CookieManager.getInstance().flush()
                ready = true
                status = "已连接"
                pendingJs?.let { js -> pendingJs = null; webView?.evaluateJavascript(js, null) }
                finishVerify(true)
            }
        }
    }

    private suspend fun awaitReady() {
        if (!ready && !verify()) throw BridgeException("已取消人机验证")
    }

    /** 后备通道：在页面上下文中执行 fetch，遇到验证页时让用户验证后重试一次 */
    suspend fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): BridgeResponse {
        awaitReady()
        val first = runFetch(url, method, headers, body)
        if (!first.isChallenge) return first
        Log.w(TAG, "challenge on $url, asking user to verify")
        if (!verify()) return first
        return runFetch(url, method, headers, body)
    }

    private suspend fun runFetch(url: String, method: String, headers: Map<String, String>, body: String?): BridgeResponse {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BridgeResponse>()
        pending[id] = deferred
        val opts = JSONObject().put("method", method).put("headers", JSONObject(headers)).put("credentials", "include").put("cache", "no-store")
        if (body != null) opts.put("body", body)
        val qid = JSONObject.quote(id)
        // 页面内超时中止：偶发卡死的连接会变成 status 0（网络失败），由上层自动重试，而不是干等到 Kotlin 侧超时
        val js = """
            (function() {
              const ctrl = new AbortController();
              const timer = setTimeout(() => ctrl.abort(), $JS_ABORT_MS);
              const done = (s, t) => { clearTimeout(timer); AndroidBridge.onResult($qid, s, t); };
              fetch(${JSONObject.quote(url)}, Object.assign($opts, { signal: ctrl.signal }))
                .then(r => r.text().then(t => done(r.status, t)))
                .catch(e => done(0, String(e)));
            })();
        """.trimIndent()
        main.post { webView?.evaluateJavascript(js, null) }
        return try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { deferred.await() } ?: throw BridgeException("请求超时：${url.substringBefore('?')}")
        } finally {
            pending.remove(id)
        }
    }

    /** 清除网页端保存的登录令牌；页面还没加载时，等加载完成后再执行 */
    fun clearWebToken() {
        val js = "localStorage.removeItem('token')"
        if (ready) main.post { webView?.evaluateJavascript(js, null) } else pendingJs = js
    }

    /**
     * 读取页面 localStorage（用于获取网页端登录后保存的令牌）。
     * 页面没加载时直接返回空，不为此去加载网页。
     */
    suspend fun localStorage(): Map<String, String> {
        if (!ready) return emptyMap()
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
