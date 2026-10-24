package com.example.iwaraphone.ui.debug

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iwaraphone.bridge.WebBridge
import com.example.iwaraphone.data.VideoRepository
import com.example.iwaraphone.data.model.IMG
import com.example.iwaraphone.data.model.objects
import com.example.iwaraphone.data.model.parseSources
import com.example.iwaraphone.data.remote.ApiClient
import com.example.iwaraphone.data.remote.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class TestResult(val name: String, val url: String, val status: Int, val ms: Long, val body: String)

/** 接口调试：逐个调用接口，结果保存到 files/api_dump/ 便于整理文档（不保存令牌和个人信息） */
class DebugViewModel : ViewModel() {
    val results = mutableStateListOf<TestResult>()
    var running by mutableStateOf(false)
        private set

    fun runAll(ctx: Context) {
        if (running) return
        running = true
        results.clear()
        val dir = File(ctx.filesDir, "api_dump").apply { deleteRecursively(); mkdirs() }
        viewModelScope.launch {
            try {
                runCases(dir)
            } finally {
                running = false
            }
        }
    }

    private suspend fun runCases(dir: File) {
        var videoId: String? = null
        var authorId: String? = null
        var fileUrl: String? = null
        var thumbUrl: String? = null
        var sourceUrl: String? = null

        record(dir, "01_localStorage_keys", "localStorage") {
            200 to JSONObject(WebBridge.localStorage().mapValues { (_, v) -> "<${v.length} chars>" }).toString(2)
        }
        api(dir, "02_videos_date", ApiClient.url("/videos", mapOf("sort" to "date", "rating" to "all", "page" to 0, "limit" to 4)))?.let {
            val first = JSONObject(it).optJSONArray("results").objects().firstOrNull()
            videoId = first?.optString("id")
            authorId = first?.optJSONObject("user")?.optString("id")
            first?.optJSONObject("file")?.let { f -> thumbUrl = "$IMG/image/thumbnail/${f.optString("id")}/thumbnail-%02d.jpg".format(first.optInt("thumbnail")) }
        }
        api(dir, "03_videos_tags", ApiClient.url("/videos", mapOf("tags" to "hatsune_miku", "sort" to "popularity", "page" to 0, "limit" to 4)))
        api(dir, "04_search_videos", ApiClient.url("/search", mapOf("type" to "videos", "query" to "miku", "page" to 0, "sort" to "relevance")))
        api(dir, "05_autocomplete_tags", ApiClient.url("/autocomplete/tags", mapOf("query" to "miku")))
        videoId?.let { id ->
            api(dir, "06_video_detail", ApiClient.url("/video/$id"))?.let { fileUrl = JSONObject(it).optString("fileUrl").ifBlank { null } }
            api(dir, "07_video_comments", ApiClient.url("/video/$id/comments", mapOf("page" to 0)))
        }
        authorId?.let { uid ->
            api(dir, "08_user_videos", ApiClient.url("/videos", mapOf("user" to uid, "sort" to "date", "page" to 0, "limit" to 4)))
            api(dir, "09_user_following", ApiClient.url("/user/$uid/following", mapOf("page" to 0, "limit" to 4)))
        }
        fileUrl?.let { f ->
            api(dir, "10_file_sources", f, mapOf("X-Version" to VideoRepository.xVersion(f)))?.let { sourceUrl = parseSources(JSONArray(it)).lastOrNull()?.url }
        }
        api(dir, "11_subscribed_feed", ApiClient.url("/videos", mapOf("subscribed" to "true", "sort" to "date", "page" to 0, "limit" to 4)))
        record(dir, "12_user_login_state", ApiClient.url("/user")) {
            val r = ApiClient.request(ApiClient.url("/user"))
            val keys = runCatching { JSONObject(r.body).keys().asSequence().toList() }.getOrDefault(emptyList())
            r.status to "顶层字段（不保存个人信息）：$keys"
        }
        thumbUrl?.let { u -> record(dir, "13_native_thumbnail", u) { nativeProbe(u) } }
        sourceUrl?.let { u -> record(dir, "14_native_video_range", u) { nativeProbe(u, range = true) } }
    }

    private suspend fun api(dir: File, name: String, url: String, headers: Map<String, String> = emptyMap()): String? {
        var ok: String? = null
        record(dir, name, url) {
            val r = ApiClient.request(url, headers = headers)
            if (r.status in 200..299) ok = r.body
            r.status to r.body
        }
        return ok
    }

    private suspend fun record(dir: File, name: String, url: String, block: suspend () -> Pair<Int, String>) {
        val t0 = System.currentTimeMillis()
        val (status, body) = runCatching { block() }.getOrElse { 0 to "EXCEPTION: $it" }
        val pretty = runCatching {
            if (body.trimStart().startsWith("[")) JSONArray(body).toString(2) else JSONObject(body).toString(2)
        }.getOrDefault(body)
        val res = TestResult(name, url, status, System.currentTimeMillis() - t0, pretty)
        results.add(res)
        withContext(Dispatchers.IO) {
            File(dir, "$name.txt").writeText("URL: ${res.url}\nSTATUS: ${res.status}\nTIME: ${res.ms}ms\n\n${res.body}")
        }
        Log.i("IwaraApiTest", "$name -> $status (${res.ms}ms)")
    }

    /** 不经过浏览器，直接用原生网络请求测试图片 / 视频 CDN 是否可访问 */
    private suspend fun nativeProbe(url: String, range: Boolean = false): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Referer", ApiClient.REFERER)
            setRequestProperty("User-Agent", Network.userAgent)
            if (range) setRequestProperty("Range", "bytes=0-1023")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            c.responseCode to "Content-Type: ${c.contentType}\nContent-Length: ${c.contentLengthLong}\nServer: ${c.getHeaderField("server")}\ncf-mitigated: ${c.getHeaderField("cf-mitigated")}"
        } finally {
            c.disconnect()
        }
    }
}

@Composable
fun DebugScreen(vm: DebugViewModel = viewModel()) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        Text("通道状态：${WebBridge.status}", modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runAll(ctx) }, enabled = !vm.running) { Text(if (vm.running) "测试中…" else "运行全部接口测试") }
            OutlinedButton(onClick = WebBridge::reload) { Text("重连通道") }
        }
        Text("结果保存在应用目录 files/api_dump/", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.results) { ResultCard(it) }
        }
    }
}

@Composable
private fun ResultCard(r: TestResult) {
    var expanded by remember { mutableStateOf(false) }
    val ok = r.status in 200..299
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }) {
        Column(Modifier.padding(10.dp)) {
            Text("${if (ok) "✅" else "❌"} ${r.name}  ·  ${r.status}  ·  ${r.ms}ms", color = if (ok) Color.Unspecified else MaterialTheme.colorScheme.error)
            Text(r.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(if (expanded) r.body.take(6000) else r.body.take(300), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
