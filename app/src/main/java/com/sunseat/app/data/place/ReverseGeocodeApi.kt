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

/** 좌표의 주소를 찾는다(지도에서 위치 선택, #85). 근처 상호를 장소 이름으로 단정하지 않도록 주소만 돌려준다. */
interface ReverseGeocodeApi {
    /** 도로명 주소, 없으면 지번 주소. 주소가 없는 곳(바다·산 등)이면 `Success(null)`. */
    suspend fun address(location: LatLng): ApiResult<String?>
}

/** 카카오 로컬 좌표→주소 변환(앱 API `GET kakao/local/geo/coord2address` → 카카오 `/v2/local/geo/coord2address.json`). */
class KakaoReverseGeocodeApi internal constructor(
    private val service: KakaoCoordService,
) : ReverseGeocodeApi {

    constructor(retrofit: Retrofit) : this(retrofit.create<KakaoCoordService>())

    override suspend fun address(location: LatLng): ApiResult<String?> = kakaoCall(
        request = { service.coord2address(x = location.longitude, y = location.latitude) },
        transform = { ApiResult.Success(it.toAddress()) },
    )
}

internal interface KakaoCoordService {
    @GET("local/geo/coord2address")
    suspend fun coord2address(
        @Query("x") x: Double,
        @Query("y") y: Double,
    ): Response<Coord2AddressResponse>
}

@Serializable
internal data class Coord2AddressResponse(
    val documents: List<Coord2AddressDocument> = emptyList(),
)

@Serializable
internal data class Coord2AddressDocument(
    @SerialName("road_address") val roadAddress: AddressName? = null,
    val address: AddressName? = null,
)

@Serializable
internal data class AddressName(
    @SerialName("address_name") val addressName: String = "",
)

/** 첫 문서의 도로명 주소, 없으면 지번 주소. 둘 다 비면 null. */
internal fun Coord2AddressResponse.toAddress(): String? = documents.firstOrNull()?.let { doc ->
    doc.roadAddress?.addressName?.trim()?.takeIf { it.isNotEmpty() }
        ?: doc.address?.addressName?.trim()?.takeIf { it.isNotEmpty() }
}
