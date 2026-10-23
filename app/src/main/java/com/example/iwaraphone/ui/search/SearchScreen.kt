package com.example.iwaraphone.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iwaraphone.data.model.Rating
import com.example.iwaraphone.data.model.SearchSort
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.ui.common.OptionRow
import com.example.iwaraphone.ui.common.VideoGrid

@Composable
fun SearchScreen(vm: SearchViewModel, onOpen: (Video) -> Unit) {
    val focus = LocalFocusManager.current
    val done = { focus.clearFocus() }
    Column {
        SearchBar(vm, onSubmit = { done(); vm.submit() })
        if (vm.tags.isNotEmpty()) SelectedTags(vm.tags, vm::removeTag)
        when {
            vm.suggestions.isNotEmpty() -> Suggestions(vm, onSearch = { done(); vm.submit() }, onTag = { done(); vm.addTag(it) })
            !vm.active -> History(vm.history, onPick = { done(); vm.submit(it) }, onClear = vm::clearHistory)
            else -> {
                Filters(vm)
                VideoGrid(vm.list, onOpen)
            }
        }
    }
}

@Composable
private fun SearchBar(vm: SearchViewModel, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = vm.query,
        onValueChange = vm::onQueryChange,
        placeholder = { Text("关键词，或输入后选择标签") },
        singleLine = true,
        trailingIcon = {
            Row {
                if (vm.query.isNotEmpty()) IconButton(onClick = vm::clearQuery) { Icon(Icons.Filled.Close, "清除") }
                IconButton(onClick = onSubmit) { Icon(Icons.Filled.Search, "搜索") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SelectedTags(tags: List<String>, onRemove: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { t ->
            InputChip(selected = true, onClick = { onRemove(t) }, label = { Text("#$t") }, trailingIcon = { Icon(Icons.Filled.Close, "移除") })
        }
    }
}

@Composable
private fun Filters(vm: SearchViewModel) {
    OptionRow("排序") {
        if (vm.keywordMode) SearchSort.entries.forEach { s -> FilterChip(vm.keywordSort == s, { vm.changeKeywordSort(s) }, { Text(s.label) }) }
        else Sort.entries.forEach { s -> FilterChip(vm.tagSort == s, { vm.changeTagSort(s) }, { Text(s.label) }) }
    }
    OptionRow("分级") {
        Rating.entries.forEach { r -> FilterChip(vm.rating == r, { vm.changeRating(r) }, { Text(r.label) }) }
    }
}

@Composable
private fun Suggestions(vm: SearchViewModel, onSearch: () -> Unit, onTag: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "搜索「${vm.query.trim()}」", color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSearch).padding(16.dp),
            )
            HorizontalDivider()
        }
        items(vm.suggestions, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().clickable { onTag(t.id) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("# ${t.id}", modifier = Modifier.weight(1f))
                Text(tagTypeLabel(t.type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun tagTypeLabel(type: String) = when (type) {
    "character" -> "角色"
    "category" -> "分类"
    "artist" -> "作者"
    "general" -> "标签"
    else -> type
}

@Composable
private fun History(history: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("搜索历史", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("清空") }
        }
        if (history.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "提示：输入两个字以上会联想标签，选中标签后可按热门、人气等排序浏览，比关键词搜索更准。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        history.forEach { h ->
            Row(Modifier.fillMaxWidth().clickable { onPick(h) }.padding(vertical = 12.dp)) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(h)
            }
        }
    }
}
