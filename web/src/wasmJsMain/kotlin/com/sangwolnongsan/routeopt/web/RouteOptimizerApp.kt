package com.sangwolnongsan.routeopt.web

import androidx.compose.runtime.Composable
import com.sangwolnongsan.routeopt.web.theme.RouteOptTheme
import com.sangwolnongsan.routeopt.web.ui.RouteOptimizerScreen

@Composable
fun RouteOptimizerApp() {
    RouteOptTheme {
        RouteOptimizerScreen()
    }
}
