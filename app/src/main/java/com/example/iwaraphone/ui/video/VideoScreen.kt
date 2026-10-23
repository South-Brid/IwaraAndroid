package com.example.iwaraphone.ui.video

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.data.model.User
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.data.model.formatCount
import com.example.iwaraphone.data.model.formatDuration
import com.example.iwaraphone.ui.common.RatingBadge
import com.example.iwaraphone.ui.common.RemoteImage

private const val DESC_COLLAPSED_LINES = 3

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoScreen(
    id: String,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    onTag: (String) -> Unit,
    onUser: (User) -> Unit,
    onOpenVideo: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val vm: VideoViewModel = viewModel { VideoViewModel(id, ctx.applicationContext) }
    val activity = ctx as Activity
    LaunchedEffect(fullscreen) { activity.setFullscreen(fullscreen) }
    // 离开视频页（如进入作者页，视频页仍在栈里）或 App 切到后台时暂停，避免后台继续播放、占用网络
    DisposableEffect(Unit) {
        vm.restoreAfterLeave()
        onDispose {
            activity.setFullscreen(false)
            vm.stopForLeave()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.player.pause()
        vm.saveProgress()
    }
    BackHandler { if (fullscreen) onFullscreen(false) else onBack() }

    val title = vm.video?.title.orEmpty()
    if (fullscreen) {
        PlayerArea(vm, title, fullscreen = true, onFullscreen, onBack, Modifier.fillMaxSize())
        return
    }
    Column(Modifier.fillMaxSize()) {
        PlayerArea(vm, title, fullscreen = false, onFullscreen, onBack, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        val v = vm.video
        if (v == null) {
            vm.error?.let {
                TextButton(onClick = vm::load, modifier = Modifier.padding(8.dp)) { Text("加载失败，点击重试") }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Header(v) }
            item { AuthorCard(v.author) { onUser(v.author) } }
            item { Description(v.body) }
            if (v.tags.isNotEmpty()) item {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    v.tags.forEach { t -> TagChip(t) { onTag(t) } }
                }
            }
            if (vm.authorVideos.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(top = 12.dp))
                    Text("作者热门作品", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                }
                items(vm.authorVideos, key = { it.id }) { r -> RelatedRow(r) { onOpenVideo(r.id) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PlayerArea(vm: VideoViewModel, title: String, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier) {
    if (vm.selected != null) {
        VideoPlayer(vm, title, fullscreen, onFullscreen, onBack, modifier)
        return
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val err = vm.error
        if (err != null) Text(err, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(24.dp)) else CircularProgressIndicator(color = Color.White)
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }
}

@Composable
private fun Header(v: Video) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        Text(
            v.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(Icons.Filled.PlayArrow, formatCount(v.views))
            Stat(Icons.Filled.Favorite, formatCount(v.likes))
            Text(v.createdAt.take(10), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RatingBadge(v.rating)
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(2.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AuthorCard(author: User, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(author.avatar, Modifier.size(42.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(author.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${author.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        OutlinedButton(onClick = onClick) { Text("作品 ›") }
    }
}

@Composable
private fun Description(body: String) {
    if (body.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflow by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 12.dp).clickable(enabled = overflow || expanded) { expanded = !expanded }) {
        Text(
            body,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else DESC_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
        )
        if (overflow || expanded) {
            Text(if (expanded) "收起" else "展开", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun TagChip(tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text("#$tag", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

/** 横向的小卡片（B 站详情页"相关推荐"样式） */
@Composable
private fun RelatedRow(v: Video, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(v.thumbnail, Modifier.fillMaxSize())
            Text(
                formatDuration(v.durationSec), color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f, fill = false))
            Text(v.author.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            Text("▶ ${formatCount(v.views)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
