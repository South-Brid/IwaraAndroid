package com.example.iwaraphone.ui.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.VideoQuery
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.ui.common.FollowButton
import com.example.iwaraphone.ui.common.FollowToggle
import com.example.iwaraphone.ui.common.OptionRow
import com.example.iwaraphone.ui.common.Paged
import com.example.iwaraphone.ui.common.RemoteImage
import com.example.iwaraphone.ui.common.VideoGrid

class UserVideosViewModel(val user: User) : ViewModel() {
    var sort by mutableStateOf(Sort.Date)
        private set
    val list = Paged(viewModelScope, Video::id) { VideoRepository.list(VideoQuery(sort, userId = user.id), it) }
    val follow = FollowToggle(viewModelScope, user)

    init {
        list.refresh()
    }

    fun select(s: Sort) {
        if (s == sort) return
        sort = s
        list.refresh()
    }
}

/** 某个作者的作品列表 */
@Composable
fun UserVideosScreen(user: User, onOpen: (Video) -> Unit, onBack: () -> Unit) {
    val vm: UserVideosViewModel = viewModel { UserVideosViewModel(user) }
    BackHandler(onBack = onBack)
    Column {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            RemoteImage(user.avatar, Modifier.size(44.dp).clip(CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            FollowButton(vm.follow, Modifier.padding(end = 8.dp))
        }
        OptionRow("排序") {
            Sort.entries.forEach { s -> FilterChip(vm.sort == s, { vm.select(s) }, { Text(s.label) }) }
        }
        VideoGrid(vm.list, onOpen)
    }
}
