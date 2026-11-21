#!/usr/bin/env bash
# 연결된 기기에서 주요 화면 흐름과 전환을 adb + uiautomator로 점검한다(#169).
# 사용법·판정 기준은 docs/device-smoke.md. 로컬 전용이며 CI에서 돌리지 않는다.
#
# 사용법: scripts/device-smoke.sh [-s <serial>] [--apk <apk>] [--flows 1,2,...] [--lock-dir <dir>] [-o <dir>] [--no-capture]
#   -s <serial>       기기 시리얼(없으면 ANDROID_SERIAL, 둘 다 없으면 연결된 기기가 하나일 때 그 기기)
#   --apk <apk>       점검 전에 이 debug APK를 설치한다(없으면 설치된 앱을 쓴다)
#   --flows <목록>    돌릴 흐름 번호(쉼표). 기본 1~8 전체
#   --lock-dir <dir>  공용 기기 잠금 폴더(SMOKE_LOCK_DIR). 시작할 때 만들고 끝나면 지운다. 이미 있으면 기기를 건드리지 않고 끝낸다
#   -o <dir>          결과 폴더(SMOKE_OUT). 기본 ${TMPDIR:-/tmp}/sun-seat-device-smoke/<시각>. 저장소 안에 두지 않는다
#   --no-capture      실패한 흐름의 화면 캡처를 남기지 않는다
# 환경 변수: ADB(adb 경로), ANDROID_SERIAL, SMOKE_LOCK_DIR, SMOKE_OUT, SMOKE_FORCE_3D=1(에뮬레이터에서도 흐름 6·7 실행)
#
# 종료 코드: 0 모든 흐름 통과(건너뜀 포함), 1 실패한 흐름 있음, 2 사용법 오류, 3 기기를 쓸 수 없음(잠금 사용 중·연결 없음·잠금 화면)
#
# 기기에 하는 일: 앱 강제 종료·실행, 탭·끌기·뒤로 가기, 글자 크기(font_scale)·회전(accelerometer_rotation, user_rotation)
# 임시 변경. 바꾼 설정은 끝날 때(실패·중단 포함) 원래 값으로 되돌린다. adb connect/disconnect/kill-server는 쓰지 않는다.
# 검색어는 공개 장소(서울역 → 강남역)만 쓴다. 앱의 최근 장소 목록에 이 두 곳이 남는다.
set -uo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'   # Git Bash가 /sdcard 같은 기기 경로를 Windows 경로로 바꾸지 않게 한다

PKG=com.sunseat.app
ACTIVITY=com.sunseat.app/.MainActivity
ORIGIN_QUERY='Seoul Station'      # adb input text는 한글을 못 넣는다. 카카오 검색이 영문 이름으로 찾는다
ORIGIN_PICK='^서울역$'
DEST_QUERY='Gangnam Station'
DEST_PICK='^강남역( |$)'

serial=${ANDROID_SERIAL:-}
apk=""
flows="1,2,3,4,5,6,7,8"
lock_dir=${SMOKE_LOCK_DIR:-}
out=${SMOKE_OUT:-}
capture=1

usage() { sed -n '4,11p' "$0" >&2; exit 2; }
while [ $# -gt 0 ]; do
  case $1 in
    -s) serial=${2:-}; shift 2 ;;
    --apk) apk=${2:-}; shift 2 ;;
    --flows) flows=${2:-}; shift 2 ;;
    --lock-dir) lock_dir=${2:-}; shift 2 ;;
    -o) out=${2:-}; shift 2 ;;
    --no-capture) capture=0; shift ;;
    -h|--help) usage ;;
    *) echo "알 수 없는 인자: $1" >&2; usage ;;
  esac
done
case ",$flows," in *[!0-9,]*) echo "--flows는 1~8 숫자를 쉼표로 이은 값이다" >&2; exit 2 ;; esac

# ---------- adb ----------
if [ -z "${ADB:-}" ]; then
  if command -v adb >/dev/null 2>&1; then ADB=adb
  else
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${LOCALAPPDATA:-}/Android/Sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
      [ -n "$sdk" ] || continue
      for c in "$sdk/platform-tools/adb" "$sdk/platform-tools/adb.exe"; do [ -x "$c" ] && { ADB=$c; break 2; }; done
    done
  fi
