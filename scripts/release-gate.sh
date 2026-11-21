#!/usr/bin/env bash
# 릴리스 워크플로(.github/workflows/release.yml)의 verify job이 빌드 전에 확인하는 것(#118, AGENTS.md "릴리스").
# 서명 시크릿 없이, 읽기 권한만으로 돈다. 로컬에서도 같은 명령으로 미리 돌려 볼 수 있다(gh 로그인 필요한 것은 ci·gateway).
#
#   scripts/release-gate.sh tag <태그> <커밋> [main 참조, 기본 origin/main]
#     - 태그 형식(vX.Y.Z, 시험판 vX.Y.Z-rc.N)과 versionCode 계산(scripts/release-version.mjs)
#     - 커밋이 main에 있다(develop → main 릴리스 PR의 머지 커밋, 또는 그 뒤 main 커밋)
#     - 그 커밋의 app/build.gradle.kts versionName·versionCode가 태그와 같다
#     - (참고용) 바로 앞 v 태그와 그 뒤 앱 API 게이트웨이(workers/api, deploy-api.yml)가 바뀌었는지. 앞 태그는 실패해
#       Release 없이 남은 태그일 수도 있어서 배포 확인을 건너뛰는 데 쓰지 않는다(gateway는 항상 돈다).
#     key=value 줄을 출력하고 GITHUB_OUTPUT이 있으면 거기에도 쓴다: version-name, version-code, prerelease, sha, previous-tag, gateway-changed
#   scripts/release-gate.sh ci <커밋>
#     그 커밋의 ci-passed 검사(main push로 생긴다)가 끝날 때까지 기다리고 성공이어야 통과한다.
#   scripts/release-gate.sh gateway <커밋>
#     main의 가장 최근 "Deploy API gateway" 실행이 끝날 때까지 기다리고, 성공했으며 그 실행이 배포한 커밋의
#     workers/api·deploy-api.yml이 이 커밋과 같아야 통과한다(운영 게이트웨이가 이 릴리스의 게이트웨이 코드를 돌린다).
#     게이트웨이가 바뀌지 않은 릴리스는 이미 끝난 앞 배포가 같은 코드라 바로 통과한다.
#
# 환경 변수: REPO(owner/name, 기본 GITHUB_REPOSITORY), WAIT_MINUTES(ci 기본 40, gateway 기본 20), POLL_SECONDS(기본 30).
# 종료 코드: 0 통과, 1 실패, 2 사용법 오류
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
gateway_paths=(workers/api .github/workflows/deploy-api.yml)
repo=${REPO:-${GITHUB_REPOSITORY:-}}
poll=${POLL_SECONDS:-30}

fail() { echo "::error::$*"; exit 1; }
emit() { echo "$1"; [ -z "${GITHUB_OUTPUT:-}" ] || echo "$1" >> "$GITHUB_OUTPUT"; }
usage() { echo "사용법: $0 tag <태그> <커밋> [main 참조] | ci <커밋> | gateway <커밋>"; exit 2; }

cmd_tag() {
  local tag=$1 rev=$2 main=${3:-origin/main} out sha prev changed gradle
  out=$(node "$here/release-version.mjs" tag "$tag") || { echo "$out"; exit 1; }
  while IFS= read -r line; do emit "$line"; done <<< "$out"

  sha=$(git rev-parse --verify --quiet "$rev^{commit}") || fail "커밋을 찾지 못했습니다: $rev"
  git rev-parse --verify --quiet "$main^{commit}" >/dev/null || fail "main 참조를 찾지 못했습니다: $main"
  git merge-base --is-ancestor "$sha" "$main" ||
    fail "태그 $tag 의 커밋 $sha 이 main에 없습니다. develop → main 릴리스 PR을 머지 커밋으로 합친 뒤 그 머지 커밋에 태그를 다세요."
  emit "sha=$sha"

  gradle=$(mktemp)
  git show "$sha:app/build.gradle.kts" > "$gradle"
  if ! node "$here/release-version.mjs" gradle "$gradle" --tag "$tag"; then
    rm -f "$gradle"
    exit 1
  fi
  rm -f "$gradle"

  # 앞 릴리스: 이 커밋에서 닿는 v 태그 중 가장 가까운 것(이 태그 제외). 같은 커밋의 시험판 태그도 앞 릴리스로 본다.
  prev=$(git describe --tags --abbrev=0 --match 'v[0-9]*' --exclude "$tag" "$sha" 2>/dev/null || true)
  if [ -z "$prev" ]; then
    changed=true
    echo "앞 v 태그가 없습니다(첫 릴리스)."
  elif git diff --quiet "$prev" "$sha" -- "${gateway_paths[@]}"; then
    changed=false
    echo "앞 v 태그 $prev 뒤 게이트웨이(${gateway_paths[*]})가 바뀌지 않았습니다."
  else
    changed=true
    echo "앞 v 태그 $prev 뒤 게이트웨이가 바뀌었습니다."
  fi
  emit "previous-tag=$prev"
  emit "gateway-changed=$changed"
}

