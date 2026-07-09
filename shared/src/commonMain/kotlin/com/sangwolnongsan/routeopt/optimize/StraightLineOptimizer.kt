package com.sangwolnongsan.routeopt.optimize

import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteLeg
import com.sangwolnongsan.routeopt.model.RouteSource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 지도 API 없이 동작하는 직선거리(haversine) 기반 방문순서 최적화.
 *
 * 티맵/OSRM 호출이 실패했을 때의 fallback. 실제 도로가 아닌 직선거리 기준이라
 * 순서는 근사치이며, 이동시간은 평균 시속 40km 가정으로 추정한다.
 *
 * 순서 계산은 [RouteCore] 사용: 경유지 ≤ [RouteCore.EXACT_LIMIT] 이면 Held-Karp
 * 전역 최적(직선거리 기준), 그 이상이면 Iterated Local Search.
 */
object StraightLineOptimizer {

    private const val AVG_SPEED_MPS = 40_000.0 / 3600.0 // 40 km/h

    /** 지구 반지름(m) 기준 두 좌표의 대권 거리. */
    fun haversineMeters(a: LatLng, b: LatLng): Double {
        val r = 6_371_000.0
        val dLat = (b.lat - a.lat) * PI / 180.0
        val dLon = (b.lon - a.lon) * PI / 180.0
        val lat1 = a.lat * PI / 180.0
        val lat2 = b.lat * PI / 180.0
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * @param start 출발지 (고정, 좌표 필수)
     * @param vias 경유지들 (좌표 필수)
     * @param end 도착지 (고정, 좌표 필수). 왕복이면 start 와 동일 좌표를 넘긴다.
     */
    fun optimize(start: Place, vias: List<Place>, end: Place): OptimizedRoute {
        requireNotNull(start.coord) { "출발지 좌표 필요" }
        requireNotNull(end.coord) { "도착지 좌표 필요" }
        vias.forEach { requireNotNull(it.coord) { "경유지 좌표 필요: ${it.address}" } }

        val pts = buildList {
            add(start)
            addAll(vias)
            add(end)
        }
        val n = pts.size
        val mat = Array(n) { i ->
            DoubleArray(n) { j ->
                if (i == j) 0.0 else haversineMeters(pts[i].coord!!, pts[j].coord!!)
            }
        }
        // 도착지를 별도 노드로 두는 open 경로 정식화 (왕복이면 caller 가 end=start 로 전달)
        val res = RouteCore.optimizeOrder(mat, roundTrip = false)
        val full = res.order.map { pts[it] }

        val legs = ArrayList<RouteLeg>(full.size - 1)
        var totalM = 0.0
        for (i in 0 until full.size - 1) {
            val d = haversineMeters(full[i].coord!!, full[i + 1].coord!!)
            totalM += d
            legs.add(
                RouteLeg(
                    from = full[i],
                    to = full[i + 1],
                    distanceMeters = d.roundToInt(),
                    timeSeconds = (d / AVG_SPEED_MPS).roundToInt(),
                ),
            )
        }
        return OptimizedRoute(
            orderedPlaces = full,
            legs = legs,
            totalDistanceMeters = totalM.roundToInt(),
            totalTimeSeconds = (totalM / AVG_SPEED_MPS).roundToInt(),
            source = RouteSource.STRAIGHT_LINE,
            exactOrder = res.exact,
        )
    }
}
