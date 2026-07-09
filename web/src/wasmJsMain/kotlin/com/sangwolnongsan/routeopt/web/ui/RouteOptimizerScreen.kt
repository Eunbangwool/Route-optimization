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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sangwolnongsan.routeopt.web.osrm.OsrmClient
import com.sangwolnongsan.routeopt.web.route.RouteProvider
import com.sangwolnongsan.routeopt.web.route.Suggestion
import com.sangwolnongsan.routeopt.web.tmap.TmapClient
import com.sangwolnongsan.routeopt.web.tmap.lsGet
import com.sangwolnongsan.routeopt.web.tmap.lsSet
import com.sangwolnongsan.routeopt.web.tmap.encodeURIComponent
import com.sangwolnongsan.routeopt.web.tmap.roShowMap
import com.sangwolnongsan.routeopt.web.util.openUrl
import com.sangwolnongsan.routeopt.web.vworld.VWorldSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private const val KEY_STORE = "tmap_appkey"

private enum class Provider { TMAP, OSM }

/** 검색어·선택결과를 담는 주소 행. 각 필드는 상태라 변경 시 재구성된다. */
private class AddressRow {
    var query by mutableStateOf("")
    var picked by mutableStateOf<Suggestion?>(null)
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
}

@Composable
fun RouteOptimizerScreen() {
    val scope = rememberCoroutineScope()

    var provider by remember { mutableStateOf(Provider.OSM) }
    var appKey by remember { mutableStateOf(lsGet(KEY_STORE)) }
    val rows = remember { mutableStateListOf(AddressRow(), AddressRow()) }
    var roundTrip by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<OptimizedRoute?>(null) }

    // 엔진/키 바뀌면 검색 클라이언트 재생성
    val client: RouteProvider = remember(provider, appKey) {
        if (provider == Provider.OSM) OsrmClient() else TmapClient(appKey.trim())
    }

    fun runOptimize() {
        error = null
        result = null
        val osm = provider == Provider.OSM
        if (!osm && appKey.trim().isEmpty()) {
            error = "티맵 앱키를 입력하거나 OSM 무료 모드를 선택하세요."; return
        }
        // 검색 후 선택(picked)된 지점만 사용
        val places = rows.mapIndexedNotNull { i, row ->
            row.picked?.let { Place(id = "p$i", address = it.label, coord = it.coord, resolvedName = it.sub) }
        }
        if (places.size < 2) {
            error = "검색해서 목록에서 주소를 2개 이상 선택하세요."; return
        }
        busy = true
        scope.launch {
            try {
                val start = places.first()
                val end = if (roundTrip) start else places.last()
                val vias = if (roundTrip) places.drop(1) else places.subList(1, places.size - 1).toList()

                status = "최적 경로 계산 중…"
                result = if (vias.isEmpty()) {
                    StraightLineOptimizer.optimize(start, emptyList(), end)
                } else {
                    try {
                        client.optimize(start, vias, end, roundTrip)
                    } catch (e: Throwable) {
                        error = "${if (osm) "OSRM" else "티맵"} 최적화 실패 → 직선거리 기준으로 대체했습니다. (${e.message})"
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
                Text("여러 주소의 방문 순서를 실도로 기준으로 최적화합니다.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))

                // 엔진 선택
                Text("경로 엔진", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = provider == Provider.TMAP,
                        onClick = { provider = Provider.TMAP },
                        label = { Text("티맵 (키 필요·정확)") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = provider == Provider.OSM,
                        onClick = { provider = Provider.OSM },
                        label = { Text("OSM 무료 (키 불필요)") },
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (provider == Provider.TMAP) {
                    OutlinedTextField(
                        value = appKey,
                        onValueChange = { appKey = it; lsSet(KEY_STORE, it.trim()) },
                        label = { Text("티맵 앱키 (SK open API AppKey)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        "주소 검색은 VWorld(국토부, 도로명·지번 정확)로 하고, 경로 최적화만 OSRM 공개 서버(무료)로 처리합니다. " +
                            "OSRM은 데모 서버라 다소 느릴 수 있습니다. (티맵 선택 시 최적화가 티맵 실도로로 바뀝니다)",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text("방문 주소 (검색 후 선택)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                rows.forEachIndexed { i, row ->
                    val badge = when {
                        i == 0 -> "출발"
                        !roundTrip && i == rows.lastIndex -> "도착"
                        else -> "$i"
                    }
                    AddressRowItem(
                        row = row,
                        badge = badge,
                        canDelete = rows.size > 2,
                        onDelete = { if (rows.size > 2) rows.removeAt(i) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { rows.add(AddressRow()) }) {
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
private fun AddressRowItem(
    row: AddressRow,
    badge: String,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    // 디바운스 검색: query 변경 시 VWorld 주소검색 (picked 상태면 검색 안 함)
    LaunchedEffect(row.query) {
        if (row.picked != null) return@LaunchedEffect
        val q = row.query.trim()
        if (q.length < 2) { row.suggestions = emptyList(); row.searchError = null; return@LaunchedEffect }
        delay(400)
        row.searching = true
        runCatching { VWorldSearch.search(q) }
            .onSuccess { row.suggestions = it; row.searchError = if (it.isEmpty()) "검색 결과가 없습니다." else null }
            .onFailure { row.suggestions = emptyList(); row.searchError = "검색 실패: ${it.message}" }
        row.searching = false
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp)) {
                Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = row.query,
                onValueChange = { row.query = it; row.picked = null },
                placeholder = { Text("주소·장소 검색") },
                singleLine = true,
                trailingIcon = if (row.searching) {
                    { CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) }
                } else null,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Default.Close, contentDescription = "삭제")
            }
        }

        if (row.picked != null) {
            Text(
                "✓ 선택됨" + (row.picked!!.sub?.let { " · $it" } ?: ""),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 44.dp, top = 2.dp),
            )
        } else if (row.searchError != null) {
            Text(
                row.searchError!!,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 44.dp, top = 2.dp),
            )
        } else if (row.suggestions.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    row.suggestions.forEachIndexed { idx, s ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    row.picked = s
                                    row.query = s.label
                                    row.suggestions = emptyList()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(s.label, fontSize = 14.sp)
                            s.sub?.let {
                                Text(it, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            }
                        }
                        if (idx < row.suggestions.lastIndex) HorizontalDivider()
                    }
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
                when (r.source) {
                    RouteSource.TMAP -> "티맵 실도로 기준"
                    RouteSource.OSRM -> "OSRM / OSM 실도로 기준 (무료)"
                    RouteSource.STRAIGHT_LINE -> "직선거리 기준 (근사치)"
                },
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

            // 외부 공유: 최적 순서를 국내 지도앱으로 넘기기 (API 키 불필요, 모바일 앱 스킴)
            val withCoords = r.orderedPlaces.filter { it.coord != null }
            if (withCoords.size >= 2) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("지도앱으로 안내 시작", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                naverUrl(r)?.let { url ->
                    Button(onClick = { openUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("네이버 지도로 열기 (경유지 포함)")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    tmapUrl(r)?.let { url ->
                        OutlinedButton(onClick = { openUrl(url) }, modifier = Modifier.weight(1f)) {
                            Text("티맵 (도착지)")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    kakaoUrl(r)?.let { url ->
                        OutlinedButton(onClick = { openUrl(url) }, modifier = Modifier.weight(1f)) {
                            Text("카카오맵 (출발·도착)")
                        }
                    }
                }
                val midCount = withCoords.size - 2
                Text(
                    buildString {
                        append("모바일에서 해당 앱 설치 시 동작합니다 (API 키 불필요).")
                        if (midCount > 5) append(" 네이버는 경유지 5개까지만 전달됩니다(현재 $midCount 개).")
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun enc(s: String): String = encodeURIComponent(s)

/** 네이버 지도 앱 스킴: 출발 + 경유지(최대 5) + 도착. 좌표 2개 미만이면 null. */
private fun naverUrl(r: OptimizedRoute): String? {
    val pts = r.orderedPlaces.filter { it.coord != null }
    if (pts.size < 2) return null
    val s = pts.first().coord!!
    val d = pts.last().coord!!
    val sb = StringBuilder("nmap://route/car?")
    sb.append("slat=${s.lat}&slng=${s.lon}&sname=${enc(pts.first().address)}")
    pts.subList(1, pts.size - 1).take(5).forEachIndexed { i, p ->
        val n = i + 1
        val c = p.coord!!
        sb.append("&v${n}lat=${c.lat}&v${n}lng=${c.lon}&v${n}name=${enc(p.address)}")
    }
    sb.append("&dlat=${d.lat}&dlng=${d.lon}&dname=${enc(pts.last().address)}")
    sb.append("&appname=com.sangwolnongsan.routeopt")
    return sb.toString()
}

/** 티맵 앱 스킴: 최종 목적지 안내. */
private fun tmapUrl(r: OptimizedRoute): String? {
    val d = r.orderedPlaces.lastOrNull { it.coord != null } ?: return null
    val c = d.coord!!
    return "tmap://route?goalname=${enc(d.address)}&goalx=${c.lon}&goaly=${c.lat}"
}

/** 카카오맵 앱 스킴: 출발→도착(경유지 미지원). */
private fun kakaoUrl(r: OptimizedRoute): String? {
    val pts = r.orderedPlaces.filter { it.coord != null }
    if (pts.size < 2) return null
    val s = pts.first().coord!!
    val d = pts.last().coord!!
    return "kakaomap://route?sp=${s.lat},${s.lon}&ep=${d.lat},${d.lon}&by=CAR"
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
private data class MapPayload(
    val engine: String,
    val markers: List<MapMarker>,
    val path: List<List<Double>>,
)

private val mapJson = Json { encodeDefaults = true }

private fun showMap(r: OptimizedRoute) {
    val markers = r.orderedPlaces.mapIndexedNotNull { i, p ->
        p.coord?.let { MapMarker(it.lat, it.lon, "${i + 1}. ${p.address}") }
    }
    val path = r.polyline.map { listOf(it.lon, it.lat) }
    // TMAP 결과는 티맵 JS SDK(키 필요), 그 외(OSRM 등)는 Leaflet+OSM 타일(키 불필요)로 렌더
    val engine = if (r.source == RouteSource.TMAP) "tmap" else "osm"
    roShowMap(mapJson.encodeToString(MapPayload.serializer(), MapPayload(engine, markers, path)))
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
