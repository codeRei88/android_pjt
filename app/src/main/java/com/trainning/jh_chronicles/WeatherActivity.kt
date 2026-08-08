package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trainning.jh_chronicles.databinding.ActivityWeatherBinding
import kotlinx.coroutines.launch

class WeatherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherBinding

    // Fragment의 onCreateView 대신 Activity는 onCreate에서 화면을 생성한다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate")

        // 화면 인테리어 시작
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면 이동 버튼 세팅
        // findNavController 대신 Activity를 실행하는 명시적 Intent만 사용한다.
        binding.recordBtn.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        binding.mapBtn.setOnClickListener {
            startActivity(Intent(this, HospitalActivity::class.java))
        }
        binding.diaryBtn.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
        binding.dDayBtn.setOnClickListener {
            startActivity(Intent(this, VaccinationActivity::class.java))
        }

        // 날씨 데이터 가져오기 메서드 호출
        fetchWeatherData()
    }

    // 날씨를 가져오는 함수는 onCreate 바깥에 독립적으로 위치
    // onCreate는 기능적으로 ui그리는 목적의 함수, 지향성이 다름
    private fun fetchWeatherData() {
        val apiKey = "653d30f1558429a1d9d417c7361a512c"
        // 성남의 위도, 경도
        val seongnamLat = 37.4201
        val seongnamLon = 127.1262

        // 고객(액티비티)이 날씨데이터를 얻기위해 코루틴 작업요청을 시작함
        // lifecycleScope: "근데 나(고객)가 없어지면 배달부도 바로 철수시켜!" 앱크레쉬 안전장치(서버에서 data가 오기전 화면을 나갈경우)
        // launch: RetrofitClient 부서에 날씨데이터 배달의뢰 시작
        lifecycleScope.launch {
            try {
                // 통신 본부(RetrofitClient)의 로비에 있는 키오스크(weatherService)로 가서
                // 'Seongnam' 날씨 버튼(getWeather)을 띡! 누름.
                // RetrofitClient의 함수내 suspend 발동: 메인 스레드는 여기서 날씨의뢰 버튼을 누르고 데이터가 오는 시간동안 화면 그리는 일로 돌아감.
                // 그 사이 백그라운드 스레드(택배 기사)가 오토바이를 타고 오픈웨더 서버로 날아감.
                val weatherData = RetrofitClient.weatherService.getWeather(
                    cityName = "Seongnam",
                    apiKey = apiKey
                )

                // 2. 미세먼지 데이터 가져오기
                val pollutionData = RetrofitClient.weatherService.getAirPollution(
                    lat = seongnamLat,
                    lon = seongnamLon,
                    apiKey = apiKey
                )

                // 가져온 날씨데이터 상자 언박싱
                val temp = weatherData.main.temp
                val currentWeather = weatherData.weather[0] //weather[0] : 주 날씨데이터 weather[1] : 부가적인 날씨데이터
                val status = currentWeather.description

                // 미세먼지 데이터 언박싱 (리스트의 첫 번째 항목에 현재 데이터가 들어있음)
                val currentPollution = pollutionData.list[0] //pullutionData[0] : 현재 미세먼지 데이터
                val aqi = currentPollution.main.aqi // 1~5 지수
                val pm10 = currentPollution.components.pm10 //미세몬지 농도
                val pm25 = currentPollution.components.pm25 // 초미세먼지 농도

                val weatherImageRes = when (currentWeather.mainStatus.lowercase()) {
                    "clear" -> R.drawable.weather_clear_jooho
                    "clouds" -> R.drawable.weather_cloudy
                    "snow" -> R.drawable.weather_snow
                    "rain", "drizzle", "thunderstorm" -> R.drawable.weather_rain
                    else -> R.drawable.weather_cloudy
                }

                val walkRecommendation = when {
                    status.contains("비") || status.contains("눈") -> "🌧️ 비/눈이 와요. 실내 놀이를 추천해요!"
                    temp < 5.0 -> "🥶 날씨가 추워요. 따뜻하게 입히고 짧게 산책하세요."
                    temp > 30.0 -> "🥵 너무 더워요. 야외 활동을 자제해 주세요."
                    else -> "😊 산책하기 딱 좋은 날씨예요!"
                }
                val dustRecommendation = when {
                    aqi >= 4 -> "😷 미세먼지가 너무 나빠요! 외출을 삼가주세요."
                    else -> "😊 공기가 맑아요!" // when 에서 else는 반드시 필요함
                }

                binding.ivWeather.setImageResource(weatherImageRes)
                binding.tvTemperature.text = "${temp.toInt()}°C"
                binding.tvWeatherDescription.text = "상세: $status"
                binding.tvDust1.text = "미세먼지: ${pm10.toInt()} / 초미세: ${pm25.toInt()}"
                binding.tvWeatherComment.text = walkRecommendation
                binding.tvDustComment.text = dustRecommendation

            } catch (e: Exception) {
                Toast.makeText(this@WeatherActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart")
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume")
    }

    override fun onPause() {
        logLifecycle("onPause")
        super.onPause()
    }

    override fun onStop() {
        logLifecycle("onStop")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart")
    }

    // 프래그먼트의 onDestroyView 대신 Activity에서는 onDestroy가 호출된다.
    override fun onDestroy() {
        logLifecycle("onDestroy")
        super.onDestroy()
    }
}
