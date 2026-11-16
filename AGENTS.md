# 작업 규칙

이 저장소에서 사람과 코딩 에이전트가 함께 지키는 규칙입니다.

## 이슈부터 시작

- 모든 코드 변경(기능·버그·리팩터링·테스트·빌드 설정·개발 문서)은 GitHub 이슈부터 만든다. 같은 범위의 열린 이슈가 있으면 그것을 쓴다.
- 이슈에는 배경, 범위, 완료 기준, 선행 이슈를 적는다.
- 서로 독립된 작업은 이슈와 PR을 나눈다.

## 브랜치

- `feat/<이슈번호>-<요약>`: 기능
- `fix/<이슈번호>-<요약>`: 버그
- `chore/<이슈번호>-<요약>`: 빌드·설정·문서
- main에서 갈라지고 main으로 PR을 보낸다. main에 직접 커밋하거나 force push하지 않는다.

## 커밋

- 형식: `<종류>: <한국어 요약> (#이슈번호)` 예) `feat: 태양 위치 계산 (#3)`
- 종류: feat, fix, refactor, test, docs, chore
- 본문에는 무엇을 왜 바꿨는지 적는다.

## PR

- 본문에 `Closes #이슈번호`, 변경 목록, 확인한 내용을 적는다.
- CI(빌드·lint·단위 테스트)가 통과해야 병합한다. 병합은 squash로 하고 브랜치는 지운다.
- main과 충돌하면 main 기준으로 다시 맞춘 뒤 테스트를 다시 돌린다.

## 테스트

- **버그 수정 PR은 먼저 실패하는 재현 테스트를 넣고 고친다.** 고치기 전 코드에서 그 테스트가 실패하는 것을 확인하고 PR 본문에 적는다.
- 기능 PR은 영향받는 기존 흐름 테스트(`ui/navigation/SunSeatFlowTest` 등)가 통과해야 한다. 흐름을 일부러 바꿨으면 테스트도 같은 PR에서 고친다.
- 화면 흐름 테스트는 `FlowTestGraph`(가짜 설정·임시 DataStore·가짜 카카오 서버·고정 시각)를 쓴다. 지도는 `LocalMapRenderer`의 테스트 렌더러로 그린다.

## 비밀 정보

- `KAKAO_REST_API_KEY`, `sdk.dir`, 서명 키 정보는 `local.properties`에만 둔다. 이 파일과 키 파일(`*.jks`, `*.keystore`)은 커밋하지 않는다.
- 실제 API 응답 원문을 커밋하지 않는다. 테스트용 예시 응답은 공식 문서의 예시나 직접 만든 값으로 쓴다.
- CI는 빈 API 키로 빌드한다.

## 빌드 환경

- JDK 25, Android SDK platform 37. Android Studio 없이 명령줄로 빌드한다.
- 의존성 버전은 `gradle/libs.versions.toml`에서만 관리한다.
- 검사: `./gradlew assembleDebug lint verifyRoborazziDebug` (CI와 같다. `verifyRoborazziDebug`는 단위 테스트 전체를 돌리며 스크린샷도 비교한다)

## 코드 구조

- 계산·추천 로직은 `com.sunseat.app.domain`에 순수 Kotlin으로 두고 JVM 단위 테스트로 검증한다.
- 화면은 `com.sunseat.app.ui`에 둔다.

## 화면 작업

- 화면과 공통 부품은 `docs/DESIGN.md`의 토큰·규칙만 쓴다(#18 이후). 색·크기 값을 화면 코드에 직접 쓰지 않는다.
- 화면 PR은 다음을 거친다.
  - Roborazzi 스크린샷 테스트(밝은·어두운 화면, 주요 상태)
  - Compose 접근성 검사(터치 영역·대비)
  - 독립 디자인 리뷰: 화면을 만든 쪽과 분리된 리뷰어가 실제 화면을 보고 위계·가독성·색·상태·접근성을 판정한다
- 좌·우는 색만으로 구분하지 않고 글자로도 표시한다.

### 스크린샷 기준 이미지

- 기준 이미지는 `app/src/test/screenshots/*.png`이고, CI(ubuntu, Robolectric NATIVE)에서 기록한 것을 정본으로 삼는다. CI는 `./gradlew verifyRoborazziDebug`로 비교하며 한 장이라도 다르면 실패한다. 허용 오차는 두지 않는다.
- 로컬 확인: `./gradlew verifyRoborazziDebug`. Windows에서도 현재 기준 이미지와 일치했지만, 로컬과 CI 결과가 다르면 CI가 맞다. 로컬에서 기록한 이미지(`recordRoborazziDebug`)는 커밋하지 않는다.
- 비교에 실패하면 CI 실행의 `screenshot-diffs` artifact에서 `*_compare.png`(기준·실제·차이)와 `*_actual.png`를 확인한다.
- 의도한 화면 변경이면 같은 PR에서 기준 이미지를 갱신한다.
  1. 브랜치를 push한 뒤 `gh workflow run record-screenshots.yml --ref <브랜치>`
  2. 끝나면 `gh run download <run-id> -n screenshots -D app/src/test/screenshots`
  3. 바뀐 이미지만 확인해 같은 PR에 커밋한다. 화면을 지운 경우 남은 이미지도 지운다.
- 리뷰어는 PR의 바뀐 이미지 diff가 의도한 변경뿐인지 확인한다.
- 스크린샷은 실행할 때마다 같아야 한다. 비동기로 읽는 리소스(Lottie 등)는 캡처 전에 미리 읽어 두고, 애니메이션은 멈춘 상태로 찍는다.
