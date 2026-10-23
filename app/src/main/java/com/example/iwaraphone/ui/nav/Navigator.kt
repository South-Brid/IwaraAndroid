package com.example.iwaraphone.ui.nav

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.iwaraphone.data.model.User

/** 二级页面（覆盖在底部标签页之上） */
sealed interface Route {
    data class VideoDetail(val id: String) : Route
    data class UserVideos(val user: User) : Route
}

/**
 * 简单的页面栈。每个页面有独立的 ViewModelStore：
 * 页面在栈里时状态保留（返回时列表不重新加载），出栈即释放（如播放器）。
 */
class Navigator {
    class Entry(val route: Route) : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    val stack = mutableStateListOf<Entry>()
    val top get() = stack.lastOrNull()

    fun push(route: Route) {
        stack.add(Entry(route))
    }

    fun pop() {
        stack.removeLastOrNull()?.viewModelStore?.clear()
    }

    fun clear() {
        while (stack.isNotEmpty()) pop()
    }
}
