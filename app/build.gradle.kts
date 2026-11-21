import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// 빌드 설정값은 local.properties에서 먼저 읽고, 없으면 같은 이름의 환경 변수에서 읽는다(릴리스 워크플로용, #118). 빈 값은 없는 것으로 본다.
fun buildSetting(name: String): String? =
    localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

// 앱 API 게이트웨이(workers/api) 주소(#189, #191). 디버그 빌드는 개발 게이트웨이(api-dev), 릴리스 빌드는 운영 게이트웨이(api)를 부른다.
// SUNSEAT_API_BASE_URL_DEBUG·SUNSEAT_API_BASE_URL_RELEASE를 읽고, 없으면 예전 이름 SUNSEAT_API_BASE_URL을 쓴다. 모두 없으면 빈 문자열(CI 포함).
fun apiBaseUrl(buildType: String): String {
    val url = buildSetting("SUNSEAT_API_BASE_URL_$buildType") ?: buildSetting("SUNSEAT_API_BASE_URL") ?: ""
    require('"' !in url && '\\' !in url) { "SUNSEAT_API_BASE_URL_$buildType must not contain quotes or backslashes." }
    return url
}

// 릴리스 서명. 로컬은 local.properties, 릴리스 워크플로(.github/workflows/release.yml)는 같은 이름의 환경 변수로 넘긴다(#118).
// 네 값이 모두 있으면 서명하고, 하나도 없으면 비서명 APK를 만든다(CI의 PR 빌드). 일부만 있으면 구성 단계에서 실패한다.
val releaseSigningKeys = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
val releaseSigning = releaseSigningKeys.associateWith { buildSetting(it) }
val releaseSigningConfigured = releaseSigning.values.all { it != null }
require(releaseSigningConfigured || releaseSigning.values.all { it == null }) {
    "Release signing requires all four RELEASE_* values (local.properties or environment variables)."
}

android {
    namespace = "com.sunseat.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sunseat.app"
        minSdk = 26
        targetSdk = 37
        // 릴리스 버전 규칙(AGENTS.md "릴리스"): versionName은 태그에서 v를 뺀 값, versionCode = 주 × 10000 + 중간 × 100 + 끝.
        // 두 값은 버전 올림 PR에서 함께 바꾼다. CI와 릴리스 워크플로가 scripts/release-version.mjs로 규칙·태그와 견준다.
        versionCode = 100
        versionName = "0.1.0"
        // 키·주소는 local.properties에서 먼저, 없으면 같은 이름의 환경 변수에서 읽는다(위 buildSetting, 릴리스 워크플로용).
        // 없으면 빈 문자열로 빌드된다(CI 포함). 지도 SDK의 네이티브 앱 키는 APK에 들어간다(패키지명·서명 키 해시로 제한).
        val mapKey = buildSetting("KAKAO_NATIVE_APP_KEY") ?: ""
        require('"' !in mapKey && '\\' !in mapKey) { "KAKAO_NATIVE_APP_KEY must not contain quotes or backslashes." }
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$mapKey\"")
        // 카카오 REST 호출은 앱 API 게이트웨이(workers/api)로 간다(#189). REST 키는 게이트웨이의 시크릿에만 두고 APK에 넣지 않는다.
        // 게이트웨이 주소 BuildConfig.SUNSEAT_API_BASE_URL은 빌드 종류마다 다르다(아래 buildTypes, #191).
        if (!localProps.getProperty("KAKAO_REST_API_KEY").isNullOrBlank()) {
            logger.warn("local.properties의 KAKAO_REST_API_KEY는 더 이상 쓰지 않습니다(#189). 지우고 SUNSEAT_API_BASE_URL_DEBUG·SUNSEAT_API_BASE_URL_RELEASE를 설정하세요.")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("personalRelease") {
                storeFile = rootProject.file(releaseSigning.getValue("RELEASE_STORE_FILE")!!)
                storePassword = releaseSigning.getValue("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning.getValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUNSEAT_API_BASE_URL", "\"${apiBaseUrl("DEBUG")}\"")
        }
        release {
            buildConfigField("String", "SUNSEAT_API_BASE_URL", "\"${apiBaseUrl("RELEASE")}\"")
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("personalRelease")
            // R8(코드 축소)은 v0.1.0에서 끈다. 켜려면 카카오맵 SDK·Filament·kotlinx.serialization 보존 규칙을 확인하고
            // 릴리스 APK로 주요 흐름을 실기기에서 다시 본다(AGENTS.md "릴리스" > "코드 축소", #118).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                // 기준 이미지만 바뀌어도 빌드 캐시가 이전 테스트 결과를 재사용하지 않도록 입력으로 둔다.
                it.inputs.dir("src/test/screenshots")
                    .withPropertyName("roborazziGoldens")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.filament)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kakao.map)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.lottie.compose)
    implementation(libs.compose.shimmer)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.commons.suncalc)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.androidx.datastore.core.okio)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    lintChecks(libs.compose.lint.checks)
}
