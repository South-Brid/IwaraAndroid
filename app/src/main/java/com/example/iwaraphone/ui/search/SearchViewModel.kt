package com.example.iwaraphone.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.iwaraphone.data.TagRepository
import com.example.iwaraphone.data.VideoQuery
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.local.Session
import com.example.iwaraphone.data.model.Rating
import com.example.iwaraphone.data.model.SearchSort
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.Tag
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.ui.common.Paged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 搜索的两种模式：
 * - 有关键词：走 /search（相关度/最新/播放/点赞），分级和已选标签在本地过滤
 * - 只有标签：走 /videos?tags=（支持热门等全部排序，分级由服务端过滤，结果更准）
 */
class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<Tag>>(emptyList())
        private set
    val tags = mutableStateListOf<String>()
    var keywordSort by mutableStateOf(SearchSort.Relevance)
        private set
    var tagSort by mutableStateOf(Sort.Popularity)
        private set
    var rating by mutableStateOf(Rating.All)
        private set
    var history by mutableStateOf(Session.searchHistory)
        private set
    var submitted by mutableStateOf("")
        private set
    val active get() = submitted.isNotEmpty() || tags.isNotEmpty()
    val keywordMode get() = submitted.isNotEmpty()

    private var suggestJob: Job? = null

    val list = Paged(viewModelScope, Video::id, filter = ::matchesLocalFilter) { page ->
        if (keywordMode) VideoRepository.search(submitted, page, keywordSort)
        else VideoRepository.list(VideoQuery(tagSort, rating, tags.toList()), page)
    }

    private fun matchesLocalFilter(v: Video) =
        !keywordMode || ((rating == Rating.All || v.rating == rating.key) && v.tags.containsAll(tags))

    fun onQueryChange(q: String) {
        query = q
        suggestJob?.cancel()
        val key = q.trim()
        if (key.length < MIN_SUGGEST_LEN) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            suggestions = runCatching { TagRepository.autocomplete(key.replace(' ', '_')) }.getOrDefault(emptyList())
        }
    }

    fun submit(q: String = query) {
        val key = q.trim()
        query = key
        clearSuggestions()
        submitted = key
        if (key.isNotEmpty()) {
            Session.addHistory(key)
            history = Session.searchHistory
        }
        if (active) list.refresh()
    }

    fun addTag(id: String) {
        if (id !in tags) tags.add(id)
        query = ""
        submitted = ""
        clearSuggestions()
        list.refresh()
    }

    fun removeTag(id: String) {
        tags.remove(id)
        if (active) list.refresh()
    }

    fun clearQuery() {
        query = ""
        clearSuggestions()
        if (submitted.isNotEmpty()) {
            submitted = ""
            if (active) list.refresh()
        }
    }

    fun changeKeywordSort(s: SearchSort) { keywordSort = s; list.refresh() }
    fun changeTagSort(s: Sort) { tagSort = s; list.refresh() }
    fun changeRating(r: Rating) { rating = r; list.refresh() }

    fun clearHistory() {
        Session.searchHistory = emptyList()
        history = emptyList()
    }

    private fun clearSuggestions() {
        suggestJob?.cancel()
        suggestions = emptyList()
    }

    private companion object {
        const val MIN_SUGGEST_LEN = 2
        const val SUGGEST_DEBOUNCE_MS = 300L
    }
}
