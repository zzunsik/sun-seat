// scripts/release-version.mjs 자체 테스트. node --test scripts/release-version.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkApk, checkGradle, parseTag, parseVersionName, readBadging, readGradle, VersionError } from './release-version.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const script = join(here, 'release-version.mjs');
const appGradle = join(here, '..', 'app', 'build.gradle.kts');

test('versionCode는 MAJOR*10000 + MINOR*100 + PATCH다', () => {
  assert.equal(parseTag('v0.1.0').versionCode, 100);
  assert.equal(parseTag('v0.1.1').versionCode, 101);
  assert.equal(parseTag('v0.2.1').versionCode, 201);
  assert.equal(parseTag('v0.10.0').versionCode, 1000);
  assert.equal(parseTag('v0.99.99').versionCode, 9999);
  assert.equal(parseTag('v1.0.0').versionCode, 10000);
  assert.equal(parseTag('v12.3.4').versionCode, 120304);
});

test('각 자리는 독립된 정수라 0.10.0이 0.9.0보다 크다', () => {
  assert.ok(parseTag('v0.10.0').versionCode > parseTag('v0.9.0').versionCode);
  assert.ok(parseTag('v0.2.0').versionCode > parseTag('v0.1.99').versionCode);
  assert.ok(parseTag('v1.0.0').versionCode > parseTag('v0.99.99').versionCode);
});

test('versionName은 태그에서 v를 뺀 값이다', () => {
  assert.equal(parseTag('v0.1.0').versionName, '0.1.0');
  assert.equal(parseTag('v0.2.0-rc.1').versionName, '0.2.0-rc.1');
});

test('시험판 -rc.N은 Pre-release이고 정식판과 versionCode가 같다', () => {
  const rc = parseTag('v0.2.0-rc.1');
  assert.equal(rc.prerelease, true);
  assert.equal(rc.rc, 1);
  assert.equal(rc.versionCode, 200);
  assert.equal(parseTag('v0.2.0-rc.12').versionCode, 200);
  assert.equal(parseTag('v0.2.0').prerelease, false);
  assert.equal(parseTag('v0.2.0').rc, null);
});

test('MINOR나 PATCH가 99를 넘으면 거부한다', () => {
  assert.throws(() => parseTag('v0.100.0'), VersionError);
  assert.throws(() => parseTag('v0.1.100'), VersionError);
  assert.throws(() => parseVersionName('1.100.0-rc.1'), VersionError);
});

test('규칙에 맞지 않는 태그를 거부한다', () => {
  for (const tag of [
    '0.1.0', 'v0.1', 'v0.1.0.0', 'v01.1.0', 'v0.01.0', 'v0.1.00', 'v0.1.0-rc', 'v0.1.0-rc.0', 'v0.1.0-rc.01',
    'v0.1.0-beta.1', 'v0.1.0-RC.1', 'v0.1.0-rc.1+build', 'v 0.1.0', 'v0.1.0\n', 'vtest', 'V0.1.0', 'v', '',
  ]) {
    assert.throws(() => parseTag(tag), VersionError, `거부해야 함: ${JSON.stringify(tag)}`);
  }
});

test('Android 최댓값을 넘는 versionCode는 거부한다', () => {
  assert.equal(parseTag('v210000.0.0').versionCode, 2_100_000_000);
  assert.throws(() => parseTag('v210000.0.1'), VersionError);
});

const gradle = (name, code) => `android {
    defaultConfig {
        applicationId = "com.sunseat.app"
        minSdk = 26
        versionCode = ${code}
        versionName = "${name}"
    }
}
`;

test('빌드 파일에서 versionName과 versionCode를 읽는다', () => {
  assert.deepEqual(readGradle(gradle('0.1.0', 100)), { versionName: '0.1.0', versionCode: 100 });
  assert.deepEqual(readGradle(gradle('0.1.0', 100).replaceAll('\n', '\r\n')), { versionName: '0.1.0', versionCode: 100 });
});

test('빌드 파일에 버전 줄이 없거나 두 번 있거나 계산식이면 거부한다', () => {
  assert.throws(() => readGradle('android {}\n'), VersionError);
  assert.throws(() => readGradle(gradle('0.1.0', 100) + '        versionCode = 101\n'), VersionError);
  assert.throws(() => readGradle(gradle('0.1.0', '1 * 100')), VersionError);
});

