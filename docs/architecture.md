# 햇빛 좌석 설계

> 화면 디자인 규칙은 `docs/DESIGN.md`(#18)에 따로 둔다. 이 문서는 구조와 계산, 외부 연동을 다룬다.

## 1. 빌드 환경

| 항목 | 값 |
|---|---|
| 언어·UI | Kotlin, Jetpack Compose |
| 모듈 | `:app` 단일 모듈, 패키지 `com.sunseat.app` |
| AGP / Gradle | 9.5.1 / 9.9.1 (wrapper) |
| Kotlin | 2.4.21 (AGP 내장 Kotlin, Compose 컴파일러 플러그인) |
| SDK | compileSdk·targetSdk 37, minSdk 26 |
| JDK | 빌드 JDK 25(LTS), 바이트코드 대상 Java 21 |
| 시작 화면 | `androidx.core:core-splashscreen` |

빌드는 명령줄에서 한다(`./gradlew assembleDebug`). Android Studio는 필요 없다.

## 2. 패키지 구조(예정)

```
com.sunseat.app
├─ domain        순수 Kotlin. 태양 위치, 노출 계산, 좌석 추천 (#3~#6)
├─ data          네트워크·API·내장 데이터·설정 저장 (#7~#9, #14~#16)
├─ location      현재 위치와 권한 (#10)
└─ ui            테마·공통 부품(#18), 화면(#11~#13, #15)
```

`domain`은 Android에 의존하지 않아 JVM 단위 테스트로 검증한다.

## 3. 계산

### 3.1 태양 위치(#3)
`SunCalculator`(commons-suncalc): 위도·경도·시각 → 방위각(북쪽 기준 시계 방향, 도), 고도(도)

### 3.2 노출(#4)
- 공통 모델: `Mode`(BUS, SUBWAY, CAR, WALK), `Leg`(수단, 좌표열, 소요시간, 출발·도착 이름), `Route`
- 선분 통과 시각 = 출발 시각 + 누적 소요시간(구간 소요시간을 선분 길이에 비례 배분)
- 상대각 r = (태양 방위각 − 진행 방위각) mod 360. 0~180°는 오른쪽, 180~360°는 왼쪽
- 가중치 = 소요시간 × cos(고도) × |sin r|, 고도 ≤ 0이면 0

### 3.3 좌석 추천(#5, #6)
- 버스·자가용: 차이 없음 기준 15%p, 눈부심 기준 ±20°·고도 15°(조정 가능한 상수)
- 지하철 반대쪽 벤치: 유효 고도 h' = atan(tan h / |sin r|)이 임계값(초기 30°) 이하인 선분만 정면 직사

## 4. 외부 연동

### 4.1 공통 HTTP(#7)
- Retrofit 3 + OkHttp 5 + kotlinx.serialization. 코드는 `com.sunseat.app.data`(`network`, `place`)
- `KakaoAuthInterceptor`가 `Authorization: KakaoAK {키}` 헤더를 붙인다. 키(`BuildConfig.KAKAO_REST_API_KEY`)가 비어 있으면 요청을 보내지 않고 바로 실패한다
- 결과는 `ApiResult`(`Success` / `Failure(ApiError, httpCode)`)로 돌려준다. `ApiError`
  - `MISSING_KEY`: 키 없음(네트워크 호출 없음)
  - `UNAUTHORIZED`: 401·403(잘못된 키, 앱에 해당 API 권한 없음) 또는 공통 오류 코드 -401·-903
  - `QUOTA_EXCEEDED`: 429 또는 공통 오류 코드 -10(호출 한도)·-11(유료 사용 한도, 둘 다 HTTP 400), 본문의 `limit`/`throttl`/`quota` 문구
  - `NETWORK`: 연결 실패·시간 초과
  - `UNKNOWN`: 그 밖의 서버 오류, 해석할 수 없는 응답
- 오류 본문 예: `{"errorType":"AccessDeniedError","message":"..."}`
- `KakaoApis`가 구현을 만들고, 화면은 인터페이스(`PlaceSearchApi` 등)만 받는다

### 4.2 경로 API(#8, #9)
- 대중교통 `/v2/routing/publictraffic`: `routes[].steps[]`(type BUS/SUBWAY/WALKING, `time`, `path.points`)를 `Route`/`Leg`로 변환. 좌표 순서는 [경도, 위도]
- 자가용: 도로 구간별 `vertexes`와 소요시간을 CAR 구간 1개인 `Route`로 변환
- 요청 파라미터·응답 구조는 첫 호출과 공식 문서로 확인해 이 절에 기록한다

### 4.3 장소 검색(#7)
`GET https://dapi.kakao.com/v2/local/search/keyword.json` → `PlaceSearchApi.search(query, near, size)` → `List<Place>`(id, 이름, 지번·도로명 주소, 좌표, 카테고리)

- 요청: `query`(필수), `x`·`y`(중심 경도·위도, 선택), `radius`(m, 0~20000), `size`(1~15, 기본 15), `page`(1~45), `sort`(`accuracy` 기본 / `distance`), `category_group_code`. 앱은 query·x·y·size만 쓴다
- 응답: `documents[]`(`id`, `place_name`, `address_name`, `road_address_name`, `category_name`, `category_group_code`, `phone`, `place_url`, `distance`, `x`=경도, `y`=위도, 좌표는 문자열), `meta`(`total_count`, `pageable_count`, `is_end`, `same_name`)
- 결과가 없으면 `documents`가 빈 배열(200). 좌표를 해석할 수 없는 문서는 건너뛴다. 빈 검색어는 호출하지 않는다

## 5. 저장
- 설정(#15): DataStore
- 최근 검색(#16): 검색어와 수단만. 좌표와 경로 결과는 저장하지 않는다
