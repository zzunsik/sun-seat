#!/usr/bin/env bash
# 기존 테스트를 지우거나 약하게 바꾼 PR을 찾아 실패시킨다(#117).
#
# 사용법: scripts/test-guard.sh <base> <head>
#   base, head: 커밋(브랜치·SHA). base와 head의 merge-base부터 head까지의 변경을 본다.
#   환경 변수
#     PR_BODY    PR 본문. "테스트 기대값 변경: <사유>" 줄이 있으면 통과시킨다.
#     PR_LABELS  쉼표로 이은 PR 라벨. test-change-approved가 있으면 통과시킨다.
#     GITHUB_STEP_SUMMARY  있으면 결과 요약을 덧붙인다.
#
# 검사 대상: app/src/test/ 아래 기존 파일(base에 있던 파일)의 수정·삭제·이름 변경
#   - @Test 줄, 검증 호출(assert*, fail(, verify*, .assert*)에 속한 줄의 삭제·변경
#     여러 줄에 걸친 호출은 괄호가 닫힐 때까지, `.assert…`로 이어지는 호출 체인은 앞줄까지 검증 줄로 본다.
#   - @Ignore 추가
#   - 테스트 파일 삭제, app/src/test/resources/ 파일의 수정·삭제·이름 변경
#   기준 스크린샷(app/src/test/screenshots/*.png)은 보지 않는다(CI 비교와 리뷰로 다룬다).
#
# 종료 코드: 0 통과(발견 없음 또는 예외 표기), 1 실패, 2 사용법 오류
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "사용법: $0 <base> <head>" >&2
  exit 2
fi
base_ref=$1
head_ref=$2
TEST_ROOT=app/src/test/
RES_ROOT=app/src/test/resources/
SHOT_ROOT=app/src/test/screenshots/
APPROVE_LABEL=test-change-approved
MAX_LINES_PER_FILE=30

mb=$(git merge-base "$base_ref" "$head_ref")
head=$(git rev-parse "$head_ref")

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

findings=()   # 실패 사유 한 줄씩
notes=()      # 판단 근거(이동으로 보이는 테스트 등)

add_finding() { findings+=("$1"); }

