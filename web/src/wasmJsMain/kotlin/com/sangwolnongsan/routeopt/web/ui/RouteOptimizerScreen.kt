package com.sangwolnongsan.routeopt.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.material3.TextButton
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
import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteSource
import com.sangwolnongsan.routeopt.optimize.StraightLineOptimizer
import com.sangwolnongsan.routeopt.web.data.SavedPlace
import com.sangwolnongsan.routeopt.web.data.SavedRoute
import com.sangwolnongsan.routeopt.web.data.SavedRoutes
import com.sangwolnongsan.routeopt.web.osrm.OsrmClient
import com.sangwolnongsan.routeopt.web.route.RouteProvider
import com.sangwolnongsan.routeopt.web.route.Suggestion
import com.sangwolnongsan.routeopt.web.tmap.encodeURIComponent
import com.sangwolnongsan.routeopt.web.tmap.lsGet
import com.sangwolnongsan.routeopt.web.tmap.lsSet
import com.sangwolnongsan.routeopt.web.tmap.roShowMap
import com.sangwolnongsan.routeopt.web.util.isMobileDevice
import com.sangwolnongsan.routeopt.web.util.jsConfirm
import com.sangwolnongsan.routeopt.web.util.jsPrompt
import com.sangwolnongsan.routeopt.web.util.openUrl
import com.sangwolnongsan.routeopt.web.vworld.VWorldSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

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

    val rows = remember { mutableStateListOf(AddressRow(), AddressRow()) }
    var roundTrip by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<OptimizedRoute?>(null) }
    // 여러 경로 후보 중 사용자가 고른 것 (0 = 추천). result 가 바뀌면 0 으로 초기화.
    var selectedAlt by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(SavedRoutes.load()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var osrmBase by remember { mutableStateOf(lsGet("osrm_base")) }
    var tileUrl by remember { mutableStateOf(lsGet("tile_url")) }

    // 최적화 엔진: OSRM(무료). 검색은 VWorld(엔진 무관).
    val client: RouteProvider = remember { OsrmClient() }

    fun pickedPlaces(): List<SavedPlace> = rows.mapNotNull { row ->
        row.picked?.let { SavedPlace(it.label, it.sub, it.coord.lat, it.coord.lon) }
    }

    fun saveCurrent() {
        error = null
        val places = pickedPlaces()
        if (places.size < 2) { error = "저장하려면 지점을 2개 이상 선택하세요."; return }
        val name = jsPrompt("저장할 경로 이름", "경로 ${saved.size + 1}").trim()
        if (name.isEmpty()) return
        // 같은 이름은 덮어쓰기
        val next = listOf(SavedRoute(name, roundTrip, places)) + saved.filterNot { it.name == name }
        SavedRoutes.persist(next)
        saved = next
    }

    fun loadRoute(sr: SavedRoute) {
        error = null
        result = null
        rows.clear()
        sr.places.forEach { sp ->
            rows.add(AddressRow().apply {
                picked = Suggestion(sp.label, sp.sub, LatLng(sp.lat, sp.lon))
                query = sp.label
            })
        }
        if (rows.size < 2) { rows.add(AddressRow()); rows.add(AddressRow()) }
        roundTrip = sr.roundTrip
    }

    fun deleteRoute(sr: SavedRoute) {
        if (!jsConfirm("‘${sr.name}’ 경로를 삭제할까요?")) return
        val next = saved.filterNot { it === sr || it.name == sr.name }
        SavedRoutes.persist(next)
        saved = next
    }

    fun runOptimize() {
        error = null
        result = null
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
                // 경유지가 없어도(A→B) 실도로 경로·대안 후보를 얻기 위해 OSRM 을 호출한다.
                // 실패 시에만 직선거리로 대체.
                selectedAlt = 0
                result = try {
                    client.optimize(start, vias, end, roundTrip)
                } catch (e: Throwable) {
                    error = "실도로 최적화 실패 → 직선거리 기준으로 대체했습니다. (${e.message})"
                    StraightLineOptimizer.optimize(start, vias, end)
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
                Text("여러 주소의 방문 순서를 실도로 기준으로 최적화합니다. (키 불필요)",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { saveCurrent() }, modifier = Modifier.fillMaxWidth()) {
                    Text("현재 경로 저장")
                }

                if (saved.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("저장된 경로", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    saved.forEach { sr ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sr.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${sr.places.size}개 지점" + if (sr.roundTrip) " · 왕복" else "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    )
                                }
                                TextButton(onClick = { loadRoute(sr) }) { Text("불러오기") }
                                IconButton(onClick = { deleteRoute(sr) }) {
                                    Icon(Icons.Default.Close, contentDescription = "삭제")
                                }
                            }
                        }
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
                    ResultCard(r, selectedAlt) { selectedAlt = it }
                }

                // 고급 설정: 상용 시 공개 데모 서버 대신 자체 인프라를 가리키게 함
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "고급 설정 닫기" else "고급 설정 (자체 서버·상용)")
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = osrmBase,
                        onValueChange = { osrmBase = it; lsSet("osrm_base", it.trim()) },
                        label = { Text("OSRM 서버 주소 (경로 최적화)") },
                        placeholder = { Text("https://osrm.mycompany.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tileUrl,
                        onValueChange = { tileUrl = it; lsSet("tile_url", it.trim()) },
                        label = { Text("지도 타일 URL") },
                        placeholder = { Text("https://tiles.example.com/{z}/{x}/{y}.png") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append("비워두면 공개 데모 서버(OSRM·OSM 타일)를 쓰는데, 이는 ")
                            append("개발/테스트용이며 상업적 사용이 금지됩니다. 상용 배포 시 ")
                            append("자체 호스팅 OSRM 주소와 상용 타일 URL을 넣으세요. (검색·주소는 VWorld 사용)")
                        },
                        fontSize = 11.sp,
                        color = if (osrmBase.isBlank())
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f)
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
                            // 후보의 지도상 위치 미리보기 (선택 전 위치 확인)
                            IconButton(onClick = { showSuggestionOnMap(s) }) {
                                Icon(Icons.Default.Place, contentDescription = "지도에서 위치 보기",
                                    tint = MaterialTheme.colorScheme.primary)
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
private fun ResultCard(routeSet: OptimizedRoute, selectedIndex: Int, onSelect: (Int) -> Unit) {
    // 후보 목록: alternatives 가 있으면 그것을, 없으면 단일 경로.
    val alts = routeSet.alternatives.ifEmpty { listOf(routeSet) }
    val sel = alts[selectedIndex.coerceIn(0, alts.lastIndex)]
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            val mobile = remember { isMobileDevice() }
            var navApp by remember { mutableStateOf(NavApp.NAVER) }

            // 경로 선택지 (후보 2개 이상일 때만)
            if (alts.size > 1) {
                RouteOptions(alts, selectedIndex.coerceIn(0, alts.lastIndex), onSelect)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("총 거리", formatKm(sel.totalDistanceMeters))
                Metric("예상 시간", formatDuration(sel.totalTimeSeconds))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (sel.source) {
                    RouteSource.TMAP -> "티맵 실도로 기준"
                    RouteSource.OSRM ->
                        if (sel.exactOrder) "OSM 실도로 시간행렬 · 방문순서 전역 최적 보장"
                        else "OSM 실도로 기준 (휴리스틱 최적화)"
                    RouteSource.STRAIGHT_LINE ->
                        if (sel.exactOrder) "직선거리 기준 근사 · 순서는 직선거리상 최적"
                        else "직선거리 기준 (근사치)"
                },
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))

            // 각 지점 '안내' 버튼에 사용할 내비 앱 선택 (구간별 릴레이)
            Text("내비 앱 (각 지점 ‘안내’)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                FilterChip(navApp == NavApp.NAVER, { navApp = NavApp.NAVER }, { Text("네이버") })
                Spacer(Modifier.width(6.dp))
                FilterChip(navApp == NavApp.TMAP, { navApp = NavApp.TMAP }, { Text("티맵") })
                Spacer(Modifier.width(6.dp))
                FilterChip(navApp == NavApp.KAKAO, { navApp = NavApp.KAKAO }, { Text("카카오") })
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            sel.orderedPlaces.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
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
                    navToUrl(p, navApp, mobile)?.let { url ->
                        OutlinedButton(
                            onClick = { openUrl(url) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) { Text("안내", fontSize = 13.sp) }
                    }
                }
                if (i < sel.legs.size) {
                    val leg = sel.legs[i]
                    Text(
                        "↓ ${formatKm(leg.distanceMeters)} · ${formatDuration(leg.timeSeconds)}",
                        fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
            }

            if (sel.polyline.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showMap(alts, selectedIndex.coerceIn(0, alts.lastIndex)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (alts.size > 1) "지도에서 경로 비교" else "지도에서 경로 보기")
                }
            }

            // 전체 경로 한 번에 열기 — 네이버만 경유지 지원(모바일 최대 5, 데스크톱 웹)
            val withCoords = sel.orderedPlaces.filter { it.coord != null }
            if (withCoords.size >= 2) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("전체 경로 한 번에 열기 (네이버)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                naverUrl(sel, mobile)?.let { url ->
                    Button(onClick = { openUrl(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mobile) "네이버 지도로 열기 (경유지 포함)" else "네이버 지도 웹으로 열기")
                    }
                }
                val midCount = withCoords.size - 2
                Text(
                    buildString {
                        append("여러 곳을 한 번에 넣는 건 네이버만 지원하며 경유지 최대 5개입니다")
                        if (midCount > 5) append(" (현재 $midCount 개 — 초과분은 잘림)")
                        append(". 경유지가 많으면 위의 지점별 ‘안내’로 한 곳씩 이동하세요.")
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 경로 후보 선택지. 각 후보의 거리·시간을 보여주고, 가장 빠른/짧은 후보에 배지를 단다.
 * 탭하면 [onSelect] 로 인덱스를 알린다.
 */
@Composable
private fun RouteOptions(alts: List<OptimizedRoute>, selected: Int, onSelect: (Int) -> Unit) {
    val minTimeIdx = alts.indices.minByOrNull { alts[it].totalTimeSeconds }
    val minDistIdx = alts.indices.minByOrNull { alts[it].totalDistanceMeters }
    Text("경로 선택 (${alts.size}개)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(6.dp))
    alts.forEachIndexed { i, a ->
        val isSel = i == selected
        val badges = listOfNotNull(
            if (i == minTimeIdx) "최소 시간" else null,
            if (i == minDistIdx) "최단 거리" else null,
        )
        Card(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(i) },
            colors = CardDefaults.cardColors(
                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.label ?: "경로 ${i + 1}", fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    )
                    badges.forEach { b ->
                        Spacer(Modifier.width(6.dp))
                        Text(b, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "${formatKm(a.totalDistanceMeters)} · ${formatDuration(a.totalTimeSeconds)}",
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private enum class NavApp { NAVER, TMAP, KAKAO }

private fun enc(s: String): String = encodeURIComponent(s)

/**
 * 구간별 릴레이: 현재위치 → 특정 지점 단일 목적지 안내.
 * 모바일=앱 스킴, 데스크톱=웹 지도(위치 표시). 경유지 개수 제약 없음.
 */
private fun navToUrl(p: Place, app: NavApp, mobile: Boolean): String? {
    val c = p.coord ?: return null
    val name = enc(p.address)
    return when (app) {
        NavApp.NAVER ->
            if (mobile) "nmap://navigation?dlat=${c.lat}&dlng=${c.lon}&dname=$name&appname=com.sangwolnongsan.routeopt"
            else "https://map.naver.com/p/search/$name"
        NavApp.TMAP ->
            if (mobile) "tmap://route?goalname=$name&goalx=${c.lon}&goaly=${c.lat}"
            else "https://map.kakao.com/link/to/$name,${c.lat},${c.lon}" // 티맵 웹 지도 없음 → 위치만 표시
        NavApp.KAKAO ->
            if (mobile) "kakaomap://route?ep=${c.lat},${c.lon}&by=CAR"
            else "https://map.kakao.com/link/to/$name,${c.lat},${c.lon}"
    }
}

/**
 * 네이버 지도. 모바일=앱 스킴(출발+경유지 최대5+도착), 데스크톱=웹 길찾기(출발→도착).
 */
private fun naverUrl(r: OptimizedRoute, mobile: Boolean): String? {
    val pts = r.orderedPlaces.filter { it.coord != null }
    if (pts.size < 2) return null
    val s = pts.first().coord!!
    val d = pts.last().coord!!
    val sName = pts.first().address
    val dName = pts.last().address
    if (mobile) {
        val sb = StringBuilder("nmap://route/car?")
        sb.append("slat=${s.lat}&slng=${s.lon}&sname=${enc(sName)}")
        pts.subList(1, pts.size - 1).take(5).forEachIndexed { i, p ->
            val n = i + 1
            val c = p.coord!!
            sb.append("&v${n}lat=${c.lat}&v${n}lng=${c.lon}&v${n}name=${enc(p.address)}")
        }
        sb.append("&dlat=${d.lat}&dlng=${d.lon}&dname=${enc(dName)}")
        sb.append("&appname=com.sangwolnongsan.routeopt")
        return sb.toString()
    }
    // 데스크톱 웹 길찾기: 경도,위도,명칭 순
    return "https://map.naver.com/p/directions/" +
        "${s.lon},${s.lat},${enc(sName)},,/" +
        "${d.lon},${d.lat},${enc(dName)},,/-/car"
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
    /** 선택되지 않은 대안 경로들 (지도에 흐리게 표시). */
    val altPaths: List<List<List<Double>>> = emptyList(),
)

private val mapJson = Json { encodeDefaults = true }

/** 후보 목록과 선택 인덱스를 받아, 선택 경로는 강조·나머지는 흐리게 지도에 그린다. */
private fun showMap(alts: List<OptimizedRoute>, selectedIndex: Int) {
    val idx = selectedIndex.coerceIn(0, alts.lastIndex)
    val sel = alts[idx]
    val markers = sel.orderedPlaces.mapIndexedNotNull { i, p ->
        p.coord?.let { MapMarker(it.lat, it.lon, "${i + 1}. ${p.address}") }
    }
    val path = sel.polyline.map { listOf(it.lon, it.lat) }
    val altPaths = alts.filterIndexed { i, _ -> i != idx }
        .map { a -> a.polyline.map { listOf(it.lon, it.lat) } }
        .filter { it.size > 1 }
    // TMAP 결과는 티맵 JS SDK(키 필요), 그 외(OSRM 등)는 Leaflet+OSM 타일(키 불필요)로 렌더
    val engine = if (sel.source == RouteSource.TMAP) "tmap" else "osm"
    roShowMap(mapJson.encodeToString(MapPayload.serializer(),
        MapPayload(engine, markers, path, altPaths)))
}

/** 검색 후보 한 지점만 지도(OSM)에 표시 — 선택 전 위치 확인용. */
private fun showSuggestionOnMap(s: Suggestion) {
    val marker = MapMarker(s.coord.lat, s.coord.lon, s.label)
    roShowMap(mapJson.encodeToString(MapPayload.serializer(), MapPayload("osm", listOf(marker), emptyList())))
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
