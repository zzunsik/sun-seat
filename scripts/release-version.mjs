#!/usr/bin/env node
// 릴리스 버전 규칙(AGENTS.md "릴리스" > "버전 번호", #118)을 태그·빌드 파일·APK에 적용한다.
//
//   versionName = 태그에서 v를 뺀 값. MAJOR.MINOR.PATCH 또는 MAJOR.MINOR.PATCH-rc.N(시험판)
//   versionCode = MAJOR * 10000 + MINOR * 100 + PATCH. MINOR와 PATCH는 0~99.
//   시험판(-rc.N)은 -rc.N을 뗀 버전과 같은 versionCode를 쓴다.
//
// versionName·versionCode 값은 app/build.gradle.kts에 적혀 있다. CI는 PR마다 그 값이 규칙에 맞는지(gradle),
// 릴리스 워크플로는 태그와 빌드 파일(gradle --tag)·만든 APK(apk --tag)가 같은지 본다.
//
// 사용:
//   node scripts/release-version.mjs tag <태그>
//     태그를 검사하고 version-name=, version-code=, prerelease=를 한 줄씩 출력한다($GITHUB_OUTPUT에 붙여 쓴다).
//   node scripts/release-version.mjs gradle <app/build.gradle.kts> [--tag <태그>]
//     빌드 파일의 versionName이 규칙에 맞고 versionCode가 계산값과 같은지 본다. --tag가 있으면 태그와도 견준다.
//   node scripts/release-version.mjs apk <aapt2 dump badging 출력 파일> [--tag <태그>]
//     APK의 versionName·versionCode를 같은 방법으로 본다.
// 종료 코드: 0 통과, 1 규칙 위반, 2 사용법 오류.
//
// Node 내장 모듈만 쓴다. 자체 테스트는 scripts/release-version.test.mjs다(CI release-version job).

import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const NUM = '(0|[1-9][0-9]*)';
const VERSION_RE = new RegExp(`^${NUM}\\.${NUM}\\.${NUM}(?:-rc\\.([1-9][0-9]*))?$`);
// Android가 받는 versionCode의 최댓값.
const MAX_VERSION_CODE = 2_100_000_000;

export class VersionError extends Error {}

// versionName을 읽는다. 규칙에 맞지 않으면 VersionError를 던진다.
export function parseVersionName(name) {
  const m = VERSION_RE.exec(name);
  if (!m) {
    throw new VersionError(`버전은 MAJOR.MINOR.PATCH 또는 MAJOR.MINOR.PATCH-rc.N 꼴이어야 합니다(예: 0.1.0, 0.2.0-rc.1): ${name}`);
  }
  const [major, minor, patch] = [m[1], m[2], m[3]].map(Number);
  if (minor > 99 || patch > 99) {
    throw new VersionError(`MINOR와 PATCH는 0~99만 씁니다: ${name}`);
  }
  const versionCode = major * 10000 + minor * 100 + patch;
  if (!Number.isSafeInteger(versionCode) || versionCode > MAX_VERSION_CODE) {
    throw new VersionError(`versionCode가 Android 최댓값(${MAX_VERSION_CODE})을 넘습니다: ${name}`);
  }
  const rc = m[4] === undefined ? null : Number(m[4]);
  return { major, minor, patch, rc, prerelease: rc !== null, versionCode };
}

// 태그(v + versionName)를 읽는다.
export function parseTag(tag) {
  if (typeof tag !== 'string' || !tag.startsWith('v')) {
    throw new VersionError(`태그는 v로 시작해야 합니다(예: v0.1.0, v0.2.0-rc.1): ${tag}`);
  }
  const versionName = tag.slice(1);
  return { versionName, ...parseVersionName(versionName) };
}

