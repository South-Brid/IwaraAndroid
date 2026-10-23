package com.example.iwaraphone

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.bridge.WebBridge
import com.example.iwaraphone.data.AuthRepository
import com.example.iwaraphone.data.local.Session
import com.example.iwaraphone.data.remote.ApiClient
import com.example.iwaraphone.ui.debug.DebugScreen
import com.example.iwaraphone.ui.follow.FollowScreen
import com.example.iwaraphone.ui.home.HomeScreen
import com.example.iwaraphone.ui.nav.Navigator
import com.example.iwaraphone.ui.nav.Route
import com.example.iwaraphone.ui.profile.ProfileScreen
import com.example.iwaraphone.ui.search.SearchScreen
import com.example.iwaraphone.ui.search.SearchViewModel
import com.example.iwaraphone.ui.theme.IwaraPhoneTheme
import com.example.iwaraphone.ui.user.UserVideosScreen
import com.example.iwaraphone.ui.video.VideoScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 组装依赖：本地存储 → 令牌提供者 → 浏览器通道
        Session.init(this)
        ApiClient.tokenProvider = AuthRepository
        WebView.setWebContentsDebuggingEnabled(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val webView = WebView(this)
        WebBridge.attach(webView)
        lifecycleScope.launch { AuthRepository.refresh() }
        setContent { IwaraPhoneTheme { App(webView) } }
    }

    override fun onDestroy() {
        WebBridge.detach()
        super.onDestroy()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    Search("搜索", Icons.Filled.Search),
    Follow("关注", Icons.Filled.Favorite),
    Me("我的", Icons.Filled.Person),
    Debug("调试", Icons.Filled.Build),
}

@Composable
private fun App(webView: WebView) {
    val nav = remember { Navigator() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var fullscreen by remember { mutableStateOf(false) }
    var webOpen by rememberSaveable { mutableStateOf(false) }
    val searchVm: SearchViewModel = viewModel()
    val showWeb = webOpen || WebBridge.needsUser
    val closeWeb: () -> Unit = {
        webOpen = false
        scope.launch { AuthRepository.refresh() }
    }
    val openVideo = { id: String -> nav.push(Route.VideoDetail(id)) }

    BackHandler(enabled = webOpen) { if (webView.canGoBack()) webView.goBack() else closeWeb() }

    Scaffold(
        contentWindowInsets = if (fullscreen) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (nav.top == null && !showWeb) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val entry = nav.top
            if (entry != null) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                    when (val r = entry.route) {
                        is Route.VideoDetail -> VideoScreen(
                            r.id, fullscreen, { fullscreen = it },
                            onTag = { t ->
                                fullscreen = false
                                nav.clear()
                                tab = Tab.Search
                                searchVm.addTag(t)
                            },
                            onUser = { nav.push(Route.UserVideos(it)) },
                        ) { fullscreen = false; nav.pop() }
                        is Route.UserVideos -> UserVideosScreen(r.user, onOpen = { openVideo(it.id) }, onBack = nav::pop)
                    }
                }
            } else when (tab) {
                Tab.Home -> HomeScreen(onOpen = { openVideo(it.id) })
                Tab.Search -> SearchScreen(searchVm, onOpen = { openVideo(it.id) })
                Tab.Follow -> FollowScreen(onUser = { nav.push(Route.UserVideos(it)) }, onOpen = { openVideo(it.id) }, onLogin = { tab = Tab.Me })
                Tab.Me -> ProfileScreen(onOpenWeb = { webOpen = true })
                Tab.Debug -> DebugScreen()
            }
            if (showWeb) WebToolbar(webView, closable = webOpen, onClose = closeWeb)
            AndroidView(
                factory = { webView },
                modifier = if (showWeb) Modifier.fillMaxSize().padding(top = 48.dp) else Modifier.size(1.dp).alpha(0f),
            )
        }
    }
}

@Composable
private fun WebToolbar(webView: WebView, closable: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (WebBridge.needsUser) "请在下方完成人机验证" else "网页版 · ${WebBridge.status}",
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        TextButton(onClick = { webView.loadUrl(WebBridge.HOME) }) { Text("主页") }
        TextButton(onClick = { webView.reload() }) { Text("刷新") }
        if (closable) TextButton(onClick = onClose) { Text("关闭") }
    }
}
