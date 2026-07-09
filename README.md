# 최단 경로 설계 (Route Optimizer)

여러 주소의 **방문 순서를 최단 이동으로 자동 설계**하는 웹 앱.
경로 간 이동 시간·거리는 **티맵(TMap) 실도로 API** 결과를 사용한다.

- **스택**: Kotlin Multiplatform + Compose Multiplatform (Kotlin/WASM), 브라우저 단독 동작
  (농작이 `farm-work-manager` 와 동일 스택)
- **모듈**
  - `:shared` — 순수 Kotlin 도메인 모델 + **TSP 최적화 코어(`RouteCore`)**
  - `:web` — Compose MP UI + 지도 API 연동(VWorld 검색 / OSRM·티맵 최적화) + 지도 렌더

## 최적화 알고리즘 (`RouteCore`)

비용 행렬 기반 TSP 솔버 (비대칭 행렬 지원, 출발·도착 고정, 왕복 지원):

- **경유지 ≤ 15개**: **Held-Karp 동적계획법** — 해당 비용행렬 기준 **전역 최적해 보장**
  (시간 `O(2^m·m²)`, 공간 `O(2^m·m)` — m=15 에서 수 MB/수십 ms)
- **경유지 16개+**: **Iterated Local Search** — 다중 구성(최근접이웃×3 + 최소삽입)
  → 2-opt/Or-opt 지역탐색(비대칭 정확 delta) → double-bridge 교란 반복 (시간 예산 내)
- OSM 모드는 OSRM **`/table`** 로 실도로 소요시간 행렬을 받아 위 솔버로 순서를 풀고
  **`/route`** 로 최종 경로를 확정한다 → OSRM 자체 `/trip` 휴리스틱보다 우수.
  (`/table` 실패 시 `/trip` fallback)
- 정합성은 `:shared:jvmTest` 에서 **브루트포스 전수해와 대조**로 검증 (CI 자동 실행).

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

## 데이터 소스

- **주소·장소 검색**: VWorld(국토부) 도로명·지번 + 장소(POI) 검색 (JSONP, 등록키)
- **경로 최적화**: OSRM `/table`(실도로 시간행렬) + `RouteCore` + `/route`
- **지도 타일**: Leaflet + 타일 URL(기본 OSM 공개타일)

### ⚠️ 상업적 사용 주의
기본값의 **공개 데모 서버는 상업/과다 사용이 금지**됩니다:
- `router.project-osrm.org`(OSRM 데모) — 개발용만
- `nominatim.openstreetmap.org`, OSM 공개 타일 — 헤비/상업 금지

**상용 배포 시** 앱 하단 **고급 설정**에서 자체 인프라를 지정하세요:
- **OSRM 서버 주소**: 자체 호스팅 OSRM(오픈소스 BSD + OSM 데이터 ODbL, 상업 가능).
  Korea OSM extract 로 `osrm-extract → osrm-partition → osrm-customize → osrm-routed` 실행 후 그 주소 입력.
- **지도 타일 URL**: 상용 타일(자체 호스팅/유료) 또는 라이선스 확인된 제공자.
- **검색**: VWorld 는 등록키로 상업 가능(약관·표기 의무 확인).

설정값은 브라우저 localStorage(`osrm_base`, `tile_url`, `tile_attr`)에 저장된다.
(티맵 연동 코드 `TmapClient` 는 유지 — 필요 시 상용 티맵 계약으로 재노출 가능)

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
