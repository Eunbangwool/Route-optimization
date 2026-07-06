@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.util

import kotlin.js.JsString
import kotlin.js.Promise

/** 헤더 없는 일반 GET. 응답 본문 텍스트를 반환하는 Promise. (Nominatim/OSRM 용) */
@JsFun("(url) => fetch(url, { headers: { 'Accept': 'application/json' } }).then(r => r.text())")
external fun httpGetText(url: String): Promise<JsString>
