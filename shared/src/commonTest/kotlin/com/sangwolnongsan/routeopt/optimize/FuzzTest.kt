package com.sangwolnongsan.routeopt.optimize

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RouteCore 퍼즈 테스트 — 고정 시드 Random(777), 400+ 케이스.
 *
 * 조합: n∈[2..9] × 대칭/비대칭 × open/roundTrip × 행렬 종류 4가지
 *  - RANDOM   : 균등 난수 비용 (1..100)
 *  - EQUAL    : 모든 간선 비용 동일 (동률 다수)
 *  - ONE_FAR  : 한 노드만 나머지와 극단적으로 멀다
 *  - TRI_VIOL : 삼각부등식 위반 (아주 작은 비용과 아주 큰 비용 혼재)
 *
 * 각 케이스 검증:
 *  1) 기본 optimizeOrder(=Held-Karp) 비용 == 브루트포스 전수순열 최적 (1e-6)
 *  2) 순열 유효성: 0 시작, 전 노드 1회씩, open 이면 n-1 종료
 *  3) exactLimit=0 으로 ILS 강제: 비용 >= 브루트포스 - 1e-6 (절대 최적을 못 이김)
 *     + 최악 비율(ils/brute) 기록
 */
class FuzzTest {

    private enum class Kind { RANDOM, EQUAL, ONE_FAR, TRI_VIOL }

    // ---------- 행렬 생성 ----------

    private fun makeMatrix(n: Int, kind: Kind, symmetric: Boolean, rng: Random): Array<DoubleArray> {
        val m = Array(n) { DoubleArray(n) }
        when (kind) {
            Kind.RANDOM -> {
                for (i in 0 until n) for (j in 0 until n) if (i != j)
                    m[i][j] = 1.0 + rng.nextDouble() * 99.0
            }
            Kind.EQUAL -> {
                val v = 1.0 + rng.nextDouble() * 9.0
                for (i in 0 until n) for (j in 0 until n) if (i != j) m[i][j] = v
            }
            Kind.ONE_FAR -> {
                for (i in 0 until n) for (j in 0 until n) if (i != j)
                    m[i][j] = 1.0 + rng.nextDouble() * 9.0
                val far = rng.nextInt(n)
                for (i in 0 until n) if (i != far) {
                    m[i][far] = 1000.0 + rng.nextDouble() * 1000.0
                    m[far][i] = 1000.0 + rng.nextDouble() * 1000.0
                }
            }
            Kind.TRI_VIOL -> {
                // 직행 간선이 우회 경로보다 훨씬 비싼 간선이 다수 → 삼각부등식 위반
                for (i in 0 until n) for (j in 0 until n) if (i != j) {
                    m[i][j] = if (rng.nextDouble() < 0.35) 100.0 + rng.nextDouble() * 900.0
                    else 0.01 + rng.nextDouble()
                }
            }
        }
        if (symmetric) {
            for (i in 0 until n) for (j in i + 1 until n) m[j][i] = m[i][j]
        }
        return m
    }

    // ---------- 브루트포스 (전수 순열) ----------

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

    private fun bruteForce(c: Array<DoubleArray>, roundTrip: Boolean): Double {
        val n = c.size
        val free = if (roundTrip) (1 until n).toList() else (1 until n - 1).toList()
        var best = Double.MAX_VALUE
        permutations(free) { perm ->
            val order = buildList { add(0); addAll(perm); if (!roundTrip) add(n - 1) }
            val cost = RouteCore.pathCost(c, order, roundTrip)
            if (cost < best) best = cost
        }
        return best
    }

    private fun orderProblem(order: List<Int>, n: Int, roundTrip: Boolean): String? = when {
        order.size != n -> "size=${order.size}≠$n"
        order.toSet().size != n -> "중복 노드"
        order.first() != 0 -> "시작≠0"
        !roundTrip && order.last() != n - 1 -> "open 인데 종료≠${n - 1}"
        else -> null
    }

    private fun matrixDump(c: Array<DoubleArray>): String =
        c.joinToString(";") { row -> row.joinToString(",") { v -> ((v * 100).toInt() / 100.0).toString() } }

    // ---------- 메인 퍼즈 ----------

    @Test
    fun fuzz_exactMatchesBruteForce_andIlsNeverBeatsIt() {
        val rng = Random(777)
        // 조합당 반복 수: 32 조합 × (6+1+2+4) = 416 케이스
        val repsPerKind = mapOf(Kind.RANDOM to 6, Kind.EQUAL to 1, Kind.ONE_FAR to 2, Kind.TRI_VIOL to 4)

        var total = 0
        var exactMismatch = 0
        var ilsBeats = 0
        var worstRatio = 1.0
        var worstCase = ""
        val failures = ArrayList<String>()

        for (n in 2..9) for (symmetric in booleanArrayOf(true, false))
            for (roundTrip in booleanArrayOf(true, false))
                for (kind in Kind.entries) repeat(repsPerKind.getValue(kind)) { rep ->
                    total++
                    val caseSeed = rng.nextInt()
                    val c = makeMatrix(n, kind, symmetric, Random(caseSeed))
                    val tag = "case#$total n=$n sym=$symmetric rt=$roundTrip kind=$kind rep=$rep seed=$caseSeed"
                    fun report(msg: String) {
                        if (failures.size < 12) failures += "$tag: $msg | matrix=${matrixDump(c)}"
                    }

                    val brute = bruteForce(c, roundTrip)

                    // 1) 기본(전역 최적) 경로
                    val exactRes = RouteCore.optimizeOrder(c, roundTrip)
                    orderProblem(exactRes.order, n, roundTrip)?.let { report("exact 순열 무효: $it (${exactRes.order})") }
                    if (abs(exactRes.cost - RouteCore.pathCost(c, exactRes.order, roundTrip)) > 1e-9)
                        report("exact 보고비용≠재계산비용")
                    if (abs(exactRes.cost - brute) > 1e-6) {
                        exactMismatch++
                        report("exact=${exactRes.cost} ≠ brute=$brute")
                    }

                    // 2) ILS 강제 (exactLimit=0)
                    val ilsRes = RouteCore.optimizeOrder(
                        c, roundTrip, exactLimit = 0, timeBudgetMs = 300, rng = Random(caseSeed),
                    )
                    orderProblem(ilsRes.order, n, roundTrip)?.let { report("ILS 순열 무효: $it (${ilsRes.order})") }
                    if (abs(ilsRes.cost - RouteCore.pathCost(c, ilsRes.order, roundTrip)) > 1e-9)
                        report("ILS 보고비용≠재계산비용")
                    if (ilsRes.cost < brute - 1e-6) {
                        ilsBeats++
                        report("ILS(${ilsRes.cost}) 가 전수최적($brute) 보다 좋음 — 브루트포스/비용계산 모순")
                    }
                    if (brute > 1e-9) {
                        val ratio = ilsRes.cost / brute
                        if (ratio > worstRatio) { worstRatio = ratio; worstCase = tag }
                    }
                }

        println("FUZZ-SUMMARY total=$total exactMismatch=$exactMismatch ilsBeats=$ilsBeats worstRatio=$worstRatio worstCase={$worstCase}")
        assertTrue(total >= 400, "케이스 수 부족: $total")
        assertTrue(
            failures.isEmpty(),
            "퍼즈 실패 ${failures.size}건 (총 $total, exactMismatch=$exactMismatch, ilsBeats=$ilsBeats):\n" +
                failures.joinToString("\n"),
        )
    }
}
