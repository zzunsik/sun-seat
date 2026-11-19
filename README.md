# 햇빛 좌석 (sun-seat)

버스·지하철·자가용으로 이동할 때 햇빛을 피하거나 받을 수 있는 좌석을 구간별로 알려 주는 Android 앱입니다.

## 현재 상태

출발·도착 검색, 경로 후보 비교, 구간별 좌석 추천, 지도와 3D 좌석 시뮬레이션까지 동작합니다. 지금은 카카오맵과 비교해 길찾기 화면의 정보 위계와 밀도를 다듬고 있습니다([디자인 계약](docs/DESIGN.md), [#97](https://github.com/zzunsik/sun-seat/issues/97)). 남은 작업은 [열린 이슈](https://github.com/zzunsik/sun-seat/issues)에 있습니다.

경로는 현재 시각 기준으로 받아 오고, 햇빛은 사용자가 고른 출발 시각으로 계산합니다. 실시간 도착·혼잡도처럼 API가 주지 않는 정보는 표시하지 않습니다.

## 빌드

JDK 25와 Android SDK(platform 37)가 필요합니다. Android Studio는 없어도 됩니다.

1. 저장소 루트에 `local.properties`를 만듭니다. 이 파일은 커밋하지 않습니다.

   ```properties
   sdk.dir=/path/to/Android/sdk
   KAKAO_REST_API_KEY=<카카오 REST API 키>
   KAKAO_NATIVE_APP_KEY=<카카오 네이티브 앱 키>
   ```

   키가 없어도 빌드는 됩니다. 이때 장소 검색·경로 찾기와 지도가 동작하지 않고, 지도 자리에는 안내가 나옵니다. 키를 받는 방법은 아래 "카카오 지도 설정"에 있습니다.

2. 디버그 APK를 빌드합니다.

   ```bash
   ./gradlew assembleDebug
   ```

   APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생깁니다.

3. 검사는 CI와 같은 명령으로 돌립니다. `verifyRoborazziDebug`는 단위 테스트 전체를 돌리면서 화면 스크린샷을 기준 이미지와 비교합니다.

   ```bash
   ./gradlew assembleDebug lint verifyRoborazziDebug
   ```

## 빌드 설정

| 항목 | 값 | 근거 |
|---|---|---|
| 빌드 JDK | 25 | CI와 같은 버전 |
| 바이트코드 대상 | Java 21 | `app/build.gradle.kts`의 `sourceCompatibility`, `jvmTarget` |
| compileSdk, targetSdk | 37 | 최신 안정 플랫폼 |
| minSdk | 26 (Android 8.0) | `app/build.gradle.kts` 설정값 |
| 애플리케이션 ID | `com.sunseat.app` | 카카오 개발자 콘솔에 등록한 패키지와 같아야 한다 |

의존성 버전은 `gradle/libs.versions.toml`에서만 관리하고, 업데이트는 Dependabot PR로 받습니다. 구조와 계산 방식은 [설계 문서](docs/architecture.md)에 있습니다.

| 용도 | 라이브러리 |
|---|---|
| 화면 | Jetpack Compose, Material 3, Lottie |
| 화면 이동 | Navigation 3 |
| 화면 상태 | Lifecycle(ViewModel) |
| 지도 | 카카오맵 Android SDK v2 |
| 3D 좌석 시뮬레이션 | Filament |
| 태양 위치 | commons-suncalc |
| 네트워크 | Retrofit, OkHttp, kotlinx.serialization(JSON) |
| 저장 | DataStore |
| 위치 | Play services location |
| 비동기 | kotlinx.coroutines |
| 테스트 | JUnit 4, Robolectric, Compose UI 테스트, Roborazzi(스크린샷 비교), MockWebServer, `kotlinx-coroutines-test`, compose-lints |

## 카카오 지도 설정

카카오맵 Android SDK v2를 씁니다([공식 문서](https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/)).

1. 카카오 개발자 콘솔에서 앱을 만들고 카카오맵 사용 설정을 켭니다. REST API 키와 네이티브 앱 키를 `local.properties`에 넣습니다.
2. 앱의 Android 플랫폼에 패키지 `com.sunseat.app`과 디버그 서명 키 해시를 등록합니다. 키 해시는 인증서 바이트에 SHA-1을 적용하고 Base64로 인코딩한 값입니다. Git Bash에서 다음 명령으로 구합니다(JDK의 keytool과 OpenSSL 필요).

   ```bash
   keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64
   ```

3. 네이티브 앱 키는 APK에 들어가므로 꺼내 볼 수 있다고 보고, 콘솔에서 패키지와 키 해시로 사용을 제한합니다.

키가 비어 있거나 지도 인증이 실패하면 지도 자리에 안내를 보여 주고, 나머지 자리 추천은 그대로 동작합니다. SDK의 R8 보존 규칙은 `app/proguard-rules.pro`에 있습니다.

지도는 일반 스타일 하나만 쓰므로 밝은 바탕으로 고정하고, 지도 주변 UI만 앱의 화면 모드를 따릅니다. 스크린샷 테스트는 `LocalMapRenderer provides MapRenderer.PREVIEW`로 네이티브 SDK 대신 경로 좌표를 단순 투영해 그립니다. 실제 지도 표시와 생명주기 복귀는 기기에서 따로 확인합니다.

## 개인용 릴리스 APK

업데이트 설치에는 **같은 패키지·서명 키**와 **더 큰 versionCode**가 필요합니다. 디버그 APK에서 릴리스 APK로 바꾸면 서명이 달라 덮어 설치할 수 없고, 지우고 설치하면 저장한 설정과 장소가 사라집니다.

### 서명 키 준비

처음 한 번만 JDK의 `keytool`로 만듭니다. 비밀번호는 프롬프트에 입력하고 명령 인수나 Git에 넣지 않습니다. 키를 잃으면 같은 앱의 업데이트를 만들 수 없으므로 저장소 밖의 안전한 곳에 백업합니다.

```bash
mkdir -p "$HOME/.sun-seat"
keytool -genkeypair -keystore "$HOME/.sun-seat/release.jks" -alias sun-seat \
  -keyalg RSA -keysize 3072 -validity 10000
```

`local.properties`에 다음을 더합니다. 경로는 절대 경로나 저장소 기준 상대 경로입니다. Windows 경로는 `/`를 쓰거나 `\`를 이스케이프합니다.

```properties
RELEASE_STORE_FILE=/path/to/.sun-seat/release.jks
RELEASE_STORE_PASSWORD=<키 저장소 비밀번호>
RELEASE_KEY_ALIAS=sun-seat
RELEASE_KEY_PASSWORD=<키 비밀번호>
```

네 항목을 모두 넣으면 릴리스 서명을 씁니다. 일부만 넣으면 구성 단계에서 실패합니다. 하나도 없으면 비서명 APK가 만들어지고 **설치할 수 없습니다**. 디버그 키로 대신 서명하지 않습니다.

### 버전·빌드·설치

1. 배포할 때마다 `app/build.gradle.kts`의 `versionCode`를 1 이상 올립니다(재사용하지 않음). `versionName`은 `주.부.수정` 형태로 변경 내용을 따라 올립니다.
2. `./gradlew assembleDebug lint verifyRoborazziDebug assembleRelease`를 실행합니다. 배포할 파일은 `app/build/outputs/apk/release/app-release.apk`입니다. `app-release-unsigned.apk`는 배포하지 않습니다.
3. build-tools의 `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`로 서명과 이전 배포와 같은 인증서 지문인지 확인합니다.
4. `adb -s <기기ID> install -r app/build/outputs/apk/release/app-release.apk`로 설치합니다. 첫 설치와 업데이트 모두 같은 명령입니다. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`은 보통 서명이 다르다는 뜻입니다. 앱을 자동으로 지우거나 낮은 버전 설치를 강제하지 않습니다.
5. 버전·설정·저장 장소가 유지되는지 확인합니다.

릴리스 APK의 지도는 콘솔의 Android 플랫폼에 **릴리스 키 해시**도 등록해야 인증됩니다. 해시는 위 명령에서 별칭과 키 저장소만 바꿔 구합니다(`-alias sun-seat -keystore "$HOME/.sun-seat/release.jks"`, 비밀번호는 프롬프트로 입력). 릴리스는 지금 minify를 끈 상태입니다. R8을 켜면 지도까지 다시 확인합니다.

## 문서

- [요구사항](docs/requirements.md): 화면별 동작과 데이터 제약
- [설계](docs/architecture.md): 패키지 구조, 햇빛·좌석 계산, 카카오 API 연동
- [디자인 계약](docs/DESIGN.md): 화면별 과업과 정보 위계, 색·글자·간격 토큰, 지도 경로선, 상태와 제스처, 접근성
- [디자인 레퍼런스](docs/design-references.md): 카카오맵과 직접 비교한 내용과 정리한 원칙
- [지하철 지상 구간 데이터](docs/subway-ground-data.md): 지상·지하 판정에 쓴 공공데이터의 출처와 판정 기준
- [3D 시뮬레이션 실기기 점검(#56)](docs/qa-56.md): 3D 좌석 시뮬레이션을 기기에서 확인한 기록

## 작업 흐름

이슈 → 이슈별 브랜치와 worktree → PR → CI(`ci-passed`) 통과 → squash 머지 순서로 진행합니다. `main`은 GitHub ruleset으로 보호되어 CI가 통과하고 최신 `main`을 포함한 PR만 머지됩니다. 자세한 규칙과 공개 저장소에서 지킬 것은 [AGENTS.md](AGENTS.md)에 있습니다.

보안 문제는 공개 이슈 대신 [SECURITY.md](SECURITY.md)의 방법으로 알려 주세요.

## 데이터

| 파일 | 출처 |
|---|---|
| `app/src/main/resources/subway_ground.csv` | 서울교통공사·국가철도공단 공공데이터(이용허락범위 제한 없음)에서 노선·역명·지상 여부만 추출. 출처와 판정 기준은 [지하철 지상 구간 데이터](docs/subway-ground-data.md) |
| `app/src/test/resources/kakao/*.json` | 직접 만든 예시 응답. 실제 API 응답이 아님 |

경로·장소 정보는 실행 중에 카카오 API에서 받아 오며 저장소에 넣지 않습니다.

## 글꼴

화면 글꼴은 [Pretendard](https://github.com/orioncactus/pretendard) 가변 글꼴(`app/src/main/res/font/pretendard_variable.ttf`)입니다. SIL Open Font License 1.1로 배포되며, 라이선스 전문은 `docs/licenses/Pretendard-OFL.txt`와 앱에 함께 들어가는 `app/src/main/res/raw/pretendard_ofl.txt`에 있습니다.

## 아이콘과 그림

- 일반 아이콘(`app/src/main/res/drawable/ic_ms_*.xml`)은 [Material Symbols](https://github.com/google/material-design-icons)(Apache License 2.0)의 Rounded 모양을 벡터 XML로 넣었습니다.
- 런처 아이콘, 스플래시, 로딩 애니메이션(`app/src/main/res/raw/sun_loading.json`)은 직접 만들었습니다.
- 3D 차량·좌석 모형은 코드(`CabinProfile`)로 만들며 외부 모델이나 텍스처를 넣지 않았습니다. 재질(`app/src/main/assets/simulation/*.filamat`)은 `tools/simulation/*.mat`을 Filament로 컴파일한 것입니다([방법](tools/simulation/README.md)).
