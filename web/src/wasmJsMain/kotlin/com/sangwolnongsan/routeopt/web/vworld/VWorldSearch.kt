@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.vworld

import com.sangwolnongsan.routeopt.model.LatLng
import com.sangwolnongsan.routeopt.web.route.Suggestion
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** index.html 의 VWorld 주소 검색 브리지 호출. */
@JsFun("(q) => window.roSearchAddress(q)")
external fun roSearchAddress(q: String): Promise<JsString>

/**
 * VWorld(국토부) 주소 검색 — 농작이와 동일한 소스. 도로명·지번 정확 매칭.
 * 라우팅 엔진(OSRM/티맵)과 무관하게 주소 검색 전용으로 사용.
 */
object VWorldSearch {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 성공 시 후보 목록. VWorld 오류 시 메시지를 담아 예외를 던진다(호출부에서 표시). */
    suspend fun search(query: String): List<Suggestion> {
        val text = roSearchAddress(query).await<JsString>().toString()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()
        val err = root["error"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
        val list = items.mapNotNull { el ->
            val o = el.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val label = o["label"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: return@mapNotNull null
            val sub = o["sub"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            Suggestion(label = label, sub = sub, coord = LatLng(lat, lon))
        }
        if (list.isEmpty() && err != null) throw VWorldException(err)
        return list
    }
}

class VWorldException(message: String) : Exception(message)
