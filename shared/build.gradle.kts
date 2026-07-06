// ===================================================================
// :shared 모듈 — 순수 Kotlin 공유 도메인 (모델 + 경로 최적화 알고리즘)
//
// Compose/Android 의존성 없음. wasmJs 타겟만 선언하여 :web 이 의존.
// 플랫폼 독립적인 데이터 모델과 TSP(직선거리 fallback) 로직을 담는다.
// ===================================================================

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
