@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.tmap

import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteLeg
import com.sangwolnongsan.routeopt.model.RouteSource
import com.sangwolnongsan.routeopt.web.route.RouteProvider
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TmapException(message: String) : Exception(message)

/**
 * 티맵 오픈API 클라이언트.
 *  - 지오코딩: fullAddrGeo(정식주소) → 실패 시 POI 키워드 검색 fallback
 *  - 경로최적화: routeOptimization20 (경유지 최대 20개, 방문순서 자동 최적화)
 *
 * 모든 함수는 브라우저 fetch 를 사용하므로 suspend.
 */
class TmapClient(private val appKey: String) : RouteProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val base = "https://apis.openapi.sk.com/tmap"

    /** 주소/장소명 → 좌표. 매칭 실패 시 null. */
    override suspend fun geocode(query: String): Pair<LatLng, String?>? {
        val enc = encodeURIComponent(query)
        // 1) 정식주소 지오코딩
        runCatching {
            val url = "$base/geo/fullAddrGeo?version=1&format=json&coordType=WGS84GEO&fullAddr=$enc"
            val text = tmapGet(url, appKey).await<JsString>().toString()
            val root = json.parseToJsonElement(text).jsonObject
            val coord = root["coordinateInfo"]?.jsonObject
                ?.get("coordinate")?.jsonArray?.firstOrNull()?.jsonObject
            if (coord != null) {
                val lat = coord.dbl("lat") ?: coord.dbl("newLat")
                val lon = coord.dbl("lon") ?: coord.dbl("newLon")
                if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                    return LatLng(lat, lon) to null
                }
            }
        }
        // 2) POI 키워드 검색 fallback
        runCatching {
            val url = "$base/pois?version=1&format=json&count=1&searchKeyword=$enc"
            val text = tmapGet(url, appKey).await<JsString>().toString()
            val root = json.parseToJsonElement(text).jsonObject
            val poi = root["searchPoiInfo"]?.jsonObject
                ?.get("pois")?.jsonObject
                ?.get("poi")?.jsonArray?.firstOrNull()?.jsonObject
            if (poi != null) {
                val lat = poi.dbl("noorLat") ?: poi.dbl("frontLat")
                val lon = poi.dbl("noorLon") ?: poi.dbl("frontLon")
                val name = poi.str("name")
                if (lat != null && lon != null) return LatLng(lat, lon) to name
            }
        }
        return null
    }

    /**
     * 티맵 경로최적화. [start]→(최적 순서 경유지)→[end].
     * @param roundTrip true 면 end 는 무시하고 start 로 복귀.
     */
    override suspend fun optimize(start: Place, vias: List<Place>, end: Place, roundTrip: Boolean): OptimizedRoute {
        val startC = requireNotNull(start.coord)
        val endC = if (roundTrip) startC else requireNotNull(end.coord)

        val viaJson = vias.joinToString(",") { p ->
            val c = p.coord!!
            """{"viaPointId":"${p.id}","viaPointName":"${escape(p.address)}","viaX":"${c.lon}","viaY":"${c.lat}"}"""
        }
        val body = """
            {
              "reqCoordType":"WGS84GEO",
              "resCoordType":"WGS84GEO",
              "startName":"출발",
              "startX":"${startC.lon}","startY":"${startC.lat}",
              "endName":"도착",
              "endX":"${endC.lon}","endY":"${endC.lat}",
              "searchOption":"0",
              "viaPoints":[$viaJson]
            }
        """.trimIndent()

        val url = "$base/routes/routeOptimization20?version=1&format=json"
        val text = tmapPost(url, appKey, body).await<JsString>().toString()
        val root = json.parseToJsonElement(text).jsonObject
        val features = root["features"]?.jsonArray
            ?: throw TmapException(errorMessage(root) ?: "티맵 응답에 features 가 없습니다.")

        // viaPointId → Place 매핑
        val viaById = vias.associateBy { it.id }

        // index 순서로 정렬
        val sorted = features.sortedBy { it.jsonObject.propObj()?.dbl("index") ?: 0.0 }

        val orderedPlaces = ArrayList<Place>()
        orderedPlaces.add(start)
        val polyline = ArrayList<LatLng>()
        val legs = ArrayList<RouteLeg>()

        var legFrom: Place = start
        var accDist = 0.0
        var accTime = 0.0
        var totalDist = 0.0
        var totalTime = 0.0

        for (f in sorted) {
            val fo = f.jsonObject
            val geom = fo["geometry"]?.jsonObject ?: continue
            val props = fo["properties"]?.jsonObject
            when (geom.str("type")) {
                "Point" -> {
                    val pt = geom["coordinates"]?.jsonArray ?: continue
                    val lon = pt.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: continue
                    val lat = pt.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: continue
                    val type = props?.str("pointType") ?: ""
                    when {
                        type == "S" -> { /* 출발점: 이미 추가됨 */ }
                        type == "E" -> {
                            val endPlace = (if (roundTrip) start else end)
                            legs.add(RouteLeg(legFrom, endPlace, accDist.toInt(), accTime.toInt()))
                            accDist = 0.0; accTime = 0.0
                            orderedPlaces.add(endPlace)
                        }
                        type.startsWith("B") -> {
                            val vid = props?.str("viaPointId")
                            val place = viaById[vid]
                                ?: Place(id = vid ?: "?", address = props?.str("name") ?: "경유지",
                                    coord = LatLng(lat, lon))
                            legs.add(RouteLeg(legFrom, place, accDist.toInt(), accTime.toInt()))
                            accDist = 0.0; accTime = 0.0
                            orderedPlaces.add(place)
                            legFrom = place
                        }
                    }
                }
                "LineString" -> {
                    val d = props?.dbl("distance") ?: 0.0
                    val t = props?.dbl("time") ?: 0.0
                    accDist += d; accTime += t
                    totalDist += d; totalTime += t
                    geom["coordinates"]?.jsonArray?.forEach { c ->
                        val arr = c.jsonArray
                        val lon = arr.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                        val lat = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                        if (lon != null && lat != null) polyline.add(LatLng(lat, lon))
                    }
                }
            }
        }

        // totalDistance/totalTime 이 properties 로 별도 제공되면 우선 사용
        val declaredDist = sorted.firstNotNullOfOrNull { it.jsonObject.propObj()?.dbl("totalDistance") }
        val declaredTime = sorted.firstNotNullOfOrNull { it.jsonObject.propObj()?.dbl("totalTime") }

        return OptimizedRoute(
            orderedPlaces = orderedPlaces,
            legs = legs,
            totalDistanceMeters = (declaredDist ?: totalDist).toInt(),
            totalTimeSeconds = (declaredTime ?: totalTime).toInt(),
            source = RouteSource.TMAP,
            polyline = polyline,
        )
    }

    // ---- JSON 헬퍼 ----
    private fun JsonObject.dbl(key: String): Double? =
        this[key]?.let { it.jsonPrimitive.content.toDoubleOrNull() }

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun JsonObject.propObj(): JsonObject? = this["properties"]?.jsonObject

    private fun errorMessage(root: JsonObject): String? {
        val err = root["error"]?.jsonObject ?: return null
        return err.str("message") ?: err.str("id") ?: "티맵 오류"
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
