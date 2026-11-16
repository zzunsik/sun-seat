# 햇빛 좌석

버스·지하철·자가용으로 이동할 때 햇빛을 피하거나 받을 수 있는 좌석을 구간별로 알려 주는 Android 앱입니다.

- 요구사항: [docs/requirements.md](docs/requirements.md)
- 설계(구조·계산·연동): [docs/architecture.md](docs/architecture.md)
- 디자인 계약: [docs/DESIGN.md](docs/DESIGN.md)

## 빌드

필요한 것: JDK 25, Android SDK(platform 37). Android Studio는 필요 없습니다.

1. 저장소 루트에 `local.properties`를 만듭니다. 이 파일은 커밋하지 않습니다.
   ```properties
   sdk.dir=C\:\\Users\\<사용자>\\AppData\\Local\\Android\\Sdk
   KAKAO_REST_API_KEY=<카카오 REST API 키>
   ```
   키가 없어도 빌드는 됩니다(검색·경로 기능만 동작하지 않음).
2. 빌드와 테스트
   ```bash
   ./gradlew assembleDebug test
   ```
   APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생깁니다.

## 작업 흐름

자세한 규칙은 [AGENTS.md](AGENTS.md)에 있습니다.

1. GitHub 이슈를 만들거나 기존 이슈를 고른다
2. `feat/<이슈번호>-<요약>` 브랜치에서 작업한다
3. `./gradlew assembleDebug lint test`가 통과하면 `Closes #번호`를 넣어 PR을 연다
4. CI(빌드·lint·단위 테스트)가 통과하면 squash 병합한다

### 지도 설정 (#38)

카카오맵 Android SDK v2를 사용한다. 카카오 개발자 콘솔의 앱에서 카카오맵 사용 설정을 켜고 플랫폼 키의 네이티브 앱 키를 로컬 설정에 추가한다.

```properties
KAKAO_NATIVE_APP_KEY=네이티브_앱_키
```

앱의 Android 플랫폼에 패키지 `com.sunseat.app`와 디버그 서명 키 해시를 등록한다. 키 해시는 인증서 바이트에 SHA-1을 적용한 뒤 Base64로 인코딩한 값이다. Git Bash에서 다음 명령으로 구한다(JDK의 keytool과 OpenSSL 필요).

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android | openssl sha1 -binary | openssl base64
```

키는 `local.properties`에만 두고 커밋하지 않는다. 키가 비어 있거나 지도 인증이 실패하면 지도 영역에 안내를 표시하고 나머지 자리 추천은 유지한다. 릴리스 APK는 #17에서 만드는 서명 키의 해시도 같은 콘솔에 등록해야 한다. SDK R8 보존 규칙은 `app/proguard-rules.pro`에 있다.

지도 스크린샷은 `LocalMapRenderer provides MapRenderer.PREVIEW`로 네이티브 SDK 대신 실제 경로 좌표를 단순 투영한다. 이는 테스트·미리보기용이며 실제 지도 표시와 생명주기 복귀는 기기에서 별도 확인한다. SDK의 공개 MapType에는 일반/위성 지도만 있어 지도 자체는 일반 스타일을 유지하고 주변 UI만 앱 화면 모드를 따른다.

공식 문서: https://apis.map.kakao.com/android_v2/docs/getting-started/quickstart/

## 개인용 릴리스 APK (#17)

업데이트 설치는 **같은 패키지·서명 키**와 **더 큰 versionCode**가 필요합니다. 기존에 릴리스 키가 있다면 그대로 사용하세요. 디버그 APK에서 릴리스 APK로 바꾸면 서명이 달라 덮어 설치할 수 없습니다. 삭제 후 설치하면 저장한 설정·장소가 사라지므로 먼저 확인하세요.

### 서명 키 준비

최초 한 번만 JDK 25의 `keytool`로 만듭니다. 비밀번호는 프롬프트에 입력하고 명령 인수나 Git에 넣지 않습니다. 키를 잃으면 같은 앱의 업데이트를 만들 수 없으므로 안전한 별도 저장소에 백업하세요.

```bash
mkdir -p "$HOME/.sun-seat"
keytool -genkeypair -keystore "$HOME/.sun-seat/release.jks" -alias sun-seat \
  -keyalg RSA -keysize 3072 -validity 10000
