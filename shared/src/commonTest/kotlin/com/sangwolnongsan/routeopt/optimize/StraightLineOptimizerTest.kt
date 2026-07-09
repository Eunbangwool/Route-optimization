package com.sangwolnongsan.routeopt.optimize

import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.Place
import com.sangwolnongsan.routeopt.model.RouteSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StraightLineOptimizerTest {

    private fun p(id: String, lat: Double, lon: Double) =
        Place(id = id, address = id, coord = LatLng(lat, lon))

    @Test
    fun haversine_seoulBusan_about325km() {
        val seoul = LatLng(37.5665, 126.9780)
        val busan = LatLng(35.1796, 129.0756)
        val d = StraightLineOptimizer.haversineMeters(seoul, busan)
        assertTrue(d in 300_000.0..350_000.0, "서울-부산 직선거리 이상: $d")
    }

    @Test
    fun optimize_visitsAllOnce_startEndFixed() {
        val start = p("start", 36.20, 127.08)
        val end = p("end", 36.30, 127.20)
        val vias = listOf(
            p("a", 36.28, 127.18), // end 근처
            p("b", 36.21, 127.09), // start 근처
            p("c", 36.25, 127.14), // 중간
        )
        val r = StraightLineOptimizer.optimize(start, vias, end)
        assertEquals(RouteSource.STRAIGHT_LINE, r.source)
        assertEquals(5, r.orderedPlaces.size)
        assertEquals("start", r.orderedPlaces.first().id)
        assertEquals("end", r.orderedPlaces.last().id)
        assertEquals(setOf("start", "a", "b", "c", "end"), r.orderedPlaces.map { it.id }.toSet())
        assertTrue(r.exactOrder, "5개 지점이면 전역 최적이어야 함")
        // 직선상 배치라 최적 순서는 지리 순서: b → c → a
        assertEquals(listOf("start", "b", "c", "a", "end"), r.orderedPlaces.map { it.id })
        // 합계 = 구간 합
        assertEquals(r.legs.sumOf { it.distanceMeters }, r.totalDistanceMeters, absDiff = 3)
        assertEquals(r.legs.size, r.orderedPlaces.size - 1)
    }

    @Test
    fun optimize_roundTrip_viaSameEnd() {
        val start = p("s", 37.50, 127.00)
        val vias = listOf(p("v1", 37.55, 127.05), p("v2", 37.45, 126.95))
        val r = StraightLineOptimizer.optimize(start, vias, start)
        assertEquals("s", r.orderedPlaces.first().id)
        assertEquals("s", r.orderedPlaces.last().id)
        assertEquals(4, r.orderedPlaces.size)
    }

    @Test
    fun optimize_noVias_singleLeg() {
        val start = p("s", 37.50, 127.00)
        val end = p("e", 37.60, 127.10)
        val r = StraightLineOptimizer.optimize(start, emptyList(), end)
        assertEquals(1, r.legs.size)
        assertEquals(listOf("s", "e"), r.orderedPlaces.map { it.id })
        // 40km/h 가정: time = dist / (40000/3600)
        val expected = r.totalDistanceMeters / (40_000.0 / 3600.0)
        assertTrue(abs(r.totalTimeSeconds - expected) < 2.0)
    }

    private fun assertEquals(a: Int, b: Int, absDiff: Int) {
        assertTrue(abs(a - b) <= absDiff, "$a != $b (허용 ±$absDiff)")
    }
}