# 같은 커밋에 실행이 여럿이면(다시 돌린 경우) 가장 나중에 만든 것을 본다. GitHub Actions가 만든 검사만 본다.
cmd_ci() {
  local sha=$1 deadline latest state status conclusion url
  deadline=$(( $(date +%s) + ${WAIT_MINUTES:-40} * 60 ))
  while :; do
    state="없음"
    if ! latest=$(gh api "repos/$repo/commits/$sha/check-runs?check_name=ci-passed&filter=all&per_page=100" \
      --jq '[.check_runs[] | select(.app.slug == "github-actions")] | sort_by(.id) | last // empty | "\(.status) \(.conclusion // "-") \(.html_url)"'); then
      latest="" state="조회 실패"
    fi
    if [ -n "$latest" ]; then
      read -r status conclusion url <<< "$latest"
      if [ "$status" = completed ]; then
        [ "$conclusion" = success ] || fail "커밋 $sha 의 ci-passed가 $conclusion 입니다($url). CI를 고쳐 성공시킨 뒤 이 실행을 다시 돌리세요."
        echo "커밋 $sha 의 ci-passed 성공: $url"
        return 0
      fi
      state=$status
    fi
    [ "$(date +%s)" -lt "$deadline" ] ||
      fail "커밋 $sha 의 ci-passed가 ${WAIT_MINUTES:-40}분 안에 끝나지 않았습니다(상태: $state). main의 CI가 끝난 뒤 이 실행을 다시 돌리세요(Re-run all jobs)."
    echo "ci-passed를 기다립니다(상태: $state). ${poll}초 뒤 다시 봅니다."
    sleep "$poll"
  done
}

cmd_gateway() {
  local sha=$1 deadline latest state status conclusion run_sha url
  deadline=$(( $(date +%s) + ${WAIT_MINUTES:-20} * 60 ))
  while :; do
    state="main 실행 없음"
    if ! latest=$(gh api "repos/$repo/actions/workflows/deploy-api.yml/runs?branch=main&per_page=30" \
      --jq '[.workflow_runs[] | select(.head_branch == "main")] | sort_by(.created_at, .id) | last // empty | "\(.status) \(.conclusion // "-") \(.head_sha) \(.html_url)"'); then
      latest="" state="조회 실패"
    fi
    if [ -n "$latest" ]; then
      read -r status conclusion run_sha url <<< "$latest"
      if [ "$status" != completed ]; then
        state="실행 중($status, $url)"
      elif [ "$conclusion" != success ]; then
        fail "main의 최근 게이트웨이 배포가 $conclusion 입니다($url). 배포를 고쳐 성공시킨 뒤(Actions에서 다시 실행) 이 실행을 다시 돌리세요. 배포가 실패한 릴리스에는 태그를 달지 않습니다."
      elif ! git cat-file -e "$run_sha^{commit}" 2>/dev/null; then
        # checkout 뒤 main이 움직였으면 배포 커밋이 아직 없다. 다음 확인 전에 main을 다시 받는다(공개 저장소라 인증 없이 된다).
        state="배포 커밋 $run_sha 을 아직 받지 못함"
        git fetch --quiet origin main 2>/dev/null || true
      elif git diff --quiet "$run_sha" "$sha" -- "${gateway_paths[@]}"; then
        echo "운영 게이트웨이 배포 성공, 이 커밋과 같은 게이트웨이 코드: $url (배포 커밋 $run_sha)"
        return 0
      else
        state="최근 배포(커밋 $run_sha, $url)의 게이트웨이 코드가 태그 커밋과 다름"
      fi
    fi
    [ "$(date +%s)" -lt "$deadline" ] ||
      fail "운영 게이트웨이 배포를 확인하지 못했습니다(${WAIT_MINUTES:-20}분, 상태: $state). main의 Deploy API gateway 실행이 이 커밋의 workers/api를 배포해 성공한 뒤 이 실행을 다시 돌리세요."
    echo "운영 게이트웨이 배포를 기다립니다(상태: $state). ${poll}초 뒤 다시 봅니다."
    sleep "$poll"
  done
}

case "${1:-}" in
  tag) [ $# -eq 3 ] || [ $# -eq 4 ] || usage; shift; cmd_tag "$@" ;;
  ci) [ $# -eq 2 ] || usage; [ -n "$repo" ] || usage; cmd_ci "$2" ;;
  gateway) [ $# -eq 2 ] || usage; [ -n "$repo" ] || usage; cmd_gateway "$2" ;;
  *) usage ;;
esac
