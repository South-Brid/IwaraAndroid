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
import com.example.iwaraphone.data.VideoQuery
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.local.HistoryStore
import com.example.iwaraphone.data.model.Sort
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.data.model.VideoSource
import com.example.iwaraphone.ui.common.FollowToggle
import kotlinx.coroutines.delay
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
    /** 作者的其他热门作品（详情页底部推荐） */
    var authorVideos by mutableStateOf<List<Video>>(emptyList())
        private set
    /** 作者的关注状态，详情加载后创建 */
    var follow by mutableStateOf<FollowToggle?>(null)
        private set
    /** 断点续播时的提示文字，显示几秒后清空 */
    var resumeHint by mutableStateOf<String?>(null)

    /** 首次播放时从上次观看的位置开始 */
    private var resumeFrom = HistoryStore.get(id)?.positionMs ?: 0L

    private var networkRetries = 0

    val player = PlayerFactory.create(ctx).apply {
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // 网络类错误（断流、超时等，错误码 2xxx）自动从当前位置重连几次，仍失败再提示
                if (error.errorCode in NETWORK_ERRORS && networkRetries < MAX_NETWORK_RETRIES) {
                    networkRetries++
                    viewModelScope.launch {
                        delay(RETRY_BASE_DELAY_MS * networkRetries)
                        retryPlayback()
                    }
                    return
                }
                playError = "${error.errorCodeName}: ${error.cause?.message ?: error.message}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) networkRetries = 0
            }
        })
    }

    /** 长按倍速：当前倍速 ×2，限制在 2~3 倍 */
    val boostSpeed get() = (speed * 2).coerceIn(2f, 3f)

    init {
        load()
        viewModelScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_MS)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    /** 保存观看进度：看到最后 [END_MARGIN_MS] 内视为看完，下次从头播放 */
    fun saveProgress() {
        val v = video ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: (v.durationSec * 1000L)
        HistoryStore.updateProgress(id, if (dur > 0 && pos > dur - END_MARGIN_MS) 0 else pos)
    }

    fun load() {
        error = null
        viewModelScope.launch {
            runCatching {
                val v = VideoRepository.detail(id)
                video = v
                if (follow == null) follow = FollowToggle(viewModelScope, v.author)
                HistoryStore.record(v)
                loadAuthorVideos(v)
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

    private fun loadAuthorVideos(v: Video) {
        if (v.author.id.isBlank()) return
        viewModelScope.launch {
            authorVideos = runCatching {
                VideoRepository.list(VideoQuery(Sort.Popularity, userId = v.author.id), 0).items.filter { it.id != id }.take(AUTHOR_VIDEOS)
            }.getOrDefault(emptyList())
        }
    }

    /** 离开视频页时停止播放和预缓冲（暂停仍会继续下载几十秒视频，占满带宽导致其它页面请求失败） */
    fun stopForLeave() {
        saveProgress()
        resumeFrom = player.currentPosition
        player.stop()
    }

    /** 回到视频页：如果之前被 stop 了，从离开时的位置重新加载（不自动播放） */
    fun restoreAfterLeave() {
        val s = selected ?: return
        if (player.playbackState != Player.STATE_IDLE || playError != null) return
        player.setMediaItem(MediaItem.fromUri(s.url), resumeFrom)
        resumeFrom = 0
        player.prepare()
    }

    fun retryPlayback() {
        playError = null
        player.prepare()
        player.play()
    }

    fun select(s: VideoSource) {
        if (s == selected) return
        selected = s
        playError = null
        val start = if (resumeFrom > MIN_RESUME_MS) resumeFrom else player.currentPosition
        if (resumeFrom > MIN_RESUME_MS) resumeHint = "已定位到上次观看位置 ${formatTime(resumeFrom)}"
        resumeFrom = 0
        player.setMediaItem(MediaItem.fromUri(s.url), start)
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

    override fun onCleared() {
        saveProgress()
        player.release()
    }

    private companion object {
        const val AUTHOR_VIDEOS = 8
        const val PROGRESS_SAVE_MS = 5_000L
        const val MIN_RESUME_MS = 5_000L
        const val END_MARGIN_MS = 10_000L
        const val MAX_NETWORK_RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
        val NETWORK_ERRORS = 2000..2999
    }
}
