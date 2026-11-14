package com.sunseat.app.data.network

/** 외부 API 호출 결과. 실패는 화면에서 안내 문구를 고를 수 있게 [ApiError]로 구분한다. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>

    /** @param httpCode 서버가 응답한 경우의 HTTP 상태 코드(진단용) */
    data class Failure(val error: ApiError, val httpCode: Int? = null) : ApiResult<Nothing>
}

/** 세 API(장소 검색·대중교통·자가용)가 함께 쓰는 오류 구분. */
enum class ApiError {
    /** `KAKAO_REST_API_KEY`가 비어 있다. 네트워크 호출 없이 바로 실패한다. */
    MISSING_KEY,

    /** 키가 틀렸거나 해당 API 사용 권한이 없다(401/403). */
    UNAUTHORIZED,

    /** 호출 한도를 넘었다(429 또는 한도 초과 오류 본문). */
    QUOTA_EXCEEDED,

    /** 연결 실패·시간 초과 등 응답을 받지 못했다. */
    NETWORK,

    /** 그 밖의 서버 오류나 해석할 수 없는 응답. */
    UNKNOWN,
}
