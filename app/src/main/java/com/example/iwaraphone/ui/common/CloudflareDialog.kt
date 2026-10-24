package com.example.iwaraphone.ui.common

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.iwaraphone.data.remote.CloudflareGate
import com.example.iwaraphone.data.remote.Network

private const val CHALLENGE_CHECK_JS =
    "(function(){return !!(window._cf_chl_opt || document.querySelector('#challenge-form, .cf-turnstile, #cf-wrapper, #challenge-running') || document.title.indexOf('Just a moment') >= 0);})()"

/** 放在界面根部：有请求需要人机验证时弹出全屏 WebView */
@Composable
fun CloudflareDialogHost() {
    val url by CloudflareGate.request.collectAsState()
    url?.let { CloudflareDialog(it, onDismiss = { CloudflareGate.finish(false) }, onSolved = { CloudflareGate.finish(true) }) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CloudflareDialog(url: String, onDismiss: () -> Unit, onSolved: () -> Unit) {
    val solved by rememberUpdatedState(onSolved)
    var loading by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
                Column {
                    Text("人机验证", style = MaterialTheme.typography.titleMedium)
                    Text("网站要求验证，完成后会自动返回", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 必须和原生请求一致，否则 cf_clearance 不被认可
                        settings.userAgentString = Network.userAgent
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                view.evaluateJavascript(CHALLENGE_CHECK_JS) { result ->
                                    if (result == "false") {
                                        CookieManager.getInstance().flush()
                                        solved()
                                    }
                                }
                            }
                        }
                        loadUrl(url)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
