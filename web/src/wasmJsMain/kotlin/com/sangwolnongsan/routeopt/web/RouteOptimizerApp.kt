package com.sangwolnongsan.routeopt.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sangwolnongsan.routeopt.web.theme.RouteOptTheme
import com.sangwolnongsan.routeopt.web.ui.RouteOptimizerScreen
import com.sangwolnongsan.routeopt.web.util.roAppReady

@Composable
fun RouteOptimizerApp() {
    // 첫 컴포지션 진입 시 로딩 스플래시 제거 (Compose 는 Shadow DOM 에 렌더하므로
    // HTML 쪽 자식 감지로는 마운트를 알 수 없어 명시적으로 신호를 보낸다).
    LaunchedEffect(Unit) { roAppReady() }
    RouteOptTheme {
        RouteOptimizerScreen()
    }
}
