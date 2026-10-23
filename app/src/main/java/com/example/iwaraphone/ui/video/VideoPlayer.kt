package com.example.iwaraphone.ui.video

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.iwaraphone.data.model.VideoSource
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val AUTO_HIDE_MS = 4_000L
private const val POLL_MS = 250L
private const val RESUME_HINT_MS = 3_000L
/** 横向滑满整个播放器宽度对应的最大跳转时长 */
private const val MAX_SEEK_SPAN_MS = 180_000L

private enum class Panel { None, Quality, Speed }
private enum class DragMode { None, Seek, Brightness, Volume }

/** 播放器界面状态（播放进度轮询 + 控制栏显隐 + 手势提示） */
@Stable
private class PlayerUi {
    var controlsVisible by mutableStateOf(true)
    var panel by mutableStateOf(Panel.None)
    var lastPanel by mutableStateOf(Panel.Quality)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var seekPreview by mutableStateOf<Long?>(null)
    var hint by mutableStateOf<String?>(null)
    /** 每次交互 +1，用于重置自动隐藏计时 */
    var interaction by mutableIntStateOf(0)

    fun touch() {
        interaction++
    }

    fun open(p: Panel) {
        panel = p
        lastPanel = p
    }
}

fun VideoSource.label(): String = when {
    name.equals("Source", true) -> "原画"
    name.toIntOrNull() != null -> "${name}P"
    name.equals("preview", true) -> "预览"
    else -> name
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

private fun Player.togglePlay() {
    if (isPlaying) {
        pause()
    } else {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        play()
    }
}

private fun Activity.currentBrightness(): Float =
    window.attributes.screenBrightness.takeIf { it >= 0 } ?: (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)

private fun Activity.overrideBrightness(v: Float?) {
    window.attributes = window.attributes.apply { screenBrightness = v ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
}

/**
 * B 站式播放器：Media3 负责解码渲染，控制层全部用 Compose 实现。
 * 手势：单击显隐控制栏、双击暂停、长按倍速、横滑跳转、左侧竖滑亮度、右侧竖滑音量。
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    vm: VideoViewModel,
    title: String,
    fullscreen: Boolean,
    onFullscreen: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = vm.player
    val ui = remember { PlayerUi() }
    val activity = LocalContext.current as Activity

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                ui.buffering = state == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        ui.isPlaying = player.isPlaying
        ui.buffering = player.playbackState == Player.STATE_BUFFERING
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            ui.position = player.currentPosition
            ui.duration = player.duration.coerceAtLeast(0)
            delay(POLL_MS)
        }
    }
    LaunchedEffect(ui.controlsVisible, ui.isPlaying, ui.panel, ui.interaction) {
        if (ui.controlsVisible && ui.isPlaying && ui.panel == Panel.None) {
            delay(AUTO_HIDE_MS)
            ui.controlsVisible = false
        }
    }
    DisposableEffect(Unit) { onDispose { activity.overrideBrightness(null) } }
    LaunchedEffect(vm.resumeHint) {
        if (vm.resumeHint != null) {
            delay(RESUME_HINT_MS)
            vm.resumeHint = null
        }
    }
    BackHandler(enabled = ui.panel != Panel.None) { ui.panel = Panel.None }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        GestureLayer(vm, ui, activity)
        if (ui.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        val badge = when {
            vm.boosting -> "▶▶ ${vm.boostSpeed}x 倍速中"
            else -> ui.hint
        }
        badge?.let { CenterBadge(it, Modifier.align(if (vm.boosting) Alignment.TopCenter else Alignment.Center)) }
        vm.resumeHint?.let { CenterBadge(it, Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp)) }
        AnimatedVisibility(ui.controlsVisible && !vm.boosting, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Controls(vm, ui, title, fullscreen, onFullscreen, onBack)
        }
        SidePanel(vm, ui, fullscreen)
        vm.playError?.let { err ->
            Column(
                Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("播放失败", color = Color.White, fontWeight = FontWeight.Bold)
                Text(err, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 3, textAlign = TextAlign.Center)
                TextButton(onClick = vm::retryPlayback) { Text("重试") }
            }
        }
    }
}

@Composable
private fun CenterBadge(text: String, modifier: Modifier) {
    Text(
        text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.padding(16.dp).background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun GestureLayer(vm: VideoViewModel, ui: PlayerUi, activity: Activity) {
    val player = vm.player
    val audio = remember { activity.getSystemService(AudioManager::class.java) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (ui.panel != Panel.None) ui.panel = Panel.None else ui.controlsVisible = !ui.controlsVisible
                        ui.touch()
                    },
                    onDoubleTap = {
                        player.togglePlay()
                        ui.controlsVisible = true
                        ui.touch()
                    },
                    onLongPress = { if (player.isPlaying) vm.setBoost(true) },
                    onPress = {
                        tryAwaitRelease()
                        if (vm.boosting) vm.setBoost(false)
                    },
                )
            }
            .pointerInput(Unit) {
                var mode = DragMode.None
                var start = 0f
                var acc = Offset.Zero
                var leftSide = true
                val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                detectDragGestures(
                    onDragStart = { o ->
                        mode = DragMode.None
                        acc = Offset.Zero
                        leftSide = o.x < size.width / 2
                    },
                    onDragEnd = {
                        if (mode == DragMode.Seek) ui.seekPreview?.let(player::seekTo)
                        ui.seekPreview = null
                        ui.hint = null
                    },
                    onDragCancel = {
                        ui.seekPreview = null
                        ui.hint = null
                    },
                ) { change, delta ->
                    change.consume()
                    acc += delta
                    if (mode == DragMode.None) {
                        mode = when {
                            abs(acc.x) > abs(acc.y) -> DragMode.Seek
                            leftSide -> DragMode.Brightness
                            else -> DragMode.Volume
                        }
                        start = when (mode) {
                            DragMode.Seek -> player.currentPosition.toFloat()
                            DragMode.Brightness -> activity.currentBrightness()
                            else -> audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                        }
                    }
                    when (mode) {
                        DragMode.Seek -> {
                            val span = min(ui.duration, MAX_SEEK_SPAN_MS).toFloat()
                            val target = (start + acc.x / size.width * span).toLong().coerceIn(0, ui.duration)
                            ui.seekPreview = target
                            ui.hint = "${formatTime(target)} / ${formatTime(ui.duration)}"
                        }
                        DragMode.Brightness -> {
                            val v = (start - acc.y / size.height).coerceIn(0.01f, 1f)
                            activity.overrideBrightness(v)
                            ui.hint = "亮度 ${(v * 100).roundToInt()}%"
                        }
                        DragMode.Volume -> {
                            val v = (start - acc.y / size.height).coerceIn(0f, 1f)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVolume).roundToInt(), 0)
                            ui.hint = "音量 ${(v * 100).roundToInt()}%"
                        }
                        DragMode.None -> Unit
                    }
                }
            },
    )
}

@Composable
private fun Controls(vm: VideoViewModel, ui: PlayerUi, title: String, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit) {
    val player = vm.player
    val white = Color.White
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (fullscreen) onFullscreen(false) else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = white)
            }
            if (fullscreen) Text(title, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
        if (!ui.buffering) {
            IconButton(
                onClick = { player.togglePlay(); ui.touch() },
                modifier = Modifier.align(Alignment.Center).size(60.dp).background(Color(0x66000000), CircleShape),
            ) {
                Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ui.isPlaying) "暂停" else "播放", tint = white, modifier = Modifier.size(36.dp))
            }
        }
        val shown = ui.seekPreview ?: ui.position
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = if (fullscreen) 12.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIcon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ui.isPlaying) "暂停" else "播放") {
                player.togglePlay()
                ui.touch()
            }
            Text(formatTime(shown), color = white, fontSize = 11.sp)
            SeekBar(
                fraction = if (ui.duration > 0) shown.toFloat() / ui.duration else 0f,
                onPreview = {
                    ui.seekPreview = (it * ui.duration).toLong()
                    ui.touch()
                },
                onCommit = {
                    ui.seekPreview?.let(player::seekTo)
                    ui.seekPreview = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatTime(ui.duration), color = white, fontSize = 11.sp)
            ControlText(vm.selected?.label() ?: "画质", "清晰度") { ui.open(Panel.Quality) }
            ControlText(if (vm.speed == 1f) "倍速" else "${vm.speed}x", "倍速") { ui.open(Panel.Speed) }
            ControlIcon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (fullscreen) "退出全屏" else "全屏") {
                onFullscreen(!fullscreen)
            }
        }
    }
}

@Composable
private fun ControlIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ControlText(text: String, desc: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .semantics { contentDescription = desc }.padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** 细进度条：点击或拖动跳转，拖动时只预览，松手才 seek */
@Composable
private fun SeekBar(fraction: Float, onPreview: (Float) -> Unit, onCommit: () -> Unit, modifier: Modifier) {
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val primary = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier.height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    preview((o.x / size.width).coerceIn(0f, 1f))
                    commit()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { commit() }, onDragCancel = { commit() }) { change, _ ->
                    change.consume()
                    preview((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val f = fraction.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth(f).height(3.dp).background(primary, RoundedCornerShape(2.dp)))
        Box(Modifier.offset(x = maxWidth * f - 6.dp).size(12.dp).background(primary, CircleShape))
    }
}

/** 右侧滑出的画质 / 倍速选择面板（竖屏、全屏通用） */
@Composable
private fun BoxScope.SidePanel(vm: VideoViewModel, ui: PlayerUi, fullscreen: Boolean) {
    if (ui.panel != Panel.None) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ui.panel = Panel.None })
    }
    AnimatedVisibility(
        visible = ui.panel != Panel.None,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        val options: List<Pair<String, () -> Unit>> = when (ui.lastPanel) {
            Panel.Speed -> SPEEDS.map { s -> "${s}x" to { vm.changeSpeed(s) } }
            else -> vm.sources.map { s -> s.label() to { vm.select(s) } }
        }
        val selected = if (ui.lastPanel == Panel.Speed) "${vm.speed}x" else vm.selected?.label()
        Column(
            Modifier.fillMaxHeight().width(if (fullscreen) 220.dp else 150.dp).background(Color(0xE6000000))
                .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            options.forEach { (label, action) ->
                val active = label == selected
                Text(
                    label,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        action()
                        ui.panel = Panel.None
                        ui.touch()
                    }.padding(vertical = 12.dp),
                )
            }
        }
    }
}
