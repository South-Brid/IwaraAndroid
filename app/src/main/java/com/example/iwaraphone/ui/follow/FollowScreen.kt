package com.example.iwaraphone.ui.follow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.AuthRepository
import com.example.iwaraphone.data.AuthState
import com.example.iwaraphone.data.UserRepository
import com.example.iwaraphone.data.VideoQuery
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.model.Page
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.ui.common.ListFooter
import com.example.iwaraphone.ui.common.OptionRow
import com.example.iwaraphone.ui.common.Paged
import com.example.iwaraphone.ui.common.RemoteImage
import com.example.iwaraphone.ui.common.VideoGrid
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class FollowTab(val label: String) { People("关注的人"), Feed("关注动态") }

class FollowViewModel : ViewModel() {
    var tab by mutableStateOf(FollowTab.People)
    private var userId: String? = null

    val people = Paged(viewModelScope, User::id) { page ->
        userId?.let { UserRepository.following(it, page) } ?: Page(emptyList(), 0, page, 1)
    }

    /** 关注的人发布的视频（需要登录） */
    val feed = Paged(viewModelScope, Video::id) { VideoRepository.list(VideoQuery(Sort.Date, subscribed = true), it) }

    init {
        viewModelScope.launch {
            AuthRepository.state.map { (it as? AuthState.LoggedIn)?.user?.id }.distinctUntilChanged().collect { id ->
                userId = id
                if (id != null) {
                    people.refresh()
                    feed.refresh()
                } else {
                    people.items.clear()
                    feed.items.clear()
                }
            }
        }
        viewModelScope.launch {
            UserRepository.followChanged.collect { _ ->
                if (userId != null) {
                    people.refresh()
                    feed.refresh()
                }
            }
        }
    }
}

@Composable
fun FollowScreen(onUser: (User) -> Unit, onOpen: (Video) -> Unit, onLogin: () -> Unit, vm: FollowViewModel = viewModel()) {
    when (AuthRepository.state.collectAsState().value) {
        AuthState.Unknown -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AuthState.LoggedOut -> Column(
            Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text("登录后查看你关注的作者")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLogin) { Text("去登录") }
        }
        is AuthState.LoggedIn -> Column {
            OptionRow("") {
                FollowTab.entries.forEach { t -> FilterChip(vm.tab == t, { vm.tab = t }, { Text(t.label) }) }
            }
            when (vm.tab) {
                FollowTab.People -> PeopleList(vm.people, onUser)
                FollowTab.Feed -> VideoGrid(vm.feed, onOpen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleList(state: Paged<User>, onUser: (User) -> Unit) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = state::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.items, key = { _, u -> u.id }) { i, u ->
                if (i >= state.items.size - 6) LaunchedEffect(state.items.size) { state.loadMore() }
                Row(
                    Modifier.fillMaxWidth().clickable { onUser(u) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(u.avatar, Modifier.size(48.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleSmall)
                        Text("@${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("作品 ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item { ListFooter(state) }
        }
    }
}
