package com.sangwolnongsan.routeopt.model

import kotlinx.serialization.Serializable

/** WGS84 좌표. */
@Serializable
data class LatLng(val lat: Double, val lon: Double)

/**
 * 방문 지점.
 * @param id 안정적인 식별자 (경유지 매핑용)
 * @param address 사용자가 입력한 주소/장소명 원문
 * @param coord 지오코딩된 좌표. 미해결 시 null
 * @param resolvedName 지오코딩이 매칭한 정식 명칭(있으면)
 */
@Serializable
data class Place(
    val id: String,
    val address: String,
    val coord: LatLng? = null,
    val resolvedName: String? = null,
)

/** 두 지점 사이 이동 구간. */
@Serializable
data class RouteLeg(
    val from: Place,
    val to: Place,
    val distanceMeters: Int,
    val timeSeconds: Int,
)

/**
 * 최적화 결과.
 * @param orderedPlaces 방문 순서대로 정렬된 지점 (출발 → … → 도착)
 * @param legs 인접 지점 간 구간
 * @param totalDistanceMeters 총 이동 거리
 * @param totalTimeSeconds 총 이동 시간
 * @param source 결과 출처 (TMAP = 티맵 실도로, STRAIGHT_LINE = 직선거리 fallback)
 * @param polyline 지도에 그릴 실도로 경로 좌표열 (TMAP 결과에만 존재)
 */
@Serializable
data class OptimizedRoute(
    val orderedPlaces: List<Place>,
    val legs: List<RouteLeg>,
    val totalDistanceMeters: Int,
    val totalTimeSeconds: Int,
    val source: RouteSource,
    val polyline: List<LatLng> = emptyList(),
)

@Serializable
enum class RouteSource { TMAP, STRAIGHT_LINE }