fi
[ -n "${ADB:-}" ] || { echo "adb를 찾지 못했다. ADB 환경 변수로 경로를 준다" >&2; exit 3; }

devices=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "$serial" ]; then
  n=$(printf '%s\n' "$devices" | grep -c . || true)
  [ "$n" = 1 ] || { echo "기기가 $n대 연결되어 있다. -s <serial>로 고른다" >&2; exit 3; }
  serial=$devices
fi
printf '%s\n' "$devices" | grep -qxF "$serial" || { echo "연결된 기기 목록에 지정한 기기가 없다(adb devices 확인)" >&2; exit 3; }
a() { "$ADB" -s "$serial" "$@"; }
sh_() { "$ADB" -s "$serial" shell "$@" | tr -d '\r'; }

# ---------- 결과 폴더 ----------
[ -n "$out" ] || out="${TMPDIR:-/tmp}/sun-seat-device-smoke/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out" || { echo "결과 폴더를 만들 수 없다: $out" >&2; exit 2; }
if top=$(git rev-parse --show-toplevel 2>/dev/null) && [ -n "$top" ]; then
  case "$(cd "$out" && pwd -P)/" in "$(cd "$top" && pwd -P)/"*) echo "결과 폴더를 저장소 안에 두지 않는다(-o로 바깥 경로를 준다)" >&2; exit 2 ;; esac
fi
log() { printf '%s\n' "$*" | tee -a "$out/summary.txt"; }

# ---------- 잠금과 설정 복원 ----------
lock_taken=0
orig_font=""; orig_rot=""; orig_accel=""; settings_saved=0
restore() {
  if [ "$settings_saved" = 1 ]; then
    set_setting font_scale "$orig_font"
    set_setting accelerometer_rotation "$orig_accel"
    set_setting user_rotation "$orig_rot"
    local now; now="$(sh_ settings get system font_scale)/$(sh_ settings get system accelerometer_rotation)/$(sh_ settings get system user_rotation)"
    log "설정 복원: font_scale/accelerometer_rotation/user_rotation = $now (시작 값 $orig_font/$orig_accel/$orig_rot)"
    settings_saved=0
  fi
  if [ "$lock_taken" = 1 ]; then rm -rf "$lock_dir"; lock_taken=0; fi
}
set_setting() { # 이름 값. 시작할 때 값이 없었으면(null) 지운다
  if [ "$2" = null ] || [ -z "$2" ]; then sh_ settings delete system "$1" >/dev/null; else sh_ settings put system "$1" "$2"; fi
}
trap restore EXIT
trap 'exit 130' INT TERM

if [ -n "$lock_dir" ]; then
  if ! mkdir "$lock_dir" 2>/dev/null; then
    echo "기기 잠금이 사용 중이다(잠금 폴더가 이미 있다). 다른 사용자가 끝낸 뒤 다시 실행한다" >&2
    exit 3
  fi
  lock_taken=1
  printf 'device-smoke.sh (pid %s, %s)\n' "$$" "$(date '+%Y-%m-%d %H:%M:%S')" > "$lock_dir/owner.txt"
fi

# ---------- 기기 준비 ----------
sh_ input keyevent KEYCODE_WAKEUP
if sh_ dumpsys window | grep -q 'isKeyguardShowing=true'; then
  sh_ wm dismiss-keyguard >/dev/null 2>&1; sleep 1
  sh_ dumpsys window | grep -q 'isKeyguardShowing=true' && { echo "잠금 화면이 풀리지 않는다. 기기에서 직접 잠금을 푼 뒤 다시 실행한다" >&2; exit 3; }
fi
orig_font=$(sh_ settings get system font_scale)
orig_rot=$(sh_ settings get system user_rotation)
orig_accel=$(sh_ settings get system accelerometer_rotation)
settings_saved=1
set_setting font_scale 1.0
set_setting accelerometer_rotation 0
set_setting user_rotation 0

emulator=0
[ "$(sh_ getprop ro.kernel.qemu)" = 1 ] || [ "$(sh_ getprop ro.boot.qemu)" = 1 ] && emulator=1
if [ -n "$apk" ]; then
  [ -f "$apk" ] || { echo "APK가 없다: $apk" >&2; exit 2; }
  local_apk=$apk; command -v cygpath >/dev/null 2>&1 && local_apk=$(cygpath -m "$apk")
  abi=()
  # 카카오 지도 SDK는 arm 라이브러리만 있다. x86_64 에뮬레이터는 arm64 변환으로 설치해야 지도가 뜬다.
  sh_ getprop ro.product.cpu.abilist | grep -q arm64-v8a && abi=(--abi arm64-v8a)
  a install -r "${abi[@]}" "$local_apk" >"$out/install.txt" 2>&1 || { cat "$out/install.txt" >&2; echo "설치 실패" >&2; exit 3; }
