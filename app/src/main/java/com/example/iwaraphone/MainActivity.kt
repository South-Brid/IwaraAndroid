package com.example.iwaraphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.AuthRepository
import com.example.iwaraphone.data.local.HistoryStore
import com.example.iwaraphone.data.local.Session
import com.example.iwaraphone.data.remote.ApiClient
import com.example.iwaraphone.data.remote.Network
import com.example.iwaraphone.ui.common.CloudflareDialogHost
import com.example.iwaraphone.ui.follow.FollowScreen
import com.example.iwaraphone.ui.history.HistoryScreen
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
        Session.init(this)
        HistoryStore.init(this)
        Network.init(this)
        ApiClient.tokenProvider = AuthRepository
        lifecycleScope.launch { AuthRepository.refresh() }
        setContent { IwaraPhoneTheme { App() } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("首页", Icons.Filled.Home),
    Search("搜索", Icons.Filled.Search),
    Follow("关注", Icons.Filled.Favorite),
    Me("我的", Icons.Filled.Person),
}

@Composable
private fun App() {
    val nav = remember { Navigator() }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var fullscreen by remember { mutableStateOf(false) }
    val searchVm: SearchViewModel = viewModel()
    val openVideo = { id: String -> nav.push(Route.VideoDetail(id)) }

    Scaffold(
        contentWindowInsets = if (fullscreen) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (nav.top == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val entry = nav.top
            if (entry != null) key(entry) {
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
                            onOpenVideo = openVideo,
                        ) { fullscreen = false; nav.pop() }
                        is Route.UserVideos -> UserVideosScreen(r.user, onOpen = { openVideo(it.id) }, onBack = nav::pop)
                        Route.History -> HistoryScreen(onOpen = openVideo, onBack = nav::pop)
                    }
                }
            } else when (tab) {
                Tab.Home -> HomeScreen(onOpen = { openVideo(it.id) }, onSearch = { tab = Tab.Search })
                Tab.Search -> SearchScreen(searchVm, onOpen = { openVideo(it.id) })
                Tab.Follow -> FollowScreen(onUser = { nav.push(Route.UserVideos(it)) }, onOpen = { openVideo(it.id) }, onLogin = { tab = Tab.Me })
                Tab.Me -> ProfileScreen(onOpenHistory = { nav.push(Route.History) })
            }
        }
    }
    CloudflareDialogHost()
}