```

저장소의 `local.properties`에 다음을 추가합니다. Windows 경로는 `/`를 쓰거나 기존 sdk.dir 예시처럼 이스케이프합니다. 경로는 절대 경로 또는 저장소 기준 상대 경로입니다.

```properties
RELEASE_STORE_FILE=/Users/<사용자>/.sun-seat/release.jks
RELEASE_STORE_PASSWORD=<키 저장소 비밀번호>
RELEASE_KEY_ALIAS=sun-seat
RELEASE_KEY_PASSWORD=<키 비밀번호>
```

네 항목을 모두 설정하면 릴리스 서명을 사용합니다. 일부만 설정하면 구성 단계에서 실패합니다. 아무것도 없으면 CI와 로컬 빌드는 비서명 APK를 만들며 **그 APK는 설치할 수 없습니다**. 디버그 키로 자동 대체하지 않습니다. 키·비밀번호는 `local.properties`에서만 읽고 환경 변수·소스에 넣지 않습니다. `.gitignore`가 local.properties, `*.jks`, `*.keystore`를 제외합니다.

### 버전·빌드·설치

1. `app/build.gradle.kts`의 `versionCode`를 배포할 때마다 1 이상 올립니다(재사용 금지). `versionName`은 사용자에게 보이는 `주.부.수정` 형태이며 변경 내용을 따라 올립니다. 같은 기능 수정 재배포도 versionCode는 올립니다. applicationId는 유지합니다.
2. `./gradlew assembleDebug lint test assembleRelease`를 실행합니다. 서명이 설정된 파일은 `app/build/outputs/apk/release/app-release.apk`입니다. `app-release-unsigned.apk`는 배포하지 않습니다.
3. SDK build-tools의 `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`로 서명 성공 및 이전 배포와 인증서 지문이 같은지 확인합니다. 키가 콘솔에 출력되는 명령은 사용하지 않습니다.
4. `adb devices`로 대상 기기를 확인한 뒤 `adb -s <기기ID> install -r app/build/outputs/apk/release/app-release.apk`로 설치합니다. 첫 설치와 업데이트 모두 같은 명령을 씁니다. 여러 기기가 있으면 항상 `-s`를 붙입니다.
5. 앱에서 버전·설정·저장 장소가 유지되는지 확인합니다. 다음 versionCode로 빌드하고 같은 키로 `install -r`해 다시 확인합니다. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`은 보통 서명이 다르다는 뜻입니다. 자동으로 앱을 삭제하거나 낮은 버전 설치를 강제하지 마세요.

### 릴리스 지도 인증

카카오 개발자 콘솔의 **앱 1650708 → 플랫폼 → Android**에 패키지 `com.sunseat.app`와 **릴리스 키 해시**를 등록합니다. 디버그 키 해시 등록만으로 릴리스 지도가 인증되지는 않습니다. 다음 명령은 인증서의 공개 해시만 계산하며 저장소 비밀번호를 프롬프트에서 받습니다.

```bash
keytool -exportcert -alias sun-seat -keystore "$HOME/.sun-seat/release.jks" \
  | openssl sha1 -binary | openssl base64
```

`KAKAO_NATIVE_APP_KEY`도 local.properties에 있어야 합니다. 실제 릴리스 APK에서 지도·검색·경로·백그라운드 복귀를 확인하세요. 지도 SDK 보존 규칙은 `app/proguard-rules.pro`에 있습니다. 현재 릴리스는 minify를 끈 상태이며, R8을 켜면 보존 규칙을 유지하고 지도까지 다시 확인합니다.

런처는 직접 제작한 해·자리 적응형 아이콘을 사용하며 Android 테마 아이콘용 단색 레이어도 포함합니다.
