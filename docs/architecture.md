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

### 수단별 자리 세부 (#55)

`TravelMode.TAXI`는 경로의 `Mode.CAR`를 재사용한다. 택시는 승객용 선택이므로 경로 API·기하 계산을 새로 늘리지 않고 `TripAdvisor.advise`에 선택한 수단을 전달한다. 택시 요금은 기존 `CarRouteOption.taxiFare`를 사용한다.

`LegAdvice.Sit`은 `SeatSpot`(좌우, 구역, 창가/통로, 자동차 좌석), 대체 자리, `SeatSwitch` 목록과 이유 코드를 담는다. 표시 문구는 리소스에만 있다. 앞·뒤는 `illumination`의 기존 ±25° 판정을 재사용해 상대각 90° 전후로 분리한다. 낮은 앞·뒤 해(고도 25° 이하) 및 옆 앞쪽(25~60°)/옆 뒤쪽(120~155°)의 누적 시간을 비교해 버스 구역을 정한다. 받기 모드는 구역을 반전한다. 이 고도·각도, 버스 유효 고도 30°, 변경 최소 5분은 조정 가능한 생성자 인자와 이름 있는 기본 상수다. 버스 유효 고도는 지하철의 `effectiveAltitude`를 재사용하며 낮은 옆 해가 구간 절반 이상이면 창가 안내를 더한다. 이 규칙은 차량 치수·선팅을 반영하지 않은 추정이다.

좌석 변경은 동일한 좌우 판정과 지하철 선호로 만든 연속 추천을 5분 이상 유지할 때만 생성한다. 첫 지속 추천이 출발 자리다. `LegResult.followedSun`과 전체 합계는 변경 시점에 따라 선택한 쪽의 `illumination`을 합산한다. 양쪽 타임라인은 계속 각 쪽의 실제 판정을 표시한다. `TripTimeline`은 같은 `SeatSwitch`를 이진 탐색해 시각별 추천 위치를 표시하며 별도 자리 계산을 하지 않는다.

