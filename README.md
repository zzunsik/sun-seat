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
