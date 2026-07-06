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
 * 티맵 호출이 실패하거나 앱키가 없을 때의 fallback. 실제 도로가 아닌 직선거리 기준이라
 * 순서는 근사치이며, 이동시간은 평균 시속 40km 가정으로 추정한다.
 *
 * 알고리즘: 최근접 이웃(greedy) 으로 초기해를 만든 뒤 2-opt 로 개선.
 * 출발지는 항상 첫 번째, 도착지는 항상 마지막으로 고정하고 중간 경유지만 재배열한다.
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
        val startC = requireNotNull(start.coord) { "출발지 좌표 필요" }
        val endC = requireNotNull(end.coord) { "도착지 좌표 필요" }

        // 초기해: 최근접 이웃.
        val remaining = vias.toMutableList()
        val order = ArrayList<Place>(vias.size)
        var cursor = startC
        while (remaining.isNotEmpty()) {
            val nextIdx = remaining.indices.minByOrNull {
                haversineMeters(cursor, remaining[it].coord!!)
            }!!
            val next = remaining.removeAt(nextIdx)
            order.add(next)
            cursor = next.coord!!
        }

        // 2-opt 개선.
        twoOpt(order, startC, endC)

        val full = buildList {
            add(start)
            addAll(order)
            add(end)
        }
        val legs = ArrayList<RouteLeg>(full.size - 1)
        var totalM = 0.0
        for (i in 0 until full.size - 1) {
            val d = haversineMeters(full[i].coord!!, full[i + 1].coord!!)
            val t = d / AVG_SPEED_MPS
            totalM += d
            legs.add(
                RouteLeg(
                    from = full[i],
                    to = full[i + 1],
                    distanceMeters = d.roundToInt(),
                    timeSeconds = t.roundToInt(),
                ),
            )
        }
        return OptimizedRoute(
            orderedPlaces = full,
            legs = legs,
            totalDistanceMeters = totalM.roundToInt(),
            totalTimeSeconds = (totalM / AVG_SPEED_MPS).roundToInt(),
            source = RouteSource.STRAIGHT_LINE,
        )
    }

    private fun twoOpt(order: MutableList<Place>, start: LatLng, end: LatLng) {
        if (order.size < 2) return
        fun coordAt(i: Int): LatLng = when {
            i < 0 -> start
            i >= order.size -> end
            else -> order[i].coord!!
        }
        var improved = true
        var guard = 0
        while (improved && guard < 100) {
            improved = false
            guard++
            for (i in 0 until order.size - 1) {
                for (k in i + 1 until order.size) {
                    // 구간 [i..k] 뒤집기 전후의 두 경계 간선 비용 비교.
                    val before = haversineMeters(coordAt(i - 1), coordAt(i)) +
                        haversineMeters(coordAt(k), coordAt(k + 1))
                    val after = haversineMeters(coordAt(i - 1), coordAt(k)) +
                        haversineMeters(coordAt(i), coordAt(k + 1))
                    if (after + 1e-6 < before) {
                        order.subList(i, k + 1).reverse()
                        improved = true
                    }
                }
            }
        }
    }
}
