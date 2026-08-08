package com.trainning.jh_chronicles

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// object 키워드는 "이 객체는 앱 전체에서 딱 하나만 만들어서 돌려 쓴다"는 뜻(싱클톤) 추가 객체를 생성하려하면 에러로 막음
object RetrofitClient {

    // OpenWeatherMap의 기본 서버 주소
    private const val BASE_URL = "https://api.openweathermap.org/"
    private const val KAKAO_BASE_URL = "https://dapi.kakao.com/"

    // Retrofit 통신기계 조립
    val weatherService: WeatherService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL) // 해당 데이터를 가저올수있는 api 주소
            .addConverterFactory(GsonConverterFactory.create()) // JSON을 코틀린 데이터로 자동 변환
            .build()
            .create(WeatherService::class.java) // 내 앱이 api에게 요청하는 주문서객체를 만듬(WeatherService 인터페이스를 구현한 객체)
    }

    // Kakao API 통신기계 조립
    val kakaoService: KakaoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KAKAO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoApiService::class.java)
    }
}