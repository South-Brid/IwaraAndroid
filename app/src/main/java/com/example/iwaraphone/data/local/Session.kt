package com.example.iwaraphone.data.local

import android.content.Context
import android.content.SharedPreferences

/** 本地存储：登录令牌、搜索历史（仅保存在本机应用私有目录） */
object Session {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    }

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(v) = prefs.edit().apply { if (v == null) remove("refresh_token") else putString("refresh_token", v) }.apply()

    var searchHistory: List<String>
        get() = prefs.getString("search_history", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("search_history", v.take(15).joinToString("\n")).apply()

    fun addHistory(q: String) {
        searchHistory = listOf(q) + searchHistory.filter { it != q }
    }
}
