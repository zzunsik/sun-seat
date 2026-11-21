package com.sunseat.app.data.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * 카카오 API 공통 HTTP 설정.
 *
 * 앱은 카카오를 직접 부르지 않고 앱 API 게이트웨이(`workers/api`, #189)의 `kakao/` 경로 묶음을 부른다.
 * REST 키는 게이트웨이에만 있고 앱은 인증 헤더를 붙이지 않는다. 네 API가 기준 주소 `<앱 API>/kakao/` 하나를 같이 쓴다.
 */
object KakaoHttp {
    /** 앱 API 게이트웨이에서 카카오 경로 묶음의 이름. 게이트웨이 `GROUPS.kakao`와 같다. */
    const val GROUP_PATH = "kakao/"

    /** 앱 API 주소가 없을 때 Retrofit을 만들기 위한 자리표시 주소. 이 주소로는 요청을 보내지 않는다. */
    internal const val UNCONFIGURED_BASE_URL = "https://sunseat-api.invalid/"

    internal val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * 빌드 설정의 앱 API 주소(`SUNSEAT_API_BASE_URL`)를 기준 주소(끝에 `/`)로 바꾼다.
     * 비었거나 http(s) 주소가 아니거나 쿼리가 붙어 있으면 null(= 설정 안 됨).
     * http 주소도 받는다(로컬 `wrangler dev`·테스트용). 실제 기기는 평문 통신을 막으므로 https 주소를 쓴다.
     */
    fun apiBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val url = (if (trimmed.endsWith("/")) trimmed else "$trimmed/").toHttpUrlOrNull() ?: return null
        if (url.query != null || url.fragment != null) return null
        return url.toString()
    }

    /** @param configured false면 모든 요청을 보내지 않고 [ApiNotConfiguredException]으로 실패시킨다. */
    fun okHttpClient(configured: Boolean = true): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ApiConfiguredInterceptor(configured))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

/** 앱 API 주소가 없을 때 요청을 보내기 전에 던진다. */
class ApiNotConfiguredException : IOException("SUNSEAT_API_BASE_URL이 비어 있습니다")

/** 앱 API 주소가 설정되지 않았으면 요청을 보내지 않는다. 인증 헤더는 붙이지 않는다(키는 게이트웨이에만 있다). */
class ApiConfiguredInterceptor(private val configured: Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        if (!configured) throw ApiNotConfiguredException()
        return chain.proceed(chain.request())
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
    } catch (_: ApiNotConfiguredException) {
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