# 주석 줄(//, /*, *)은 검증 줄로 보지 않는다.
# 입력: 파일 내용. 출력: 검증 줄과 @Test 줄의 줄 번호와 종류("번호 assert" / "번호 test 이름").
mark_lines() {
  awk '
    function strip(s) { gsub(/"([^"\\]|\\.)*"/, "\"\"", s); gsub(/\/\/.*$/, "", s); return s }
    function is_comment(s) { return s ~ /^[ \t]*(\/\/|\/\*|\*)/ }
    function count(s, ch,   n, i) { n = 0; for (i = 1; i <= length(s); i++) if (substr(s, i, 1) == ch) n++; return n }
    { line[NR] = $0 }
    END {
      for (i = 1; i <= NR; i++) {
        s = line[i]
        if (is_comment(s)) continue
        c = strip(s)
        if (c ~ /@Test([^A-Za-z0-9_]|$)/) {
          name = ""
          for (j = i; j <= NR && j < i + 5; j++) {
            if (match(line[j], /fun[ \t]+(`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)/)) {
              name = substr(line[j], RSTART, RLENGTH); sub(/^fun[ \t]+/, "", name); break
            }
          }
          print i, "test", name
        }
        if (c ~ /(^|[^A-Za-z0-9_])(assert[A-Za-z0-9_]*|fail|verify[A-Za-z0-9_]*)[ \t]*[(<{]/ || c ~ /\.assert[A-Za-z0-9_]*/) {
          # 앞줄로 이어지는 호출 체인(.assertExists() 등)
          k = i
          while (k > 1 && line[k] ~ /^[ \t]*\./) { k--; if (!is_comment(line[k])) mark[k] = 1 }
          # 괄호가 닫힐 때까지
          depth = 0
          for (j = i; j <= NR && j < i + 40; j++) {
            cj = strip(line[j])
            mark[j] = 1
            depth += count(cj, "(") + count(cj, "{") - count(cj, ")") - count(cj, "}")
            if (depth <= 0) break
          }
        }
      }
      for (k in mark) print k, "assert"
    }'
}

# 두 blob의 -U0 diff에서 지운 줄(옛 번호)과 추가한 줄(새 번호)을 뽑는다.
# 출력: "-<번호>\t<내용>" / "+<번호>\t<내용>"
diff_lines() {
  git diff --no-color --no-ext-diff -U0 "$1" "$2" | awk '
    /^@@/ {
      match($0, /-[0-9]+/); o = substr($0, RSTART + 1, RLENGTH - 1)
      match($0, /\+[0-9]+/); n = substr($0, RSTART + 1, RLENGTH - 1)
      inh = 1; next
    }
    !inh { next }
    /^-/ { printf "-%d\t%s\n", o, substr($0, 2); o++; next }
    /^\+/ { printf "+%d\t%s\n", n, substr($0, 2); n++; next }
  '
}

trim() { local s=$1; s=${s#"${s%%[![:space:]]*}"}; s=${s%"${s##*[![:space:]]}"}; printf '%s' "$s"; }

# 지운 테스트가 head의 다른 곳에 같은 이름으로 있으면 이동으로 보고 근거를 남긴다.
check_move() {
  local name=$1 from=$2 plain where
  [ -n "$name" ] || return 0
  plain=${name//\`/}
  where=$(git grep -nF -e "fun $name" "$head" -- "$TEST_ROOT" 2>/dev/null | sed "s/^$head://" | head -3 || true)
  if [ -z "$where" ] && [ "$plain" != "$name" ]; then
    where=$(git grep -nF -e "$plain" "$head" -- "$TEST_ROOT" 2>/dev/null | sed "s/^$head://" | grep -F "fun " | head -3 || true)
  fi
  if [ -n "$where" ]; then
    notes+=("이동으로 보임: $from 의 테스트 '$name' 이(가) head에 있음 → $(echo "$where" | cut -d: -f1,2 | paste -sd' ' -)")
  fi
}

check_modified() {
  local old=$1 new=$2 marks n kind name text msg shown=0 more=0
  git show "$mb:$old" > "$tmp/old"
  marks="$tmp/marks"
  mark_lines < "$tmp/old" > "$marks"
  declare -A assert_line=() test_name=() kept_test=()
  while read -r n kind name; do
    if [ "$kind" = test ]; then test_name[$n]=$name; else assert_line[$n]=1; fi
  done < "$marks"
  # head의 같은 파일에 같은 이름의 @Test가 남아 있으면 그 @Test 줄의 변경은 테스트 삭제로 보지 않는다
  # (예: `@Test fun a() {` → `@Test fun a() = helper()`). 테스트 안의 검증 줄은 따로 본다.
  while read -r n kind name; do
    [ "$kind" = test ] && [ -n "$name" ] && kept_test[$name]=1
  done < <(git show "$head:$new" | mark_lines)

  while IFS=$'\t' read -r tag text; do
    n=${tag:1}
    msg=""
    case $tag in
      -*)
        if [ -n "${test_name[$n]+x}" ]; then
          name=${test_name[$n]}
          if [ -n "$name" ] && [ -n "${kept_test[$name]:-}" ]; then
            notes+=("$old:$n: @Test 줄이 바뀌었지만 같은 파일에 테스트 '$name'이(가) 남아 있어 삭제로 보지 않음")
          else
            check_move "$name" "$old:$n"
            msg="$old:$n: @Test 삭제·변경 | $(trim "$text")"
          fi
        fi
        if [ -z "$msg" ] && [ -n "${assert_line[$n]:-}" ]; then
          msg="$old:$n: 검증 줄 삭제·변경 | $(trim "$text")"
        fi
        ;;
      +*)
        [[ $text =~ @Ignore ]] || continue
        [[ $text =~ ^[[:space:]]*(//|/\*|\*) ]] && continue
        msg="$new:$n: @Ignore 추가 | $(trim "$text")"
        ;;
    esac
    [ -n "$msg" ] || continue
    if [ $shown -lt $MAX_LINES_PER_FILE ]; then add_finding "$msg"; shown=$((shown + 1)); else more=$((more + 1)); fi
  done < <(diff_lines "$mb:$old" "$head:$new")
  [ $more -eq 0 ] || add_finding "$new: 같은 파일에서 ${more}건 더(생략)"
}

check_deleted() {
  local old=$1 tests=0 asserts=0 n kind name
  git show "$mb:$old" > "$tmp/old"
  while read -r n kind name; do
    if [ "$kind" = test ]; then tests=$((tests + 1)); check_move "$name" "$old:$n"; else asserts=$((asserts + 1)); fi
  done < <(mark_lines < "$tmp/old")
  add_finding "$old: 테스트 파일 삭제 (@Test ${tests}개, 검증 줄 ${asserts}줄)"
}

while IFS=$'\t' read -r status p1 p2; do
  # 기준 스크린샷 PNG는 보지 않는다(이름 변경이면 양쪽 모두 기준 스크린샷일 때만).
  if [[ $p1 == "$SHOT_ROOT"*.png && ( -z ${p2:-} || $p2 == "$SHOT_ROOT"*.png ) ]]; then continue; fi
  case $status in
    A*) continue ;;
    M*|T*)
      if [[ $p1 == "$RES_ROOT"* ]]; then add_finding "$p1: 테스트 리소스 수정"; else check_modified "$p1" "$p1"; fi ;;
    D*)
      if [[ $p1 == "$RES_ROOT"* ]]; then add_finding "$p1: 테스트 리소스 삭제"; else check_deleted "$p1"; fi ;;
    R*|C*)
      [[ $status == C* ]] && continue   # 복사는 기존 파일을 건드리지 않는다
      if [[ $p1 == "$RES_ROOT"* ]]; then add_finding "$p1 → $p2: 테스트 리소스 이름 변경"
      elif [[ $p2 != "$TEST_ROOT"* ]]; then check_deleted "$p1"
      else check_modified "$p1" "$p2"; fi ;;
  esac
done < <(git diff --no-color --name-status -M "$mb" "$head" -- "$TEST_ROOT")

# 예외 표기
reason=""
while IFS= read -r line; do
  line=${line%$'\r'}
  if [[ $line =~ ^[[:space:]]*([-*>][[:space:]]*)*테스트\ 기대값\ 변경:(.*)$ ]]; then
    r=$(trim "${BASH_REMATCH[2]}")
    [[ -z $r || $r == "<사유>"* ]] && continue
    reason=$r
    break
  fi
done <<< "${PR_BODY:-}"
approved_label=""
case ",${PR_LABELS:-}," in *",$APPROVE_LABEL,"*) approved_label=1 ;; esac

out() { echo "$1"; [ -z "${GITHUB_STEP_SUMMARY:-}" ] || echo "$1" >> "$GITHUB_STEP_SUMMARY"; }

out "## test-guard"
out ""
out "비교: \`$(git rev-parse --short "$mb")...$(git rev-parse --short "$head")\` (\`$TEST_ROOT\`)"
out ""
if [ ${#findings[@]} -eq 0 ]; then
  out "기존 테스트를 지우거나 약하게 바꾼 곳이 없습니다."
else
  out "기존 테스트를 지우거나 바꾼 곳 ${#findings[@]}건:"
  out ""
  for f in "${findings[@]}"; do out "- $f"; done
fi
if [ ${#notes[@]} -gt 0 ]; then
  out ""
  out "판단 근거:"
  for f in "${notes[@]}"; do out "- $f"; done
  if printf '%s\n' "${notes[@]}" | grep -q '^이동으로 보임'; then
    out ""
    out "테스트를 옮기기만 했어도 예외 표기가 필요합니다."
  fi
fi
[ ${#findings[@]} -gt 0 ] || exit 0
out ""
if [ -n "$reason" ]; then
  out "통과: PR 본문의 예외 표기 — 테스트 기대값 변경: $reason"
  exit 0
fi
if [ -n "$approved_label" ]; then
  out "통과: 라벨 \`$APPROVE_LABEL\`"
  exit 0
fi
out "실패: 의도한 사양 변경이면 PR 본문에 \`테스트 기대값 변경: <사유>\` 줄을 적거나 라벨 \`$APPROVE_LABEL\`을 붙이세요(AGENTS.md \"테스트\"). 본문을 고치면 CI가 다시 돕니다."
exit 1