// app/build.gradle.kts에서 versionName = "…"과 versionCode = 숫자를 꺼낸다. 각각 정확히 한 번 있어야 한다.
// 계산식이나 변수로 바꾸면 여기서 실패하므로, 바꿀 때는 이 함수와 테스트도 함께 고친다.
export function readGradle(text) {
  const names = [...text.matchAll(/^\s*versionName\s*=\s*"([^"]*)"\s*$/gm)];
  const codes = [...text.matchAll(/^\s*versionCode\s*=\s*([0-9]+)\s*$/gm)];
  if (names.length !== 1 || codes.length !== 1) {
    throw new VersionError(
      `app/build.gradle.kts에 versionName = "…"과 versionCode = 숫자가 한 번씩 있어야 합니다(versionName ${names.length}개, versionCode ${codes.length}개).`,
    );
  }
  return { versionName: names[0][1], versionCode: Number(codes[0][1]) };
}

// aapt2 dump badging 출력의 package: 줄에서 versionName과 versionCode를 꺼낸다.
export function readBadging(text) {
  const line = text.split(/\r?\n/).find((l) => l.startsWith('package:'));
  if (!line) throw new VersionError('badging 출력에 package: 줄이 없습니다.');
  const name = /\bversionName='([^']*)'/.exec(line);
  const code = /\bversionCode='([^']*)'/.exec(line);
  if (!name || !code) throw new VersionError(`package: 줄에 versionName이나 versionCode가 없습니다: ${line}`);
  return { versionName: name[1], versionCode: Number(code[1]) };
}

// 읽은 버전(source: 'app/build.gradle.kts' 또는 'APK')이 규칙(그리고 tag가 있으면 태그)과 맞는지 본다.
// 틀린 것을 문장 목록으로 돌려준다.
export function checkVersion(actual, source, tag) {
  const errors = [];
  let expected;
  try {
    expected = tag === undefined ? parseVersionName(actual.versionName) : parseTag(tag);
  } catch (e) {
    if (e instanceof VersionError) return [e.message];
    throw e;
  }
  if (tag !== undefined && actual.versionName !== expected.versionName) {
    errors.push(`${source}의 versionName(${actual.versionName})이 태그(${tag})와 다릅니다. 버전 올림 PR로 app/build.gradle.kts를 고친 뒤 릴리스하세요.`);
  }
  if (actual.versionCode !== expected.versionCode) {
    errors.push(`${source}의 versionCode(${actual.versionCode})가 규칙으로 계산한 값(${expected.versionCode})과 다릅니다. versionCode = 주 × 10000 + 중간 × 100 + 끝입니다.`);
  }
  return errors;
}

export const checkGradle = (text, tag) => checkVersion(readGradle(text), 'app/build.gradle.kts', tag);
export const checkApk = (badging, tag) => checkVersion(readBadging(badging), 'APK', tag);

function main(argv) {
  const [command, arg, ...rest] = argv;
  const tagOption = rest.length === 0 || (rest.length === 2 && rest[0] === '--tag');
  try {
    if (command === 'tag' && arg && rest.length === 0) {
      const v = parseTag(arg);
      console.log(`version-name=${v.versionName}`);
      console.log(`version-code=${v.versionCode}`);
      console.log(`prerelease=${v.prerelease}`);
      return 0;
    }
    if ((command === 'gradle' || command === 'apk') && arg && tagOption) {
      const text = readFileSync(arg, 'utf8');
      const read = command === 'gradle' ? readGradle : readBadging;
      const check = command === 'gradle' ? checkGradle : checkApk;
      const errors = check(text, rest[1]);
      if (errors.length) {
        for (const e of errors) console.log(`::error::${e}`);
        return 1;
      }
      const v = read(text);
      console.log(`${command === 'gradle' ? 'app/build.gradle.kts' : 'APK'} 버전 확인: versionName=${v.versionName} versionCode=${v.versionCode}`);
      return 0;
    }
  } catch (e) {
    if (e instanceof VersionError) {
      console.log(`::error::${e.message}`);
      return 1;
    }
    throw e;
  }
  console.error('사용: node scripts/release-version.mjs tag <태그> | gradle <app/build.gradle.kts> [--tag <태그>] | apk <badging 파일> [--tag <태그>]');
  return 2;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  process.exitCode = main(process.argv.slice(2));
}
