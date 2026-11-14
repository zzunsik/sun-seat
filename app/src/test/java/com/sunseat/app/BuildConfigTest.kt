package com.sunseat.app

import org.junit.Assert.assertNotNull
import org.junit.Test

class BuildConfigTest {
    @Test
    fun kakaoKeyFieldExists() {
        // 키가 없으면 빈 문자열로 빌드된다. 필드 자체는 항상 있어야 한다.
        assertNotNull(BuildConfig.KAKAO_REST_API_KEY)
    }
}
