# Claude Code 작업 규칙 (Route Optimizer)

## 토큰 절약
1. 세션 시작: `git log -3` + 변경 대상 파일만 읽기. repo 전체 탐색 금지.
2. 필요한 파일/범위만 읽기, 중복 읽기 금지.
3. 수정 보고는 diff 요약만. 빌드 로그는 오류 줄만 발췌.
4. 한 세션 = 한 작업. 완료 즉시 commit/push.

## 아키텍처
- `:shared` = 순수 Kotlin 모델 + 최적화 알고리즘 (플랫폼 독립, 테스트 용이).
- `:web` = Compose MP(WASM) UI + 티맵 REST/JS SDK 연동. HTTP 는 fetch interop(`tmap/TmapInterop.kt`).
- 티맵 응답 파싱은 `tmap/TmapClient.kt` 에 집중. 지도 렌더 JS 는 `resources/index.html` 의 `window.roShowMap`.

## 빌드 검증
- 원격 환경에선 로컬 WASM 빌드가 무겁다 → push 후 CI(`build-web.yml`)가 검증.
- `claude/**` 브랜치 빌드 실패 시 CI 가 `ci-logs/web-build-error.log` 를 같은 브랜치에 커밋한다. 그 파일을 읽어 진단.
