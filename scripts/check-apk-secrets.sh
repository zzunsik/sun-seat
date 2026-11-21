#!/usr/bin/env bash
# 빌드에 카카오 REST 키를 앱에 넣는 코드 경로가 없는지 확인한다(#189).
# REST 키는 앱 API 게이트웨이(workers/api)의 시크릿에만 둔다. CI는 키 없이 빌드하므로 키 값 대신
# 키를 담거나 쓰는 경로(BuildConfig 필드, 인증 헤더 문자열, 카카오 REST 직접 호출 주소)가 사라졌는지 본다.
# CI는 debug와 (서명 없는) release를, 릴리스 워크플로는 서명한 release APK를 본다(#118).
#
# 사용법: scripts/check-apk-secrets.sh [debug|release]   (기본 debug. ./gradlew assembleDebug/assembleRelease 뒤, 저장소 최상위에서)
# 종료 코드: 0 통과, 1 발견, 2 빌드 산출물 없음·사용법 오류
set -euo pipefail

variant=${1:-debug}
case "$variant" in
  debug) apk=app/build/outputs/apk/debug/app-debug.apk ;;
  # 서명했으면 app-release.apk, 서명 정보가 없으면(CI의 PR 빌드) app-release-unsigned.apk가 생긴다.
  release)
    apk=app/build/outputs/apk/release/app-release.apk
    [ -f "$apk" ] || apk=app/build/outputs/apk/release/app-release-unsigned.apk
    ;;
  *) echo "사용법: $0 [debug|release]"; exit 2 ;;
esac
Variant=$(printf '%s' "${variant:0:1}" | tr '[:lower:]' '[:upper:]')${variant:1}
build_config=app/build/generated/source/buildConfig/$variant/com/sunseat/app/BuildConfig.java

[ -f "$apk" ] || { echo "APK가 없습니다: $apk"; exit 2; }
[ -f "$build_config" ] || { echo "BuildConfig가 없습니다: $build_config"; exit 2; }

found=0
report() { echo "발견: $*"; found=1; }

# 1. BuildConfig: 앱 API 주소 필드만 있고 REST 키 필드는 없다.
grep -q 'SUNSEAT_API_BASE_URL' "$build_config" || report "BuildConfig에 SUNSEAT_API_BASE_URL 필드가 없음(검사 대상이 바뀌었는지 확인)"
if grep -n 'REST_API_KEY' "$build_config"; then report "BuildConfig에 REST 키 필드"; fi

# 2. 앱 코드(com.sunseat): 인증 헤더·키 이름·카카오 REST 호스트를 직접 쓰지 않는다.
#    카카오 지도 SDK는 자체 요청용으로 같은 문자열을 갖고 있어 APK 전체가 아니라 앱 클래스만 본다.
mapfile -t classes < <(find app/build/intermediates -path "*compile${Variant}Kotlin/classes/com/sunseat/*" -name '*.class' 2>/dev/null)
[ ${#classes[@]} -gt 0 ] || { echo "앱 클래스 파일을 찾지 못했습니다(빌드 경로가 바뀌었는지 확인)"; exit 2; }
for pattern in 'KakaoAK' 'KAKAO_REST_API_KEY' 'dapi.kakao.com' 'kakaomobility.com'; do
  hits=$(grep -a -l -F "$pattern" "${classes[@]}" || true)
  [ -z "$hits" ] || report "앱 클래스에 '$pattern': $hits"
done

# 3. APK 전체: 키 이름과 카카오모빌리티 길찾기 주소(SDK에도 없다)가 없다.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
# 릴리스 APK는 리소스 경로가 짧게 바뀌어 대소문자만 다른 이름이 생긴다(Windows에서 겹침). 묻지 않고 덮어쓴다.
unzip -q -o "$apk" -d "$tmp" < /dev/null
for pattern in 'KAKAO_REST_API_KEY' 'apis-navi.kakaomobility.com'; do
  hits=$(grep -a -r -l -F "$pattern" "$tmp" | sed "s#^$tmp/##" || true)
  [ -z "$hits" ] || report "APK에 '$pattern': $hits"
done

if [ "$found" -ne 0 ]; then
  echo "REST 키 경로가 앱에 남아 있습니다. 카카오 REST 호출은 앱 API 게이트웨이(workers/api)로만 보냅니다."
  exit 1
fi
echo "APK 검사 통과($variant, $(basename "$apk")): REST 키 필드·인증 헤더·카카오 REST 직접 호출 없음 (앱 클래스 ${#classes[@]}개)"
