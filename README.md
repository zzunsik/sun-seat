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
