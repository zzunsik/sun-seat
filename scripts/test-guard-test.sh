#!/usr/bin/env bash
# scripts/test-guard.sh 자체 테스트(#117). 임시 git 저장소에 경우별 브랜치를 만들어 판정을 확인한다.
# 사용법: scripts/test-guard-test.sh
set -euo pipefail

GUARD=$(cd "$(dirname "$0")" && pwd)/test-guard.sh
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"

git init -q -b main repo
cd repo
git config user.email test@example.invalid
git config user.name test-guard-test
git config core.autocrlf false

T=app/src/test/java/com/example/SampleTest.kt
mkdir -p app/src/test/java/com/example app/src/test/resources app/src/test/screenshots app/src/main/java/com/example
cat > "$T" <<'EOF'
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleTest {
    @Test
    fun addsNumbers() {
        val sum = 1 + 2
        // assertEquals는 기대값을 먼저 쓴다
        assertEquals(3, sum)
    }

    @Test
    fun multiLine() {
        assertEquals(
            "기대값",
            "기대" + "값",
        )
    }

    @Test
    fun chained() {
        composeRule.onNodeWithText("확인")
            .assertExists()
        assertTrue(true)
    }
}
EOF
echo '{"a":1}' > app/src/test/resources/sample.json
printf 'PNG1' > app/src/test/screenshots/shot.png
echo 'fun main() = Unit' > app/src/main/java/com/example/App.kt
git add -A
git commit -qm base

fails=0
# case <이름> <기대(pass|fail)> <브랜치 변경 함수> [PR_BODY] [PR_LABELS] [출력에 있어야 할 문자열]
run_case() {
  local name=$1 expect=$2 change=$3 body=${4:-} labels=${5:-} needle=${6:-} out status
  git checkout -q -b "case-$name" main
  $change
  git add -A
  git commit -qm "$name" --allow-empty
  set +e
  out=$(PR_BODY=$body PR_LABELS=$labels GITHUB_STEP_SUMMARY= bash "$GUARD" main HEAD 2>&1)
  status=$?
  set -e
  local got=pass
  [ $status -eq 0 ] || got=fail
  [ $status -le 1 ] || got="error($status)"
  if [ "$got" = "$expect" ] && { [ -z "$needle" ] || grep -qF -- "$needle" <<< "$out"; }; then
    echo "ok   $name ($got)"
  else
    echo "FAIL $name: 기대 $expect${needle:+, 출력에 '$needle'}, 실제 $got"
    echo "$out" | sed 's/^/     | /'
    fails=$((fails + 1))
  fi
  git checkout -q main
}

