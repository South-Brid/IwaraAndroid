package com.example.iwaraphone.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.data.model.formatCount
import com.example.iwaraphone.ui.common.OptionRow
import com.example.iwaraphone.ui.common.RemoteImage

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoScreen(
    id: String,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    onTag: (String) -> Unit,
    onUser: (User) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val vm: VideoViewModel = viewModel { VideoViewModel(id, ctx.applicationContext) }
    val activity = ctx as Activity
    LaunchedEffect(fullscreen) { activity.setFullscreen(fullscreen) }
    DisposableEffect(Unit) { onDispose { activity.setFullscreen(false) } }
    BackHandler { if (fullscreen) onFullscreen(false) else onBack() }

    if (fullscreen) {
        PlayerArea(vm, fullscreen = true, onFullscreen, onBack, Modifier.fillMaxSize())
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PlayerArea(vm, fullscreen = false, onFullscreen, onBack, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
        val v = vm.video ?: return@LazyColumn
        item { VideoInfo(v, onUser) }
        if (vm.sources.isNotEmpty()) item { PlaybackOptions(vm) }
        if (vm.error != null && vm.selected == null) item {
            TextButton(onClick = vm::load, modifier = Modifier.padding(horizontal = 4.dp)) { Text("重新加载") }
        }
        if (v.tags.isNotEmpty()) item {
            FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                v.tags.forEach { t -> AssistChip(onClick = { onTag(t) }, label = { Text("#$t", fontSize = 12.sp) }) }
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(v.body.ifBlank { "暂无简介" }, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun PlayerArea(vm: VideoViewModel, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            vm.selected != null -> Player(vm, fullscreen, onFullscreen)
            vm.error != null -> Text(vm.error!!, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            else -> CircularProgressIndicator()
        }
        if (!fullscreen) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x66000000), contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Player(vm: VideoViewModel, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                val pv = PlayerView(c).apply {
                    player = vm.player
                    keepScreenOn = true
                    setFullscreenButtonClickListener { full -> onFullscreen(full) }
                }
                LongPressLayout(c).apply {
                    addView(pv)
                    // 控制栏显示时，底部 25% 区域（进度条、按钮）不触发长按
                    canStart = { e -> !pv.isControllerFullyVisible || e.y < height * 0.75f }
                    onStart = {
                        pv.hideController()
                        vm.setBoost(true)
                    }
                    onEnd = { vm.setBoost(false) }
                }
            },
            update = { (it.getChildAt(0) as PlayerView).setFullscreenButtonState(fullscreen) },
            modifier = Modifier.fillMaxSize(),
        )
        if (vm.boosting) {
            Text(
                "▶▶ ${vm.boostSpeed}x 倍速播放中", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color(0x99000000), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        vm.playError?.let {
            Text("播放失败\n$it", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(16.dp))
        }
    }
}

@Composable
private fun VideoInfo(v: Video, onUser: (User) -> Unit) {
    Row(Modifier.clickable { onUser(v.author) }.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RemoteImage(v.author.avatar, Modifier.size(36.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(v.author.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("作品 ›", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
    Text(v.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
    Text(
        "▶ ${formatCount(v.views)}   ♥ ${formatCount(v.likes)}   ${v.createdAt.take(10)}   ${v.rating}",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PlaybackOptions(vm: VideoViewModel) {
    OptionRow("清晰度") {
        vm.sources.forEach { s -> FilterChip(vm.selected == s, { vm.select(s) }, { Text(s.name) }) }
    }
    OptionRow("倍速") {
        SPEEDS.forEach { s -> FilterChip(vm.speed == s, { vm.changeSpeed(s) }, { Text("${s}x") }) }
    }
    Text(
        "提示：长按画面 ${vm.boostSpeed}x 倍速播放，松手恢复",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp),
    )
}

private fun Activity.setFullscreen(on: Boolean) {
    requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    WindowCompat.getInsetsController(window, window.decorView).apply {
        if (on) {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        } else {
            show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
