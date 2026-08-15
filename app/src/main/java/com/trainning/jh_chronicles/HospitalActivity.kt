package com.trainning.jh_chronicles

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
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
import com.google.android.gms.tasks.CancellationTokenSource
import com.trainning.jh_chronicles.databinding.ActivityHospitalBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    // onPause/onStop에서 아직 진행 중인 현재 위치 요청(GPS요청)을 취소하기 위한 토큰 보관함
    private var locationCancellationSource: CancellationTokenSource? = null

    // onPause/onStop에서 카카오 병원 검색 네트워크 작업을 취소하기 위해 Job 객체를 보관
    private var hospitalSearchJob: Job? = null

    // 권한 팝업과 위치 요청이 생명주기 재호출로 중복 실행되지 않도록 현재 진행 상태를 기억
    private var isPermissionRequestRunning = false
    private var isLocationRequestInProgress = false

    // 이미 병원 목록을 정상적으로 받았다면 다른 Activity에서 돌아올 때 불필요하게 다시 검색하지 않기 위한 값
    private var hasLoadedHospitals = false

    // 지금 병원화면이 활성 상태인지 아닌지
    private var canHandleLocationResult = false

    // 4. 권한안내 데스크 셋팅, 사용자가 권한요청에대한 답을 하면 그때 행동수칙 적어놈
    private val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

        // 권한 결과가 돌아왔으므로 다음 요청이 필요할 때 다시 실행할 수 있게 진행 표시를 해제
        isPermissionRequestRunning = false

        // 손님이 '정확한 위치'나 '대략적인 위치' 중 하나라도 허락했다면?
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
        ) {
            // 권한을 허용했더라도 GPS가 꺼져 있을 수 있으므로 위치 사용 가능 여부까지 다시 확인
            if (canHandleLocationResult && isLocationAvailable()) {
                fetchLocationAndHospitals()
            } else if (canHandleLocationResult) {
                showError("위치를 사용할 수 없어요. 핸드폰 GPS를 켜주세요. 📡")
            }
        } else {
            // 손님이 거절했다면?
            showError("위치 권한이 없으면 주변 소아과를 찾을 수 없어요 😢")
            Toast.makeText(this, "위치 권한을 허용해주세요.", Toast.LENGTH_SHORT).show()
        }
    }

    // 보여지기전 화면의 구성을 준비하고 셋팅을 끝냄(onCreate는 인테리어 업자 사용자가 집에 들어오기전 사용자가 보고 상호작용할수있는 모든걸 셋팅 후 퇴장)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - 위치 권한 Launcher, RecyclerView와 GPS 객체 초기화")

        // 도면을 펼칩니다.
        binding = ActivityHospitalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 버튼(다른 방으로 가는 문)과 진열대(리사이클러뷰)를 준비합니다.
        setupNavigationButtons()
        setupRecyclerView()

        // GPS 요원을 구글(LocationServices)에서 고용해 옵니다.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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

    // 진열대(리사이클러뷰)와 직원(어댑터) 준비하기
    private fun setupRecyclerView() {
        val hospitalList = mutableListOf<Place>() // 빈 바구니 준비
        hospitalAdapter = AdapterHospital(hospitalList = hospitalList,

            // 전화 버튼을 누르면 전화번호를 ACTION_DIAL 암시적 Intent로 전달
            onDialClick = { hospital ->
                openPhoneDialer(hospital.phone)
            },

            // 지도 버튼을 누르면 도로명 주소 또는 지번 주소를 ACTION_VIEW 암시적 Intent로 전달
            onMapClick = { hospital ->
                val address = hospital.road_address_name.ifBlank { hospital.address_name }
                openAddressInMap(address)
            }
        ) // 진열 직원에게 빈 바구니와 버튼 클릭 처리방법 쥐어주기
        binding.hospitalRc.adapter = hospitalAdapter // 진열대를 직원에게 맡기기
        binding.hospitalRc.layoutManager = LinearLayoutManager(this) // 위에서 아래로(수직) 진열하라고 지시
    }

    // 병원 전화번호를 기본 전화 앱의 번호 입력 화면으로 보내는 암시적 Intent
    private fun openPhoneDialer(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            Toast.makeText(this, "등록된 전화번호가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val dialIntent = Intent(
            Intent.ACTION_DIAL, //전화 화면을 열어라
            Uri.parse("tel:$phoneNumber") // 해당 번호를 넣어라
        )
        try {
            startActivity(dialIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "전화 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 병원 주소를 처리할 수 있는 지도 앱에 전달하는 암시적 Intent
    private fun openAddressInMap(address: String) {
        if (address.isBlank()) {
            Toast.makeText(this, "등록된 주소가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val mapIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        )

        try {
            startActivity(mapIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "지도를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 사용자 위치를 딸수있는 권한이 있는지 체크
    private fun checkLocationPermission() {
        if (hasLocationPermission()) { //권한이 있을경우
            // 권한이 있어도 설정에서 GPS가 꺼져 있으면 위치 요청을 시작하지 않음
            if (!isLocationAvailable()) {
                hasLoadedHospitals = false
                showError("위치를 사용할 수 없어요. 핸드폰 GPS를 켜주세요. 📡")
                return
            }
            // 아직 목록을 받지 못했고 현재 요청 중도 아닐 때만 GPS 요원을 출동시킴
            if (!hasLoadedHospitals && !isLocationRequestInProgress) {
                fetchLocationAndHospitals()
            }
        } else { //권한이 없을 경우
            showError("주변 소아과를 찾으려면 위치 권한이 필요해요.")
            // onResume이 여러 번 호출돼도 권한 팝업이 중복으로 뜨지 않도록 진행 중일 때는 다시 요청하지 않음
            if (!isPermissionRequestRunning) {
                isPermissionRequestRunning = true
                locationPermissionRequest.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

    // 정확한 위치 또는 대략적인 위치 중 하나라도 허용됐는지 확인하는 공통 함수
    private fun hasLocationPermission(): Boolean {
        val hasFineLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        return hasFineLocation || hasCoarseLocation
    }

    // 폰의 GPS가 켜저있는지 확인
    private fun isLocationAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // GPS 작동 내 위치 찾아내기
    @SuppressLint("MissingPermission")
    private fun fetchLocationAndHospitals() {
        // 같은 위치 요청이 이미 실행 중이면 중복 요청하지 않음
        if (isLocationRequestInProgress) return

        isLocationRequestInProgress = true
        showLoading("GPS 요원이 현재 위치를 찾고 있어요! 🕵️‍♂️")

        // onPause,onStop에서 이 요청만 정확히 취소할 수 있도록 새 취소 토큰을 생성하여 보관
        val cancellationSource = CancellationTokenSource()
        locationCancellationSource = cancellationSource

        // 요원에게 가장 정확한(HIGH_ACCURACY) 지금 내 위치를 찾아오라고 명령
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationSource.token
        )
            .addOnSuccessListener { location ->
                // Activity가 가려졌거나 종료 중이면 결과가 와도 화면과 다음 네트워크 작업을 변경하지 않음
                if (!canHandleLocationResult || isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }

                if (location == null) {
                    showError("위치를 못 찾았어요. 핸드폰 GPS가 켜져 있나요? 📡")
                    return@addOnSuccessListener
                }

                // 위치찾기 성공, 위도와 경도를 따로 담아둠
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
                 * 먼저 한국 안의 좌표인지 확인
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
                 * 대략적인 위치만 허용하면 오차가 수 km까지 커질 수 있음
                 * 반경 3km 검색에서는 정확한 위치 권한이 필요
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
            .addOnFailureListener { error ->
                // onPause/onStop에서 의도적으로 취소한 경우에는 사용자에게 실패 메시지를 보여주지 않음
                if (!canHandleLocationResult || error is CancellationException) {
                    return@addOnFailureListener
                }
                showError("위치 찾기 실패! 권한과 GPS를 확인해 주세요.")
            }
            .addOnCompleteListener { //위치 찾기가 끝나면(성공,실패,취소 상관없이) 실행될 코드
                isLocationRequestInProgress = false
                if (locationCancellationSource === cancellationSource) {
                    locationCancellationSource = null
                }
            }
    }

    // 통신 본부(Retrofit)를 통해 카카오 서버에 데이터 택배 주문하기!
    private fun loadHospitals(latitude: Double, longitude: Double) {

        // 새로운 검색을 시작하기 전에 이전 검색 Job이 남아 있다면 취소
        hospitalSearchJob?.cancel()

        // 배달부(코루틴) 출발! (네트워크 통신은 백그라운드에서 해야 안 멈춰요)
        hospitalSearchJob = lifecycleScope.launch {
            showLoading("카카오 본사에서 소아과 목록 택배가 오고 있어요! 📦")

            try {
                // 카카오 본사 문을 여는 마법의 열쇠(API KEY)
                val kakaoKey = "KakaoAK 1fe8888145997cf462b7ba8feed50227"

                // Retrofit 통신 본부 안의 카카오 부서(kakaoService)에 전화를 걸어 소아청소년과를 주문
                val hospitalData = RetrofitClient.kakaoService.searchPlaces(
                    apiKey = kakaoKey,
                    query = "소아청소년과",
                    latitude = latitude,
                    longitude = longitude,
                    radius = SEARCH_RADIUS_METERS,
                    sort = "distance"
                )

                // Activity가 종료 중이면 택배 버리고 퇴근
                if (!canHandleLocationResult || isFinishing || isDestroyed) return@launch

                // 도착한 택배 상자에서 병원 리스트만 꺼냄
                /*
                 * 서버에 3km 반경을 요청했지만 화면에 넣기 전에도 한 번 더 확인
                 * 이렇게 하면 거리 정보가 없거나 3km를 넘는 장소는 목록에 들어오지 않음
                 */
                val placeList = hospitalData.documents.filter { hospital ->
                    val distanceInMeters = hospital.distance.toIntOrNull()
                    distanceInMeters != null &&
                        distanceInMeters <= SEARCH_RADIUS_METERS
                }

                // 진열 직원(어댑터)에게 "이 물건들로 싹 다시 진열해 줘!" 라고 넘겨요.
                hospitalAdapter.updateData(placeList)
                hasLoadedHospitals = true

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
                // 화면이 가려진 뒤 도착한 네트워크 오류라면 UI 안내를 띄우지 않음
                if (!canHandleLocationResult) return@launch

                // 🚨 카카오 본사에서 입구컷 당함 (열쇠가 틀렸거나, 권한이 없거나!)
                val errorMessage = when (e.code()) {
                    401 -> "열쇠(API 키)가 틀렸대요! 카카오 열쇠를 다시 확인해 주세요."
                    403 -> "카카오 본사에서 우리 앱을 차단했어요. (설정 확인 필요)"
                    else -> "알 수 없는 이유로 카카오 본사에서 쫓겨났어요. (오류: ${e.code()})"
                }
                showError(errorMessage)
            } catch (e: Exception) {
                // onPause/onStop 이후에는 숨겨진 화면을 오류 상태로 바꾸지 않음
                if (!canHandleLocationResult) return@launch

                // 🚧 가는 길에 다리가 끊김 (인터넷 연결 끊김 등 배달 사고)
                showError("배달 사고 발생! 인터넷이 잘 터지나 확인해 보세요 📶")
            }
        }
    }

    // onPause와 onStop에서 공통으로 호출하여 위치 요청과 병원 검색 작업을 중단
    private fun cancelLocationAndSearchWork() {
        locationCancellationSource?.cancel() //GPS 찾으런 간 요원에게 취소 무전치기
        locationCancellationSource = null //한번 취소무전 친 무전기 버리기 1회용
        isLocationRequestInProgress = false

        hospitalSearchJob?.cancel() //병원 목록가저오기 취소
        hospitalSearchJob = null //코루틴 작업 메니저객체 버리기
    }

    /*
     * 카카오 로컬 검색은 국내 장소 검색용이므로 좌표가 한국 영역인지 확인
     * 아래 범위는 대한민국을 감싸는 위도·경도 범위
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
    // 사용자가 집에 들어와 집의 전경이 보이는 시점
    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - 위치 사용 가능 여부 확인")

        // Activity가 보이기 시작할 때 GPS장치가 꺼져 있으면 위치 요청 전에 사용자에게 먼저 안내
        if (!isLocationAvailable()) {
            hasLoadedHospitals = false
            showError("위치를 사용할 수 없어요. 핸드폰 GPS를 켜주세요. 📡")
        }
    }

    override fun onResume() {
        super.onResume()
        canHandleLocationResult = true // 사용자와 상호작용 가능
        logLifecycle("onResume - 위치 권한과 GPS 상태 다시 확인")

        // 권한 또는 위치 설정 화면에서 돌아온 경우 변경된 상태를 다시 검사하고 필요하면 병원 검색 시작
        checkLocationPermission()
    }

    override fun onPause() {
        canHandleLocationResult = false
        cancelLocationAndSearchWork()
        logLifecycle("onPause - 현재 위치 요청과 병원 검색 작업 중단")
        super.onPause()
    }

    override fun onStop() {
        // onPause에서 이미 정리했더라도 완전히 가려지는 시점에 한 번 더 안전하게 정리
        cancelLocationAndSearchWork()
        logLifecycle("onStop - 위치 관련 진행 작업 정리 확인")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart")
    }

    override fun onDestroy() {
        cancelLocationAndSearchWork()
        logLifecycle("onDestroy - 위치 작업과 RecyclerView 참조 정리")
        super.onDestroy()
    }
}
