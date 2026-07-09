@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.osrm

import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteLeg
import com.sangwolnongsan.routeopt.model.RouteSource
import com.sangwolnongsan.routeopt.optimize.RouteCore
import com.sangwolnongsan.routeopt.web.route.RouteProvider
import com.sangwolnongsan.routeopt.web.route.Suggestion
import com.sangwolnongsan.routeopt.web.util.httpGetText
import kotlin.js.JsString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OsrmException(message: String) : Exception(message)

/**
 * 키 불필요 무료 경로 제공자.
 *  - 지오코딩: Nominatim (OpenStreetMap)
 *  - 최적화(1차): /table 로 실도로 소요시간 행렬 → RouteCore 가 방문순서 계산
 *    (경유지 ≤ 15 는 Held-Karp **전역 최적**, 그 이상은 ILS) → /route 로 최종
 *    경로/거리/시간/폴리라인 확정. OSRM 자체 /trip 휴리스틱보다 우수하다.
 *  - 최적화(fallback): /table 실패 시 기존 /trip.
 *
 * 공개 데모 서버라 rate limit(대략 초당 1회)·무SLA·비상업 용도. 한국 주소 정확도는
 * OSM 데이터 특성상 티맵/카카오보다 낮을 수 있다.
 */
class OsrmClient : RouteProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val nominatim = "https://nominatim.openstreetmap.org"
    private val osrm = "https://router.project-osrm.org"

    /**
     * Nominatim(OSM) 자동완성 검색 — 키 불필요. accept-language=ko 로 한국어 결과,
     * countrycodes=kr 로 한국 한정. 공개 서버 정책상 과도한 요청 금지(디바운스 사용).
     */
    override suspend fun search(query: String): List<Suggestion> {
        val enc = com.sangwolnongsan.routeopt.web.tmap.encodeURIComponent(query)
        val url = "$nominatim/search?format=jsonv2&accept-language=ko&countrycodes=kr&limit=6&q=$enc"
        val text = httpGetText(url).await<JsString>().toString()
        val arr = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val disp = o["display_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            Suggestion(
                label = name ?: disp.substringBefore(",").trim(),
                sub = disp,
                coord = LatLng(lat, lon),
            )
        }
    }

    override suspend fun geocode(query: String): Pair<LatLng, String?>? {
        val enc = encode(query)
        val url = "$nominatim/search?format=json&limit=1&countrycodes=kr&accept-language=ko&q=$enc"
        val text = httpGetText(url).await<JsString>().toString()
        val arr = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return null
        val first = arr.firstOrNull()?.jsonObject ?: return null
        val lat = first["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = first["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val name = first["display_name"]?.jsonPrimitive?.content
        return LatLng(lat, lon) to name
    }

    override suspend fun optimize(start: Place, vias: List<Place>, end: Place, roundTrip: Boolean): OptimizedRoute {
        // 1차: 시간행렬 + RouteCore (전역최적/ILS). 실패 시 기존 /trip 휴리스틱.
        return try {
            tableOptimize(start, vias, end, roundTrip)
        } catch (e: CancellationException) {
            throw e // 코루틴 취소는 fallback 하지 말고 그대로 전파
        } catch (e: Throwable) {
            tripOptimize(start, vias, end, roundTrip)
        }
    }

    /**
     * /table 로 n×n 실도로 소요시간 행렬을 받아 RouteCore 로 방문순서를 풀고,
     * 확정 순서로 /route 를 호출해 실도로 폴리라인·구간별 거리/시간을 얻는다.
     *
     * 왕복은 도착 노드를 출발 좌표 복제로 두는 open-path 정식화 — TSP 관점에서
     * roundtrip 과 동치이며 행렬 행/열이 그대로 유효하다.
     */
    private suspend fun tableOptimize(start: Place, vias: List<Place>, end: Place, roundTrip: Boolean): OptimizedRoute {
        val pts = buildList {
            add(start)
            addAll(vias)
            add(if (roundTrip) start else end)
        }
        val n = pts.size
        val coordStr = pts.joinToString(";") { "${it.coord!!.lon},${it.coord!!.lat}" }

        // ---- 1) 시간행렬 ----
        val tUrl = "$osrm/table/v1/driving/$coordStr?annotations=duration"
        val tText = httpGetText(tUrl).await<JsString>().toString()
        val tRoot = json.parseToJsonElement(tText).jsonObject
        if (tRoot["code"]?.jsonPrimitive?.contentOrNull != "Ok") {
            throw OsrmException(tRoot["message"]?.jsonPrimitive?.contentOrNull ?: "table 실패")
        }
        val durRows = tRoot["durations"]?.jsonArray ?: throw OsrmException("durations 없음")
        if (durRows.size != n) throw OsrmException("durations 크기 불일치")
        val mat = Array(n) { i ->
            val row = durRows[i].jsonArray
            DoubleArray(n) { j ->
                // null = 경로 불가 → 매우 큰 비용으로 회피 유도
                row.getOrNull(j)?.jsonPrimitive?.doubleOrNull ?: 1e9
            }
        }

        // ---- 2) 방문순서 (경유지 ≤15 전역최적 Held-Karp / 이상 ILS) ----
        val res = RouteCore.optimizeOrder(mat, roundTrip = false)
        val ordered = res.order.map { pts[it] }

        // ---- 3) 확정 순서의 실도로 경로 ----
        val rCoord = ordered.joinToString(";") { "${it.coord!!.lon},${it.coord!!.lat}" }
        val rUrl = "$osrm/route/v1/driving/$rCoord?overview=full&geometries=geojson&steps=false"
        val rText = httpGetText(rUrl).await<JsString>().toString()
        val rRoot = json.parseToJsonElement(rText).jsonObject
        if (rRoot["code"]?.jsonPrimitive?.contentOrNull != "Ok") {
            throw OsrmException(rRoot["message"]?.jsonPrimitive?.contentOrNull ?: "route 실패")
        }
        val route = rRoot["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw OsrmException("routes 없음")

        val legsJson = route["legs"]?.jsonArray
        val legs = ArrayList<RouteLeg>()
        if (legsJson != null) {
            for (i in 0 until minOf(legsJson.size, ordered.size - 1)) {
                val lo = legsJson[i].jsonObject
                val d = lo["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val t = lo["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                legs.add(RouteLeg(ordered[i], ordered[i + 1], d.toInt(), t.toInt()))
            }
        }
        val polyline = ArrayList<LatLng>()
        route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray?.forEach { c ->
            val a = c.jsonArray
            val lon = a.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            if (lon != null && lat != null) polyline.add(LatLng(lat, lon))
        }
        val totalDist = route["distance"]?.jsonPrimitive?.doubleOrNull
            ?: legs.sumOf { it.distanceMeters.toDouble() }
        val totalTime = route["duration"]?.jsonPrimitive?.doubleOrNull
            ?: legs.sumOf { it.timeSeconds.toDouble() }

        return OptimizedRoute(
            orderedPlaces = ordered,
            legs = legs,
            totalDistanceMeters = totalDist.toInt(),
            totalTimeSeconds = totalTime.toInt(),
            source = RouteSource.OSRM,
            polyline = polyline,
            exactOrder = res.exact,
        )
    }

    /** (fallback) OSRM /trip — 서버측 TSP 휴리스틱. */
    private suspend fun tripOptimize(start: Place, vias: List<Place>, end: Place, roundTrip: Boolean): OptimizedRoute {
        // 입력 좌표 순서: [출발] + 경유지들 (+ [도착] — 왕복이 아닐 때)
        val inputs = buildList {
            add(start)
            addAll(vias)
            if (!roundTrip) add(end)
        }
        val coordStr = inputs.joinToString(";") { "${it.coord!!.lon},${it.coord!!.lat}" }
        val opts = if (roundTrip) {
            "source=first&roundtrip=true"
        } else {
            "source=first&destination=last&roundtrip=false"
        }
        val url = "$osrm/trip/v1/driving/$coordStr?$opts&geometries=geojson&overview=full&annotations=false"

        val text = httpGetText(url).await<JsString>().toString()
        val root = json.parseToJsonElement(text).jsonObject
        val code = root["code"]?.jsonPrimitive?.content
        if (code != "Ok") {
            throw OsrmException(root["message"]?.jsonPrimitive?.content ?: "OSRM 응답 코드: $code")
        }
        val trip = root["trips"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw OsrmException("OSRM trips 없음")
        val waypoints = root["waypoints"]?.jsonArray
            ?: throw OsrmException("OSRM waypoints 없음")

        // waypoints[i] 는 inputs[i] 에 대응. waypoint_index = 최적 방문 순서.
        val orderPairs = waypoints.mapIndexedNotNull { i, w ->
            val wi = w.jsonObject["waypoint_index"]?.jsonPrimitive?.intOrNull ?: return@mapIndexedNotNull null
            if (i < inputs.size) inputs[i] to wi else null
        }.sortedBy { it.second }
        val orderedStops = orderPairs.map { it.first }

        // 왕복이면 출발지로 복귀하는 마지막 지점을 추가해 닫는다.
        val fullOrdered = if (roundTrip) orderedStops + orderedStops.first() else orderedStops

        // legs: trips[0].legs[i] = fullOrdered[i] → fullOrdered[i+1]
        val legsJson = trip["legs"]?.jsonArray
        val legs = ArrayList<RouteLeg>()
        if (legsJson != null) {
            for (i in 0 until minOf(legsJson.size, fullOrdered.size - 1)) {
                val lo = legsJson[i].jsonObject
                val d = lo["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val t = lo["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                legs.add(RouteLeg(fullOrdered[i], fullOrdered[i + 1], d.toInt(), t.toInt()))
            }
        }

        val polyline = ArrayList<LatLng>()
        trip["geometry"]?.jsonObject?.get("coordinates")?.jsonArray?.forEach { c ->
            val a = c.jsonArray
            val lon = a.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            if (lon != null && lat != null) polyline.add(LatLng(lat, lon))
        }

        val totalDist = trip["distance"]?.jsonPrimitive?.doubleOrNull ?: legs.sumOf { it.distanceMeters.toDouble() }
        val totalTime = trip["duration"]?.jsonPrimitive?.doubleOrNull ?: legs.sumOf { it.timeSeconds.toDouble() }

        return OptimizedRoute(
            orderedPlaces = fullOrdered,
            legs = legs,
            totalDistanceMeters = totalDist.toInt(),
            totalTimeSeconds = totalTime.toInt(),
            source = RouteSource.OSRM,
            polyline = polyline,
        )
    }

    private fun encode(s: String): String = com.sangwolnongsan.routeopt.web.tmap.encodeURIComponent(s)
}
