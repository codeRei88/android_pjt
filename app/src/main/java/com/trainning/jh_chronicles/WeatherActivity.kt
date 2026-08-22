package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.trainning.jh_chronicles.databinding.ActivityWeatherBinding
import kotlinx.coroutines.launch

class WeatherActivity : AppCompatActivity() {

    companion object {
        // 마지막 조회 후 날씨를 다시 받아야 하는 시간 간격: 10분
        private const val WEATHER_REFRESH_INTERVAL_MILLIS = 10 * 60 * 1000L

        // 시스템이 Activity를 다시 만들 때 마지막 날씨 화면과 조회 시각을 복원하기 위한 Bundle key
        // 마지막 날씨 조회 성공 시각을 저장할 key를 선언
        private const val STATE_LAST_FETCHED_AT = "state_weather_last_fetched_at"

        // 화면에 표시한 날씨 이미지의 drawable id를 저장할 key를 선언
        private const val STATE_WEATHER_IMAGE = "state_weather_image"

        // 화면에 표시한 온도를 저장할 key를 선언
        private const val STATE_TEMPERATURE = "state_weather_temperature"

        // 화면에 표시한 날씨 설명을 저장할 key를 선언
        private const val STATE_DESCRIPTION = "state_weather_description"

        // 화면에 표시한 미세먼지 정보를 저장할 key를 선언
        private const val STATE_DUST = "state_weather_dust"

        // 화면에 표시한 산책 추천 문구를 저장할 key를 선언
        private const val STATE_WEATHER_COMMENT = "state_weather_comment"

        // 화면에 표시한 미세먼지 추천 문구를 저장할 key를 선언
        private const val STATE_DUST_COMMENT = "state_weather_dust_comment"
    }

    // activity_weather.xml의 View들을 Kotlin 코드에서 사용하기 위해 바인딩 변수를 선언
    private lateinit var binding: ActivityWeatherBinding

    // 마지막 날씨 조회가 성공한 시간을 저장하여 onResume에서 10분 경과 여부를 확인
    private var lastWeatherFetchedAt = 0L

    // ImageView에 표시한 마지막 날씨 그림도 Bundle에 저장할 수 있도록 drawable id를 기억
    private var currentWeatherImageResource = 0

    // onStop에서 진행 중인 날씨 네트워크 작업을 취소하려면 Job 객체를 기억하고 있어야 함
    private var weatherFetchJob: Job? = null

    // Activity가 처음 생성될 때 화면과 클릭 이벤트를 준비
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - 날씨 화면과 이동 버튼 초기화")

        // 화면 인테리어 시작
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면 이동 버튼 세팅
        // 기록 버튼을 누르면 명시적 Intent로 RecordActivity를 실행
        binding.recordBtn.setOnClickListener {
            logLifecycle("(INTENT 발신) RecordActivity 실행")
            startActivity(Intent(this, RecordActivity::class.java))
        }

        // 병원 버튼을 누르면 명시적 Intent로 HospitalActivity를 실행
        binding.mapBtn.setOnClickListener {
            logLifecycle("(INTENT 발신) HospitalActivity 실행")
            startActivity(Intent(this, HospitalActivity::class.java))
        }

        // 일기 버튼을 누르면 명시적 Intent로 DiaryActivity를 실행
        binding.diaryBtn.setOnClickListener {
            logLifecycle("(INTENT 발신) DiaryActivity 실행")
            startActivity(Intent(this, DiaryActivity::class.java))
        }

        // 접종 버튼을 누르면 명시적 Intent로 VaccinationActivity를 실행
        binding.dDayBtn.setOnClickListener {
            logLifecycle("(INTENT 발신) VaccinationActivity 실행")
            startActivity(Intent(this, VaccinationActivity::class.java))
        }


