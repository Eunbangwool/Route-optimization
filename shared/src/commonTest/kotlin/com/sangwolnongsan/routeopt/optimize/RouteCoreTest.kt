package com.sangwolnongsan.routeopt.optimize

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RouteCore 정합성 검증.
 * - Held-Karp(전역최적)는 브루트포스(전수 순열)와 **정확히 일치**해야 한다.
 * - ILS(휴리스틱)는 브루트포스 최적보다 나쁠 수 있지만 절대 더 좋을 수 없고,
 *   작은 인스턴스에서는 사실상 최적을 찾아야 한다.
 */
class RouteCoreTest {

    // ---------- 헬퍼 ----------

    private fun randomMatrix(n: Int, rng: Random, symmetric: Boolean): Array<DoubleArray> {
        val m = Array(n) { DoubleArray(n) }
        for (i in 0 until n) for (j in 0 until n) {
            if (i == j) continue
            m[i][j] = 1.0 + rng.nextDouble() * 99.0
        }
        if (symmetric) {
            for (i in 0 until n) for (j in i + 1 until n) m[j][i] = m[i][j]
        }
        return m
    }

    private fun permutations(items: List<Int>, visit: (List<Int>) -> Unit) {
        if (items.isEmpty()) { visit(emptyList()); return }
        fun rec(cur: MutableList<Int>, rest: MutableList<Int>) {
            if (rest.isEmpty()) { visit(cur.toList()); return }
            for (idx in rest.indices) {
                val x = rest.removeAt(idx)
                cur.add(x)
                rec(cur, rest)
                cur.removeAt(cur.size - 1)
                rest.add(idx, x)
            }
        }
        rec(mutableListOf(), items.toMutableList())
    }

    /** 전수 순열로 최적 비용 계산. */
    private fun bruteForce(c: Array<DoubleArray>, roundTrip: Boolean): Double {
        val n = c.size
        val free = if (roundTrip) (1 until n).toList() else (1 until n - 1).toList()
        var best = Double.MAX_VALUE
        permutations(free) { perm ->
            val order = buildList {
                add(0); addAll(perm); if (!roundTrip) add(n - 1)
            }
            val cost = RouteCore.pathCost(c, order, roundTrip)
            if (cost < best) best = cost
        }
        return best
    }

    private fun assertValidOrder(order: List<Int>, n: Int, roundTrip: Boolean) {
        assertEquals(n, order.size, "모든 노드 포함")
        assertEquals(n, order.toSet().size, "중복 없음")
        assertEquals(0, order.first(), "출발 고정")
        if (!roundTrip) assertEquals(n - 1, order.last(), "도착 고정")
    }

    // ---------- Held-Karp = 전역 최적 ----------

    @Test
    fun heldKarp_matchesBruteForce_openPath_symmetric() {
        val rng = Random(11)
        for (n in 3..9) repeat(6) {
            val c = randomMatrix(n, rng, symmetric = true)
            val res = RouteCore.optimizeOrder(c, roundTrip = false)
            assertTrue(res.exact)
            assertValidOrder(res.order, n, roundTrip = false)
            val brute = bruteForce(c, roundTrip = false)
            assertTrue(abs(res.cost - brute) < 1e-6, "n=$n: core=${res.cost} brute=$brute")
        }
    }

    @Test
    fun heldKarp_matchesBruteForce_openPath_asymmetric() {
        val rng = Random(22)
        for (n in 3..9) repeat(6) {
            val c = randomMatrix(n, rng, symmetric = false)
            val res = RouteCore.optimizeOrder(c, roundTrip = false)
            val brute = bruteForce(c, roundTrip = false)
            assertTrue(abs(res.cost - brute) < 1e-6, "n=$n: core=${res.cost} brute=$brute")
        }
    }

    @Test
    fun heldKarp_matchesBruteForce_roundTrip() {
        val rng = Random(33)
        for (n in 2..8) repeat(6) {
            val c = randomMatrix(n, rng, symmetric = false)
            val res = RouteCore.optimizeOrder(c, roundTrip = true)
            assertValidOrder(res.order, n, roundTrip = true)
            val brute = bruteForce(c, roundTrip = true)
            assertTrue(abs(res.cost - brute) < 1e-6, "n=$n: core=${res.cost} brute=$brute")
        }
    }

    @Test
    fun heldKarp_reportedCost_equalsRecomputedCost() {
        val rng = Random(44)
        for (n in 3..10) {
            val c = randomMatrix(n, rng, symmetric = false)
            val res = RouteCore.optimizeOrder(c, roundTrip = false)
            assertTrue(abs(res.cost - RouteCore.pathCost(c, res.order, false)) < 1e-9)
        }
    }

    // ---------- ILS (exactLimit=0 으로 강제) ----------

