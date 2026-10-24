package com.example.iwaraphone.ui.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.iwaraphone.data.remote.ApiClient
import com.example.iwaraphone.data.remote.Network

object PlayerFactory {
    private const val LOAD_RETRIES = 6

    @OptIn(UnstableApi::class)
    fun create(ctx: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Network.userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to ApiClient.REFERER))
            .setAllowCrossProtocolRedirects(true)
        // 模拟器的 goldfish 硬解码器对 H.264 High Profile 支持不稳定，排到最后改用软解；真机不受影响
        val codecSelector = MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { it.name.contains("goldfish") }
        }
        val renderers = DefaultRenderersFactory(ctx).setMediaCodecSelector(codecSelector).setEnableDecoderFallback(true)
        // 分段加载失败时多重试几次（默认 3 次），减少弱网下的断流
        val sources = DefaultMediaSourceFactory(http).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))
        return ExoPlayer.Builder(ctx, renderers).setMediaSourceFactory(sources).build()
    }
}