test('빌드 파일 버전이 규칙과 태그에 맞으면 통과한다', () => {
  assert.deepEqual(checkGradle(gradle('0.1.0', 100)), []);
  assert.deepEqual(checkGradle(gradle('0.1.0', 100), 'v0.1.0'), []);
  assert.deepEqual(checkGradle(gradle('0.2.0-rc.1', 200), 'v0.2.0-rc.1'), []);
});

test('빌드 파일 versionCode가 규칙과 다르면 실패한다(0.1.0의 예전 값 1)', () => {
  const errors = checkGradle(gradle('0.1.0', 1));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /app\/build\.gradle\.kts의 versionCode\(1\)가 규칙으로 계산한 값\(100\)과 다릅니다/);
});

test('빌드 파일 versionName이 태그와 다르면 실패한다(버전 올림 PR을 빠뜨림)', () => {
  const errors = checkGradle(gradle('0.1.0', 100), 'v0.1.1');
  assert.equal(errors.length, 2);
  assert.match(errors[0], /versionName\(0\.1\.0\)이 태그\(v0\.1\.1\)와 다릅니다/);
  assert.equal(checkGradle(gradle('0.2.0-rc.1', 200), 'v0.2.0').length, 1);
});

test('저장소의 app/build.gradle.kts 버전이 규칙에 맞는다', () => {
  assert.deepEqual(checkGradle(readFileSync(appGradle, 'utf8')), []);
});

const badging = (name, code) =>
  `package: name='com.sunseat.app' versionCode='${code}' versionName='${name}' platformBuildVersionName='17'\n` +
  "uses-permission: name='android.permission.INTERNET'\n";

test('badging에서 versionName과 versionCode를 읽는다', () => {
  assert.deepEqual(readBadging(badging('0.1.0', 100)), { versionName: '0.1.0', versionCode: 100 });
  assert.throws(() => readBadging('application: label=x'), VersionError);
});

test('APK 버전이 태그와 규칙에 맞으면 통과한다', () => {
  assert.deepEqual(checkApk(badging('0.1.0', 100), 'v0.1.0'), []);
  assert.deepEqual(checkApk(badging('0.2.0-rc.1', 200), 'v0.2.0-rc.1'), []);
  assert.deepEqual(checkApk(badging('0.1.0', 100)), []);
});

test('APK versionName이나 versionCode가 태그·규칙과 다르면 실패한다', () => {
  assert.equal(checkApk(badging('0.1.0', 100), 'v0.1.1').length, 2);
  assert.equal(checkApk(badging('0.1.0', 1000), 'v0.1.0').length, 1);
  assert.equal(checkApk(badging('0.1.0', 100), 'v0.1.0-beta.1').length, 1);
  assert.equal(checkApk(badging('0.1', 100)).length, 1);
});

test('명령줄: 종료 코드와 GITHUB_OUTPUT 줄', () => {
  const run = (...args) => spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });
  const ok = run('tag', 'v0.2.0-rc.1');
  assert.equal(ok.status, 0);
  assert.equal(ok.stdout, 'version-name=0.2.0-rc.1\nversion-code=200\nprerelease=true\n');
  const bad = run('tag', 'v0.1');
  assert.equal(bad.status, 1);
  assert.match(bad.stdout, /^::error::/);
  assert.equal(run('tag').status, 2);
  assert.equal(run('gradle', appGradle, '--tag').status, 2);

  const dir = mkdtempSync(join(tmpdir(), 'release-version-'));
  try {
    const file = join(dir, 'build.gradle.kts');
    writeFileSync(file, gradle('0.1.0', 100));
    assert.equal(run('gradle', file).status, 0);
    assert.equal(run('gradle', file, '--tag', 'v0.1.0').status, 0);
    assert.equal(run('gradle', file, '--tag', 'v0.1.1').status, 1);
    writeFileSync(file, 'android {}\n');
    assert.equal(run('gradle', file).status, 1);
    const apk = join(dir, 'badging.txt');
    writeFileSync(apk, badging('0.1.0', 100));
    assert.equal(run('apk', apk, '--tag', 'v0.1.0').status, 0);
    assert.equal(run('apk', apk, '--tag', 'v0.2.0').status, 1);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
