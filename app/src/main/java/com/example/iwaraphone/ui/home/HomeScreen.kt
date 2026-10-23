package com.example.iwaraphone.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.VideoQuery
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.ui.common.OptionRow
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
fun HomeScreen(onOpen: (Video) -> Unit, vm: HomeViewModel = viewModel()) {
    Column {
        OptionRow("排序") {
            Sort.entries.forEach { s -> FilterChip(vm.sort == s, { vm.select(s) }, { Text(s.label) }) }
        }
        VideoGrid(vm.list, onOpen)
    }
}
