package com.sangwolnongsan.routeopt.web.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteSource
import com.sangwolnongsan.routeopt.optimize.StraightLineOptimizer
import com.sangwolnongsan.routeopt.web.data.ProLicense
import com.sangwolnongsan.routeopt.web.data.SavedPlace
import com.sangwolnongsan.routeopt.web.data.SavedRoute
import com.sangwolnongsan.routeopt.web.data.SavedRoutes
import com.sangwolnongsan.routeopt.web.osrm.OsrmClient
import com.sangwolnongsan.routeopt.web.tmap.TmapClient
import com.sangwolnongsan.routeopt.web.route.RouteProvider
import com.sangwolnongsan.routeopt.web.route.Suggestion
import com.sangwolnongsan.routeopt.web.tmap.currentLocation
import com.sangwolnongsan.routeopt.web.tmap.decodeURIComponent
import com.sangwolnongsan.routeopt.web.tmap.encodeURIComponent
import com.sangwolnongsan.routeopt.web.tmap.reverseGeocode
import com.sangwolnongsan.routeopt.web.tmap.locationBaseUrl
import com.sangwolnongsan.routeopt.web.tmap.locationHash
import com.sangwolnongsan.routeopt.web.tmap.lsGet
import com.sangwolnongsan.routeopt.web.tmap.lsSet
import com.sangwolnongsan.routeopt.web.tmap.pickOnMap
import com.sangwolnongsan.routeopt.web.tmap.roShowMap
import com.sangwolnongsan.routeopt.web.tmap.shareUrl
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
    // 공유 링크로 열었을 때 복원할 후보 인덱스 (계산 완료 후 1회 적용).
    var pendingAlt by remember { mutableStateOf(0) }
    var saved by remember { mutableStateOf(SavedRoutes.load()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var osrmBase by remember { mutableStateOf(lsGet("osrm_base")) }
    var tileUrl by remember { mutableStateOf(lsGet("tile_url")) }

    // 경로 엔진: 기본 OSRM(무료·키 불필요). 티맵 PRO 는 유료 이용권 코드 + 앱키가
    // 모두 유효할 때만 활성화. 검색은 VWorld(엔진 무관).
    var engine by remember { mutableStateOf(lsGet("ro_engine").ifBlank { "osrm" }) }
    var proCode by remember { mutableStateOf(lsGet("ro_pro_code")) }
    var tmapKey by remember { mutableStateOf(lsGet("tmap_appkey")) }
    val tmapReady = ProLicense.isValid(proCode) && tmapKey.isNotBlank()
    val effEngine = if (engine == "tmap" && tmapReady) "tmap" else "osrm"
    val client: RouteProvider = remember(effEngine, tmapKey) {
        if (effEngine == "tmap") TmapClient(tmapKey.trim()) else OsrmClient()
    }

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

    fun useCurrentLocation() {
        error = null
        scope.launch {
            status = "현재 위치 확인 중…"
            val loc = runCatching { currentLocation() }.getOrElse { "err:${it.message}" }
            if (loc.startsWith("err:")) {
                status = null
                error = "현재 위치를 가져오지 못했습니다: ${loc.removePrefix("err:")}"
                return@launch
            }
            val parts = loc.split(",")
            val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lon = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (lat == null || lon == null) { status = null; error = "위치 형식 오류"; return@launch }
            val addr = runCatching { reverseGeocode(lat, lon) }.getOrDefault("")
            val label = addr.ifBlank { "현재 위치" }
            // 첫 행(출발지)에 채운다
            rows[0].picked = Suggestion(label = label, sub = "📍 현재 위치", coord = LatLng(lat, lon))
            rows[0].query = label
            status = null
        }
    }

    fun pickFromMap() {
        error = null
        scope.launch {
            val res = runCatching { pickOnMap() }.getOrDefault("")
            if (res.isBlank()) return@launch // 취소/닫기
            val loc = runCatching { pickJson.decodeFromString(PickedLoc.serializer(), res) }.getOrNull()
                ?: run { error = "지도 위치를 읽지 못했습니다."; return@launch }
            val label = loc.address.ifBlank { "지도 선택 위치" }
            // 빈 행이 있으면 채우고, 없으면 새 행 추가
            val target = rows.firstOrNull { it.picked == null && it.query.isBlank() }
                ?: AddressRow().also { rows.add(it) }
            target.picked = Suggestion(label, "📍 지도 선택", LatLng(loc.lat, loc.lon))
            target.query = label
        }
    }

    fun resetAll() {
        val hasInput = rows.any { it.picked != null || it.query.isNotBlank() } || result != null
        if (hasInput && !jsConfirm("입력한 주소와 계산 결과를 모두 지울까요?")) return
        rows.clear()
        rows.add(AddressRow()); rows.add(AddressRow())
        roundTrip = false
        result = null
        selectedAlt = 0
        pendingAlt = 0
        error = null
        status = null
    }

    fun shareCurrent() {
        error = null
        val places = pickedPlaces()
        if (places.size < 2) { error = "공유하려면 지점을 2개 이상 선택하세요."; return }
        val payload = SharePayload(roundTrip = roundTrip, alt = selectedAlt, places = places)
        val url = locationBaseUrl() + "#r=" +
            encodeURIComponent(shareJson.encodeToString(SharePayload.serializer(), payload))
        scope.launch {
            when (shareUrl(url)) {
                "copied" -> {
                    status = "공유 링크를 클립보드에 복사했습니다. 붙여넣기 하세요."
                    delay(4000)
                    if (status?.startsWith("공유 링크") == true) status = null
                }
                "failed" -> error = "공유 실패 — 링크를 복사할 수 없습니다."
                else -> {} // shared(공유시트 완료) / cancelled(사용자 취소)
            }
        }
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
                // 공유 링크 복원: 보낸 사람이 골랐던 경로 후보를 다시 선택
                val altCount = result?.alternatives?.size ?: 0
                if (altCount > 1) selectedAlt = pendingAlt.coerceIn(0, altCount - 1)
                pendingAlt = 0
            } catch (e: Throwable) {
                error = "오류: ${e.message}"
            } finally {
                busy = false
                status = null
            }
        }
    }

    // 공유 링크(#r=…)로 열린 경우: 지점·설정 복원 후 자동 재계산 (앱 시작 시 1회)
    LaunchedEffect(Unit) {
        val h = locationHash()
        if (!h.startsWith("#r=")) return@LaunchedEffect
        runCatching {
            val d = shareJson.decodeFromString(
                SharePayload.serializer(), decodeURIComponent(h.removePrefix("#r=")))
            if (d.places.size < 2) return@LaunchedEffect
            rows.clear()
            d.places.forEach { sp ->
                rows.add(AddressRow().apply {
                    picked = Suggestion(sp.label, sp.sub, LatLng(sp.lat, sp.lon))
                    query = sp.label
                })
            }
            roundTrip = d.roundTrip
            pendingAlt = d.alt
            runOptimize()
        }.onFailure { error = "공유 링크를 읽지 못했습니다. (링크가 잘렸는지 확인하세요)" }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppHeader()
                Spacer(Modifier.height(18.dp))

              Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              ) {
               Column(Modifier.padding(18.dp)) {
                SectionLabel("방문 주소", "검색 후 목록에서 선택")
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { rows.add(AddressRow()) },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("추가")
                    }
                    TextButton(onClick = { useCurrentLocation() },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("내 위치")
                    }
                    TextButton(onClick = { pickFromMap() },
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("지도")
                    }
                }

                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = roundTrip, onCheckedChange = { roundTrip = it })
                    Text("출발지로 돌아오기 (왕복)", fontSize = 14.sp)
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))

                // 경로 엔진 선택 — 티맵 PRO 는 유료 이용권 게이트
                SectionLabel("경로 엔진")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(effEngine == "osrm", {
                        engine = "osrm"; lsSet("ro_engine", "osrm")
                    }, { Text("기본 (무료)") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(effEngine == "tmap", {
                        if (tmapReady) {
                            engine = "tmap"; lsSet("ro_engine", "tmap"); error = null
                        } else {
                            showAdvanced = true
                            error = "티맵 PRO 는 유료 이용권 전용입니다. 고급 설정에 이용권 코드와 티맵 앱키를 입력하세요."
                        }
                    }, { Text(if (tmapReady) "티맵 PRO" else "티맵 PRO 🔒") })
                }
                if (effEngine == "tmap") {
                    Text(
                        "실시간 교통 반영 · 추천/무료우선/최단거리 경로 옵션 제공",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { runOptimize() },
                    enabled = !busy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("최적 경로 계산", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { saveCurrent() }, modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium) {
                        Text("현재 경로 저장")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { resetAll() }, modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium) {
                        Text("경로 초기화")
                    }
                }
               }
              }

                if (saved.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel("저장된 경로")
                    Spacer(Modifier.height(8.dp))
                    saved.forEach { sr ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    Spacer(Modifier.height(12.dp))
                    Banner(it, isError = false)
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Banner(it, isError = true)
                }

                result?.let { r ->
                    Spacer(Modifier.height(20.dp))
                    ResultCard(r, selectedAlt, onSelect = { selectedAlt = it },
                        onShare = { shareCurrent() })
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

                    // 티맵 PRO (유료 이용권): 코드 + 앱키 둘 다 유효해야 열림
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proCode,
                        onValueChange = { proCode = it; lsSet("ro_pro_code", it.trim()) },
                        label = { Text("이용권 코드 (티맵 PRO)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tmapKey,
                        onValueChange = { tmapKey = it; lsSet("tmap_appkey", it.trim()) },
                        label = { Text("티맵 앱키") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            tmapReady -> "티맵 PRO 사용 가능 ✓ — 위 ‘경로 엔진’에서 티맵 PRO 를 선택하세요."
                            ProLicense.isValid(proCode) -> "이용권 확인됨 — 티맵 앱키를 마저 입력하세요."
                            else -> "티맵 PRO(실시간 교통·경로옵션)는 유료 이용권 구매 시 코드와 앱키가 제공됩니다."
                        },
                        fontSize = 11.sp,
                        color = if (tmapReady) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
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
            RoleBadge(badge)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = row.query,
                onValueChange = { row.query = it; row.picked = null },
                placeholder = { Text("주소·장소 검색") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
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
                modifier = Modifier.padding(start = 42.dp, top = 2.dp),
            )
        } else if (row.searchError != null) {
            Text(
                row.searchError!!,
                fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 42.dp, top = 2.dp),
            )
        } else if (row.suggestions.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(start = 42.dp, top = 4.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
private fun ResultCard(
    routeSet: OptimizedRoute,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onShare: () -> Unit,
) {
    // 후보 목록: alternatives 가 있으면 그것을, 없으면 단일 경로.
    val alts = routeSet.alternatives.ifEmpty { listOf(routeSet) }
    val sel = alts[selectedIndex.coerceIn(0, alts.lastIndex)]
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            val mobile = remember { isMobileDevice() }
            var navApp by remember { mutableStateOf(NavApp.NAVER) }

            // 경로 선택지 (후보 2개 이상일 때만)
            if (alts.size > 1) {
                RouteOptions(alts, selectedIndex.coerceIn(0, alts.lastIndex), onSelect)
                Spacer(Modifier.height(14.dp))
            }

            Row(Modifier.fillMaxWidth()) {
                StatTile("총 거리", formatKm(sel.totalDistanceMeters), Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                StatTile("예상 시간", formatDuration(sel.totalTimeSeconds), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
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
                    val isEnd = i == 0 || (i == sel.orderedPlaces.lastIndex)
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(
                                if (isEnd) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.address, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        p.resolvedName?.let {
                            Text(it, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    navToUrl(p, navApp, mobile)?.let { url ->
                        OutlinedButton(
                            onClick = { openUrl(url) },
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) { Text("안내", fontSize = 13.sp) }
                    }
                }
                if (i < sel.legs.size) {
                    val leg = sel.legs[i]
                    Text(
                        "↓  ${formatKm(leg.distanceMeters)} · ${formatDuration(leg.timeSeconds)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 38.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }

            if (sel.polyline.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Button(onClick = { showMap(alts, selectedIndex.coerceIn(0, alts.lastIndex)) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(if (alts.size > 1) "지도에서 경로 비교" else "지도에서 경로 보기",
                        fontWeight = FontWeight.SemiBold)
                }
            }

            // 경로 공유: 지점+설정을 URL 해시에 담아 전달 (받는 쪽에서 자동 재계산)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("경로 공유 (링크)")
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
    SectionLabel("경로 선택", "${alts.size}개 후보")
    Spacer(Modifier.height(8.dp))
    alts.forEachIndexed { i, a ->
        val isSel = i == selected
        val badges = listOfNotNull(
            if (i == minTimeIdx) "최소 시간" else null,
            if (i == minDistIdx) "최단 거리" else null,
        )
        Card(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(i) },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
            border = if (isSel) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 선택 표시 라디오 점
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) Box(Modifier.size(6.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary))
                }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.label ?: "경로 ${i + 1}", fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    badges.forEach { b -> Spacer(Modifier.width(6.dp)); Pill(b) }
                }
                Text(
                    "${formatKm(a.totalDistanceMeters)} · ${formatDuration(a.totalTimeSeconds)}",
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 로고 마크 + 제목/부제 헤더. */
@Composable
private fun AppHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Place, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("최단 경로 설계", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Text("여러 주소의 방문 순서를 실도로 기준으로 최적화",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 섹션 제목 (강조 바 + 제목 + 선택적 힌트). */
@Composable
private fun SectionLabel(title: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        hint?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 출발/도착/경유 역할 원형 배지. */
@Composable
private fun RoleBadge(badge: String) {
    val isStart = badge == "출발"
    val isEnd = badge == "도착"
    val bg = when {
        isStart -> MaterialTheme.colorScheme.primary
        isEnd -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isStart -> MaterialTheme.colorScheme.onPrimary
        isEnd -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(badge, fontSize = if (isStart || isEnd) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold, color = fg)
    }
}

/** 거리·시간 통계 타일. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
    }
}

/** 상태(안내)·오류 배너. */
@Composable
private fun Banner(text: String, isError: Boolean) {
    val bg = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, color = fg)
    }
}

/** 작은 강조 배지 (최소 시간/최단 거리). */
@Composable
private fun Pill(text: String) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onTertiary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---- 공유 링크 페이로드 ----
/**
 * URL 해시(#r=…)에 담는 공유 데이터. 폴리라인은 URL 에 담기엔 너무 커서 제외하고
 * 지점·설정만 보내 수신 측에서 자동 재계산한다.
 */
@Serializable
private data class SharePayload(
    val v: Int = 1,
    val roundTrip: Boolean = false,
    val alt: Int = 0,
    val places: List<SavedPlace> = emptyList(),
)

/** encodeDefaults=false 로 기본값·null 을 생략해 URL 을 최대한 짧게 유지. */
private val shareJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** 지도 선택 결과(roPickOnMap 반환 JSON). */
@Serializable
private data class PickedLoc(val lat: Double, val lon: Double, val address: String = "")

private val pickJson = Json { ignoreUnknownKeys = true }

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
