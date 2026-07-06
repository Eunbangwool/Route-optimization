package com.sangwolnongsan.routeopt.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/** 웹 진입점. index.html 의 #compose-target 에 Compose UI 마운트. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val target = document.getElementById("compose-target")
        ?: error("Element with id 'compose-target' not found in index.html")
    ComposeViewport(target) {
        RouteOptimizerApp()
    }
}
