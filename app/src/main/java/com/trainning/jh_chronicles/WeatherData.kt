package com.trainning.jh_chronicles

import com.google.gson.annotations.SerializedName

// 서버가 보내주는 전체 택배 상자
data class WeatherResponse(
    // main 상자 안에 온도 정보가 들어있음
    @SerializedName("main") val main: MainData, //api서버에서 보내주는 json데이터중 main에 해당하는 값을 코틀린변수 main에 넣어라
    // weather 리스트 안에 날씨 상태(맑음, 구름 등) 정보가 들어있음
    @SerializedName("weather") val weather: List<WeatherDescription>, // weather에 해당하는 리스트 데이터를 weather 변수에 매핑
    // 도시 이름
    @SerializedName("name") val cityName: String // name에 해당하는 도시 이름 문자열을 cityName 변수에 매핑
)

// 온도 관련 상세 데이터 상자
data class MainData(
    // 섭씨 온도
    @SerializedName("temp") val temp: Double,
    // 습도
    @SerializedName("humidity") val humidity: Int
)

// 날씨 상태 상세 데이터 상자
data class WeatherDescription(
    // 예: "Clear", "Clouds", "Rain" 등
    @SerializedName("main") val mainStatus: String,
    // 예: "clear sky", "few clouds" 등 상세 설명
    @SerializedName("description") val description: String
)


// 서버가 보내주는 미세먼지 전체 택배 상자
data class AirPollutionResponse(
    @SerializedName("list") val list: List<PollutionData>
)

data class PollutionData(
    @SerializedName("main") val main: AqiData,
    @SerializedName("components") val components: PollutionComponents
)

// 통합 대기질 지수 (1: 매우 좋음 ~ 5: 매우 나쁨)
data class AqiData(
    @SerializedName("aqi") val aqi: Int
)

// 상세 미세먼지 데이터
data class PollutionComponents(
    @SerializedName("pm10") val pm10: Double,   // 미세먼지
    @SerializedName("pm2_5") val pm25: Double   // 초미세먼지
)