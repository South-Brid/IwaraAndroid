package com.example.iwaraphone.ui.video

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.data.model.VideoSource
import kotlinx.coroutines.launch

val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 播放器放在 ViewModel 里：切换全屏 / 清晰度时不重建，进度不丢；页面出栈时释放 */
class VideoViewModel(private val id: String, ctx: Context) : ViewModel() {
    var video by mutableStateOf<Video?>(null)
        private set
    var sources by mutableStateOf<List<VideoSource>>(emptyList())
        private set
    var selected by mutableStateOf<VideoSource?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var playError by mutableStateOf<String?>(null)
        private set
    var speed by mutableStateOf(1f)
        private set
    var boosting by mutableStateOf(false)
        private set

    val player = PlayerFactory.create(ctx).apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playError = "${error.errorCodeName}: ${error.cause?.message ?: error.message}"
            }
        })
    }

    /** 长按倍速：当前倍速 ×2，限制在 2~3 倍 */
    val boostSpeed get() = (speed * 2).coerceIn(2f, 3f)

    init {
        load()
    }

    fun load() {
        error = null
        viewModelScope.launch {
            runCatching {
                val v = VideoRepository.detail(id)
                video = v
                when {
                    v.fileUrl != null -> {
                        sources = VideoRepository.sources(v.fileUrl)
                        sources.firstOrNull()?.let(::select) ?: run { error = "没有可播放的视频源" }
                    }
                    v.embedUrl != null -> error = "这是外链视频：${v.embedUrl}"
                    else -> error = "没有视频文件"
                }
            }.onFailure { error = it.message ?: it.toString() }
        }
    }

    fun select(s: VideoSource) {
        if (s == selected) return
        selected = s
        playError = null
        player.setMediaItem(MediaItem.fromUri(s.url), player.currentPosition)
        player.prepare()
        player.playWhenReady = true
    }

    fun changeSpeed(s: Float) {
        speed = s
        if (!boosting) player.setPlaybackSpeed(s)
    }

    fun setBoost(on: Boolean) {
        boosting = on
        player.setPlaybackSpeed(if (on) boostSpeed else speed)
    }

    override fun onCleared() = player.release()
}