카카오 응답의 정차역에는 이름만 있으므로 정확한 중간 정류장 변경 시각을 만들지 않는다. 이름과 시각을 함께 아는 지점만 `SeatSwitch.stopName`으로 전달할 수 있으며, 현재는 출발 후 분을 쓴다. 지하철 정면 직사 시간은 같은 벤치에 대한 연속 선분을 합친다. 지상·지하가 섞인 탑승 단계는 역 사이 동일 비율로 시간과 경로 길이를 나눈 **추정** 구간이며 화면에 이를 표시한다. 지하/미확인 양끝 역 사이는 제외한다. 이 근사는 실제 지상 진입 시점과 다를 수 있다. 호차 앞·뒤는 옆 창 노출을 개선할 근거가 없어 추천하지 않는다.

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
- 대중교통(#8): `GET https://dapi.kakao.com/v2/routing/publictraffic` → `TransitRouteApi.search(origin, destination)` → `ApiResult<TransitRoutes>`
  - 요청: `start_x`·`start_y`·`end_x`·`end_y`(경도·위도, 필수). 선택 `s_name`·`e_name`(지점 이름), `input_coord`·`output_coord`(기본 WGS84). 출발 시각 파라미터는 없다
  - 응답: `status`, `properties`(`total`, `bus`, `subway`, `busAndSubway`, `landingURL`), `routes[]`(status가 OK일 때)
    - `routes[].properties`: `type`(BUS/SUBWAY/BUS_AND_SUBWAY), `totalDistance`(m), `totalTime`(초), `transfers`, `fare.value`(원, 없을 수 있음)
    - `routes[].steps[].properties`: `type`(BUS/SUBWAY/WALKING), `time`(초), `distance`(m), `guidance`, `stops[].name`, `vehicles[]`(`name`, `type`: 간선·지선·직행·시외·일반·급행 등, 도보는 없음)
    - `routes[].steps[].path.points`: `[경도, 위도]` 배열. 버스·지하철·도보 단계 모두 있다
  - `status`: OK, NO_RESULTS, STARTNODES_NULL, ENDNODES_NULL, EQUAL_POINTS → 빈 후보 + `NoRouteReason`. INVALID_REQUEST → `ApiError.UNKNOWN`
  - 변환: 단계마다 `Leg` 1개(BUS→BUS, SUBWAY→SUBWAY, WALKING→WALK). 이름은 `stops` 첫·끝. 문서에 없는 단계 종류가 섞인 경로는 건너뛴다
  - 첫 단계는 승차 정류장에서 시작하고 마지막 단계는 하차 정류장에서 끝난다. 출발지→승차, 하차→도착지 도보는 응답에 없다
  - `Route.duration`(단계 합)은 이동 시간만이고 대기·접근 시간이 빠진다. 전체 소요시간은 `TransitRouteOption.totalDuration`(`totalTime`)
  - 구간별 노선·정류장·안내 문구는 `TransitRouteOption.legDetails`(`legs`와 같은 순서)
- 자가용(#9): `GET https://apis-navi.kakaomobility.com/v1/directions` → `CarRouteApi.search(origin, destination)` → `ApiResult<CarRouteResult>`
  - 요청: `origin`·`destination`(`경도,위도`, 필수). 선택 `waypoints`(최대 5개, `|` 구분), `priority`(RECOMMEND 기본/TIME/DISTANCE/MAIN_ROAD/NO_TRAFFIC_INFO), `avoid`(ferries·toll·motorway·schoolzone·uturn), `alternatives`, `road_details`, `car_type`·`car_fuel`·`car_hipass`, `summary`. 앱은 origin·destination만 쓴다. 출발 시각은 이 API로 지정할 수 없다(호출 시점 교통 기준)
  - 응답: `trans_id`, `routes[]`(`result_code`, `result_msg`, `summary`, `sections[]`)
    - `summary`: `origin`·`destination`(`name`은 좌표 입력 시 빈 문자열, `x`, `y`), `priority`, `bound`, `fare`(`taxi`, `toll`, 원), `distance`(m), `duration`(초)
    - `sections[]`(경유지 수 + 1): `distance`, `duration`, `bound`, `roads[]`(`name`, `distance`, `duration`, `traffic_speed`, `traffic_state`, `vertexes`), `guides[]`
    - `vertexes`: `[x0, y0, x1, y1, ...]` 평면 배열(경도, 위도 순). 이어지는 도로의 끝·시작 좌표가 겹친다
  - `result_code`: 0 성공. 실패도 HTTP 200으로 온다. 확인한 값 102(출발지 주변 도로 없음), 103(도착지 주변 도로 없음), 104(출발·도착 5m 이내) → `CarNoRouteReason`. 좌표 형식 오류는 HTTP 400 `{"code":-2,...}` → `ApiError.UNKNOWN`
  - 변환: 모든 `roads[].vertexes`를 이어 CAR 구간 1개(연속 중복 좌표 제거), 소요시간은 `summary.duration`. 거리·통행료·택시 요금은 `CarRouteOption`
- 요청 파라미터·응답 구조는 첫 호출과 공식 문서로 확인해 이 절에 기록한다

### 4.3 장소 검색(#7)
`GET https://dapi.kakao.com/v2/local/search/keyword.json` → `PlaceSearchApi.search(query, near, size)` → `List<Place>`(id, 이름, 지번·도로명 주소, 좌표, 카테고리)

- 요청: `query`(필수), `x`·`y`(중심 경도·위도, 선택), `radius`(m, 0~20000), `size`(1~15, 기본 15), `page`(1~45), `sort`(`accuracy` 기본 / `distance`), `category_group_code`. 앱은 query·x·y·size만 쓴다
- 응답: `documents[]`(`id`, `place_name`, `address_name`, `road_address_name`, `category_name`, `category_group_code`, `phone`, `place_url`, `distance`, `x`=경도, `y`=위도, 좌표는 문자열), `meta`(`total_count`, `pageable_count`, `is_end`, `same_name`)
- 결과가 없으면 `documents`가 빈 배열(200). 좌표를 해석할 수 없는 문서는 건너뛴다. 빈 검색어는 호출하지 않는다

### 4.4 좌표→주소(#85)
`GET https://dapi.kakao.com/v2/local/geo/coord2address.json` → `ReverseGeocodeApi.address(location)` → `String?`(도로명 주소, 없으면 지번 주소, 둘 다 없으면 null)

- 장소 검색과 같은 REST 키·호스트·오류 분류(`kakaoCall`)를 쓴다
- 요청: `x`(경도)·`y`(위도) 필수, `input_coord`(기본 WGS84). 앱은 x·y만 쓴다
- 응답: `documents[]`의 `road_address`(`address_name`, `building_name` 등, 없으면 null)와 `address`(`address_name` 등). 주소가 없는 좌표(바다 등)는 빈 배열
- 지도 선택 화면이 지도가 멈출 때만 디바운스해 부른다(매 프레임 호출 금지). 건물 이름·근처 상호는 장소 이름으로 쓰지 않는다

## 5. 저장
- 설정(#15): DataStore
- 최근 검색(#16): 검색어와 수단만. 좌표와 경로 결과는 저장하지 않는다

## 3D 햇빛 시뮬레이션 (#56)

#55의 수단·SeatSpot·CarSeat·자리 바꾸기 결과는 추천의 단일 원본이다.
시뮬레이션은 이를 표준 차량의 대표 좌석에 매핑하고 사용자 선택은 구간별로
별도 저장한다. 버스·광역버스·지하철·택시·자가용 프로필을 구분한다.

TripTimeline은 재생 시각과 보간 위치의 태양 방위각·고도를 계산한다.
CabinSimulation은 순수 Kotlin으로 차량 좌표(+X 오른쪽/+Y 위/-Z 앞)에서
창 개구부·차체·지붕·기둥·좌석 등받이에 의한 직사광 도달을 판정한다.
좌판/등받이 표본의 반직선을 태양 쪽으로 추적하며 창 법선 기준 입사각
(정면 입사 0°)을 태양 고도와 구분한다. 지하·미상·야간·좌표 누락을 구분한다.

Filament는 구조와 표본 기반 햇빛/그늘 영역을 3D로 표시한다. 카메라·색보정·
그림자 픽셀 밝기는 좌석 판정에 영향을 주지 않는다. 절개 화면에서 지붕을
생략해도 계산상 지붕은 유지한다. GPU 자원은 UI 스레드에서 생성/해제하고
백그라운드에서는 프레임을 중지한다. 자산·프로필 가정과 검증 진입점은
 tools/simulation/README.md를 따른다.

실제 차량 치수·선팅·커튼·승객·주변 건물·날씨는 반영하지 않는다. #55의
구간 전체 근사 추천과 현재 순간의 상세 표준모델 노출은 다를 수 있으며
상세 장면이 추천을 새로 계산하거나 추천에 맞춰 광선을 숨기지 않는다.

새 시뮬레이션 진입은 자동으로 재생한다. 저장된 일시정지/완료 상태는 유지하며 동작 줄이기 설정이면 수동 탐색만 제공한다. 위 지도는 이동/회전·시각·진행률과 대략적인 햇빛 방향을, 아래 3D는 같은 시각의 선택 좌석 상세 범위를 표시한다. 지도 스타일 변경은 카카오 SDK의 invalidate를 호출해 즉시 반영한다.

