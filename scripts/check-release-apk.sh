#!/usr/bin/env bash
# 릴리스 APK의 매니페스트·버전을 확인한다(#118). CI는 PR마다 서명 없는 릴리스 빌드에, 릴리스 워크플로는 서명한 APK에 돌린다.
#   - 패키지 이름이 com.sunseat.app이다
#   - 디버그 가능(debuggable)·테스트 전용(testOnly)·평문 통신 허용(usesCleartextTraffic)이 켜져 있지 않다
#   - 디버그 전용 화면(app/src/debug의 LocationDebugActivity·SimulationFixtureActivity)이 들어 있지 않다
#   - 권한이 아래 허용 목록과 같다. 의존성이 권한을 더하면 여기서 실패하므로, 의도한 것인지 보고 목록을 고친다.
#   - versionName·versionCode가 버전 규칙(scripts/release-version.mjs)에 맞고, --tag가 있으면 태그와 같다
#
# 사용법: scripts/check-release-apk.sh <apk> [--tag <태그>]   (저장소 최상위에서)
# aapt2는 ANDROID_HOME(없으면 ANDROID_SDK_ROOT, Windows는 %LOCALAPPDATA%/Android/Sdk)의 가장 새 build-tools에서 찾는다.
# 종료 코드: 0 통과, 1 위반, 2 사용법 오류·도구 없음
set -euo pipefail

usage() { echo "사용법: $0 <apk> [--tag <태그>]"; exit 2; }
[ $# -eq 1 ] || [ $# -eq 3 ] || usage
apk=$1
tag_args=()
if [ $# -eq 3 ]; then
  [ "$2" = --tag ] || usage
  tag_args=(--tag "$3")
fi
[ -f "$apk" ] || { echo "APK가 없습니다: $apk"; exit 2; }

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-}/Android/Sdk}}
bt=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)
aapt2="$bt/aapt2"
[ -x "$aapt2" ] || aapt2="$bt/aapt2.exe"
[ -x "$aapt2" ] || { echo "aapt2를 찾지 못했습니다: $bt"; exit 2; }

package=com.sunseat.app
allowed_permissions=(
  android.permission.INTERNET
  android.permission.ACCESS_COARSE_LOCATION
  android.permission.ACCESS_FINE_LOCATION
  # androidx.core가 targetSdk 33 이상에서 더하는 앱 내부 권한(동적 리시버 보호용).
  com.sunseat.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
)
debug_only=(com.sunseat.app.location.LocationDebugActivity com.sunseat.app.SimulationFixtureActivity)

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
"$aapt2" dump badging "$apk" | tr -d '\r' > "$tmp/badging.txt"
"$aapt2" dump xmltree --file AndroidManifest.xml "$apk" | tr -d '\r' > "$tmp/manifest.txt"

found=0
report() { echo "::error::$*"; found=1; }

grep -E "^package:|^(min|target)SdkVersion:" "$tmp/badging.txt"
grep -q "^package: name='$package' " "$tmp/badging.txt" || report "패키지 이름이 $package 가 아닙니다."
grep -q '^application-debuggable' "$tmp/badging.txt" && report "디버그 가능한(debuggable) APK입니다."
for attr in debuggable testOnly usesCleartextTraffic; do
  grep -q "android:$attr([^)]*)=true" "$tmp/manifest.txt" && report "매니페스트에 android:$attr=true가 있습니다."
done
for activity in "${debug_only[@]}"; do
  grep -q "android:name([^)]*)=\"$activity\"" "$tmp/manifest.txt" && report "디버그 전용 화면이 들어 있습니다: $activity"
done

actual=$(sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" "$tmp/badging.txt" | sort -u)
expected=$(printf '%s\n' "${allowed_permissions[@]}" | sort -u)
echo "권한:"; sed 's/^/  /' <<< "$actual"
if [ "$actual" != "$expected" ]; then
  report "권한이 허용 목록(scripts/check-release-apk.sh)과 다릅니다. 추가: $(comm -13 <(echo "$expected") <(echo "$actual") | tr '\n' ' ') 빠짐: $(comm -23 <(echo "$expected") <(echo "$actual") | tr '\n' ' ')"
fi

node "$(dirname "$0")/release-version.mjs" apk "$tmp/badging.txt" "${tag_args[@]}" || found=1

if [ "$found" -ne 0 ]; then
  echo "릴리스 APK 확인 실패: $apk"
  exit 1
fi
echo "릴리스 APK 확인 통과: $apk"
