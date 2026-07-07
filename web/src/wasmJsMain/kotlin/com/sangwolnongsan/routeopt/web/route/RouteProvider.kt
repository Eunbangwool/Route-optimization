package com.sangwolnongsan.routeopt.web.route

import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.model.OptimizedRoute
import com.sangwolnongsan.routeopt.model.Place

/**
 * 지오코딩 + 경로최적화 제공자 공통 인터페이스.
 * 구현체: TmapClient(키 필요·정확), OsrmClient(키 불필요·무료 데모서버).
 */
/** 주소 검색 자동완성 후보. */
data class Suggestion(val label: String, val sub: String?, val coord: LatLng)

interface RouteProvider {
    /** 검색어 → 후보 목록 (자동완성용, 최대 몇 개). */
    suspend fun search(query: String): List<Suggestion>

    /** 주소/장소명 → (좌표, 매칭명). 실패 시 null. */
    suspend fun geocode(query: String): Pair<LatLng, String?>?

    /** [start]→(최적 순서 경유지)→[end]. roundTrip 이면 end 무시하고 start 로 복귀. */
    suspend fun optimize(start: Place, vias: List<Place>, end: Place, roundTrip: Boolean): OptimizedRoute
}
