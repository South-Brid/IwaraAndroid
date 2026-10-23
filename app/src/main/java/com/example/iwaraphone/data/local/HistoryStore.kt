package com.example.iwaraphone.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.iwaraphone.data.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val author: String,
    val durationSec: Int,
    val positionMs: Long,
    val watchedAt: Long,
)

/** 观看历史：仅保存在本机，按最近观看排序，最多 [MAX_ENTRIES] 条 */
object HistoryStore {
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 200
    private lateinit var prefs: SharedPreferences

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)
        _entries.value = load()
    }

    fun get(id: String): HistoryEntry? = _entries.value.firstOrNull { it.id == id }

    /** 打开视频时记录（保留已有进度），并移到最前 */
    fun record(v: Video) {
        val old = get(v.id)
        put(
            HistoryEntry(
                id = v.id, title = v.title, thumbnail = v.thumbnail, author = v.author.name,
                durationSec = v.durationSec, positionMs = old?.positionMs ?: 0, watchedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun updateProgress(id: String, positionMs: Long) {
        val old = get(id) ?: return
        put(old.copy(positionMs = positionMs, watchedAt = System.currentTimeMillis()))
    }

    fun remove(id: String) = save(_entries.value.filterNot { it.id == id })

    fun clear() = save(emptyList())

    private fun put(e: HistoryEntry) = save((listOf(e) + _entries.value.filterNot { it.id == e.id }).take(MAX_ENTRIES))

    private fun save(list: List<HistoryEntry>) {
        _entries.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("thumbnail", it.thumbnail).put("author", it.author)
                    .put("duration", it.durationSec).put("position", it.positionMs).put("at", it.watchedAt),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<HistoryEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryEntry(
                id = o.getString("id"), title = o.optString("title"), thumbnail = o.optString("thumbnail").ifBlank { null },
                author = o.optString("author"), durationSec = o.optInt("duration"), positionMs = o.optLong("position"), watchedAt = o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())
}
