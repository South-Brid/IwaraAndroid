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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 某个用户的关注状态。跟随登录状态：登录后查询是否已关注，退出后清空。
 * [following] 为 null 表示不显示按钮（未登录、是自己、或还没查到）。
 */
class FollowToggle(private val scope: CoroutineScope, val user: User) {
    var following by mutableStateOf<Boolean?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    init {
        scope.launch {
            AuthRepository.state.map { (it as? AuthState.LoggedIn)?.user?.id }.distinctUntilChanged().collect { me ->
                following = null
                if (me != null && user.id.isNotBlank() && user.id != me && user.username.isNotBlank()) {
                    following = runCatching { UserRepository.isFollowing(user.username) }.getOrNull()
                }
            }
        }
        scope.launch {
            UserRepository.followChanged.collect { (id, on) -> if (id == user.id && following != null) following = on }
        }
    }

    fun toggle() {
        val cur = following ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (cur) UserRepository.unfollow(user.id) else UserRepository.follow(user.id)
                following = !cur
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "${if (cur) "取消关注" else "关注"}失败：${e.message ?: e}"
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun FollowButton(state: FollowToggle, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    state.error?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            state.error = null
        }
    }
    val following = state.following ?: return
    var confirm by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        if (state.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (following) "已关注" else "+ 关注")
    }
    if (following) OutlinedButton(onClick = { confirm = true }, enabled = !state.busy, modifier = modifier) { content() }
    else Button(onClick = state::toggle, enabled = !state.busy, modifier = modifier) { content() }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text("确定不再关注 ${state.user.name}？") },
        confirmButton = { TextButton(onClick = { confirm = false; state.toggle() }) { Text("取消关注") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("再想想") } },
    )
}
