package com.example.iwaraphone.data

import com.example.iwaraphone.data.local.Session
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.parseUser
import com.example.iwaraphone.data.remote.ApiClient
import com.example.iwaraphone.data.remote.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: User) : AuthState
}

/**
 * 登录与令牌管理，并对外提供唯一的登录状态 [state]。
 * iwara 的令牌分两种：登录得到长期的刷新令牌，再用它换取短期的访问令牌（请求时携带）。
 */
object AuthRepository : TokenProvider {
    private const val ACCESS_TTL_MS = 30 * 60_000L
    private var accessToken: String? = null
    private var accessTokenAt = 0L

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** 重新确认登录状态（启动时调用） */
    suspend fun refresh() {
        _state.value = runCatching { currentUser() }.getOrNull()?.let(AuthState::LoggedIn) ?: AuthState.LoggedOut
    }

    override suspend fun accessToken(): String? {
        if (accessToken != null && System.currentTimeMillis() - accessTokenAt < ACCESS_TTL_MS) return accessToken
        val refresh = Session.refreshToken ?: return null
        val r = ApiClient.request(ApiClient.url("/user/token"), "POST", headers = mapOf("Authorization" to "Bearer $refresh"), auth = false, retry = true)
        if (r.status == 401) Session.refreshToken = null
        accessToken = runCatching { JSONObject(r.body).getString("accessToken") }.getOrNull()
        accessTokenAt = System.currentTimeMillis()
        return accessToken
    }

    suspend fun login(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password)
        Session.refreshToken = JSONObject(ApiClient.post("/user/login", body, auth = false, retry = true)).getString("token")
        accessToken = null
        refresh()
    }

    /** 未登录返回 null */
    private suspend fun currentUser(): User? {
        if (accessToken() == null) return null
        val r = ApiClient.request(ApiClient.url("/user"))
        if (r.status == 401) return null
        val u = JSONObject(ApiClient.checked(r)).optJSONObject("user") ?: return null
        return parseUser(u).takeIf { it.id.isNotBlank() }
    }

    fun logout() {
        Session.refreshToken = null
        accessToken = null
        _state.value = AuthState.LoggedOut
    }
}
