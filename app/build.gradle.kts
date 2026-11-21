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

val releaseSigningKeys = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = releaseSigningKeys.all { !localProps.getProperty(it).isNullOrBlank() }
require(releaseSigningConfigured || releaseSigningKeys.none { !localProps.getProperty(it).isNullOrBlank() }) {
    "Release signing requires all four RELEASE_* properties in local.properties."
}

android {
    namespace = "com.sunseat.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sunseat.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // 키·주소는 local.properties(게이트웨이 주소는 환경 변수도, 위 buildSetting)에서 읽는다. 없으면 빈 문자열로 빌드된다(CI 포함).
        // 지도 SDK의 네이티브 앱 키는 APK에 들어간다(패키지명·서명 키 해시로 제한).
        val mapKey = localProps.getProperty("KAKAO_NATIVE_APP_KEY", "")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$mapKey\"")
        // 카카오 REST 호출은 앱 API 게이트웨이(workers/api)로 간다(#189). REST 키는 게이트웨이의 시크릿에만 두고 APK에 넣지 않는다.
        // 게이트웨이 주소 BuildConfig.SUNSEAT_API_BASE_URL은 빌드 종류마다 다르다(아래 buildTypes, #191).
        if (!localProps.getProperty("KAKAO_REST_API_KEY").isNullOrBlank()) {
            logger.warn("local.properties의 KAKAO_REST_API_KEY는 더 이상 쓰지 않습니다(#189). 지우고 SUNSEAT_API_BASE_URL을 설정하세요.")
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("personalRelease") {
                storeFile = rootProject.file(localProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
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
