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
Retrofit + OkHttp + kotlinx.serialization. `Authorization: KakaoAK {키}` 헤더. 오류 구분: 키 없음, 인증 실패, 한도 초과, 네트워크 오류

### 4.2 경로 API(#8, #9)
- 대중교통 `/v2/routing/publictraffic`: `routes[].steps[]`(type BUS/SUBWAY/WALKING, `time`, `path.points`)를 `Route`/`Leg`로 변환. 좌표 순서는 [경도, 위도]
- 자가용: 도로 구간별 `vertexes`와 소요시간을 CAR 구간 1개인 `Route`로 변환
- 요청 파라미터·응답 구조는 첫 호출과 공식 문서로 확인해 이 절에 기록한다

### 4.3 장소 검색(#7)
카카오 로컬 키워드 검색 → 장소 이름·주소·좌표 목록. 요청 파라미터와 응답 필드는 #7에서 공식 문서로 확인해 기록한다

## 5. 저장
- 설정(#15): DataStore
- 최근 검색(#16): 검색어와 수단만. 좌표와 경로 결과는 저장하지 않는다
