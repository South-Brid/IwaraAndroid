package com.example.iwaraphone.data.model

import org.json.JSONArray
import org.json.JSONObject

const val IMG = "https://i.iwara.tv"

data class Video(
    val id: String,
    val title: String,
    val body: String,
    val views: Int,
    val likes: Int,
    val createdAt: String,
    val rating: String,
    val author: User,
    val thumbnail: String?,
    val durationSec: Int,
    val fileUrl: String?,
    val embedUrl: String?,
    val tags: List<String>,
)

data class User(val id: String, val name: String, val username: String, val avatar: String?)

data class VideoSource(val name: String, val url: String)

data class Tag(val id: String, val type: String)

data class Page<T>(val items: List<T>, val count: Int, val page: Int, val limit: Int) {
    val hasMore get() = items.size >= limit && (page + 1) * limit < count
}

/** /videos 支持的排序 */
enum class Sort(val key: String, val label: String) {
    Date("date", "最新"), Trending("trending", "热门"), Popularity("popularity", "人气"), Views("views", "播放最多"), Likes("likes", "点赞最多")
}

/** /search 支持的排序（不支持 trending） */
enum class SearchSort(val key: String, val label: String) {
    Relevance("relevance", "相关度"), Date("date", "最新"), Views("views", "播放最多"), Likes("likes", "点赞最多")
}

enum class Rating(val key: String, val label: String) {
    All("all", "全部"), General("general", "普通"), Ecchi("ecchi", "R18")
}

// ---------------------------------------------------------------- JSON 解析

private fun JSONObject.str(key: String) = if (isNull(key)) "" else optString(key)

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun parseUser(o: JSONObject?): User {
    val avatar = o?.optJSONObject("avatar")
    return User(
        id = o?.str("id").orEmpty(),
        name = o?.str("name").orEmpty(),
        username = o?.str("username").orEmpty(),
        avatar = avatar?.let { "$IMG/image/avatar/${it.str("id")}/${it.str("name")}" },
    )
}

fun parseVideo(o: JSONObject): Video {
    val file = o.optJSONObject("file")
    val custom = o.optJSONObject("customThumbnail")
    return Video(
        id = o.str("id"),
        title = o.str("title"),
        body = o.str("body"),
        views = o.optInt("numViews"),
        likes = o.optInt("numLikes"),
        createdAt = o.str("createdAt"),
        rating = o.str("rating"),
        author = parseUser(o.optJSONObject("user")),
        thumbnail = when {
            custom != null -> "$IMG/image/thumbnail/${custom.str("id")}/${custom.str("name")}"
            file != null -> "$IMG/image/thumbnail/${file.str("id")}/thumbnail-%02d.jpg".format(o.optInt("thumbnail", 0))
            else -> null
        },
        durationSec = file?.optInt("duration") ?: 0,
        fileUrl = o.str("fileUrl").ifBlank { null },
        embedUrl = o.str("embedUrl").ifBlank { null },
        tags = o.optJSONArray("tags").objects().map { it.str("id") }.filter { it.isNotBlank() },
    )
}

fun <T> parsePage(o: JSONObject, item: (JSONObject) -> T) = Page(
    items = o.optJSONArray("results").objects().map(item),
    count = o.optInt("count"),
    page = o.optInt("page"),
    limit = o.optInt("limit").coerceAtLeast(1),
)

/** 文件地址返回的清晰度列表：Source 在前，数字清晰度从高到低，preview 等放最后 */
fun parseSources(arr: JSONArray): List<VideoSource> = arr.objects().mapNotNull { o ->
    val view = o.optJSONObject("src")?.str("view").orEmpty()
    if (view.isBlank()) null else VideoSource(o.str("name"), if (view.startsWith("//")) "https:$view" else view)
}.sortedBy {
    when {
        it.name.equals("Source", true) -> -1
        it.name.toIntOrNull() != null -> 100_000 - it.name.toInt()
        else -> Int.MAX_VALUE
    }
}

// ---------------------------------------------------------------- 格式化

fun formatCount(n: Int): String = if (n >= 10_000) "%.1f万".format(n / 10_000.0) else n.toString()

fun formatDuration(sec: Int): String = when {
    sec <= 0 -> ""
    sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)
    else -> "%d:%02d".format(sec / 60, sec % 60)
}
