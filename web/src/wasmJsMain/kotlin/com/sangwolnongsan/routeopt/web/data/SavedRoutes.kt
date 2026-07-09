package com.sangwolnongsan.routeopt.web.data

import com.sangwolnongsan.routeopt.web.tmap.lsGet
import com.sangwolnongsan.routeopt.web.tmap.lsSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 저장된 경로의 지점 (검색 선택 결과 스냅샷). */
@Serializable
data class SavedPlace(val label: String, val sub: String? = null, val lat: Double, val lon: Double)

/** 사용자가 저장한 경로 (이름 + 왕복여부 + 지점 목록). */
@Serializable
data class SavedRoute(val name: String, val roundTrip: Boolean, val places: List<SavedPlace>)

/** 저장 경로 목록을 브라우저 localStorage 에 보관. 서버·키 불필요. */
object SavedRoutes {
    private const val KEY = "saved_routes_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(SavedRoute.serializer())

    fun load(): List<SavedRoute> {
        val t = lsGet(KEY)
        if (t.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(ser, t) }.getOrDefault(emptyList())
    }

    fun persist(list: List<SavedRoute>) {
        lsSet(KEY, json.encodeToString(ser, list))
    }
}
