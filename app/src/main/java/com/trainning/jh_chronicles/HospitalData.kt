package com.trainning.jh_chronicles

data class KakaoSearchResponse(
    val meta: PlaceMeta,       // 응답 메타데이터
    val documents: List<Place>  // 검색된 장소 리스트
)

data class PlaceMeta(
    val is_end: Boolean,       // 현재 페이지가 마지막 페이지인지 여부
    val total_count: Int       // 검색어에 검색된 전체 문서 수
)
data class Place(
    val place_name: String,   // 장소 이름 (예: 튼튼소아과)
    val distance: String,     // 내 위치로부터의 거리 (미터 단위)
    val phone: String,        // 전화번호
    val address_name: String, // 전체 지번 주소
    val road_address_name: String, // 전체 도로명 주소
    val place_url: String,    // 장소 상세 페이지 URL
)
