package com.sunseat.app.data.place

import com.sunseat.app.data.network.ApiResult
import com.sunseat.app.domain.LatLng

/** 검색된 장소. 결과 목록은 저장하지 않으며 사용자가 저장한 장소는 별도 저장소에서 관리한다. */
data class Place(
    val id: String,
    val name: String,
    /** 지번 주소 */
    val address: String,
    /** 도로명 주소. 없으면 빈 문자열 */
    val roadAddress: String,
    val location: LatLng,
    /** 카테고리 이름(예: "교통,수송 > 지하철,전철 > 수도권2호선"). 없으면 빈 문자열 */
    val category: String,
)

/** 상호·주소 키워드로 장소를 찾는다. */
interface PlaceSearchApi {
    /**
     * 빈 검색어는 호출 없이 빈 목록을 돌려준다.
     * @param near 주어지면 이 좌표를 중심으로 요청한다(정렬은 정확도순)
     * @param size 결과 개수 1~15
     */
    suspend fun search(query: String, near: LatLng? = null, size: Int = 15): ApiResult<List<Place>>
}