        //화면 회전이나 시스템 재생성으로 전달된 Bundle이 있으면 마지막 날씨와 조회 시각을 먼저 복원
        restoreWeatherState(savedInstanceState)
    }

    // onSaveInstanceState에서 저장한 마지막 날씨 화면과 조회시각을 다시 화면에 표시하는 함수
    private fun restoreWeatherState(savedInstanceState: Bundle?) {
        // 저장된 Bundle이 없는 최초 실행이면 복원할 값이 없어 함수 리턴
        if (savedInstanceState == null) {
            logLifecycle("(STATE 확인) 최초 실행이라 복원할 날씨 화면 없음")
            return
        }

        logLifecycle("(STATE 복원) Bundle에서 마지막 날씨 화면과 조회 시각 복원 시작")

        // 이전 Activity에서 저장한 마지막 날씨조회 시각을 복원
        lastWeatherFetchedAt = savedInstanceState.getLong(STATE_LAST_FETCHED_AT, 0L)

        // 저장된 drawable id가 있을 때만 날씨 이미지를 복원
        val weatherImageRes = savedInstanceState.getInt(STATE_WEATHER_IMAGE, 0)
        if (weatherImageRes != 0) {
            currentWeatherImageResource = weatherImageRes
            binding.ivWeather.setImageResource(weatherImageRes)
        }

        // 저장된 온도와 날씨 문구가 있으면 각 TextView에 다시 표시
        binding.tvTemperature.text =
            savedInstanceState.getString(STATE_TEMPERATURE, binding.tvTemperature.text.toString())
        binding.tvWeatherDescription.text =
            savedInstanceState.getString(STATE_DESCRIPTION, binding.tvWeatherDescription.text.toString())
        binding.tvDust1.text =
            savedInstanceState.getString(STATE_DUST, binding.tvDust1.text.toString())
        binding.tvWeatherComment.text =
            savedInstanceState.getString(STATE_WEATHER_COMMENT, binding.tvWeatherComment.text.toString())
        binding.tvDustComment.text =
            savedInstanceState.getString(STATE_DUST_COMMENT, binding.tvDustComment.text.toString())
        logLifecycle("(STATE 복원) 마지막 날씨 UI 복원 완료")
    }

    // 날씨를 가져오는 함수는 onCreate 바깥에 독립적으로 위치
    // onCreate는 기능적으로 ui그리는 목적의 함수, 지향성이 다름
    private fun fetchWeatherData() {
        // 이미 같은 네트워크 작업이 진행 중이라면 onResume이 다시 호출돼도 중복 요청하지 않음
        if (weatherFetchJob?.isActive == true) {
            logLifecycle("(조회 생략) 기존 날씨 조회가 실행 중이라 중복 요청하지 않음")
            return
        }

        // OpenWeather 서버에 요청할 API key를 준비했음
        val apiKey = "653d30f1558429a1d9d417c7361a512c"
        // 성남의 위도, 경도
        val seongnamLat = 37.4201
        val seongnamLon = 127.1262

        // 고객(액티비티)이 날씨데이터를 얻기위해 코루틴 작업요청을 시작함
        // lifecycleScope: 화면이 없어지면 배달부도 바로 철수, 앱크레쉬 안전장치(서버에서 data가 오기전 화면을 나갈경우)
        // launch: RetrofitClient 부서에 날씨데이터 배달의뢰 시작
        logLifecycle("날씨 조회 실행")
        weatherFetchJob = lifecycleScope.launch {
            try {
                // 통신 본부(RetrofitClient)의 로비에 있는 키오스크(weatherService)로 가서
                // 'Seongnam' 날씨 버튼(getWeather)을 띡! 누름.
                // RetrofitClient의 함수내 suspend 발동: 메인 스레드는 여기서 날씨의뢰 버튼을 누르고 데이터가 오는 시간동안 화면 그리는 일로 돌아감.
                // 그 사이 백그라운드 스레드(택배 기사)가 오토바이를 타고 오픈웨더 서버로 날아감.
                logLifecycle("(데이터 요청) OpenWeather API에 현재 날씨 요청")
                val weatherData = RetrofitClient.weatherService.getWeather(
                    cityName = "Seongnam",
                    apiKey = apiKey
                )
                logLifecycle("(데이터 응답) 현재 날씨 응답 수신")

                // 성남의 위도와 경도를 전달해 미세먼지 데이터를 요청했음
                logLifecycle("(데이터 요청) OpenWeather API에 미세먼지 요청")
                val pollutionData = RetrofitClient.weatherService.getAirPollution(
                    lat = seongnamLat,
                    lon = seongnamLon,
                    apiKey = apiKey
                )
                logLifecycle("(데이터 응답) 미세먼지 응답 수신")

                // 가져온 날씨데이터 상자 언박싱
                val temp = weatherData.main.temp
                val currentWeather = weatherData.weather[0] //weather[0] : 주 날씨데이터 weather[1] : 부가적인 날씨데이터
                val status = currentWeather.description

                // 미세먼지 데이터 언박싱 (리스트의 첫 번째 항목에 현재 데이터가 들어있음)
                val currentPollution = pollutionData.list[0] //pullutionData[0] : 현재 미세먼지 데이터
                val aqi = currentPollution.main.aqi // 1~5 지수
                val pm10 = currentPollution.components.pm10 //미세몬지 농도
                val pm25 = currentPollution.components.pm25 // 초미세먼지 농도

                // 서버가 돌려준 날씨 상태에 맞는 이미지를 선택했음
                val weatherImageRes = when (currentWeather.mainStatus.lowercase()) {
                    "clear" -> R.drawable.weather_clear_jooho
                    "clouds" -> R.drawable.weather_cloudy
                    "snow" -> R.drawable.weather_snow
                    "rain", "drizzle", "thunderstorm" -> R.drawable.weather_rain
                    else -> R.drawable.weather_cloudy
                }

                // 강수 여부와 온도에 따라 산책 추천 문구를 선택했음
                val walkRecommendation = when {
                    status.contains("비") || status.contains("눈") -> "🌧️ 비/눈이 와요. 실내 놀이를 추천해요!"
                    temp < 5.0 -> "🥶 날씨가 추워요. 따뜻하게 입히고 짧게 산책하세요."
                    temp > 30.0 -> "🥵 너무 더워요. 야외 활동을 자제해 주세요."
                    else -> "😊 산책하기 딱 좋은 날씨예요!"
                }

                // 대기질 지수에 따라 외출 추천 문구를 선택했음
                val dustRecommendation = when {
                    aqi >= 4 -> "😷 미세먼지가 너무 나빠요! 외출을 삼가주세요."
                    else -> "😊 공기가 맑아요!" // when 에서 else는 반드시 필요함
                }

                // 조회한 날씨와 미세먼지 결과를 화면의 각 View에 표시했음
                binding.ivWeather.setImageResource(weatherImageRes)
                currentWeatherImageResource = weatherImageRes
                binding.tvTemperature.text = "${temp.toInt()}°C"
                binding.tvWeatherDescription.text = "상세: $status"
                binding.tvDust1.text = "미세먼지: ${pm10.toInt()} / 초미세: ${pm25.toInt()}"
                binding.tvWeatherComment.text = walkRecommendation
                binding.tvDustComment.text = dustRecommendation

                // 날씨와 미세먼지를 모두 화면에 표시한 시점을 마지막 성공 조회 시간으로 저장
                lastWeatherFetchedAt = System.currentTimeMillis()
                logLifecycle("날씨·미세먼지 데이터 표시 및 조회 시각 갱신")

            } catch (e: CancellationException) {
                // onStop에서 화면이 가려져 Job을 취소한 것은 정상적인 생명주기 정리이므로 오류를 띄우지 않음
                // 코루틴 취소 신호가 일반 오류로 처리되지 않도록 다시 전달했음
                logLifecycle("화면이 가려져 날씨 조회 작업 종료")
                throw e
            } catch (e: Exception) {
                logLifecycle("날씨 또는 미세먼지 조회 실패: ${e.message}")
                // 서버 요청에 실패하면 사용자에게 오류 내용을 Toast로 보여줬음
                Toast.makeText(this@WeatherActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Activity가 사용자에게 보이기 시작한 시점을 로그로 확인했음
    override fun onStart() {
        super.onStart()
        logLifecycle("onStart")
    }

    // Activity가 전면에 나타날 때 날씨를 다시 받아야 하는지 확인했음
    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - 마지막 조회 후 10분 경과 여부 확인")

        // 처음 조회하거나 마지막 조회 후 10분 이상 지났을 때만 서버에 다시 요청
        // 현재 시각에서 마지막 조회 시각을 빼 경과 시간을 계산
        val elapsedTime = System.currentTimeMillis() - lastWeatherFetchedAt // 경과시간
        if (lastWeatherFetchedAt == 0L || elapsedTime >= WEATHER_REFRESH_INTERVAL_MILLIS) { //처음조회 거나 경과시간 10분이 자날시
            logLifecycle("(갱신 판단) 최초 조회 또는 10분 경과 - 새로운 날씨 데이터 요청")
            fetchWeatherData() //날씨 호출
        } else {
            logLifecycle("(갱신 판단) 마지막 조회 후 10분 미만 - 기존 날씨 유지")
        }
    }

    // 다른 화면이 위에 나타나 Activity가 전면 상태를 잃은 시점을 로그로 확인했음
    override fun onPause() {
        logLifecycle("onPause")
        super.onPause()
    }

    // Activity가 완전히 보이지 않을 때 진행 중인 네트워크 작업 정리
    override fun onStop() {
        // 화면이 완전히 가려지면 불필요한 네트워크 작업을 중단하여 결과가 숨겨진 화면을 수정하지 않도록 함
        val wasWeatherJobActive = weatherFetchJob?.isActive == true
        weatherFetchJob?.cancel()
        weatherFetchJob = null
        logLifecycle(
            "onStop - 날씨 조회작업 정리, 진행 중이었음=$wasWeatherJobActive"
        )
        super.onStop()
    }

    // 중단됐던 Activity가 다시 시작될 때 호출된 시점을 로그로 확인
    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart")
    }


    override fun onSaveInstanceState(outState: Bundle) {
        // 마지막 날씨조회 시각과 날씨 이미지 id를 Bundle에 저장
        outState.putLong(STATE_LAST_FETCHED_AT, lastWeatherFetchedAt)
        outState.putInt(STATE_WEATHER_IMAGE, currentWeatherImageResource)

        // 현재 화면에 표시된 날씨와 미세먼지 문구를 Bundle에 저장
        outState.putString(STATE_TEMPERATURE, binding.tvTemperature.text.toString())
        outState.putString(STATE_DESCRIPTION, binding.tvWeatherDescription.text.toString())
        outState.putString(STATE_DUST, binding.tvDust1.text.toString())
        outState.putString(STATE_WEATHER_COMMENT, binding.tvWeatherComment.text.toString())
        outState.putString(STATE_DUST_COMMENT, binding.tvDustComment.text.toString())
        logLifecycle("onSaveInstanceState - 마지막 날씨 화면과 조회 시각 저장")
        super.onSaveInstanceState(outState)
    }


    override fun onDestroy() {
        // 보통 onStop에서 취소되지만 예외적인 종료에도 Job이 남지 않도록 한 번 더 정리
        weatherFetchJob?.cancel()
        weatherFetchJob = null
        logLifecycle("onDestroy - 날씨 작업 정리")
        super.onDestroy()
    }

}
