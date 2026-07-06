package com.sangwolnongsan.routeopt.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteSource
import com.sangwolnongsan.routeopt.optimize.StraightLineOptimizer
import com.sangwolnongsan.routeopt.web.tmap.TmapClient
import com.sangwolnongsan.routeopt.web.tmap.lsGet
import com.sangwolnongsan.routeopt.web.tmap.lsSet
import com.sangwolnongsan.routeopt.web.tmap.roShowMap
import com.sangwolnongsan.routeopt.web.util.copyToClipboard
import com.sangwolnongsan.routeopt.web.util.openUrl
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private const val KEY_STORE = "tmap_appkey"

@Composable
fun RouteOptimizerScreen() {
    val scope = rememberCoroutineScope()

    var appKey by remember { mutableStateOf(lsGet(KEY_STORE)) }
    val addresses = remember { mutableStateListOf("", "") }
    var roundTrip by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<OptimizedRoute?>(null) }

    fun runOptimize() {
        error = null
        result = null
        val key = appKey.trim()
        val queries = addresses.map { it.trim() }.filter { it.isNotEmpty() }
        when {
            key.isEmpty() -> { error = "티맵 앱키를 입력하세요."; return }
            queries.size < 2 -> { error = "주소를 2개 이상 입력하세요."; return }
        }
        busy = true
        scope.launch {
            try {
                val client = TmapClient(key)
                val places = ArrayList<Place>()
                val failed = ArrayList<String>()
                queries.forEachIndexed { i, q ->
                    status = "주소 변환 중 (${i + 1}/${queries.size}): $q"
                    val geo = client.geocode(q)
                    if (geo == null) {
                        failed.add(q)
                    } else {
                        places.add(Place(id = "p$i", address = q, coord = geo.first, resolvedName = geo.second))
                    }
                }
                if (failed.isNotEmpty()) {
                    error = "좌표를 찾지 못한 주소: ${failed.joinToString(", ")}"
                }
                if (places.size < 2) {
                    busy = false; status = null; return@launch
                }

                val start = places.first()
                val end = if (roundTrip) start else places.last()
                val vias = if (roundTrip) places.drop(1) else places.subList(1, places.size - 1).toList()

                status = "최적 경로 계산 중…"
                result = if (vias.isEmpty()) {
                    // 경유지가 없으면 직선거리로 단일 구간 계산
                    StraightLineOptimizer.optimize(start, emptyList(), end)
                } else {
                    try {
                        client.optimize(start, vias, end, roundTrip)
                    } catch (e: Throwable) {
                        error = "티맵 최적화 실패 → 직선거리 기준으로 대체했습니다. (${e.message})"
                        StraightLineOptimizer.optimize(start, vias, end)
                    }
                }
            } catch (e: Throwable) {
                error = "오류: ${e.message}"
            } finally {
                busy = false
                status = null
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("최단 경로 설계", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text("여러 주소의 방문 순서를 티맵 실도로 기준으로 최적화합니다.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))

                // 앱키
                OutlinedTextField(
                    value = appKey,
                    onValueChange = { appKey = it; lsSet(KEY_STORE, it.trim()) },
                    label = { Text("티맵 앱키 (SK open API AppKey)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                Text("방문 주소", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                addresses.forEachIndexed { i, value ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val badge = when {
                            i == 0 -> "출발"
                            !roundTrip && i == addresses.lastIndex -> "도착"
                            else -> "${i}"
                        }
                        Box(Modifier.width(44.dp)) {
                            Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { addresses[i] = it },
                            placeholder = { Text("주소 또는 장소명") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { if (addresses.size > 2) addresses.removeAt(i) },
                            enabled = addresses.size > 2,
                        ) { Icon(Icons.Default.Close, contentDescription = "삭제") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { addresses.add("") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("주소 추가")
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = roundTrip, onCheckedChange = { roundTrip = it })
                    Text("출발지로 돌아오기 (왕복)")
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { runOptimize() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("최적 경로 계산", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }

                result?.let { r ->
                    Spacer(Modifier.height(20.dp))
                    ResultCard(r)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(r: OptimizedRoute) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("총 거리", formatKm(r.totalDistanceMeters))
                Metric("예상 시간", formatDuration(r.totalTimeSeconds))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (r.source == RouteSource.TMAP) "티맵 실도로 기준" else "직선거리 기준 (근사치)",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            r.orderedPlaces.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(28.dp)) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.address, fontSize = 15.sp)
                        p.resolvedName?.let {
                            Text(it, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                if (i < r.legs.size) {
                    val leg = r.legs[i]
                    Text(
                        "↓ ${formatKm(leg.distanceMeters)} · ${formatDuration(leg.timeSeconds)}",
                        fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
            }

            if (r.polyline.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showMap(r) }, modifier = Modifier.fillMaxWidth()) {
                    Text("지도에서 경로 보기")
                }
            }

            // 외부 공유: 최적 순서를 지도앱으로 넘기는 다중경유 링크 (API 키 불필요)
            googleDirUrl(r)?.let { url ->
                var copied by remember { mutableStateOf(false) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { openUrl(url) }, modifier = Modifier.weight(1f)) {
                        Text("구글맵으로 열기")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { copyToClipboard(url); copied = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (copied) "복사됨 ✓" else "경로 링크 복사") }
                }
                Text(
                    "최적 순서를 외부 지도앱으로 공유합니다 (API 키 불필요). " +
                        "국내 자동차 길안내는 지도앱에 따라 제한될 수 있습니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 최적 순서 좌표로 구글맵 다중경유 길찾기 URL 생성. 좌표가 2개 미만이면 null. */
private fun googleDirUrl(r: OptimizedRoute): String? {
    val coords = r.orderedPlaces.mapNotNull { it.coord }
    if (coords.size < 2) return null
    return "https://www.google.com/maps/dir/" + coords.joinToString("/") { "${it.lat},${it.lon}" }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- 지도 페이로드 ----
@Serializable
private data class MapMarker(val lat: Double, val lon: Double, val label: String)

@Serializable
private data class MapPayload(val markers: List<MapMarker>, val path: List<List<Double>>)

private val mapJson = Json { encodeDefaults = true }

private fun showMap(r: OptimizedRoute) {
    val markers = r.orderedPlaces.mapIndexedNotNull { i, p ->
        p.coord?.let { MapMarker(it.lat, it.lon, "${i + 1}. ${p.address}") }
    }
    val path = r.polyline.map { listOf(it.lon, it.lat) }
    roShowMap(mapJson.encodeToString(MapPayload.serializer(), MapPayload(markers, path)))
}

// ---- 포맷 ----
private fun formatKm(meters: Int): String {
    val km = (meters / 100.0).roundToInt() / 10.0
    return if (km >= 1.0) "$km km" else "$meters m"
}

private fun formatDuration(seconds: Int): String {
    val totalMin = (seconds / 60.0).roundToInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h}시간 ${m}분"
        else -> "${m}분"
    }
}