replace() { # replace <파일> <찾을 문자열> <바꿀 문자열>  (첫 일치만)
  awk -v a="$2" -v b="$3" '!done && (i = index($0, a)) { $0 = substr($0, 1, i - 1) b substr($0, i + length(a)); done = 1 } { print }' "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}
delete_line() { # delete_line <파일> <문자열>  (첫 일치 줄)
  awk -v a="$2" '!done && index($0, a) { done = 1; next } { print }' "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

c_add_only() {
  awk '/^}$/ { print "\n    @Test\n    fun added() {\n        assertEquals(1, 1)\n    }" } { print }' "$T" > "$T.tmp" && mv "$T.tmp" "$T"
  printf 'package com.example\n\nclass NewTest {\n    @Test\n    fun x() = assertTrue(true)\n}\n' > app/src/test/java/com/example/NewTest.kt
}
c_app_only() { echo 'fun other() = Unit' >> app/src/main/java/com/example/App.kt; }
c_remove_assert() { delete_line "$T" 'assertEquals(3, sum)'; }
c_change_expected() { replace "$T" 'assertEquals(3, sum)' 'assertEquals(4, sum)'; }
c_multiline_expected() { replace "$T" '"기대값",' '"다른 값",'; }
c_chain_receiver() { replace "$T" 'onNodeWithText("확인")' 'onNodeWithText("취소")'; }
c_remove_test() { delete_line "$T" '@Test'; }
c_edit_test_line() { replace "$T" "@Test" "@Test @Suppress(\"x\")"; }
c_add_ignore() { replace "$T" "    fun chained() {" "    @Ignore(\"잠시\")\n    fun chained() {"; }
c_comment_only() { replace "$T" '// assertEquals는 기대값을 먼저 쓴다' '// 합을 확인한다'; }
c_body_change() { replace "$T" 'val sum = 1 + 2' 'val sum = 2 + 1'; }
c_resource_modify() { echo '{"a":2}' > app/src/test/resources/sample.json; }
c_resource_delete() { git rm -q app/src/test/resources/sample.json; }
c_png_change() { printf 'PNG2' > app/src/test/screenshots/shot.png; printf 'PNG3' > app/src/test/screenshots/new.png; }
c_png_delete() { git rm -q app/src/test/screenshots/shot.png; }
c_delete_file() { git rm -q "$T"; }
c_move_test() { # chained 테스트를 다른 파일로 옮긴다
  awk '/@Test/ { held = $0; next } held && /fun chained/ { skip = 1 } held && !skip { print held } { held = "" } skip { if ($0 ~ /^    }$/) skip = 0; next } { print }' "$T" > "$T.tmp" && mv "$T.tmp" "$T"
  cat > app/src/test/java/com/example/MovedTest.kt <<'KT'
package com.example

class MovedTest {
    @Test
    fun chained() {
        assertTrue(true)
    }
}
KT
}
c_rename_file() { # 내용을 거의 그대로 둔 파일 이름 변경은 테스트를 지운 것이 아니다
  mkdir -p app/src/test/java/com/example/moved
  git mv "$T" app/src/test/java/com/example/moved/SampleTest.kt
  replace app/src/test/java/com/example/moved/SampleTest.kt "package com.example" "package com.example.moved"
}

DECL=$'## 변경\n- 사양 변경\n\n테스트 기대값 변경: 합 계산 규칙이 바뀌었다'
TEMPLATE=$'## 확인\n- [x] 기존 테스트를 지우거나 약하게 바꾸지 않았다(기대값을 바꿨으면 `테스트 기대값 변경: <사유>`)'

run_case add-only          pass c_add_only
run_case app-only          pass c_app_only
run_case comment-only      pass c_comment_only
run_case body-change       pass c_body_change
run_case png-change        pass c_png_change
run_case png-delete        pass c_png_delete
run_case remove-assert     fail c_remove_assert "" "" "SampleTest.kt:12: 검증 줄 삭제·변경"
run_case change-expected   fail c_change_expected
run_case multiline-expected fail c_multiline_expected "" "" "SampleTest.kt:18:"
run_case chain-receiver    fail c_chain_receiver "" "" "SampleTest.kt:25:"
run_case remove-test       fail c_remove_test "" "" "@Test 삭제·변경"
run_case edit-test-line    pass c_edit_test_line "" "" "남아 있어 삭제로 보지 않음"
run_case add-ignore        fail c_add_ignore "" "" "@Ignore 추가"
run_case resource-modify   fail c_resource_modify "" "" "테스트 리소스 수정"
run_case resource-delete   fail c_resource_delete "" "" "테스트 리소스 삭제"
run_case delete-file       fail c_delete_file "" "" "테스트 파일 삭제"
run_case move-test         fail c_move_test "" "" "이동으로 보임: app/src/test/java/com/example/SampleTest.kt:23 의 테스트 'chained'"
run_case rename-file       pass c_rename_file
run_case declaration       pass c_remove_assert "$DECL" "" "합 계산 규칙이 바뀌었다"
run_case declaration-crlf  pass c_remove_assert $'본문\r\n- 테스트 기대값 변경: 줄 끝이 CRLF\r\n' "" "줄 끝이 CRLF"
run_case label             pass c_remove_assert "" "세팅,test-change-approved" "test-change-approved"
run_case other-label       fail c_remove_assert "" "세팅,test-change-approved-not"
run_case empty-reason      fail c_remove_assert $'테스트 기대값 변경:   \n'
run_case template-only     fail c_remove_assert "$TEMPLATE"
run_case findings-on-pass  pass c_remove_assert "$DECL" "" "assertEquals(3, sum)"

echo
if [ $fails -ne 0 ]; then
  echo "test-guard 자체 테스트 ${fails}건 실패"
  exit 1
fi
echo "test-guard 자체 테스트 모두 통과"
