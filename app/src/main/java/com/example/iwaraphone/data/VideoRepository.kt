package com.example.iwaraphone.data

import android.net.Uri
import com.example.iwaraphone.data.model.Page
import com.example.iwaraphone.data.model.Rating
import com.example.iwaraphone.data.model.SearchSort
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.data.model.VideoSource
import com.example.iwaraphone.data.model.parsePage
import com.example.iwaraphone.data.model.parseSources
import com.example.iwaraphone.data.model.parseVideo
import com.example.iwaraphone.data.remote.ApiClient
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** 视频列表筛选条件，对应 /videos 的查询参数 */
data class VideoQuery(
    val sort: Sort = Sort.Date,
    val rating: Rating = Rating.All,
    val tags: List<String> = emptyList(),
    val userId: String? = null,
    val subscribed: Boolean = false,
)

object VideoRepository {
    private const val FILE_SALT = "mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN"
    private const val PAGE_SIZE = 24

    suspend fun list(q: VideoQuery, page: Int): Page<Video> {
        val params = mapOf(
            "sort" to q.sort.key, "rating" to q.rating.key, "page" to page, "limit" to PAGE_SIZE,
            "tags" to q.tags.takeIf { it.isNotEmpty() }?.joinToString(","),
            "user" to q.userId,
            "subscribed" to if (q.subscribed) "true" else null,
        )
        return parsePage(JSONObject(ApiClient.get("/videos", params)), ::parseVideo)
    }

    /** 关键词搜索：type 必须是 videos（复数），不支持 limit（固定 32 条）和 rating */
    suspend fun search(query: String, page: Int, sort: SearchSort): Page<Video> =
        parsePage(JSONObject(ApiClient.get("/search", mapOf("type" to "videos", "query" to query, "page" to page, "sort" to sort.key))), ::parseVideo)

    suspend fun detail(id: String): Video = parseVideo(JSONObject(ApiClient.get("/video/$id")))

    /** 视频详情中的 fileUrl 需带 X-Version 签名头才能拿到各清晰度的播放地址 */
    suspend fun sources(fileUrl: String): List<VideoSource> =
        parseSources(JSONArray(ApiClient.get(fileUrl, headers = mapOf("X-Version" to xVersion(fileUrl)))))

    fun xVersion(fileUrl: String): String {
        val uri = Uri.parse(fileUrl)
        val seed = "${uri.lastPathSegment}_${uri.getQueryParameter("expires")}_$FILE_SALT"
        return MessageDigest.getInstance("SHA-1").digest(seed.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
