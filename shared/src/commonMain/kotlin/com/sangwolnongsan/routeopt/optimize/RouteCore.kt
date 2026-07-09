package com.sangwolnongsan.routeopt.optimize

import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * 방문 순서 최적화 결과.
 * @param order 0(출발)으로 시작하는 전체 노드 순열. open 경로면 마지막 = n-1(도착).
 * @param cost  총 비용 (roundTrip 이면 마지막→0 복귀 간선 포함)
 * @param exact Held-Karp 전역 최적해 여부 (false = ILS 휴리스틱)
 */
data class OrderResult(val order: List<Int>, val cost: Double, val exact: Boolean)

/**
 * 비용 행렬 기반 방문순서 최적화 코어 (TSP, 플랫폼 독립).
 *
 * 입력 규약: cost 는 n×n 비음수 행렬(비대칭 허용). node 0 = 출발(고정).
 * roundTrip=false 면 node n-1 = 도착(고정), 자유 노드 = 1..n-2.
 * roundTrip=true 면 마지막에 0 으로 복귀, 자유 노드 = 1..n-1.
 *
 * - 자유 노드 ≤ [EXACT_LIMIT]: Held-Karp 동적계획법으로 **전역 최적** 순서.
 *   시간 O(2^m·m²), 공간 O(2^m·m) — m=15 에서 수 MB / 수십 ms 수준.
 * - 그 이상: 다중 구성(최근접이웃 + 최소삽입) → 2-opt/Or-opt 지역탐색 →
 *   double-bridge 교란을 반복하는 Iterated Local Search (시간 예산 내).
 *   모든 이동의 비용 변화는 비대칭 행렬에서도 정확하게 계산한다.
 */
object RouteCore {

    /** 이 자유 노드 수까지 Held-Karp 전역 최적 (메모리 ≈ 2^m·m·12B). */
    const val EXACT_LIMIT = 15

    fun optimizeOrder(
        cost: Array<DoubleArray>,
        roundTrip: Boolean,
        exactLimit: Int = EXACT_LIMIT,
        timeBudgetMs: Long = 600,
        rng: Random = Random(0x5EED),
    ): OrderResult {
        val n = cost.size
        require(n >= 1 && cost.all { it.size == n }) { "정방 행렬 필요" }
        val free: IntArray =
            if (roundTrip) IntArray(n - 1) { it + 1 }
            else IntArray(maxOf(0, n - 2)) { it + 1 }
        val endNode = if (roundTrip) 0 else n - 1

        if (free.isEmpty()) {
            val order = (0 until n).toList()
            return OrderResult(order, pathCost(cost, order, roundTrip), exact = true)
        }
        val exact = free.size <= exactLimit
        val seq =
            if (exact) heldKarp(cost, free, endNode)
            else ils(cost, free, endNode, rng, timeBudgetMs)
        val order = ArrayList<Int>(n).apply {
            add(0)
            seq.forEach(::add)
            if (!roundTrip) add(n - 1)
        }
        return OrderResult(order, pathCost(cost, order, roundTrip), exact)
    }

    /** 순열의 총 경로 비용. roundTrip 이면 복귀 간선 포함. */
    fun pathCost(cost: Array<DoubleArray>, order: List<Int>, roundTrip: Boolean): Double {
        var s = 0.0
        for (i in 0 until order.size - 1) s += cost[order[i]][order[i + 1]]
        if (roundTrip && order.size > 1) s += cost[order.last()][order.first()]
        return s
    }

    // ================= Held-Karp (전역 최적, 비대칭 지원) =================

    /**
     * dp[mask][j] = 0 에서 출발해 mask 의 자유노드를 모두 방문하고 free[j] 에서 끝나는 최소비용.
     * 최종적으로 endNode 로 가는 간선을 더해 최적 종점을 고른 뒤 역추적.
     */
    private fun heldKarp(c: Array<DoubleArray>, free: IntArray, endNode: Int): IntArray {
        val m = free.size
        val full = (1 shl m) - 1
        val dp = DoubleArray((full + 1) * m) { Double.MAX_VALUE }
        val par = IntArray((full + 1) * m) { -1 }
        for (j in 0 until m) dp[(1 shl j) * m + j] = c[0][free[j]]
        for (mask in 1..full) {
            for (j in 0 until m) {
                if (mask and (1 shl j) == 0) continue
                val d = dp[mask * m + j]
                if (d == Double.MAX_VALUE) continue
                for (k in 0 until m) {
                    if (mask and (1 shl k) != 0) continue
                    val nm = mask or (1 shl k)
                    val nd = d + c[free[j]][free[k]]
                    if (nd < dp[nm * m + k]) {
                        dp[nm * m + k] = nd
                        par[nm * m + k] = j
                    }
                }
            }
        }
        var bestJ = 0
        var best = Double.MAX_VALUE
        for (j in 0 until m) {
            val d = dp[full * m + j]
            if (d == Double.MAX_VALUE) continue
            val t = d + c[free[j]][endNode]
            if (t < best) { best = t; bestJ = j }
        }
        val seq = IntArray(m)
        var mask = full
        var j = bestJ
        for (pos in m - 1 downTo 0) {
            seq[pos] = free[j]
            val pj = par[mask * m + j]
            mask = mask xor (1 shl j)
            j = pj
        }
        return seq
    }

