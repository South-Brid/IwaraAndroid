package com.example.iwaraphone.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.iwaraphone.data.AuthRepository
import com.example.iwaraphone.data.AuthState
import com.example.iwaraphone.data.UserRepository
import com.example.iwaraphone.data.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 某个用户的关注状态，跟随登录状态变化：
 * 未登录时按钮照常显示，点击提示先登录；登录后查询是否已关注；看自己的主页时不显示。
 */
class FollowToggle(private val scope: CoroutineScope, val user: User) {
    var visible by mutableStateOf(user.id.isNotBlank())
        private set
    var loggedIn by mutableStateOf(false)
        private set
    /** 登录后的关注状态，null 表示还在查询 */
    var following by mutableStateOf<Boolean?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    init {
        scope.launch {
            AuthRepository.state.collect { s ->
                val me = (s as? AuthState.LoggedIn)?.user?.id
                loggedIn = me != null
                visible = user.id.isNotBlank() && user.id != me
                following = null
                if (me != null && visible) {
                    // 查询失败时按"未关注"处理，按钮仍可用
                    following = runCatching { UserRepository.isFollowing(user.username) }.getOrDefault(false)
                }
            }
        }
        scope.launch {
            UserRepository.followChanged.collect { (id, on) -> if (id == user.id && loggedIn) following = on }
        }
    }

    fun toggle() {
        if (!loggedIn) {
            message = "请先在「我的」页面登录"
            return
        }
        val cur = following ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (cur) UserRepository.unfollow(user.id) else UserRepository.follow(user.id)
                following = !cur
                message = if (cur) "已取消关注" else "关注成功"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "${if (cur) "取消关注" else "关注"}失败：${e.message ?: e}"
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun FollowButton(state: FollowToggle, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    state.message?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            state.message = null
        }
    }
    if (!state.visible) return
    val following = state.following == true
    val loading = state.busy || (state.loggedIn && state.following == null)
    var confirm by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (following) "已关注" else "+ 关注")
    }
    if (following) OutlinedButton(onClick = { confirm = true }, enabled = !loading, modifier = modifier) { content() }
    else Button(onClick = state::toggle, enabled = !loading, modifier = modifier) { content() }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text("确定不再关注 ${state.user.name}？") },
        confirmButton = { TextButton(onClick = { confirm = false; state.toggle() }) { Text("取消关注") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("再想想") } },
    )
}