fi
sh_ pm path "$PKG" | grep -q . || { echo "$PKG이 설치되어 있지 않다(--apk로 debug APK를 준다)" >&2; exit 3; }
density=$(sh_ wm density | awk -F': ' '/Override/ {o=$2} /Physical/ {p=$2} END {print (o!="" ? o : p)}')
dp() { echo $(( $1 * 160 / density )); }   # px → dp
px() { echo $(( $1 * density / 160 )); }   # dp → px

# ---------- 화면 읽기 ----------
# dump: 화면 노드를 $out/screen.tsv(아이디 글자 설명 x1 y1 x2 y2 enabled)로 만든다.
dump() {
  local i
  for i in 1 2 3 4 5 6; do
    sh_ rm -f /sdcard/sunseat-smoke.xml
    sh_ uiautomator dump /sdcard/sunseat-smoke.xml >/dev/null 2>&1
    a exec-out cat /sdcard/sunseat-smoke.xml > "$out/screen.xml" 2>/dev/null
    grep -q '<hierarchy' "$out/screen.xml" && break
    sleep 1
  done
  sh_ rm -f /sdcard/sunseat-smoke.xml
  sed 's/<node /\n<node /g' "$out/screen.xml" | awk -v OFS='\t' '
    function attr(n,   s) { if (match($0, " " n "=\"[^\"]*\"")) { s = substr($0, RSTART + length(n) + 3, RLENGTH - length(n) - 4)
        gsub(/&#10;/, " ", s); gsub(/&quot;/, "\"", s); gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s); gsub(/&amp;/, "\\&", s); gsub(/\t/, " ", s); return s } return "" }
    /^<node / { b = attr("bounds"); gsub(/[\[\]]/, " ", b); gsub(/,/, " ", b); split(b, p, " ")
      print attr("resource-id"), attr("text"), attr("content-desc"), p[1]+0, p[2]+0, p[3]+0, p[4]+0, attr("enabled") }' > "$out/screen.tsv"
}
# 첫 번째로 맞는 노드의 "x1 y1 x2 y2". 열 1=아이디, 2=글자, 3=설명. 없으면 빈 줄과 실패.
find_node() { awk -F'\t' -v c="$1" -v re="$2" '$c ~ re { print $4, $5, $6, $7; f=1; exit } END { exit !f }' "$out/screen.tsv"; }
by_id() { find_node 1 "^$1\$"; }
by_text() { find_node 2 "$1"; }
has_id() { by_id "$1" >/dev/null; }
enabled_text() { awk -F'\t' -v re="$1" '$2 ~ re { e = $8 } END { exit e != "true" }' "$out/screen.tsv"; }
fmt() { set -- $1; [ $# = 4 ] && echo "[$1,$2][$3,$4]" || echo "(없음)"; }
center() { set -- $1; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
tap() { set -- $1; sh_ input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); }
drag() { sh_ input touchscreen swipe "$1" "$2" "$3" "$4" "${5:-800}"; }   # 지도·시트는 600ms 이상 천천히 끈다
motion() { sh_ input motionevent "$@" 2>&1 | grep -qiE "error|unknown|usage" && return 1; return 0; }   # 누른 채 움직이기(Android 11+)
alive() { sh_ pidof "$PKG" | grep -q .; }
wait_id() { # 아이디 시간(초)
  local end=$(( SECONDS + ${2:-15} ))
  while [ $SECONDS -lt $end ]; do dump; has_id "$1" && return 0; alive || return 1; sleep 1; done
  return 1
}
wait_text() {
  local end=$(( SECONDS + ${2:-15} ))
  while [ $SECONDS -lt $end ]; do dump; by_text "$1" >/dev/null && return 0; alive || return 1; sleep 1; done
  return 1
}

# ---------- 결과 기록 ----------
declare -A result=()
current=""
want() { case ",$flows," in *",$1,"*) return 0 ;; esac; return 1; }
begin() { current=$1; log ""; log "== 흐름 $1: $2"; }
note() { log "   $*"; }
pass() { result[$current]=PASS; log "[PASS] 흐름 $current — $*"; }
skip() { result[$1]=SKIP; log "[SKIP] 흐름 $1 — $2"; }
fail() {
  result[$current]=FAIL; log "[FAIL] 흐름 $current — $*"
  cp "$out/screen.xml" "$out/flow$current-fail.xml" 2>/dev/null
  if ! alive; then sh_ logcat -d -b crash -t 80 > "$out/flow$current-crash.txt" 2>/dev/null; log "   앱 프로세스가 없다(종료됨). 크래시 로그: flow$current-crash.txt"; fi
  [ "$capture" = 1 ] && a exec-out screencap -p > "$out/flow$current-fail.png" 2>/dev/null
}
check() { # 조건(0/1) 설명
  if [ "$1" = 0 ]; then note "통과: $2"; else note "실패: $2"; failed_checks+=("$2"); fi
}
finish() { if [ ${#failed_checks[@]} = 0 ]; then pass "$1"; else fail "${failed_checks[*]}"; fi; failed_checks=(); }
failed_checks=()
lt() { [ "$1" -le "$2" ] && echo 0 || echo 1; }   # a ≤ b

# ---------- 공통 준비 ----------
launch() {
  sh_ am force-stop "$PKG"
  sh_ am start -n "$ACTIVITY" >/dev/null
  wait_id input_map 30 && wait_id journey_header 10
}
pick_place() { # 필드 라벨 검색어 고를 글자(정규식)
  local b
  dump; b=$(awk -F'\t' -v l="$1" '$2 == l { print $4, $5, $6, $7; exit }' "$out/screen.tsv")
  [ -n "$b" ] || return 1
  tap "$b"; sleep 2
  sh_ input text "${2// /%s}"
  wait_text "$3" 20 || return 1
  b=$(by_text "$3"); tap "$b"; sleep 2
}
to_list() { # 입력 → 서울역 → 강남역 후보 목록
  launch || return 1
  pick_place 출발 "$ORIGIN_QUERY" "$ORIGIN_PICK" || return 1
  pick_place 도착 "$DEST_QUERY" "$DEST_PICK" || return 1
  wait_id candidate_0 40
}
visible_cards() { # 화면 안에 48dp 이상 보이는 후보 카드 수
  awk -F'\t' -v min="$(px 48)" '$1 ~ /^candidate_[0-9]+$/ && $7 - $5 >= min { n++ } END { print n + 0 }' "$out/screen.tsv"
}
full_expanded() { # 시트가 완전 확장: 지도 띠가 없고 손잡이가 헤더 바로 아래
  local h s; h=$(by_id candidates_header) || return 1; s=$(by_id candidates_sheet_handle) || return 1
  set -- $h; local hb=$4; set -- $s
  [ $(( $2 - hb )) -le "$(px 8)" ] && { ! by_id candidate_map >/dev/null || map_band_px | grep -qx 0; }
}
map_band_px() { # 헤더 아래끝 ~ 손잡이 위끝 사이 지도 띠 높이
  local h s; h=$(by_id candidates_header) && s=$(by_id candidates_sheet_handle) || { echo 0; return; }
  set -- $h; local hb=$4; set -- $s; local v=$(( $2 - hb )); [ $v -lt 0 ] && v=0; echo $v
}
field_value() { # 여정 헤더에서 라벨(출발·도착) 다음에 오는 첫 글자 노드
  awk -F'\t' -v l="$1" 'f && $2 != "" { print $2; exit } $2 == l { f = 1 }' "$out/screen.tsv"
}
pick_card() { # 누를 카드 번호: 두 번째 카드가 있으면 그것(선택 변화가 보이게), 없으면 첫 번째
  if has_id candidate_time_1; then echo 1; else echo 0; fi
}

log "sun-seat 기기 점검 $(date '+%Y-%m-%d %H:%M')  기기: $(sh_ getprop ro.product.model) / Android $(sh_ getprop ro.build.version.release)$([ $emulator = 1 ] && echo ' (에뮬레이터)')  밀도 ${density}dpi"
log "앱: $(sh_ dumpsys package $PKG | awk -F= '/versionName/ {print $2; exit}')  흐름: $flows"

# ---------- 흐름 1~5: 후보 목록 ----------
list_ok=1
if want 1 || want 2 || want 3 || want 4 || want 5; then
  if ! to_list; then
    list_ok=0; current=0; log "[FAIL] 준비 — 서울역 → 강남역 후보 목록에 들어가지 못했다"; cp "$out/screen.xml" "$out/setup-fail.xml" 2>/dev/null
    for f in 1 2 3 4 5; do want $f && result[$f]=FAIL; done
  fi
fi

if [ $list_ok = 1 ] && want 1; then
  begin 1 "목록 첫 진입: 헤더 아래끝 ≤ 손잡이 위끝, 지도 띠 높이 > 0"
  dump; h=$(by_id candidates_header); s=$(by_id candidates_sheet_handle); m=$(map_band_px)
  note "헤더 $(fmt "$h") 손잡이 $(fmt "$s") 지도 띠 ${m}px($(dp $m)dp)"
  if [ -n "$h" ] && [ -n "$s" ]; then
    set -- $h; hb=$4; set -- $s
    check "$(lt $hb $2)" "헤더 아래끝($hb) ≤ 손잡이 위끝($2)"
    check "$([ $m -gt 0 ] && echo 0 || echo 1)" "지도 띠 높이 > 0"
  else check 1 "헤더·손잡이 노드가 있다"; fi
  finish "헤더 $(fmt "$h"), 손잡이 $(fmt "$s"), 지도 띠 $(dp $m)dp"
fi

if [ $list_ok = 1 ] && want 3; then
  begin 3 "시트를 약 60dp 올렸다 내림(#148)"
  dump; s0=$(by_id candidates_sheet_handle); m0=$(map_band_px)
  if [ -n "$s0" ]; then
    # 손을 떼지 않고 올린 채로 화면을 읽은 뒤 제자리로 내리고 뗀다(input motionevent). 떼고 다시 잡으면 시트가 걸림점으로 돌아가 버린다.
    read -r cx cy <<<"$(center "$s0")"; d=$(px 60)
    if motion DOWN "$cx" "$cy"; then
      for i in 1 2 3 4 5 6; do motion MOVE "$cx" $(( cy - d * i / 6 )); done
      dump; s1=$(by_id candidates_sheet_handle); m1=$(map_band_px)
      for i in 5 4 3 2 1 0; do motion MOVE "$cx" $(( cy - d * i / 6 )); done
      motion UP "$cx" "$cy"
      note "올린 채 손잡이 $(fmt "$s1") 지도 띠 $(dp $m1)dp"
      set -- ${s1:-0 99999 0 0}; up=$(( cy - ($2 + $4) / 2 ))
      check "$(lt "$(px 20)" $up)" "누른 채 올리면 시트가 따라 올라간다($(dp $up)dp)"
    else
      note "이 기기는 input motionevent가 없어 올렸다 뗀 뒤 다시 내린다"
      drag "$cx" "$cy" "$cx" $(( cy - d )); sleep 1; dump; s1=$(by_id candidates_sheet_handle)
      read -r cx cy <<<"$(center "${s1:-$s0}")"; drag "$cx" "$cy" "$cx" $(( cy + d ))
    fi
    sleep 2; dump; s2=$(by_id candidates_sheet_handle); m2=$(map_band_px)
    note "처음 손잡이 $(fmt "$s0") 지도 띠 $(dp $m0)dp → 내린 뒤 $(fmt "$s2") 지도 띠 $(dp $m2)dp"
    set -- $s0; y0=$2; set -- ${s2:-0 99999 0 0}; y2=$2; diff=$(( y2 - y0 )); [ $diff -lt 0 ] && diff=$(( -diff ))
    check "$(lt $diff "$(px 8)")" "내린 뒤 손잡이 위치가 처음과 8dp 안(차이 $(dp $diff)dp)"
    check "$([ "$m2" -gt 0 ] && echo 0 || echo 1)" "지도 띠가 다시 보인다"
    alive; check $? "앱이 살아 있다"
    note "지도 발자국(표식 위치) 비교: #168의 debug 로그가 들어온 뒤 켠다(지금은 시트 위치만 본다)"
  else check 1 "손잡이 노드가 있다"; fi
  finish "손잡이 $(fmt "$s0") → $(fmt "${s2:-}") (지도 발자국 비교는 #168 뒤 활성화)"
fi

if [ $list_ok = 1 ] && { want 2 || want 4 || want 5; }; then
  begin 2 "손잡이로 끌어 완전 확장 → 카드 누름 → 미리보기 열림(#162)"
  dump; s=$(by_id candidates_sheet_handle)
  if [ -n "$s" ]; then
    read -r cx cy <<<"$(center "$s")"
    drag "$cx" "$cy" "$cx" "$(px 40)" 900; sleep 2; dump
    full_expanded; check $? "끈 뒤 완전 확장(손잡이 $(fmt "$(by_id candidates_sheet_handle)"), 헤더 $(fmt "$(by_id candidates_header)"))"
    card=$(pick_card); t=$(by_id candidate_time_$card)
    if [ -n "$t" ]; then
      tap "$t"; wait_id candidates_preview_sheet 8
      p=$(by_id candidates_preview_sheet); [ -n "$p" ]; check $? "카드 $card 누름 뒤 미리보기 열림 $(fmt "$p")"
    else check 1 "누를 카드가 보인다"; fi
  else check 1 "손잡이 노드가 있다"; fi
  want 2 && finish "끌기 뒤 카드 $card 누름 → 미리보기 $(fmt "${p:-}")" || failed_checks=()
  flow2_ok=$([ -n "${p:-}" ] && echo 1 || echo 0)

  if want 4; then
    begin 4 "완전 확장에서 미리보기 열기 → 닫기 → 시트 완전 확장·선택 카드 보임(#145 #135)"
    if [ "$flow2_ok" = 1 ]; then
      sh_ input keyevent KEYCODE_BACK; wait_id candidate_list 8
      ! has_id candidates_preview_sheet; check $? "미리보기가 닫혔다"
      full_expanded; check $? "시트가 다시 완전 확장(손잡이 $(fmt "$(by_id candidates_sheet_handle)"))"
      c=$(by_id candidate_$card); check "$([ -n "$c" ] && set -- $c && [ $(( $4 - $2 )) -ge "$(px 48)" ] && echo 0 || echo 1)" "고른 카드 $card 이 보인다 $(fmt "$c")"
      has_id route_timeline_$card; check $? "고른 카드가 선택 상태로 펼쳐져 있다(route_timeline_$card)"
      finish "닫은 뒤 손잡이 $(fmt "$(by_id candidates_sheet_handle)"), 카드 $(fmt "$c")"
    else failed_checks=("흐름 2에서 미리보기를 열지 못했다"); finish ""; fi
  fi

  if want 5; then
    begin 5 "목록·미리보기에서 회전 → 카드 1장 이상 보임(#144)"
    dump; has_id candidates_preview_sheet && { sh_ input keyevent KEYCODE_BACK; sleep 2; }
    set_setting user_rotation 1; sleep 3; wait_id candidates_header 10
    n=$(visible_cards); check "$([ "$n" -ge 1 ] && echo 0 || echo 1)" "가로 목록에서 카드 ${n}장 보임"
    alive; check $? "회전 뒤 앱이 살아 있다"
    t=$(by_id candidate_time_$(pick_card))
    if [ -n "$t" ]; then
      tap "$t"; wait_id candidates_preview_sheet 8; p=$(by_id candidates_preview_sheet); [ -n "$p" ]; check $? "가로에서 미리보기 열림 $(fmt "$p")"
      set_setting user_rotation 0; sleep 3; wait_id candidates_header 10
      p=$(by_id candidates_preview_sheet); [ -n "$p" ]; check $? "미리보기에서 세로로 돌린 뒤 미리보기 유지 $(fmt "$p")"
      sh_ input keyevent KEYCODE_BACK; sleep 2; dump
      n2=$(visible_cards); check "$([ "$n2" -ge 1 ] && echo 0 || echo 1)" "세로 목록으로 돌아와 카드 ${n2}장 보임"
    else check 1 "가로 목록에서 누를 카드가 보인다"; set_setting user_rotation 0; sleep 2; fi
    finish "가로 목록 카드 ${n}장, 미리보기 회전 뒤 $(fmt "${p:-}"), 세로 복귀 카드 ${n2:-0}장"
  fi
fi

# ---------- 흐름 6~7: 햇빛 따라가기 ----------
if want 6 || want 7; then
  if [ $emulator = 1 ] && [ "${SMOKE_FORCE_3D:-0}" != 1 ]; then
    reason="에뮬레이터: 3D(Filament) 화면이 arm64 변환에서 종료되어 실기기에서만 본다(SMOKE_FORCE_3D=1로 강제)"
    want 6 && skip 6 "$reason"; want 7 && skip 7 "$reason"
  else
    sim_ok=0
    current=6
    if to_list; then
      card=$(pick_card); tap "$(by_id candidate_time_$card)"
      if wait_id preview_seat 8; then
        tap "$(by_id preview_seat)"
        if wait_text '^햇빛 따라가기$' 15; then
          set_setting font_scale 1.5; sleep 3
          wait_text '^햇빛 따라가기$' 10 && tap "$(by_text '^햇빛 따라가기$')" && wait_id simulation_info_card 20 && sim_ok=1
        fi
      fi
    fi
    if want 6; then
      begin 6 "글자 1.5배 햇빛 따라가기: 정보 카드·조작 패널과 본문 글자 비겹침(#113 #155 #161)"
      if [ $sim_ok = 1 ]; then
        sleep 2; dump
        mp=$(by_id simulation_map); ic=$(by_id simulation_info_card); de=$(by_id simulation_details); co=$(by_id simulation_controls)
        note "지도 $(fmt "$mp") 정보 카드 $(fmt "$ic") 본문 $(fmt "$de") 조작 패널 $(fmt "$co")"
        if [ -n "$mp" ] && [ -n "$ic" ] && [ -n "$de" ] && [ -n "$co" ]; then
        set -- $mp; my2=$4; set -- $ic; iy1=$2; iy2=$4; set -- $de; dy1=$2; dy2=$4; set -- $co; cy1=$2; cy2=$4
        check "$(lt $iy2 $my2)" "정보 카드가 지도 안에 들어간다(카드 아래끝 $iy2 ≤ 지도 아래끝 $my2)"
        check "$(lt "$(px 48)" $(( my2 - iy2 )))" "정보 카드 아래로 지도가 48dp 이상 보인다($(dp $(( my2 - iy2 )))dp)"
        check "$(lt $dy2 $cy1)" "본문 아래끝($dy2) ≤ 조작 패널 위끝($cy1)"
        # 카드·패널 안에서 시작하는 글자가 그 부품 밖으로 넘치지 않고, 본문 글자가 패널 아래로 들어가지 않는다
        over=$(awk -F'\t' -v iy1=$iy1 -v iy2=$iy2 -v cy1=$cy1 -v cy2=$cy2 -v dy1=$dy1 -v dy2=$dy2 '
          $2 == "" { next }
          $5 >= iy1 && $5 < iy2 && $7 > iy2 { printf "정보카드[%d,%d] ", $5, $7 }
          $5 >= cy1 && $5 < cy2 && $7 > cy2 { printf "조작패널[%d,%d] ", $5, $7 }
          $5 >= dy1 && $5 < dy2 && $7 > cy1 && $7 <= cy2 + 1 && $5 < cy1 { printf "본문→패널[%d,%d] ", $5, $7 }' "$out/screen.tsv")
        check "$([ -z "$over" ] && echo 0 || echo 1)" "글자가 카드·패널 경계를 넘지 않는다${over:+ ($over)}"
        else check 1 "지도·정보 카드·본문·조작 패널 노드가 모두 있다"; fi
        finish "정보 카드 $(fmt "$ic"), 본문 $(fmt "$de"), 조작 패널 $(fmt "$co")"
      else failed_checks=("햇빛 따라가기 화면에 들어가지 못했다"); finish ""; fi
    fi
    set_setting font_scale "$orig_font"; sleep 2
    if want 7; then
      begin 7 "따라가기 끄기 → 따라가기 줄이 지도와 겹치지 않음(#122)"
      if [ $sim_ok = 1 ] && wait_id simulation_map 10; then
        mp=$(by_id simulation_map); read -r cx cy <<<"$(center "$mp")"
        drag "$cx" "$cy" $(( cx - $(px 80) )) $(( cy + $(px 40) )) 800
        if wait_id simulation_follow_row 8; then
          mp=$(by_id simulation_map); fr=$(by_id simulation_follow_row); de=$(by_id simulation_details)
          note "지도 $(fmt "$mp") 따라가기 줄 $(fmt "$fr") 본문 $(fmt "$de")"
          set -- ${mp:-0 0 0 0}; my2=$4; set -- $fr; fy1=$2; fy2=$4; set -- ${de:-0 99999 0 99999}; dy1=$2
          check "$(lt $my2 $fy1)" "지도 아래끝($my2) ≤ 따라가기 줄 위끝($fy1)"
          check "$(lt $fy2 $dy1)" "따라가기 줄 아래끝($fy2) ≤ 본문 위끝($dy1)"
          b=$(by_text '^따라가기$'); check "$([ -n "$b" ] && set -- $b && [ $2 -ge $fy1 ] && [ $4 -le $fy2 ] && echo 0 || echo 1)" "따라가기 버튼이 줄 안에 있다 $(fmt "$b")"
          [ -n "$b" ] && { tap "$b"; sleep 2; dump; ! has_id simulation_follow_row; check $? "따라가기를 누르면 줄이 사라진다"; }
        else check 1 "지도를 끈 뒤 따라가기 줄이 나타난다"; fi
        finish "지도 $(fmt "$mp"), 따라가기 줄 $(fmt "${fr:-}")"
      else failed_checks=("햇빛 따라가기 화면에 들어가지 못했다"); finish ""; fi
    fi
  fi
fi

# ---------- 흐름 8: 지도에서 위치 선택 ----------
if want 8; then
  begin 8 "지도에서 위치 선택 열기·선택(#85)"
  set_setting font_scale "$orig_font"; set_setting user_rotation 0
  if launch && pick_place 도착 "$DEST_QUERY" "$DEST_PICK" && wait_id journey_header 10; then
    dump; before=$(field_value 도착)
    b=$(awk -F'\t' '$2 == "도착" { print $4, $5, $6, $7; exit }' "$out/screen.tsv"); tap "$b"
    if wait_text '^지도에서 선택$' 8; then
      tap "$(by_text '^지도에서 선택$')"
      if wait_text '^도착지로 선택$' 15; then
        check 0 "지도 선택 화면이 열렸다"
        dump; mp=$(awk -F'\t' '$3 ~ / 지도\. 가운데 핀 위치: / { print $4, $5, $6, $7; exit }' "$out/screen.tsv")
        [ -n "$mp" ] || mp="0 $(px 200) $(sh_ wm size | awk -F'[ x]' '/size/ {w=$(NF-1)} END {print w}') $(px 500)"
        read -r cx cy <<<"$(center "$mp")"
        drag "$cx" "$cy" $(( cx + $(px 40) )) $(( cy + $(px 40) )) 800
        end=$(( SECONDS + 15 )); ok=1
        while [ $SECONDS -lt $end ]; do sleep 1; dump; enabled_text '^도착지로 선택$' && { ok=0; break; }; done
        check $ok "핀을 옮긴 뒤 선택 버튼이 켜진다"
        b=$(by_text '^도착지로 선택$'); [ -n "$b" ] && tap "$b"
        wait_id input_map 10; ! by_text '^도착지로 선택$' >/dev/null; check $? "선택하면 입력 화면으로 돌아온다"
        after=$(field_value 도착)
        check "$([ -n "$after" ] && [ "$after" != "장소 검색" ] && [ "$after" != "$before" ] && echo 0 || echo 1)" "도착지가 지도에서 고른 곳으로 바뀌었다(이름은 출력하지 않음)"
      else check 1 "지도 선택 화면이 열렸다"; fi
    else check 1 "검색 화면에 '지도에서 선택'이 보인다"; fi
  else check 1 "입력 화면에서 도착지(강남역)를 넣었다"; fi
  finish "지도 선택 열기 → 핀 이동 → 도착지로 선택"
fi

# ---------- 요약 ----------
log ""
log "결과 폴더: $out (로컬 전용. 덤프·캡처는 커밋하지 않는다. PR에는 아래 요약과 [PASS]/[FAIL] 줄만 붙인다)"
log ""
log "== 요약"
fails=0
for f in 1 2 3 4 5 6 7 8; do
  want $f || continue
  r=${result[$f]:-FAIL}; [ "$r" = FAIL ] && fails=$((fails + 1))
  log "흐름 $f: $r"
done
sh_ am force-stop "$PKG"
[ $fails = 0 ]
