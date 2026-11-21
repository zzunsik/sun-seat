package com.sunseat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildConfigTest {
    @Test
    fun kakaoKeyFieldExists() {
        // 주소·키가 없으면 빈 문자열로 빌드된다. 필드 자체는 항상 있어야 한다.
        assertNotNull(BuildConfig.SUNSEAT_API_BASE_URL)
        assertNotNull(BuildConfig.KAKAO_NATIVE_APP_KEY)
    }

    @Test
    fun restApiKeyIsNotInBuildConfig() {
        // REST 키는 앱 API 게이트웨이의 시크릿에만 둔다(#189). APK에 들어가는 BuildConfig에 키 필드가 없어야 한다.
        val names = BuildConfig::class.java.declaredFields.map { it.name }
        assertTrue("SUNSEAT_API_BASE_URL" in names)
        assertEquals(emptyList<String>(), names.filter { it.contains("REST_API_KEY") })
    }
}
