package com.trainning.jh_chronicles

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    // https://api.openweathermap.org/data/2.5/weather 주소 뒤에
    // ?q=Seoul&appid=내키값&units=metric 형태로 요청을 보낸다는 의미
    @GET("data/2.5/weather")
    suspend fun getWeather( // suspend를 붙이면 일시중지 될수 있는 함수, 서버에 요청을 보내놓고 데이터가 올떄까지 기다렸다 데이터가 오면 재개 하겟다는 메서드
        @Query("q") cityName: String,       // 도시 이름 (예: "Seoul")
        @Query("appid") apiKey: String,     // 발급받은 API Key
        @Query("units") units: String = "metric", // 섭씨 온도로 받기 위한 설정
        @Query("lang") lang: String = "kr"        // 한국어 설명을 받기 위한 설정
    ): WeatherResponse // 결과물로 WeatherResponse 택배 상자를 받음!

    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): AirPollutionResponse
}