# 최단 경로 설계 (Route Optimizer)

여러 주소의 **방문 순서를 최단 이동으로 자동 설계**하는 웹 앱.
경로 간 이동 시간·거리는 **티맵(TMap) 실도로 API** 결과를 사용한다.

- **스택**: Kotlin Multiplatform + Compose Multiplatform (Kotlin/WASM), 브라우저 단독 동작
  (농작이 `farm-work-manager` 와 동일 스택)
- **모듈**
  - `:shared` — 순수 Kotlin 도메인 모델 + 직선거리(haversine) fallback 최적화(TSP: 최근접이웃+2-opt)
  - `:web` — Compose MP UI + 티맵 REST 연동(지오코딩·경로최적화) + 티맵 JS SDK 지도

## 동작 방식

1. 방문 주소들을 입력 (첫 번째 = 출발지, 마지막 = 도착지, `왕복` 체크 시 출발지 복귀)
2. 각 주소를 티맵 **지오코딩**(`geo/fullAddrGeo` → 실패 시 `pois` 키워드 검색)으로 좌표 변환
3. 티맵 **경로최적화**(`routes/routeOptimization20`)로 경유지 방문 순서를 실도로 기준 최적화
4. 순서·구간별 거리/시간·총계 표시, `지도에서 경로 보기`로 실도로 폴리라인 렌더
5. 국내 지도앱으로 안내 시작 (**API 키 불필요**, 모바일 앱 스킴):
   - **네이버 지도** `nmap://` — 출발+경유지(최대 5)+도착 전체 전달
   - **티맵** `tmap://` — 최종 목적지 안내
   - **카카오맵** `kakaomap://` — 출발→도착 (경유지 미지원)
6. 티맵 호출 실패 시 직선거리 기준 근사 순서로 자동 대체

## 경로 엔진 (앱 상단에서 선택)

| 엔진 | 지오코딩 | 최적화 | 키 | 비고 |
|------|----------|--------|----|------|
| **티맵** | `geo/fullAddrGeo`·`pois` | `routeOptimization20` | 필요 | 한국 주소 정확도 높음 |
| **OSM 무료** | Nominatim(OSM) | OSRM 공개 데모 `/trip` | **불필요** | 데모서버(rate limit·무SLA·비상업), 정확도 낮을 수 있음 |

- OSM 무료 모드는 키 없이 **주소→좌표부터 순서 최적화·실도로 경로까지** 전부 동작한다.
- 지도 렌더: 티맵 결과는 티맵 JS SDK, OSM 결과는 **Leaflet + OSM 타일**(키 불필요).
- 두 엔진 모두 결과를 국내 지도앱(네이버/티맵/카카오)으로 넘길 수 있다.

## 티맵 앱키 발급 (티맵 엔진 사용 시)

1. https://openapi.sk.com 가입 후 **프로젝트 생성**
2. **자동차 경로안내(경로최적화)** + **지오코딩** + **Maps(JS SDK)** API 추가
3. 발급된 **AppKey** 를 앱 상단 입력칸에 붙여넣기 (브라우저 localStorage 에 저장)
4. 콘솔에서 배포 도메인(및 `localhost`)을 **허용 도메인**에 등록 (CORS/SDK 로드용)

## 로컬 실행

```bash
./gradlew :web:wasmJsBrowserDevelopmentRun   # 개발 서버 (핫리로드)
./gradlew :web:wasmJsBrowserDistribution      # 프로덕션 정적 산출물
# 산출물: web/build/dist/wasmJs/productionExecutable
```

## 배포 · 동작 확인

푸시(`main` 또는 `claude/**`) 시 GitHub Actions(`build-web.yml`)가 WASM 빌드 후 산출물을
`gh-pages` 브랜치로 배포한다.

**최초 1회 설정**: 저장소 **Settings → Pages → Source** 를 `Deploy from a branch` →
브랜치 `gh-pages` / `(root)` 로 지정. (첫 빌드가 `gh-pages` 브랜치를 만든 뒤 설정 가능)

이후 접속 URL:

```
https://eunbangwool.github.io/Route-optimization/
```

**빠른 동작 확인 (키 불필요)**: 상단에서 `OSM 무료` 선택 → 주소 3~4개 입력
(예: `서울역`, `강남역`, `여의도`, `잠실역`) → `최적 경로 계산` → 순서·거리/시간 확인 →
`지도에서 경로 보기` → `네이버 지도로 열기`(모바일).

## 알려진 제한

- 앱키는 브라우저에서 사용되므로 티맵 콘솔의 **도메인 화이트리스트**로 보호해야 한다.
- 티맵 `routeOptimization20` 경유지 수 제한(약 20개)을 초과하지 않도록 입력.
- 브라우저→티맵 직접 호출은 CORS 허용 도메인에서만 동작한다.
