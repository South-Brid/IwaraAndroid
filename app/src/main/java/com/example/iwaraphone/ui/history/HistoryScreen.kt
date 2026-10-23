package com.example.iwaraphone.ui.history

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iwaraphone.data.local.HistoryEntry
import com.example.iwaraphone.data.local.HistoryStore
import com.example.iwaraphone.ui.common.RemoteImage
import com.example.iwaraphone.ui.video.formatTime

@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val entries by HistoryStore.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("观看历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("清空") }
        }
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.History, null, Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("还没有观看记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { e -> HistoryRow(e, onClick = { onOpen(e.id) }, onRemove = { HistoryStore.remove(e.id) }) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空观看历史？") },
            text = { Text("清空后无法恢复。") },
            confirmButton = { TextButton(onClick = { HistoryStore.clear(); confirmClear = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    val durationMs = e.durationSec * 1000L
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
            RemoteImage(e.thumbnail, Modifier.fillMaxSize())
            if (durationMs > 0 && e.positionMs > 0) {
                LinearProgressIndicator(
                    progress = { (e.positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    trackColor = Color(0x66000000),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(e.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            Text(
                progressText(e, durationMs) + " · " + DateUtils.getRelativeTimeSpanString(e.watchedAt),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, "删除记录", Modifier.background(Color.Transparent), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun progressText(e: HistoryEntry, durationMs: Long) = when {
    e.positionMs <= 0 -> "已看完或未开始"
    durationMs > 0 -> "看到 ${formatTime(e.positionMs)} / ${formatTime(durationMs)}"
    else -> "看到 ${formatTime(e.positionMs)}"
}
