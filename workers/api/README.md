# 앱 API 게이트웨이 (`workers/api`)

sun-seat 앱이 외부 API를 부를 때 거치는 Cloudflare Workers입니다(#189). 지금은 카카오 REST API(장소 검색, 좌표→주소, 대중교통·자동차 길찾기)만 전달합니다.

카카오 REST API 키를 APK에 넣으면 누구나 APK에서 꺼내 이 앱의 쿼터로 호출할 수 있습니다. 그래서 키는 이 Worker의 시크릿에만 두고, 앱은 키 없이 이 Worker를 부릅니다. 지도 SDK의 네이티브 앱 키는 그대로 앱에 있습니다(카카오 콘솔에서 패키지명·서명 키 해시로 제한).

## 경로

경로의 첫 마디가 경로 묶음입니다. 새 외부 API는 `src/index.ts`의 `GROUPS`에 묶음을 더해 붙이고, 앱의 기준 주소(`SUNSEAT_API_BASE_URL`)는 바꾸지 않습니다.

| 앱이 부르는 경로 | 전달하는 곳 | 허용 파라미터(굵게: 필수) |
|---|---|---|
| `GET /kakao/local/search/keyword` | `https://dapi.kakao.com/v2/local/search/keyword.json` | **query**, x, y, size, page |
| `GET /kakao/local/geo/coord2address` | `https://dapi.kakao.com/v2/local/geo/coord2address.json` | **x**, **y** |
| `GET /kakao/routing/publictraffic` | `https://dapi.kakao.com/v2/routing/publictraffic` | **start_x**, **start_y**, **end_x**, **end_y** |
| `GET /kakao/navi/directions` | `https://apis-navi.kakaomobility.com/v1/directions` | **origin**, **destination** |

앱이 쓰는 경로·파라미터만 허용합니다. 앱에서 경로나 파라미터를 바꾸면 이 표와 `GROUPS`도 함께 바꿉니다. 둘이 어긋나면 앱 단위 테스트(`KakaoApisTest`)가 실패합니다.

| 요청 | 응답 |
|---|---|
| 허용 경로·파라미터의 GET | 카카오 응답의 상태와 본문 그대로(`Content-Type`만 전달, `Cache-Control: no-store`) |
| GET이 아님 | 405 `{"error":"method_not_allowed"}`, `Allow: GET` |
| 모르는 경로 묶음(예: `/v2/...`, `/naver/...`) | 404 `{"error":"not_found"}` |
| 묶음 안에서 허용 밖 경로, 모르는·중복·200자 넘는 파라미터, 필수 파라미터 없음 | 403 `{"error":"forbidden"}` |
| IP별 호출 제한 초과 | 429 `{"error":"rate_limited"}`, `Retry-After: 60`. 카카오를 부르지 않음 |
| 시크릿 `KAKAO_REST_API_KEY` 없음 | 503 `{"error":"not_configured"}` |
| 카카오에 연결 실패, 10초 안에 응답 없음 | 502 `{"error":"upstream_unavailable"}` |
| 카카오가 리디렉션(3xx)으로 응답 | 502 `{"error":"upstream_redirect"}`. 인증 헤더가 다른 호스트로 가지 않게 따르지 않음 |

- 앱이 보낸 헤더는 카카오로 넘기지 않습니다. 인증 헤더는 Worker가 시크릿으로 만듭니다.
- 키와 인증 헤더는 응답과 로그에 남기지 않습니다. 카카오 응답 본문에 키가 섞여 오면 `[redacted]`로 가립니다. Worker는 로그를 쓰지 않습니다.
- 403·404·405는 호출 제한에 세지 않습니다(카카오 쿼터를 쓰지 않으므로).

## 호출 제한

`wrangler.jsonc`의 `ratelimits`(Workers [Rate Limiting 바인딩](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/))로 경로 묶음마다 IP(`CF-Connecting-IP`)별 상한을 둡니다.

| 묶음 | 바인딩 | 상한 |
|---|---|---|
| `kakao` | `KAKAO_RATE_LIMITER` | IP당 60초에 60회 |

근거(값은 설정값이라 카카오 쿼터를 확인한 뒤 `wrangler.jsonc`에서 바꿉니다. 바꾸면 이 표도 함께 고칩니다. 테스트는 `wrangler.jsonc` 값을 읽어 검사하고, 기간을 바꾸면 `src/index.ts`의 `RETRY_AFTER_SECONDS`도 같게 맞춥니다. 어긋나면 테스트가 실패합니다):

- 정상 사용량. 한 사람이 가장 많이 부르는 순간도 1분에 수십 회를 넘지 않습니다.
  - 장소 검색: 입력이 300ms 멈출 때마다 1회(디바운스). 빠르게 고쳐 쓰며 여러 장소를 찾아도 1분에 20회 안팎입니다.
  - 지도에서 위치 고르기: 지도가 멈출 때마다 좌표→주소 1회. 지도를 여러 번 끌어도 1분에 20~30회입니다.
  - 경로: 후보 화면을 열거나 이동 수단을 바꾸거나 다시 시도할 때 1회씩. 1분에 10회 이내입니다.
  - 세 가지를 한 화면에서 동시에 하지는 않으므로 60회/분이면 정상 사용은 막히지 않고, 막혀도 앱이 "잠시 뒤 다시"와 다시 시도를 보여 줍니다.
- 남용 상한. IP 하나가 쉬지 않고 불러도 분당 60회(하루 최대 약 86,400회)로 묶입니다. 기간은 바인딩이 허용하는 10초·60초 중 짧은 몰림을 견디는 60초를 골랐습니다.
- 한계. 카운터는 Cloudflare 위치(데이터센터)마다 따로이고 근사값입니다. 여러 IP를 쓰는 호출은 막지 못합니다. Cloudflare 문서는 휴대폰 통신사망처럼 여러 사람이 IP 하나를 함께 쓰는 경우가 있어 IP를 키로 쓰는 것을 권하지 않지만, 이 앱은 로그인이 없어 IP 말고 믿을 만한 식별자가 없습니다. 사용자가 늘어 같은 IP의 정상 사용자가 막히면 상한을 올립니다.

### 일일 상한을 두지 않는 이유

카카오 개발자 콘솔에서 유료 API가 모두 "사용 안 함"이고 비즈월렛이 연결되어 있지 않습니다. 쿼터를 넘으면 과금되지 않고 그날 호출이 거부될 뿐이라 일일 상한은 두지 않고 IP별 제한만 둡니다.

**유료 API를 켜거나 비즈월렛을 연결하면 그 전에 일일 상한(예: Durable Object나 KV로 하루 호출 수를 세어 넘으면 429)을 이 Worker에 넣어야 합니다.** 그러지 않으면 남용 호출이 그대로 요금이 됩니다.

## 배포 (저장소 소유자)

처음 한 번만 합니다. Node.js(npm 포함)가 필요합니다. Wrangler는 이 폴더의 `package.json`에 고정된 버전을 `npx`로 씁니다.

1. 의존성을 설치합니다.

   ```bash
   cd workers/api
   npm ci
   ```

2. Cloudflare에 로그인합니다. 브라우저가 열리면 계정으로 허용합니다.

   ```bash
   npx wrangler login
   ```

3. 카카오 REST API 키를 시크릿으로 넣습니다. 프롬프트에 키를 붙여 넣습니다(명령 인수·파일·셸 기록에 남기지 않습니다). `api`라는 Worker가 아직 없어 새로 만들지 물으면 `y`로 답합니다.

   ```bash
   npx wrangler secret put KAKAO_REST_API_KEY
   ```

4. 배포합니다.

   ```bash
   npx wrangler deploy
   ```

   끝에 배포 주소가 나옵니다. `https://api.<계정의 workers.dev 하위 도메인>.workers.dev` 형태입니다. 하위 도메인은 Cloudflare 대시보드의 "Workers & Pages" 화면에서도 확인할 수 있습니다.

5. 동작을 확인합니다. 장소 검색은 JSON(`documents`)이, 허용 밖 경로는 403이 와야 합니다.

   ```bash
   curl -s "https://api.<하위 도메인>.workers.dev/kakao/local/search/keyword?query=%EC%84%9C%EC%9A%B8%EC%97%AD&size=1"
   curl -s -o /dev/null -w "%{http_code}\n" "https://api.<하위 도메인>.workers.dev/kakao/local/search/address?query=a"
   ```

6. 앱 빌드 설정에 주소를 넣습니다. 저장소 루트의 `local.properties`(커밋하지 않음)에 다음 줄을 넣고, 예전의 `KAKAO_REST_API_KEY` 줄은 지웁니다. 그 뒤 `./gradlew assembleDebug`로 다시 빌드합니다.

   ```properties
   SUNSEAT_API_BASE_URL=https://api.<하위 도메인>.workers.dev
   ```

   릴리스 빌드(#118)도 같은 이름의 설정을 씁니다. 주소는 비밀이 아니지만 저장소에 고정하지 않고 빌드 설정으로 넣습니다.

7. 키를 재발급합니다. 지금까지 로컬 빌드의 APK에 REST 키가 들어갔으므로, 앱에서 게이트웨이로 동작하는 것을 확인한 뒤 카카오 개발자 콘솔에서 REST API 키를 재발급하고 3번 명령으로 새 키만 넣습니다(`secret put`은 바로 반영됩니다). 새 키는 `local.properties`에 넣지 않습니다.

코드를 바꾼 뒤 다시 배포할 때는 `npm ci` 뒤 `npx wrangler deploy`만 합니다. 자동 배포 워크플로는 없습니다.

## 개발

```bash
cd workers/api
npm ci
npm test            # vitest(@cloudflare/vitest-plugin): Workers 런타임에서 허용·거부·405·429·키 비노출을 검사. 가짜 키를 쓰고 카카오를 부르지 않는다
npm run typecheck   # tsc --noEmit
```

CI의 `api-worker` job이 같은 명령을 돌리고 `ci-passed`에 포함됩니다. 배포는 하지 않습니다.

`npx wrangler dev`로 로컬에서 띄울 때는 이 폴더에 `.dev.vars` 파일을 만들어 `KAKAO_REST_API_KEY=<키>`를 넣습니다. `.dev.vars`와 `.env`는 이 폴더의 `.gitignore`로 제외되며 커밋하지 않습니다. 실제 카카오 응답 원문은 저장소에 넣지 않습니다.

의존성 버전은 `package.json`에 정확히 고정하고 `package-lock.json`과 함께 커밋합니다. 업데이트는 Dependabot PR로 받습니다.
