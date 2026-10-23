package com.example.iwaraphone.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.iwaraphone.data.model.Video
import com.example.iwaraphone.data.model.formatCount
import com.example.iwaraphone.data.model.formatDuration
import com.example.iwaraphone.data.remote.ApiClient

private const val SKELETON_COUNT = 8

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(ctx).data(url).addHeader("Referer", ApiClient.REFERER).crossfade(true).build() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** 带标题的一行可横向滚动的选项（筛选、清晰度、倍速等） */
@Composable
fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp).height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** 分级角标：只有 R18（ecchi）显示 */
@Composable
fun RatingBadge(rating: String, modifier: Modifier = Modifier) {
    if (rating != "ecchi") return
    Text(
        "R18", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private val CardShape = RoundedCornerShape(10.dp)

@Composable
fun VideoCard(v: Video, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(4.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                RemoteImage(v.thumbnail, Modifier.fillMaxSize())
                RatingBadge(v.rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("▶ ${formatCount(v.views)}", color = Color.White, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("♥ ${formatCount(v.likes)}", color = Color.White, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(v.durationSec), color = Color.White, fontSize = 11.sp)
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    v.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    v.author.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 首次加载时的占位卡片（呼吸闪烁） */
@Composable
private fun SkeletonCard() {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(4.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(block))
            Column(Modifier.padding(8.dp)) {
                Box(Modifier.fillMaxWidth().height(12.dp).background(block, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(12.dp).background(block, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(10.dp).background(block, RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** 下拉刷新 + 双列视频网格 + 滚动到底自动加载；header 会占满整行显示在列表顶部 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoGrid(state: Paged<Video>, onOpen: (Video) -> Unit, modifier: Modifier = Modifier, header: (LazyGridScope.() -> Unit)? = null) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = state::refresh, modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            header?.invoke(this)
            if (state.items.isEmpty() && state.loading) {
                items(SKELETON_COUNT) { SkeletonCard() }
                return@LazyVerticalGrid
            }
            itemsIndexed(state.items, key = { _, v -> v.id }) { i, v ->
                if (i >= state.items.size - 6) LaunchedEffect(state.items.size) { state.loadMore() }
                VideoCard(v) { onOpen(v) }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { ListFooter(state) }
        }
    }
}

/** 列表底部：只要它出现在屏幕上且还有下一页，就自动继续加载（本地过滤导致一屏不满时也能续上） */
@Composable
fun ListFooter(state: Paged<*>) {
    if (!state.end && state.error == null) LaunchedEffect(state.loadCount) { state.loadMore() }
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("加载失败：${state.error}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                TextButton(onClick = state::retry) { Text("重试") }
            }
            state.loading && !state.refreshing -> CircularProgressIndicator()
            state.end && state.items.isNotEmpty() -> Text("没有更多了", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.end -> Text("没有结果", fontSize = 13.sp)
        }
    }
}
