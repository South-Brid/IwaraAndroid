package com.example.iwaraphone.data

import com.example.iwaraphone.data.model.Page
import com.example.iwaraphone.data.model.Tag
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.objects
import com.example.iwaraphone.data.model.parsePage
import com.example.iwaraphone.data.model.parseUser
import com.example.iwaraphone.data.remote.ApiClient
import org.json.JSONObject

object UserRepository {
    /** 通过用户名获取用户（含 id） */
    suspend fun profile(username: String): User =
        parseUser(JSONObject(ApiClient.get("/profile/$username")).optJSONObject("user"))

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
