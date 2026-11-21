package com.sunseat.app.data.place

import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.data.network.kakaoCall
import com.sunseat.app.domain.LatLng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query

/** 카카오 로컬 키워드 검색(앱 API `GET kakao/local/search/keyword` → 카카오 `/v2/local/search/keyword.json`). */
class KakaoPlaceSearchApi internal constructor(
    private val service: KakaoLocalService,
) : PlaceSearchApi {

    constructor(retrofit: Retrofit) : this(retrofit.create<KakaoLocalService>())

    override suspend fun search(query: String, near: LatLng?, size: Int): ApiResult<List<Place>> {
        // 앱 API 게이트웨이는 파라미터 값이 200자를 넘으면 거부한다(#189). 그보다 짧게 잘라 보낸다.
        val trimmed = query.trim().take(MAX_QUERY_LENGTH).trim()
        if (trimmed.isEmpty()) return ApiResult.Success(emptyList())
        return kakaoCall(
            request = {
                service.keyword(
                    query = trimmed,
                    x = near?.longitude,
                    y = near?.latitude,
                    size = size.coerceIn(1, 15),
                )
            },
            transform = { ApiResult.Success(it.toPlaces()) },
        )
    }

    internal companion object {
        /** 검색어 최대 길이. 게이트웨이의 파라미터 길이 상한(workers/api MAX_PARAM_LENGTH = 200)보다 짧다. */
        const val MAX_QUERY_LENGTH = 100
    }
}

internal interface KakaoLocalService {
    @GET("local/search/keyword")
    suspend fun keyword(
        @Query("query") query: String,
        @Query("x") x: Double? = null,
        @Query("y") y: Double? = null,
        @Query("size") size: Int? = null,
        @Query("page") page: Int? = null,
    ): Response<KeywordSearchResponse>
}

@Serializable
internal data class KeywordSearchResponse(
    val documents: List<KeywordDocument> = emptyList(),
)

@Serializable
internal data class KeywordDocument(
    val id: String = "",
    @SerialName("place_name") val placeName: String = "",
    @SerialName("address_name") val addressName: String = "",
    @SerialName("road_address_name") val roadAddressName: String = "",
    @SerialName("category_name") val categoryName: String = "",
    /** 경도(문자열) */
    val x: String = "",
    /** 위도(문자열) */
    val y: String = "",
)

/** 좌표를 해석할 수 없는 문서는 건너뛴다. */
internal fun KeywordSearchResponse.toPlaces(): List<Place> = documents.mapNotNull { doc ->
    val lng = doc.x.toDoubleOrNull() ?: return@mapNotNull null
    val lat = doc.y.toDoubleOrNull() ?: return@mapNotNull null
    Place(
        id = doc.id,
        name = doc.placeName,
        address = doc.addressName,
        roadAddress = doc.roadAddressName,
        location = LatLng(lat, lng),
        category = doc.categoryName,
    )
}
