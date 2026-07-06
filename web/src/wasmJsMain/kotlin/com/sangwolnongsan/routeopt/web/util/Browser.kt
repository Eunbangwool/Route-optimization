@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.util

/** 새 탭으로 URL 열기 (외부 지도앱 핸드오프). */
@JsFun("(url) => { window.open(url, '_blank', 'noopener'); }")
external fun openUrl(url: String)

/** 클립보드에 텍스트 복사 (지원 안 되면 무시). */
@JsFun("(t) => { if (navigator.clipboard) navigator.clipboard.writeText(t); }")
external fun copyToClipboard(text: String)
