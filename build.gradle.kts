// 최상위 빌드 파일. 하위 모듈(:shared, :web)이 사용할 플러그인을 apply false 로 등록.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}
