package com.trainning.jh_chronicles

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trainning.jh_chronicles.databinding.ActivityHospitalBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HospitalActivity : AppCompatActivity() {

    private companion object {
        const val SEARCH_RADIUS_METERS = 3000
    }

    private lateinit var binding: ActivityHospitalBinding

    // 2. 내 위치를 찾아내는 GPS 전담 요원 현재는 화면이 그려지기 전이므로 context가 없는상태라 메모리에 빈그릇만 선언
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 3. 진열대에 가저온 데이터를 꽂아 보여주는 객체 현재는 화면이 그려지기 전이므로 context가 없는상태라 메모리에 빈그릇만 선언
    private lateinit var hospitalAdapter: AdapterHospital

    // 4. 권한안내 데스크 셋팅, 사용자가 권한요청에대한 답을 하면 그때 행동수칙 적어놈
    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

        // 손님이 '정확한 위치'나 '대략적인 위치' 중 하나라도 허락했다면?
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            // 허락받았으니 바로 GPS 요원 출동시키고 병원 찾기 함수 호출
            fetchLocationAndHospitals()
        } else {
            // 손님이 "싫어!" 하고 거절했다면?
            showError("위치 권한이 없으면 주변 소아과를 찾을 수 없어요 😢")
            Toast.makeText(this, "위치 권한을 허용해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    // 🏪 매장 문을 처음 여는 곳 (화면이 만들어질 때)
    // Fragment의 onCreateView 대신 Activity는 onCreate에서 화면을 만든다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate")

        // 도면을 펼칩니다.
        binding = ActivityHospitalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 버튼(다른 방으로 가는 문)과 진열대(리사이클러뷰)를 준비합니다.
        setupNavigationButtons()
        setupRecyclerView()

        // GPS 요원을 구글(LocationServices)에서 고용해 옵니다.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 손님에게 퍼미션 체크 메서드
        checkLocationPermission()
    }

    // 🚪 다른 방(화면)으로 넘어가는 버튼들 세팅
    // findNavController 대신 Activity를 실행하는 명시적 Intent만 사용한다.
    private fun setupNavigationButtons() {
        binding.recordBtn.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        binding.weatherBtn.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }
        binding.diaryBtn.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
        binding.dDayBtn.setOnClickListener {
            startActivity(Intent(this, VaccinationActivity::class.java))
        }
    }

    // 🛒 진열대(리사이클러뷰)와 직원(어댑터) 준비하기
    private fun setupRecyclerView() {
        val hospitalList = mutableListOf<Place>() // 빈 바구니 준비
        hospitalAdapter = AdapterHospital(hospitalList) // 진열 직원에게 빈 바구니 쥐어주기
        binding.hospitalRc.adapter = hospitalAdapter // 진열대를 직원에게 맡기기
        binding.hospitalRc.layoutManager = LinearLayoutManager(this) // 위에서 아래로(수직) 진열하라고 지시
    }

    // 👮 보안 요원의 신분증(권한) 검사 시간!
    private fun checkLocationPermission() {
        // 이미 예전에 허락(VIP 패스)을 받았는지 확인해요.
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            // 이미 허락받은 VIP 손님이면 팝업 없이 바로 GPS 출동!
            fetchLocationAndHospitals()
        } else {
            // 처음 온 손님이면 안내문구를 띄우고 "허락해 주세요~" 팝업을 띄워요.
            showError("주변 소아과를 찾으려면 위치 권한이 필요해요.")
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // 📍 GPS 요원 출동! 내 위치 찾아내기
    @SuppressLint("MissingPermission")
    private fun fetchLocationAndHospitals() {
        showLoading("GPS 요원이 현재 위치를 찾고 있어요! 🕵️‍♂️")

        // 요원에게 가장 정확한(HIGH_ACCURACY) 지금 내 위치를 찾아오라고 명령
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                // Activity가 종료 중이면 일 멈추기
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                if (location == null) {
                    showError("위치를 못 찾았어요. 핸드폰 GPS가 켜져 있나요? 📡")
                    return@addOnSuccessListener
                }

                // 성공! 내 위도(가로줄)와 경도(세로줄)를 알아냈어요.
                val myLatitude = location.latitude
                val myLongitude = location.longitude

                // 에뮬레이터가 실제로 어떤 위치를 보내는지 Logcat에서 확인
                Log.d(
                    "HospitalActivity",
                    "현재 위치: latitude=$myLatitude, longitude=$myLongitude, " +
                        "accuracy=${location.accuracy}m"
                )

                /*
                 * Android 에뮬레이터의 기본 위치는 미국
                 * 한국 밖 좌표를 카카오 로컬 검색에 보내면 엉뚱한 검색 결과가 나올 수 있으므로
                 * 먼저 한국 안의 좌표인지 확인합니다.
                 */
                if (!isLocationInKorea(myLatitude, myLongitude)) {
                    showError(
                        "현재 GPS가 한국 밖을 가리키고 있어요.\n" +
                            "에뮬레이터 위치를 성남으로 설정해주세요.\n" +
                            "현재 좌표: %.5f, %.5f".format(myLatitude, myLongitude)
                    )
                    return@addOnSuccessListener
                }

                /*
                 * 대략적인 위치만 허용하면 오차가 수 km까지 커질 수 있어요.
                 * 반경 3km 검색에서는 정확한 위치 권한이 중요합니다.
                 */
                if (location.accuracy > 3000f) {
                    showError(
                        "위치 오차가 너무 커서 주변 3km를 정확히 찾을 수 없어요.\n" +
                            "앱 위치 권한에서 '정확한 위치'를 켜주세요."
                    )
                    return@addOnSuccessListener
                }

                // 알아낸 위치를 바탕으로 카카오 본사에 택배(병원 목록)를 주문해요!
                loadHospitals(myLatitude, myLongitude)
            }
            .addOnFailureListener {
                showError("위치 찾기 실패! 권한과 GPS를 확인해 주세요.")
            }
    }

    // 🚚 통신 본부(Retrofit)를 통해 카카오 서버에 데이터 택배 주문하기!
    private fun loadHospitals(latitude: Double, longitude: Double) {

        // 배달부(코루틴) 출발! (네트워크 통신은 백그라운드에서 해야 안 멈춰요)
        lifecycleScope.launch {
            showLoading("카카오 본사에서 소아과 목록 택배가 오고 있어요! 📦")

            try {
                // 카카오 본사 문을 여는 마법의 열쇠(API KEY)
                val kakaoKey = "KakaoAK 1fe8888145997cf462b7ba8feed50227"

                // Retrofit 통신 본부 안의 카카오 부서(kakaoService)에 전화를 걸어 "소아청소년과"를 주문해요.
                val hospitalData = RetrofitClient.kakaoService.searchPlaces(
                    apiKey = kakaoKey,
                    query = "소아청소년과",
                    latitude = latitude,
                    longitude = longitude,
                    radius = SEARCH_RADIUS_METERS,
                    sort = "distance"
                )

                // Activity가 종료 중이면 택배 버리고 퇴근!
                if (isFinishing || isDestroyed) return@launch

                // 도착한 택배 상자에서 알맹이(병원 리스트)만 쏙 꺼내요.
                /*
                 * 서버에 3km 반경을 요청했지만 화면에 넣기 전에도 한 번 더 확인합니다.
                 * 이렇게 하면 거리 정보가 없거나 3km를 넘는 장소는 목록에 들어오지 않습니다.
                 */
                val placeList = hospitalData.documents.filter { hospital ->
                    val distanceInMeters = hospital.distance.toIntOrNull()
                    distanceInMeters != null &&
                        distanceInMeters <= SEARCH_RADIUS_METERS
                }

                // 진열 직원(어댑터)에게 "이 물건들로 싹 다시 진열해 줘!" 라고 넘겨요.
                hospitalAdapter.updateData(placeList)

                // 결과에 따라 화면 방송하기
                if (placeList.isEmpty()) {
                    showEmpty("근처 3km 안에는 소아과가 없네요 텅~ 🌬️")
                } else {
                    showSuccess(placeList.size)
                }

            } catch (e: CancellationException) {
                // 손님이 변심해서 뒤로가기 누름 -> 배달 취소 (자연스러운 거라 오류 아님)
                throw e
            } catch (e: HttpException) {
                // 🚨 카카오 본사에서 입구컷 당함 (열쇠가 틀렸거나, 권한이 없거나!)
                val errorMessage = when (e.code()) {
                    401 -> "열쇠(API 키)가 틀렸대요! 카카오 열쇠를 다시 확인해 주세요."
                    403 -> "카카오 본사에서 우리 앱을 차단했어요. (설정 확인 필요)"
                    else -> "알 수 없는 이유로 카카오 본사에서 쫓겨났어요. (오류: ${e.code()})"
                }
                showError(errorMessage)
            } catch (e: Exception) {
                // 🚧 가는 길에 다리가 끊김 (인터넷 연결 끊김 등 배달 사고)
                showError("배달 사고 발생! 인터넷이 잘 터지나 확인해 보세요 📶")
            }
        }
    }

    /*
     * 카카오 로컬 검색은 국내 장소 검색용이므로 좌표가 한국 영역인지 확인합니다.
     * 아래 범위는 대한민국을 넉넉하게 감싸는 위도·경도 범위입니다.
     */
    private fun isLocationInKorea(latitude: Double, longitude: Double): Boolean {
        return latitude in 33.0..39.5 && longitude in 124.0..132.0
    }

    private fun showLoading(message: String) {
        binding.progressBar.visibility = View.VISIBLE // 빙글빙글 로딩 보여주기
        binding.statusText.visibility = View.VISIBLE // 안내글자 보여주기
        binding.statusText.text = message
        binding.hospitalRc.visibility = View.GONE // 아직 진열대(리스트)는 숨기기
    }

    private fun showSuccess(hospitalCount: Int) {
        binding.progressBar.visibility = View.GONE // 로딩 끄기
        binding.statusText.visibility = View.GONE // 안내글자 끄기
        binding.hospitalRc.visibility = View.VISIBLE // 짠! 진열대 보여주기
        Toast.makeText(this, "소아과 ${hospitalCount}곳 발견! 🏥", Toast.LENGTH_SHORT).show()
    }

    private fun showEmpty(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.hospitalRc.visibility = View.GONE // 진열대 숨기기
        binding.statusText.visibility = View.VISIBLE // 텅 비었다고 글자로 알려주기
        binding.statusText.text = message
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.hospitalRc.visibility = View.GONE
        binding.statusText.visibility = View.VISIBLE // 에러 났다고 글자로 알려주기
        binding.statusText.text = message
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

    // 🧹 Fragment의 onDestroyView 대신 Activity에서는 onDestroy가 호출돼요.
    override fun onDestroy() {
        logLifecycle("onDestroy")
        super.onDestroy()
    }
}
