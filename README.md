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

## 티맵 앱키 발급

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

## 배포

`main` 브랜치 푸시 시 GitHub Actions(`build-web.yml`)가 WASM 빌드 → artifact 생성.
정적 산출물을 임의의 정적 호스팅(GitHub Pages 등)에 올리면 된다.

## 알려진 제한

- 앱키는 브라우저에서 사용되므로 티맵 콘솔의 **도메인 화이트리스트**로 보호해야 한다.
- 티맵 `routeOptimization20` 경유지 수 제한(약 20개)을 초과하지 않도록 입력.
- 브라우저→티맵 직접 호출은 CORS 허용 도메인에서만 동작한다.
