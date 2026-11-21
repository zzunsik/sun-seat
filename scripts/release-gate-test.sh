#!/usr/bin/env bash
# scripts/release-gate.sh tag 자체 테스트(#118). 임시 git 저장소에 develop·main·태그를 만들어 판정을 확인한다.
# ci·gateway는 GitHub API를 부르므로 여기서는 보지 않는다.
# 사용법: scripts/release-gate-test.sh
set -euo pipefail

scripts=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/repo/scripts"
cp "$scripts/release-gate.sh" "$scripts/release-version.mjs" "$work/repo/scripts/"
cd "$work/repo"
unset GITHUB_OUTPUT

git init -q -b develop
git config user.email test@example.invalid
git config user.name release-gate-test
git config core.autocrlf false

pass=0
failures=0
ok() { pass=$((pass + 1)); }
bad() { echo "실패: $*"; failures=$((failures + 1)); }

# expect <0|1> <설명> <태그> <커밋> [기대 출력 줄...]
expect() {
  local want=$1 desc=$2 tag=$3 rev=$4 out code line
  shift 4
  set +e
  out=$(bash scripts/release-gate.sh tag "$tag" "$rev" main 2>&1)
  code=$?
  set -e
  if [ "$code" != "$want" ]; then bad "$desc: 종료 코드 $code (기대 $want)"; echo "$out" | sed 's/^/    /'; return; fi
  for line in "$@"; do
    grep -qxF -- "$line" <<< "$out" || { bad "$desc: 출력에 '$line' 없음"; echo "$out" | sed 's/^/    /'; return; }
  done
  ok
}

version() { # version <name> <code>
  mkdir -p app
  printf 'android {\n    defaultConfig {\n        versionCode = %s\n        versionName = "%s"\n    }\n}\n' "$2" "$1" > app/build.gradle.kts
  git add app/build.gradle.kts
}
commit() { git commit -q --allow-empty -m "$1"; }
# release <메시지>: develop을 main에 머지 커밋으로 합친다(릴리스 PR).
release() { git checkout -q main; git merge -q --no-ff -m "$1" develop; git checkout -q develop; }

mkdir -p workers/api
echo 'export default {}' > workers/api/index.ts
version 0.1.0 100
git add workers/api/index.ts
commit "첫 커밋"
git branch main
version 0.1.0 100
echo '// app' > app/Main.kt
git add app/Main.kt
commit "기능"
release "릴리스 0.1.0"
r1=$(git rev-parse main)

expect 0 "첫 릴리스: 게이트웨이 확인 필요" v0.1.0 "$r1" \
  "version-name=0.1.0" "version-code=100" "prerelease=false" "sha=$r1" "previous-tag=" "gateway-changed=true"
expect 1 "형식이 틀린 태그" v0.1 "$r1"
expect 1 "시험판 형식 오류" v0.1.0-beta.1 "$r1"
expect 1 "versionName이 태그와 다름" v0.1.1 "$r1"
commit "아직 릴리스하지 않은 develop 커밋"
expect 1 "develop에만 있는 커밋" v0.1.0 "$(git rev-parse develop)"
expect 1 "없는 커밋" v0.1.0 0000000000000000000000000000000000000000
git tag v0.1.0 "$r1"

# 게이트웨이를 바꾸지 않은 끝번호 릴리스
version 0.1.1 101
echo '// fix' >> app/Main.kt
git add app/Main.kt
commit "버그 수정, 버전 0.1.1"
release "릴리스 0.1.1"
r2=$(git rev-parse main)
expect 0 "게이트웨이 변경 없음" v0.1.1 "$r2" "previous-tag=v0.1.0" "gateway-changed=false"
version 0.1.1 1
commit "versionCode 규칙 위반"
release "릴리스 잘못된 versionCode"
expect 1 "versionCode가 규칙과 다름" v0.1.1 "$(git rev-parse main)"
version 0.1.1 101
commit "versionCode 되돌림"
git tag v0.1.1 "$r2"

# 게이트웨이를 바꾼 시험판 → 같은 커밋 정식판
version 0.2.0-rc.1 200
echo 'export default { fetch() {} }' > workers/api/index.ts
git add workers/api/index.ts
commit "게이트웨이 변경, 버전 0.2.0-rc.1"
release "릴리스 0.2.0-rc.1"
r3=$(git rev-parse main)
expect 0 "게이트웨이 변경 + 시험판" v0.2.0-rc.1 "$r3" \
  "prerelease=true" "version-code=200" "previous-tag=v0.1.1" "gateway-changed=true"
expect 1 "시험판 versionName에 정식 태그" v0.2.0 "$r3"
git tag v0.2.0-rc.1 "$r3"
version 0.2.0 200
commit "버전 0.2.0"
release "릴리스 0.2.0"
r4=$(git rev-parse main)
expect 0 "시험판 뒤 정식판(게이트웨이 그대로)" v0.2.0 "$r4" "prerelease=false" "previous-tag=v0.2.0-rc.1" "gateway-changed=false"

# 배포 워크플로만 바뀌어도 게이트웨이 변경으로 본다.
git tag v0.2.0 "$r4"
version 0.2.1 201
mkdir -p .github/workflows
echo 'name: Deploy API gateway' > .github/workflows/deploy-api.yml
git add .github/workflows/deploy-api.yml
commit "배포 워크플로 변경, 버전 0.2.1"
release "릴리스 0.2.1"
expect 0 "deploy-api.yml 변경" v0.2.1 "$(git rev-parse main)" "previous-tag=v0.2.0" "gateway-changed=true"

# GITHUB_OUTPUT에 key=value가 쓰인다.
GITHUB_OUTPUT="$work/out.txt" bash scripts/release-gate.sh tag v0.2.1 main main > /dev/null
if grep -qx 'gateway-changed=true' "$work/out.txt" && grep -qx 'version-code=201' "$work/out.txt"; then ok; else bad "GITHUB_OUTPUT 기록"; fi

echo "release-gate 자체 테스트: 통과 $pass, 실패 $failures"
[ "$failures" -eq 0 ]
