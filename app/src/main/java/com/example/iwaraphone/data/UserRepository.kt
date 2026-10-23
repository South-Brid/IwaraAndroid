package com.example.iwaraphone.data

import com.example.iwaraphone.data.model.Page
import com.example.iwaraphone.data.model.Tag
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.objects
import com.example.iwaraphone.data.model.parsePage
import com.example.iwaraphone.data.model.parseUser
import com.example.iwaraphone.data.remote.ApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

object UserRepository {
    private val _followChanged = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 8)
    /** 关注 / 取消关注成功后发出（用户 id, 是否已关注），各页面的关注按钮和关注列表据此同步 */
    val followChanged: SharedFlow<Pair<String, Boolean>> = _followChanged.asSharedFlow()

    /** 通过用户名获取用户（含 id） */
    suspend fun profile(username: String): User =
        parseUser(JSONObject(ApiClient.get("/profile/$username")).optJSONObject("user"))

    /** 当前登录用户是否已关注该用户（/profile 登录后返回的 user.following） */
    suspend fun isFollowing(username: String): Boolean =
        JSONObject(ApiClient.get("/profile/$username")).optJSONObject("user")?.optBoolean("following") ?: false

    suspend fun follow(userId: String) {
        ApiClient.post("/user/$userId/followers", retry = true)
        _followChanged.tryEmit(userId to true)
    }

    suspend fun unfollow(userId: String) {
        ApiClient.delete("/user/$userId/followers")
        _followChanged.tryEmit(userId to false)
    }

    /** 关注列表：每一项是 {id, createdAt, user:{...}} */
    suspend fun following(userId: String, page: Int): Page<User> =
        parsePage(JSONObject(ApiClient.get("/user/$userId/following", mapOf("page" to page, "limit" to 32)))) {
            parseUser(it.optJSONObject("user"))
        }
}

object TagRepository {
    suspend fun autocomplete(query: String): List<Tag> =
        JSONObject(ApiClient.get("/autocomplete/tags", mapOf("query" to query))).optJSONArray("results").objects()
            .map { Tag(it.optString("id"), it.optString("type")) }
}
