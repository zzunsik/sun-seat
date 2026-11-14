package com.sunseat.app.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** 카카오 API 공통 HTTP 설정. */
object KakaoHttp {
    /** 카카오 로컬·대중교통 경로. */
    const val DAPI_BASE_URL = "https://dapi.kakao.com/"

    /** 카카오모빌리티 자동차 길찾기. */
    const val NAVI_BASE_URL = "https://apis-navi.kakaomobility.com/"

    internal val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun okHttpClient(apiKey: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(KakaoAuthInterceptor(apiKey))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

/** 키가 비어 있을 때 요청을 보내기 전에 던진다. */
class MissingApiKeyException : IOException("KAKAO_REST_API_KEY가 비어 있습니다")

/** `Authorization: KakaoAK {키}` 헤더를 붙인다. 키가 비어 있으면 요청을 보내지 않는다. */
class KakaoAuthInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        if (apiKey.isBlank()) throw MissingApiKeyException()
        val request = chain.request().newBuilder()
            .header("Authorization", "KakaoAK $apiKey")
            .build()
        return chain.proceed(request)
    }
}

/**
 * 요청을 실행하고 결과를 [ApiResult]로 바꾼다.
 * 취소(CancellationException)는 잡지 않고 그대로 전파한다.
 */
internal suspend fun <T : Any, R> kakaoCall(
    request: suspend () -> Response<T>,
    transform: (T) -> ApiResult<R>,
): ApiResult<R> {
    val response = try {
        request()
    } catch (_: MissingApiKeyException) {
        return ApiResult.Failure(ApiError.MISSING_KEY)
    } catch (_: IOException) {
        return ApiResult.Failure(ApiError.NETWORK)
    } catch (_: SerializationException) {
        return ApiResult.Failure(ApiError.UNKNOWN)
    }
    val code = response.code()
    if (!response.isSuccessful) {
        val body = try {
            response.errorBody()?.string()
        } catch (_: IOException) {
            null
        }
        return ApiResult.Failure(classifyHttpError(code, body), code)
    }
    val body = response.body() ?: return ApiResult.Failure(ApiError.UNKNOWN, code)
    return transform(body)
}

/** 카카오 공통 오류 코드 -10(호출 한도), -11(유료 사용 한도) 또는 한도를 뜻하는 문구. */
private val quotaPattern = Regex(
    """limit|throttl|quota|"code"\s*:\s*-1[01]\b""",
    RegexOption.IGNORE_CASE,
)

/** 카카오 공통 오류 코드 -401(잘못된 앱 키), -903(등록되지 않은 개발자의 앱 키). */
private val authPattern = Regex(""""code"\s*:\s*-(401|903)\b""")

/** HTTP 오류 응답을 공통 오류로 분류한다. */
internal fun classifyHttpError(code: Int, body: String?): ApiError {
    val text = body.orEmpty()
    return when {
        code == 429 || quotaPattern.containsMatchIn(text) -> ApiError.QUOTA_EXCEEDED
        code == 401 || code == 403 || authPattern.containsMatchIn(text) -> ApiError.UNAUTHORIZED
        else -> ApiError.UNKNOWN
    }
}
