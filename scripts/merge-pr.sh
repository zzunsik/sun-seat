#!/usr/bin/env bash
# CI가 통과한 PR만 squash 머지한다. 사용: scripts/merge-pr.sh <PR 번호>
# 비공개·무료 저장소라 GitHub 브랜치 보호를 쓸 수 없어서, 머지는 이 스크립트로만 한다(AGENTS.md "PR").
set -euo pipefail

REQUIRED_CHECK="ci-passed"
pr="${1:-}"
[[ "$pr" =~ ^[0-9]+$ ]] || { echo "사용: $0 <PR 번호>" >&2; exit 2; }

fail() { echo "머지 거부: $*" >&2; exit 1; }

read -r state base head_sha draft < <(gh pr view "$pr" --json state,baseRefName,headRefOid,isDraft \
  --jq '[.state, .baseRefName, .headRefOid, .isDraft] | @tsv')

[[ "$state" == "OPEN" ]] || fail "PR #$pr 상태가 $state 입니다."
[[ "$base" == "main" ]] || fail "PR #$pr 의 대상 브랜치가 main이 아닙니다($base)."
[[ "$draft" == "false" ]] || fail "PR #$pr 은 초안입니다."

# 현재 head 커밋 기준 검사 결과. 하나라도 진행 중이거나 실패면 거부한다.
checks=$(gh api "repos/{owner}/{repo}/commits/$head_sha/check-runs?per_page=100" \
  --jq '.check_runs[] | [.name, .status, (.conclusion // "-")] | @tsv')
[[ -n "$checks" ]] || fail "커밋 ${head_sha:0:7} 에 CI 검사가 없습니다."
echo "$checks" | sed 's/^/  /'

awk -F'\t' -v n="$REQUIRED_CHECK" '$1 == n && $2 == "completed" && $3 == "success" { ok = 1 } END { exit !ok }' <<<"$checks" \
  || fail "필수 검사 '$REQUIRED_CHECK' 가 커밋 ${head_sha:0:7} 에서 성공하지 않았습니다."
bad=$(awk -F'\t' '$2 != "completed" || ($3 != "success" && $3 != "skipped" && $3 != "neutral")' <<<"$checks")
[[ -z "$bad" ]] || fail "끝나지 않았거나 실패한 검사가 있습니다:
$bad"

# main 최신 커밋을 포함해야 한다(main 기준으로 맞춘 뒤 CI를 다시 통과한 상태).
behind=$(gh api "repos/{owner}/{repo}/compare/main...$head_sha" --jq '.behind_by')
[[ "$behind" == "0" ]] || fail "PR #$pr 이 main보다 커밋 ${behind}개 뒤처져 있습니다. main을 합치고 CI를 다시 통과시키세요."

# 검사 이후 새 커밋이 올라왔다면 GitHub이 머지를 거부한다.
gh pr merge "$pr" --squash --delete-branch --match-head-commit "$head_sha"
echo "PR #$pr 머지 완료(${head_sha:0:7})."
