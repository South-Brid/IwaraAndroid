package com.example.iwaraphone.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.iwaraphone.data.model.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 通用分页列表状态。
 * @param key 去重用的唯一键
 * @param filter 客户端过滤（接口不支持的筛选在本地做），整页被过滤光时自动继续加载下一页
 */
class Paged<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> String,
    private val filter: (T) -> Boolean = { true },
    private val loader: suspend (page: Int) -> Page<T>,
) {
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var end by mutableStateOf(false)
        private set
    /** 每完成一次加载 +1，列表底部据此判断是否需要继续加载 */
    var loadCount by mutableStateOf(0)
        private set
    private var page = 0
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        page = 0
        end = false
        error = null
        refreshing = items.isNotEmpty()
        job = scope.launch { load(reset = true) }
    }

    fun loadMore() {
        if (loading || end || error != null) return
        job = scope.launch { load(reset = false) }
    }

    fun retry() {
        error = null
        if (items.isEmpty()) refresh() else loadMore()
    }

    private suspend fun load(reset: Boolean) {
        loading = true
        try {
            var added = 0
            var tries = 0
            while (added == 0 && !end && tries++ < MAX_EMPTY_PAGES) {
                val p = loader(page)
                if (reset && tries == 1) items.clear()
                val seen = items.mapTo(HashSet(), key)
                val fresh = p.items.filter { key(it) !in seen && filter(it) }
                items.addAll(fresh)
                added += fresh.size
                page++
                end = !p.hasMore
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        } finally {
            loading = false
            refreshing = false
            loadCount++
        }
    }

    private companion object {
        const val MAX_EMPTY_PAGES = 5
    }
}
