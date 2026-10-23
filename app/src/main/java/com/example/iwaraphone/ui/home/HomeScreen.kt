package com.example.iwaraphone.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.VideoQuery
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.ui.common.Paged
import com.example.iwaraphone.ui.common.VideoGrid

class HomeViewModel : ViewModel() {
    var sort by mutableStateOf(Sort.Date)
        private set
    val list = Paged(viewModelScope, Video::id) { VideoRepository.list(VideoQuery(sort), it) }

    init {
        list.refresh()
    }

    fun select(s: Sort) {
        if (s == sort) return
        sort = s
        list.refresh()
    }
}

@Composable
fun HomeScreen(onOpen: (Video) -> Unit, onSearch: () -> Unit, vm: HomeViewModel = viewModel()) {
    Column {
        TopBar(onSearch)
        PrimaryScrollableTabRow(
            selectedTabIndex = vm.sort.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {},
        ) {
            Sort.entries.forEach { s ->
                Tab(
                    selected = vm.sort == s,
                    onClick = { vm.select(s) },
                    text = { Text(s.label, fontWeight = if (vm.sort == s) FontWeight.Bold else FontWeight.Normal) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        VideoGrid(vm.list, onOpen)
    }
}

@Composable
private fun TopBar(onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Iwara", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("Phone", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Surface(
                onClick = onSearch,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(36.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("搜索视频、标签", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
