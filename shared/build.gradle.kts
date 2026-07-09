// ===================================================================
// :shared 모듈 — 순수 Kotlin 공유 도메인 (모델 + 경로 최적화 알고리즘)
//
// Compose/Android 의존성 없음. wasmJs(:web 이 의존) + jvm(알고리즘 단위테스트).
// 플랫폼 독립적인 데이터 모델과 TSP 최적화 코어(RouteCore)를 담는다.
// 테스트: ./gradlew :shared:jvmTest (브루트포스 대조 정합성 검증)
// ===================================================================

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