    // ================= Iterated Local Search (대규모 휴리스틱) =================

    private fun ils(
        c: Array<DoubleArray>,
        free: IntArray,
        endNode: Int,
        rng: Random,
        timeBudgetMs: Long,
    ): IntArray {
        val m = free.size
        val mark = TimeSource.Monotonic.markNow()
        fun timeLeft() = mark.elapsedNow().inWholeMilliseconds < timeBudgetMs

        // p: 자유 "노드 id" 의 방문순서 배열 (길이 m)
        fun seqCost(p: IntArray): Double {
            var s = c[0][p[0]]
            for (i in 0 until m - 1) s += c[p[i]][p[i + 1]]
            s += c[p[m - 1]][endNode]
            return s
        }

        // ---- 구성 1: 최근접 이웃 (firstPick ≥ 0 이면 첫 노드 강제) ----
        fun nn(firstPick: Int): IntArray {
            val used = BooleanArray(m)
            val p = IntArray(m)
            var cur = 0
            for (pos in 0 until m) {
                var pick = -1
                if (pos == 0 && firstPick in 0 until m) {
                    pick = firstPick
                } else {
                    var bd = Double.MAX_VALUE
                    for (t in 0 until m) if (!used[t]) {
                        val d = c[cur][free[t]]
                        if (d < bd) { bd = d; pick = t }
                    }
                }
                // 방어: 남은 후보 비용이 전부 비유한(MAX_VALUE/Inf/NaN)이면 첫 미사용 노드 선택
                if (pick < 0) for (t in 0 until m) if (!used[t]) { pick = t; break }
                used[pick] = true
                p[pos] = free[pick]
                cur = free[pick]
            }
            return p
        }

        // ---- 구성 2: 최소 삽입 (매 단계 삽입비용 최소 노드를 최적 위치에) ----
        fun cheapestInsertion(): IntArray {
            val route = ArrayList<Int>(m)
            val remaining = ArrayList<Int>(m).apply { free.forEach(::add) }
            while (remaining.isNotEmpty()) {
                var bestNode = remaining[0]
                var bestPos = 0
                var bestDelta = Double.MAX_VALUE
                for (node in remaining) {
                    for (pos in 0..route.size) {
                        val prev = if (pos == 0) 0 else route[pos - 1]
                        val next = if (pos == route.size) endNode else route[pos]
                        val delta = c[prev][node] + c[node][next] - c[prev][next]
                        if (delta < bestDelta) { bestDelta = delta; bestNode = node; bestPos = pos }
                    }
                }
                route.add(bestPos, bestNode)
                remaining.remove(bestNode)
            }
            return route.toIntArray()
        }

        // ---- 2-opt: 구간 뒤집기. 비대칭 행렬은 내부 간선을 양방향 재계산해 정확한 delta ----
        fun twoOptPass(p: IntArray): Boolean {
            var any = false
            for (i in 0 until m - 1) {
                if (!timeLeft()) return any
                for (k in i + 1 until m) {
                    val a = if (i == 0) 0 else p[i - 1]
                    val e = if (k == m - 1) endNode else p[k + 1]
                    var removed = c[a][p[i]] + c[p[k]][e]
                    var added = c[a][p[k]] + c[p[i]][e]
                    for (t in i until k) {
                        removed += c[p[t]][p[t + 1]]
                        added += c[p[t + 1]][p[t]]
                    }
                    if (added + 1e-9 < removed) {
                        var x = i; var y = k
                        while (x < y) { val tmp = p[x]; p[x] = p[y]; p[y] = tmp; x++; y-- }
                        any = true
                    }
                }
            }
            return any
        }

        // 세그먼트 [i, i+L) 제거 후 남는 순열의 idx 번째 노드
        fun remAt(p: IntArray, i: Int, L: Int, idx: Int): Int =
            if (idx < i) p[idx] else p[idx + L]

        // ---- Or-opt: 길이 1..3 세그먼트를 다른 슬롯으로 이동(정/역방향), O(1) delta ----
        fun orOptPass(p: IntArray): Boolean {
            var L = 1
            while (L <= 3 && L < m) {
                var i = 0
                while (i + L <= m) {
                    if (!timeLeft()) return false
                    val prev = if (i == 0) 0 else p[i - 1]
                    val next = if (i + L == m) endNode else p[i + L]
                    val s0 = p[i]
                    val s1 = p[i + L - 1]
                    var inner = 0.0
                    var innerRev = 0.0
                    for (t in i until i + L - 1) {
                        inner += c[p[t]][p[t + 1]]
                        innerRev += c[p[t + 1]][p[t]]
                    }
                    // 외부 간선만의 제거 이득 (내부는 정방향 재삽입 시 상쇄)
                    val removeGain = c[prev][s0] + c[s1][next] - c[prev][next]
                    val mR = m - L
                    var bestDelta = -1e-7
                    var bestT = -1
                    var bestRev = false
                    for (t in 0..mR) {
                        val q = if (t == 0) 0 else remAt(p, i, L, t - 1)
                        val r = if (t == mR) endNode else remAt(p, i, L, t)
                        val base = c[q][r]
                        if (t != i) { // t==i 정방향은 원위치(무의미)
                            val delta = (c[q][s0] + c[s1][r] - base) - removeGain
                            if (delta < bestDelta) { bestDelta = delta; bestT = t; bestRev = false }
                        }
                        if (L >= 2) { // 역방향 삽입 (L=1 은 정방향과 동일)
                            val delta = (c[q][s1] + c[s0][r] - base) - removeGain + (innerRev - inner)
                            if (delta < bestDelta) { bestDelta = delta; bestT = t; bestRev = true }
                        }
                    }
                    if (bestT >= 0) {
                        // 재구성: 세그먼트 제거 → bestT 슬롯에 (역방향이면 뒤집어) 삽입
                        val seg = IntArray(L) { p[i + it] }
                        if (bestRev) seg.reverse()
                        val out = IntArray(m)
                        var w = 0
                        for (t2 in 0 until bestT) out[w++] = remAt(p, i, L, t2)
                        for (s in seg) out[w++] = s
                        for (t2 in bestT until mR) out[w++] = remAt(p, i, L, t2)
                        out.copyInto(p)
                        return true // 인덱스가 바뀌었으므로 패스 재시작
                    }
                    i++
                }
                L++
            }
            return false
        }

        fun localSearch(p: IntArray) {
            while (timeLeft()) {
                val imp1 = twoOptPass(p)
                val imp2 = orOptPass(p)
                if (!imp1 && !imp2) break
            }
        }

        // ---- double-bridge 교란 (4-opt 계열: 지역최적 탈출) ----
        fun doubleBridge(p: IntArray): IntArray {
            if (m < 4) {
                val q = p.copyOf()
                for (x in m - 1 downTo 1) {
                    val y = rng.nextInt(x + 1)
                    val tmp = q[x]; q[x] = q[y]; q[y] = tmp
                }
                return q
            }
            val a = 1 + rng.nextInt(m - 3)
            val b = a + 1 + rng.nextInt(m - 2 - a)
            val cp = b + 1 + rng.nextInt(m - 1 - b)
            val q = IntArray(m)
            var w = 0
            for (t in 0 until a) q[w++] = p[t]
            for (t in b until cp) q[w++] = p[t]
            for (t in a until b) q[w++] = p[t]
            for (t in cp until m) q[w++] = p[t]
            return q
        }

        // ---- 메인 ILS 루프 ----
        var best = nn(-1)
        localSearch(best)
        var bestCost = seqCost(best)
        for (s in listOf(cheapestInsertion(), nn(rng.nextInt(m)), nn(rng.nextInt(m)))) {
            localSearch(s)
            val sc = seqCost(s)
            if (sc < bestCost) { best = s; bestCost = sc }
        }
        var cur = best.copyOf()
        var curCost = bestCost
        var iter = 0
        val maxIter = 40 + 10 * m
        while (iter < maxIter && timeLeft()) {
            iter++
            val cand = doubleBridge(cur)
            localSearch(cand)
            val cc = seqCost(cand)
            if (cc < curCost - 1e-9 || rng.nextDouble() < 0.05) {
                cur = cand
                curCost = cc
            }
            if (curCost < bestCost - 1e-9) {
                best = cur.copyOf()
                bestCost = curCost
            }
        }
        return best
    }
}
