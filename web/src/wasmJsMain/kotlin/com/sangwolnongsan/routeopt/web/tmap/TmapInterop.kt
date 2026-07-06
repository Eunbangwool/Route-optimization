@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.tmap

import kotlin.js.JsString
import kotlin.js.Promise

/**
 * 티맵 REST 호출과 브라우저 저장소를 위한 최소한의 JS interop.
 *
 * fetch 는 브라우저에서 apis.openapi.sk.com 으로 직접 요청한다 (앱키는 헤더/쿼리).
 * CORS: 티맵 오픈API 는 등록된 도메인/localhost 에 대해 허용된다.
 */

/** GET 요청. 응답 본문을 텍스트로 반환하는 Promise. */
@JsFun(
    """(url, appKey) => fetch(url, {
        method: 'GET',
        headers: { 'appKey': appKey, 'Accept': 'application/json' }
    }).then(r => r.text())""",
)
external fun tmapGet(url: String, appKey: String): Promise<JsString>

/** POST(JSON) 요청. 응답 본문을 텍스트로 반환하는 Promise. */
@JsFun(
    """(url, appKey, body) => fetch(url, {
        method: 'POST',
        headers: { 'appKey': appKey, 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: body
    }).then(r => r.text())""",
)
external fun tmapPost(url: String, appKey: String, body: String): Promise<JsString>

/** URL 컴포넌트 인코딩 (한글 주소 쿼리용). */
@JsFun("(s) => encodeURIComponent(s)")
external fun encodeURIComponent(s: String): String

/** localStorage 읽기 (없으면 빈 문자열). */
@JsFun("(k) => (localStorage.getItem(k) ?? '')")
external fun lsGet(key: String): String

/** localStorage 쓰기. */
@JsFun("(k, v) => { localStorage.setItem(k, v); }")
external fun lsSet(key: String, value: String)

/**
 * index.html 의 지도 헬퍼 호출. 최적화 결과(JSON)를 넘기면 티맵 JS SDK 로
 * 마커 + 실도로 폴리라인을 그리고 전체화면 오버레이를 띄운다.
 */
@JsFun("(json) => window.roShowMap(json)")
external fun roShowMap(json: String)