    @Test
    fun ils_neverBeatsOptimum_andNearOptimal_onSmall() {
        val rng = Random(55)
        var hits = 0
        var total = 0
        for (n in 4..9) repeat(6) {
            for (symmetric in listOf(true, false)) {
                val c = randomMatrix(n, rng, symmetric)
                val res = RouteCore.optimizeOrder(c, roundTrip = false, exactLimit = 0)
                assertTrue(!res.exact)
                assertValidOrder(res.order, n, roundTrip = false)
                val brute = bruteForce(c, roundTrip = false)
                assertTrue(res.cost > brute - 1e-6, "휴리스틱이 전수최적보다 좋을 수 없음")
                assertTrue(res.cost <= brute * 1.05 + 1e-6, "n=$n sym=$symmetric: ils=${res.cost} brute=$brute")
                total++
                if (abs(res.cost - brute) < 1e-6) hits++
            }
        }
        // 작은 인스턴스에선 대부분 전역최적을 찾아야 함
        assertTrue(hits >= (total * 8) / 10, "최적 적중률 낮음: $hits/$total")
    }

    @Test
    fun ils_roundTrip_validAndSane() {
        val rng = Random(66)
        for (n in 4..8) {
            val c = randomMatrix(n, rng, symmetric = false)
            val res = RouteCore.optimizeOrder(c, roundTrip = true, exactLimit = 0)
            assertValidOrder(res.order, n, roundTrip = true)
            val brute = bruteForce(c, roundTrip = true)
            assertTrue(res.cost > brute - 1e-6)
            assertTrue(res.cost <= brute * 1.05 + 1e-6)
        }
    }

    @Test
    fun ils_notWorseThan_greedyBaseline_onLarger() {
        // 자유노드 20~30 규모: ILS 결과가 단순 최근접이웃보다 나빠선 안 된다.
        val rng = Random(77)
        for (n in intArrayOf(22, 32)) {
            val c = randomMatrix(n, rng, symmetric = false)
            val res = RouteCore.optimizeOrder(c, roundTrip = false, exactLimit = 0)
            assertValidOrder(res.order, n, roundTrip = false)
            // greedy NN baseline
            val used = BooleanArray(n)
            used[0] = true; used[n - 1] = true
            var cur = 0
            var nnCost = 0.0
            repeat(n - 2) {
                var pick = -1; var bd = Double.MAX_VALUE
                for (t in 1 until n - 1) if (!used[t] && c[cur][t] < bd) { bd = c[cur][t]; pick = t }
                nnCost += bd; used[pick] = true; cur = pick
            }
            nnCost += c[cur][n - 1]
            assertTrue(res.cost <= nnCost + 1e-6, "ILS(${res.cost}) > NN($nnCost)")
        }
    }

    // ---------- 경계/특수 케이스 ----------

    @Test
    fun edgeCases() {
        // 자유노드 0: open n=2
        val c2 = randomMatrix(2, Random(88), symmetric = false)
        val r2 = RouteCore.optimizeOrder(c2, roundTrip = false)
        assertEquals(listOf(0, 1), r2.order)
        assertTrue(abs(r2.cost - c2[0][1]) < 1e-9)

        // roundTrip n=1: 출발지뿐
        val c1 = arrayOf(doubleArrayOf(0.0))
        val r1 = RouteCore.optimizeOrder(c1, roundTrip = true)
        assertEquals(listOf(0), r1.order)
        assertEquals(0.0, r1.cost)

        // 자유노드 1: 순서 자명
        val c3 = randomMatrix(3, Random(99), symmetric = false)
        val r3 = RouteCore.optimizeOrder(c3, roundTrip = false)
        assertEquals(listOf(0, 1, 2), r3.order)

        // Held-Karp 상한 직전(자유 12)이 정상 동작 + NN 보다 나쁘지 않음
        val n = 14
        val cBig = randomMatrix(n, Random(101), symmetric = true)
        val rBig = RouteCore.optimizeOrder(cBig, roundTrip = false)
        assertTrue(rBig.exact)
        assertValidOrder(rBig.order, n, roundTrip = false)
        val rIls = RouteCore.optimizeOrder(cBig, roundTrip = false, exactLimit = 0)
        assertTrue(rIls.cost >= rBig.cost - 1e-6, "전역최적(${rBig.cost})보다 좋은 ILS(${rIls.cost}) 불가")
    }

    @Test
    fun unreachablePairs_handledViaLargeCost() {
        // 일부 간선 비용이 매우 큰(1e9 = 경로불가 표식) 행렬도 순서 유효성 유지
        val n = 6
        val c = randomMatrix(n, Random(123), symmetric = false)
        c[1][2] = 1e9; c[2][1] = 1e9
        val res = RouteCore.optimizeOrder(c, roundTrip = false)
        assertValidOrder(res.order, n, roundTrip = false)
        val brute = bruteForce(c, roundTrip = false)
        assertTrue(abs(res.cost - brute) < 1e-3)
    }
}
