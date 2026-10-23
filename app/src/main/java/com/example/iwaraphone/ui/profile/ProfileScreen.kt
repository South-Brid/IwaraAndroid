package com.example.iwaraphone.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.AuthRepository
import com.example.iwaraphone.data.AuthState
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.ui.common.RemoteImage
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    var loggingIn by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            error = "请输入邮箱和密码"
            return
        }
        error = null
        loggingIn = true
        viewModelScope.launch {
            runCatching { AuthRepository.login(email.trim(), password) }.onFailure { error = it.message ?: it.toString() }
            loggingIn = false
        }
    }

    fun logout() {
        viewModelScope.launch { AuthRepository.logout() }
    }
}

@Composable
fun ProfileScreen(onOpenWeb: () -> Unit, vm: ProfileViewModel = viewModel()) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = AuthRepository.state.collectAsState().value) {
            AuthState.Unknown -> CircularProgressIndicator(Modifier.padding(top = 48.dp))
            is AuthState.LoggedIn -> UserCard(s.user, vm::logout)
            AuthState.LoggedOut -> LoginForm(vm)
        }
        TextButton(onClick = onOpenWeb) { Text("打开网页版（网页登录 / 人机验证）") }
    }
}

@Composable
private fun UserCard(user: User, onLogout: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    RemoteImage(user.avatar, Modifier.size(88.dp).clip(CircleShape))
    Text(user.name, style = MaterialTheme.typography.titleLarge)
    Text("@${user.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
}

@Composable
private fun LoginForm(vm: ProfileViewModel) {
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPwd by rememberSaveable { mutableStateOf(false) }
    val submit = {
        focus.clearFocus()
        vm.login(email, password)
    }
    Spacer(Modifier.height(24.dp))
    Text("登录 iwara", style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        value = email, onValueChange = { email = it }, label = { Text("邮箱") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true,
        visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { showPwd = !showPwd }) { Text(if (showPwd) "隐藏" else "显示") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
    Button(onClick = submit, enabled = !vm.loggingIn, modifier = Modifier.fillMaxWidth()) {
        Text(if (vm.loggingIn) "登录中…" else "登录")
    }
    Text("密码只用于本次登录请求，不会保存；保存在本机的只有登录令牌。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
