package com.trainning.jh_chronicles

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoApiService {
    @GET("v2/local/search/keyword.json")
    suspend fun searchPlaces(
        @Header("Authorization") apiKey: String, // 카카오는 헤더에 키를 넣어야함
        @Query("query") query: String,           // "소아과" 또는 "약국"
        @Query("y") latitude: Double,            // 내 위도
        @Query("x") longitude: Double,           // 내 경도
        @Query("radius") radius: Int = 3000,     // 반경 3km (3000m)
        @Query("sort") sort: String = "distance" // 가까운 순 정렬
    ): KakaoSearchResponse
}