package com.sunseat.app.data.network

/** 외부 API 호출 결과. 실패는 화면에서 안내 문구를 고를 수 있게 [ApiError]로 구분한다. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>

    /** @param httpCode 서버가 응답한 경우의 HTTP 상태 코드(진단용) */
    data class Failure(val error: ApiError, val httpCode: Int? = null) : ApiResult<Nothing>
}

/** 세 API(장소 검색·대중교통·자가용)가 함께 쓰는 오류 구분. */
enum class ApiError {
    /**
     * 앱 API 게이트웨이 주소(`SUNSEAT_API_BASE_URL`)가 비어 있다. 네트워크 호출 없이 바로 실패한다.
     * REST 키를 앱에 넣던 때(#189 이전)의 "키 없음"과 같은 안내를 쓴다.
     */
    MISSING_KEY,

    /**
     * 게이트웨이나 카카오가 거부했다(401/403: 허용되지 않은 요청, 서버 쪽 키 문제 등).
     * 키는 게이트웨이에 있어 서버 쪽에서 고칠 수 있으므로 잠시 뒤 다시 시도하게 한다.
     */
    UNAUTHORIZED,

    /** 호출 한도를 넘었다(429: 게이트웨이의 IP별 제한, 또는 카카오 한도 초과 오류 본문). */
    QUOTA_EXCEEDED,

    /** 연결 실패·시간 초과 등 응답을 받지 못했다. */
    NETWORK,

    /** 그 밖의 서버 오류(5xx 등)나 해석할 수 없는 응답. */
    UNKNOWN,
}

/** 다시 시도해 볼 만한 오류인지. 앱 API 주소가 없는 빌드만 다시 시도해도 소용없다. */
val ApiError.retryable: Boolean get() = this != ApiError.MISSING_KEY
