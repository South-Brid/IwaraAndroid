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
import com.example.iwaraphone.bridge.WebBridge
import com.example.iwaraphone.data.remote.ApiClient

object PlayerFactory {
    @OptIn(UnstableApi::class)
    fun create(ctx: Context): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(WebBridge.userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to ApiClient.REFERER))
            .setAllowCrossProtocolRedirects(true)
        // 模拟器的 goldfish 硬解码器对 H.264 High Profile 支持不稳定，排到最后改用软解；真机不受影响
        val codecSelector = MediaCodecSelector { mime, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { it.name.contains("goldfish") }
        }
        val renderers = DefaultRenderersFactory(ctx).setMediaCodecSelector(codecSelector).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(ctx, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }
}
