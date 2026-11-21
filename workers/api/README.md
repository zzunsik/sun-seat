# 앱 API 게이트웨이 (`workers/api`)

sun-seat 앱이 외부 API를 부를 때 거치는 Cloudflare Workers입니다(#189). 지금은 카카오 REST API(장소 검색, 좌표→주소, 대중교통·자동차 길찾기)만 전달합니다.

카카오 REST API 키를 APK에 넣으면 누구나 APK에서 꺼내 이 앱의 쿼터로 호출할 수 있습니다. 그래서 키는 이 Worker의 시크릿에만 두고, 앱은 키 없이 이 Worker를 부릅니다. 지도 SDK의 네이티브 앱 키는 그대로 앱에 있습니다(카카오 콘솔에서 패키지명·서명 키 해시로 제한).

## 경로

경로의 첫 마디가 경로 묶음입니다. 새 외부 API는 `src/index.ts`의 `GROUPS`에 묶음을 더해 붙이고, 앱의 기준 주소(빌드 설정 `SUNSEAT_API_BASE_URL_DEBUG`·`_RELEASE`)는 바꾸지 않습니다.

| 앱이 부르는 경로 | 전달하는 곳 | 허용 파라미터(굵게: 필수) |
|---|---|---|
| `GET /kakao/local/search/keyword` | `https://dapi.kakao.com/v2/local/search/keyword.json` | **query**, x, y, size, page |
| `GET /kakao/local/geo/coord2address` | `https://dapi.kakao.com/v2/local/geo/coord2address.json` | **x**, **y** |
| `GET /kakao/routing/publictraffic` | `https://dapi.kakao.com/v2/routing/publictraffic` | **start_x**, **start_y**, **end_x**, **end_y** |
| `GET /kakao/navi/directions` | `https://apis-navi.kakaomobility.com/v1/directions` | **origin**, **destination** |

앱이 쓰는 경로·파라미터만 허용합니다. 앱에서 경로나 파라미터를 바꾸면 이 표와 `GROUPS`도 같은 PR에서 함께 바꿉니다(배포 순서는 아래 "앱과 게이트웨이를 함께 바꿀 때"). 둘이 어긋나면 앱 단위 테스트(`KakaoApisTest`)가 실패합니다.

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

## 배포

게이트웨이는 환경 두 개로 나눠 배포합니다(#191).

| 환경 | Worker | 주소 | 배포 시점 | 부르는 앱 | GitHub Environment |
|---|---|---|---|---|---|
| 운영 | `api` (`wrangler.jsonc` 최상위) | `https://api.<하위 도메인>.workers.dev` | `main`에 머지(릴리스 PR) | 릴리스 빌드 | `api-production`(배포 브랜치 `main`만) |
| 개발 | `api-dev` (`env.development`) | `https://api-dev.<하위 도메인>.workers.dev` | `develop`에 머지 | 디버그 빌드 | `api-development`(배포 브랜치 `develop`만) |

`<하위 도메인>`은 계정의 workers.dev 하위 도메인입니다(Cloudflare 대시보드 "Workers & Pages"). 두 Worker는 같은 코드·허용 목록·호출 상한을 쓰고, 호출 제한 카운터(`namespace_id`)와 카카오 키 시크릿만 따로 둡니다. `ratelimits`·`secrets`는 wrangler 환경으로 이어지지 않아 `env.development`에 다시 적었습니다. 운영 값을 바꾸면 개발 값도 함께 바꿉니다(어긋나면 테스트 "배포 환경"이 실패합니다).

### 자동 배포 (`.github/workflows/deploy-api.yml`)

- `develop`·`main`에 대한 push 중 `workers/api/**`나 워크플로 파일이 바뀐 경우에 돕니다. 수동 실행(`workflow_dispatch`)으로 대상 환경을 골라 다시 배포할 수도 있습니다. 개발은 `develop`, 운영은 `main`에서만 실행됩니다(환경의 배포 브랜치 규칙과 워크플로의 확인 단계).

  ```bash
  gh workflow run deploy-api.yml --ref main -f environment=production     # 운영 다시 배포
  gh workflow run deploy-api.yml --ref develop -f environment=development # 개발 다시 배포
  ```

- 순서: 타입 검사·테스트(시크릿 없는 job) → 통과하면 환경 job에서 `npm ci` → `npx wrangler deploy`(운영 `--env=""`, 개발 `--env=development`) → 스모크 확인.
- 스모크 확인: 배포된 주소에 허용 경로(장소 검색 `서울역` 1건)가 200, 허용 밖 경로가 403인지 상태 코드만 봅니다. 응답 본문·헤더는 로그에 남기지 않습니다. 허용 경로가 503이면 그 Worker에 카카오 키 시크릿이 없다는 뜻이라 바로 실패합니다.
- 실행 요약에 환경, Worker, 주소, 버전 ID, 커밋, 스모크 결과가 남습니다. 같은 환경의 배포는 한 번에 하나씩 돌고(뒤 실행은 기다림) 배포 중인 실행은 취소하지 않습니다.
- PR·포크에서는 돌지 않습니다(PR 트리거가 없고, 토큰은 `develop`·`main`에서만 쓸 수 있는 환경 시크릿에만 있습니다). 그래서 이 워크플로나 `workers/api`를 바꾼 PR은 머지된 뒤에야 배포가 확인됩니다.
- 인증: 각 환경에 시크릿 `CLOUDFLARE_API_TOKEN`과 변수 `CLOUDFLARE_ACCOUNT_ID`(비밀 아님)를 둡니다. 토큰은 이 계정의 Workers Scripts 편집만 가진 사용자 지정 토큰입니다.

### 앱과 게이트웨이를 함께 바꿀 때

- 새 API 경로·파라미터를 붙일 때는 `src/index.ts`의 `GROUPS`(와 위 "경로" 표)와 앱 코드를 **한 PR**에서 바꿉니다. 한쪽만 바꾸면 앱 단위 테스트(`KakaoApisTest`, 앱이 보내는 경로·파라미터를 게이트웨이 허용 목록과 대조)가 실패합니다.
- 경로를 **더하는** 변경은 게이트웨이가 먼저 나가야 합니다. `develop`에 머지되면 개발 게이트웨이가 바로 배포되고, 그 뒤 디버그 빌드로 확인합니다. 릴리스에서는 `main` 머지 → 운영 게이트웨이 배포(이 워크플로) → 태그 → 릴리스 APK 순서가 되어 게이트웨이가 앱보다 먼저 나갑니다. 배포 워크플로가 실패했으면 태그를 달기 전에 고칩니다.
- 경로를 **빼는** 변경은 그 경로를 쓰는 앱 버전이 남아 있으면 그 앱이 403을 받습니다. 앱에서 먼저 빼고 릴리스한 뒤, 다음 릴리스에서 게이트웨이에서 뺍니다.

### 카카오 키 넣기·교체 (저장소 소유자)

키는 Worker마다 따로 넣습니다. 프롬프트에 붙여 넣습니다(명령 인수·파일·셸 기록에 남기지 않습니다). `secret put`은 바로 반영되고 배포가 필요 없습니다.

```bash
cd workers/api
npm ci
npx wrangler login                                          # 처음 한 번
npx wrangler secret put KAKAO_REST_API_KEY --env=""         # 운영 api
npx wrangler secret put KAKAO_REST_API_KEY --env development # 개발 api-dev
```

`wrangler.jsonc`가 이 시크릿을 필수(`secrets.required`)로 두므로 시크릿이 없는 Worker에는 배포가 거부됩니다. 새 환경을 만들 때는 시크릿을 먼저 넣습니다(Worker가 없으면 `secret put`이 빈 Worker를 만들지 묻습니다. `y`로 답합니다).

카카오 REST 키를 재발급하면 위 두 명령으로 새 키를 넣습니다. 키는 `local.properties`나 저장소에 두지 않습니다.

### Cloudflare 토큰 교체 (저장소 소유자)

1. Cloudflare 대시보드 "My Profile → API Tokens"에서 "Edit Cloudflare Workers" 템플릿으로 새 토큰을 만들고 권한을 이 계정의 Workers Scripts 편집만 남깁니다.
2. GitHub 환경 두 곳에 넣습니다. 값은 프롬프트에 붙여 넣습니다(명령 인수·셸 기록에 남기지 않습니다).

   ```bash
   gh secret set CLOUDFLARE_API_TOKEN --env api-development
   gh secret set CLOUDFLARE_API_TOKEN --env api-production
   ```

3. `workflow_dispatch`로 개발 환경을 한 번 배포해 새 토큰을 확인한 뒤, 대시보드에서 예전 토큰을 지웁니다. 토큰이 유출되면 먼저 대시보드에서 지우고 같은 절차로 새로 넣습니다.

계정 ID는 환경 변수입니다: `gh variable set CLOUDFLARE_ACCOUNT_ID --env <환경> --body <계정 ID>`.

### 수동 배포 (비상용)

워크플로를 쓸 수 없을 때(GitHub Actions 장애 등)만 합니다. 배포하는 커밋은 운영이면 `origin/main`, 개발이면 `origin/develop`의 최신 커밋이어야 합니다. 다음 자동 배포가 그 브랜치 상태로 덮어쓰기 때문입니다.

```bash
cd workers/api
npm ci
npm run typecheck && npm test
npx wrangler deploy --env=""          # 운영 api
npx wrangler deploy --env development # 개발 api-dev
```

배포 뒤 스모크 확인과 같은 두 요청으로 상태 코드만 확인합니다.

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://api-dev.<하위 도메인>.workers.dev/kakao/local/search/keyword?query=%EC%84%9C%EC%9A%B8%EC%97%AD&size=1"  # 200
curl -s -o /dev/null -w "%{http_code}\n" "https://api-dev.<하위 도메인>.workers.dev/kakao/local/search/address?query=a"                              # 403
```

### 앱 빌드 설정

저장소 루트의 `local.properties`(커밋하지 않음)에 빌드 종류별 주소를 넣습니다. 주소는 비밀이 아니지만 저장소에 고정하지 않습니다.

```properties
SUNSEAT_API_BASE_URL_DEBUG=https://api-dev.<하위 도메인>.workers.dev
SUNSEAT_API_BASE_URL_RELEASE=https://api.<하위 도메인>.workers.dev
```

- 디버그 빌드는 `SUNSEAT_API_BASE_URL_DEBUG`, 릴리스 빌드는 `SUNSEAT_API_BASE_URL_RELEASE`를 씁니다. 없으면 예전 이름 `SUNSEAT_API_BASE_URL`을 두 빌드에 씁니다(이전 설정 호환). 모두 없으면 빈 주소로 빌드되고 장소 검색·경로가 안내와 함께 동작하지 않습니다(CI).
- `local.properties`에 없으면 같은 이름의 환경 변수도 읽습니다. 릴리스 워크플로(#118)는 `SUNSEAT_API_BASE_URL_RELEASE`를 환경 변수로 넘깁니다.

## 개발

```bash
cd workers/api
npm ci
npm test            # vitest(@cloudflare/vitest-plugin): Workers 런타임에서 허용·거부·405·429·키 비노출을 검사. 가짜 키를 쓰고 카카오를 부르지 않는다
npm run typecheck   # tsc --noEmit
```

CI의 `api-worker` job이 같은 명령을 돌리고 `ci-passed`에 포함됩니다. CI는 배포하지 않습니다. 배포는 `develop`·`main`에 머지된 뒤 위 "자동 배포" 워크플로가 합니다.

`npx wrangler dev`로 로컬에서 띄울 때는 이 폴더에 `.dev.vars` 파일을 만들어 `KAKAO_REST_API_KEY=<키>`를 넣습니다. `.dev.vars`와 `.env`는 이 폴더의 `.gitignore`로 제외되며 커밋하지 않습니다. 실제 카카오 응답 원문은 저장소에 넣지 않습니다.

의존성 버전은 `package.json`에 정확히 고정하고 `package-lock.json`과 함께 커밋합니다. 업데이트는 Dependabot PR로 받습니다.
