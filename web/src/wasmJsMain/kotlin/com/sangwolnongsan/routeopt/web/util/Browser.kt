@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.sangwolnongsan.routeopt.web.util

/** Compose 첫 프레임 진입 시 호출 → index.html 의 로딩 스플래시 제거. */
@JsFun("() => { if (window.roAppReady) window.roAppReady(); }")
external fun roAppReady()

/** 모바일 기기 여부 (앱 스킴 vs 웹 지도 URL 분기용). */
@JsFun("() => /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)")
external fun isMobileDevice(): Boolean

/** 새 탭으로 URL 열기 (외부 지도앱 핸드오프). */
@JsFun("(url) => { window.open(url, '_blank', 'noopener'); }")
external fun openUrl(url: String)

/** 클립보드에 텍스트 복사 (지원 안 되면 무시). */
@JsFun("(t) => { if (navigator.clipboard) navigator.clipboard.writeText(t); }")
external fun copyToClipboard(text: String)
