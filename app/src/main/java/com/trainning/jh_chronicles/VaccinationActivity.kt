package com.trainning.jh_chronicles

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.trainning.jh_chronicles.databinding.ActivityVaccinationBinding
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Calendar

class VaccinationActivity : AppCompatActivity() {

    companion object {
        // 시스템이 Activity를 다시 만들 때 사용자가 선택한 생년월일을 복원하기 위한 Bundle key
        private const val STATE_SELECTED_BIRTH_DATE = "state_vaccination_selected_birth_date"
    }

    private lateinit var binding: ActivityVaccinationBinding

    private lateinit var vaccineAdapter: AdapterVaccine
    private val vaccineList = mutableListOf<VaccineData>() // 앱내 저장된 백신데이터

    // 현재 로그인 사용자의 예방접종 저장 경로입니다.
    private lateinit var userVaccineRef: DatabaseReference

    // 화면이 사라질 때 Firebase 리스너를 제거하기 위해 보관합니다.
    private var vaccineValueListener: ValueEventListener? = null

    // 사용자가 입력한 아기 생년월일입니다.
    private var birthDate: LocalDate? = null

    /*
     * 사용자가 요청한 핵심 변수입니다.
     * 오늘까지 아기가 태어난 지 며칠인지 저장합니다.
     * 생년월일을 아직 입력하지 않았다면 null입니다.
     */
    private var daysSinceBirth: Int? = null

    // Fragment의 onCreateView 대신 Activity는 onCreate에서 화면을 생성한다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - RecyclerView, 생년월일 버튼과 Firebase 경로 초기화")

        binding = ActivityVaccinationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면 회전 전에 선택한 생년월일이 있다면 Firebase 결과가 도착하기 전에도 먼저 복원
        birthDate = savedInstanceState
            ?.getString(STATE_SELECTED_BIRTH_DATE)
            ?.let { savedDate ->
                try {
                    LocalDate.parse(savedDate)
                } catch (_: DateTimeParseException) {
                    null
                }
            }

        setupNavigationButtons() //각 액티비티 이동 지정 메서드
        setupRecyclerView() //리사이클러뷰, 레이아웃메니저 연결, 앱내 저장된 백신데이터 화면에 표시
        setupBirthDateButton() //생년월일 입력버튼을 눌렀을때 생일을 파이어베이스에 저장 및 백신데이터 저장

        // Bundle에서 생년월일을 복원했다면 현재 날짜 기준 D-Day도 함께 다시 계산
        recalculateDays()
        refreshVaccineList()

        val currentUser = Firebase.auth.currentUser

        if (currentUser == null) {
            binding.babyAgeText.text =
                "로그인 정보를 찾지 못해 접종 기록을 불러올 수 없습니다."
            binding.babyBirthDay.isEnabled = false
            return
        }

        userVaccineRef = Firebase.database
            .getReference("vaccine_entries")
            .child(currentUser.uid)
    }

    /*
     * RecyclerView와 체크박스 저장 이벤트를 연결합니다.
     */
    private fun setupRecyclerView() {
        vaccineAdapter = AdapterVaccine { vaccine, isChecked ->
            saveCompletion(vaccine, isChecked)
        }

        binding.vaccineRc.layoutManager =
            LinearLayoutManager(this)
        binding.vaccineRc.adapter = vaccineAdapter

        // Firebase를 읽기 전에도 기본 접종 목록을 먼저 보여줍니다.
        vaccineList.clear()
        vaccineList.addAll(VaccineData.standardSchedule())
        refreshVaccineList()
    }

    /*
     * Firebase 구조
     *
     * vaccine_entries
     *   └─ 사용자 uid
     *       ├─ birthDate: "2026-01-15"
     *       ├─ daysSinceBirth: 196
     *       └─ vaccines
     *           ├─ hepb_1
     *           │   ├─ name: "B형간염 1차"
     *           │   ├─ dDay: 0
     *           │   └─ complete: true
     *           └─ pcv_1 ...
     */

    //백신데이터가 업데이트 되면 저장소에가서 최신 데이터를 가지고와 어뎁터에게 넘겨서 화면에 띄우게 해주는 메서드
    // 단 파이어베이스 데이터를 가저올때 기존 앱내 백신데이터와 비교를 해서 기존 앱에 추가된 백신이 있다면 그것도 가져옴
    private fun observeVaccineData() {
        // onStart가 다시 호출돼도 같은 Firebase 리스너가 중복 등록되지 않도록 확인
        if (vaccineValueListener != null || !::userVaccineRef.isInitialized) return

        //파이어베이스 백신저장소에 파견할 리스너 생성
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loadBirthDate(snapshot)
                // 파이어베이스에서 가져온 백신 데이터
                val savedById = snapshot
                    .child("vaccines")
                    .children
                    .mapNotNull { child ->
                        child.getValue(VaccineData::class.java) //데이터베이스에 있는 json파일을 VaccineData::class로 파싱
                    }
                    .associateBy { it.id } // 파싱한 데이터를 id별로 꼬리표를 단다음 서랍에 넣음

                // VaccineData.standardSchedule() 함수에 있는 모든 접종데이터카드들을 map이라는 변환 터널에 넣겠다 그 한장의 데이터카드이름이 standard
                // mergedList는 앱내 새로 추가된 백신일정표를 가져온 그릇
                // 앱내 백신일정표와 파이어베이스에서 가져온 기록을 비교하여 최신 상태로 합칩니다.
                // 기존 일정표에 새로운 접종이 추가될경우 그 접종값을 포함시켜 어뎁터에 알려주기 위함
                val mergedList = VaccineData.standardSchedule().map { standard ->
                    val saved = savedById[standard.id] // Firebase에 저장된 데이터 중 standard.id와 같은 백신을 찾는다. 저장된 항목이 없으면 saved는 null이다.
                    standard.copy(
                        complete = saved?.complete ?: false, // 저장된 기록이 있으면 그 값을, 없으면 미완료(false)를 사용
                        dDay = saved?.dDay ?: standard.dDay // 저장된 dDay가 있으면 사용하고 없으면 기본값을 사용
                    )
                }
                vaccineList.clear() // 기존 리스트를 비우고
                vaccineList.addAll(mergedList) // 합쳐진 최신 리스트를 추가합니다.

                recalculateDays() // 생후 일수에 따른 D-Day 재계산
                refreshVaccineList() // 앱내 백신데이터를 재정렬
                saveMissingScheduleItems(snapshot) // 앱내 백신데이터에는 있는데 파이어베이스에 없는 데이터를 파이어베이스에 추가
            }

            override fun onCancelled(error: DatabaseError) {
                if (isFinishing || isDestroyed) return

                binding.babyAgeText.text =
                    "접종 기록을 불러오지 못했습니다."

                Toast.makeText(
                    this@VaccinationActivity,
                    "접종 기록 불러오기 실패: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        vaccineValueListener = listener
        userVaccineRef.addValueEventListener(listener)
    }

    // onStart에서 연결한 Firebase 리스너를 화면이 완전히 가려지는 onStop에서 제거
    private fun stopObservingVaccineData() {
        vaccineValueListener?.let { listener ->
            if (::userVaccineRef.isInitialized) {
                userVaccineRef.removeEventListener(listener)
            }
        }
        vaccineValueListener = null
    }

    // 파이어베이스에 저장된 생년월일을 읽습니다 달력의 날짜형태로 가저옴
    private fun loadBirthDate(snapshot: DataSnapshot) {
        val savedBirthDate =
            snapshot.child("birthDate").getValue(String::class.java)

        birthDate = try {
            savedBirthDate?.let { LocalDate.parse(it) }
        } catch (_: DateTimeParseException) {
            null
        }

        if (birthDate?.isAfter(LocalDate.now()) == true) {
            birthDate = null
        }
    }

    /*
     * 기존 사용자의 Firebase에 없는 새 접종 항목만 추가합니다.
     * 이미 저장된 체크 상태는 덮어쓰지 않습니다.
     */
    private fun saveMissingScheduleItems(snapshot: DataSnapshot) {
        val updates = mutableMapOf<String, Any>() // 파이어베이스에 없는 데이터를 key, value형태로 담아두기 위한 그릇
        val savedVaccines = snapshot.child("vaccines") //현재 저장된 파이어베이스 데이터를 담아두는 그릇

        vaccineList.forEach { vaccine -> // 앱에 저장된 백신데이터를 하나씩 가지고 옴
            // Firebase에 아직 없는 접종 항목만 새로 저장합니다.
            if (!savedVaccines.child(vaccine.id).exists()) { // 파이어베이스에 해당 백신 ID가 없으면
                updates["vaccines/${vaccine.id}"] = vaccine //updates그릇에 key/value형태로 저장
            }
        }

        val savedDaysSinceBirth = //파이어베이스에 저장된 과거 생후 일수
            snapshot.child("daysSinceBirth").getValue(Long::class.java)?.toInt() // 파이어베이스에서 정수데이터를 가저올때 Long으로 파싱해야함
        daysSinceBirth?.let { currentDays -> //현재 계산된 생후일수 daysSinceBirth(currentDays)
            if (savedDaysSinceBirth != currentDays) {
                updates["daysSinceBirth"] = currentDays
            }
        }

        if (updates.isNotEmpty()) {
            userVaccineRef.updateChildren(updates)
        }
    }

    /*
     * 생년월일 선택 버튼을 준비합니다.
     */
    private fun setupBirthDateButton() {
        binding.babyBirthDay.setOnClickListener {
            showBirthDatePicker()
        }
    }

    // 생년월일 입력버튼을 누를시 생년월일을 지정할수있는 달력 호출하여 생년월일 및 백신데이터 파이어베이스에 저장
    private fun showBirthDatePicker() {
        val initialDate = birthDate ?: LocalDate.now() //아이 생일이 없다면 현재달력을 보여준다

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate =
                    LocalDate.of(year, month + 1, dayOfMonth)

                if (selectedDate.isAfter(LocalDate.now())) { //오늘 날짜보다 미래날짜 선택했을시 예외처리
                    Toast.makeText(
                        this,
                        "태어난 날짜는 오늘보다 미래일 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@DatePickerDialog
                }

                birthDate = selectedDate
                recalculateDays() //생일을 알았으니 생후 일수와 백신 디데이를 계산하여 업데이트
                refreshVaccineList() // 디데이계산이 됐으니 리사이클러뷰 재 정렬
                saveBirthDateAndSchedule() // 생일과 생후날짜, 백신데이터를 파이어베이스에 저장
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        // 미래 날짜는 달력에서 선택하지 못하게 막습니다.
        dialog.datePicker.maxDate = Calendar.getInstance().timeInMillis
        dialog.show()
    }

    //생후 일수와 각 백신의 dDay를 다시 계산해서 백신데이터를 업데이트하는 메서드
    private fun recalculateDays() {
        val currentBirthDate = birthDate // 아기 생일년월데이터를 저장

        if (currentBirthDate == null) {
            daysSinceBirth = null //생일입력이 되지않았으면 총생후일수도 없으므로 null
            updateBabyAgeText()
            return
        }
        // 아기 생일과 현재 달력날짜 사이의 gap을 day단위 int로 반환해서 저장
        daysSinceBirth = ChronoUnit.DAYS.between(
            currentBirthDate,
            LocalDate.now()
        ).toInt()

        vaccineList.forEach { vaccine ->
            vaccine.startDay =
                vaccine.calculateStartDayFromBirth(currentBirthDate)
            vaccine.dDay =
                vaccine.calculateDayFromBirth(currentBirthDate)
        }

        updateBabyAgeText()
    }

    /*
     * 미접종 항목을 위에, 완료 항목을 아래에 표시하도록 정리해주는 메서드
     * 같은 그룹에서는 startDay - daysSinceBirth 값이 작은 순서,
     * 즉 접종 시작일이 먼저 다가오는 접종이 위로 옴
     */
    private fun refreshVaccineList() {
        val currentDaysSinceBirth = daysSinceBirth // 생후 며칠됐는지 값
        // 생일 입력 전에는 맞아야할 날짜가 빠른순으로 정렬
        val sortedList = if (currentDaysSinceBirth == null) {
            vaccineList.sortedWith(
                compareBy<VaccineData> { it.complete }
                    .thenBy { it.targetMonths }
                    .thenBy { it.extraDays }
                    .thenBy { it.name }
            )
        } else {
            // 생후 날짜를 기준으로 접종 시작일이 가까운 순서대로 정렬
            vaccineList.sortedWith(
                compareBy<VaccineData> { it.complete }
                    .thenBy { it.startDay - currentDaysSinceBirth }
                    .thenBy { it.name }
            )
        }
        // 정렬한 리스트를 어뎁터에게 전달
        vaccineAdapter.submitList(
            newList = sortedList,
            newDaysSinceBirth = currentDaysSinceBirth
        )
    }

    //생년월일과 생후며칠됐는지 일수, 이걸 토대로 계산된 전체 접종 목록을 한 번에 Firebase에 저장
    private fun saveBirthDateAndSchedule() {
        val currentBirthDate = birthDate ?: return
        val currentDaysSinceBirth = daysSinceBirth ?: return

        val updates = mutableMapOf<String, Any>( //생일, 생후일수를 백신이라는 폴더 아래 모든 백신데이터를 담는 상자임 (키, 벨류) 형태로된
            "birthDate" to currentBirthDate.toString(),
            "daysSinceBirth" to currentDaysSinceBirth
        )

        vaccineList.forEach { vaccine -> // 벡신데이터 하나씩 가져와서 updates상자에 vaccines이라는 폴더를 만들고 그 안에 백신id(key)백신데이터(벨류)를 넣음
            updates["vaccines/${vaccine.id}"] = vaccine
        }

        userVaccineRef.updateChildren(updates) //파이어베이스저장소에 가서 updates상자안의 내용만 추가시켜라
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                Toast.makeText(
                    this,
                    "생년월일과 접종 일정이 저장되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { error ->
                if (isFinishing || isDestroyed) return@addOnFailureListener

                Toast.makeText(
                    this,
                    "접종 일정 저장 실패: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    /*
     * 체크박스 한 개가 바뀌면 해당 백신 항목을 Firebase에 저장하는 메서드
     */
    private fun saveCompletion(vaccine: VaccineData, isChecked: Boolean) {
        vaccine.complete = isChecked
        refreshVaccineList() // 체크박스에 체크를 하는순간 바로 체크한항목을 밑으로 재정렬

        userVaccineRef
            .child("vaccines")
            .child(vaccine.id)
            .setValue(vaccine)
            .addOnFailureListener { error ->
                if (isFinishing || isDestroyed) return@addOnFailureListener

                Toast.makeText(
                    this,
                    "접종 여부 저장 실패: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    //생년월일을 화면에 표시하고 오늘 생후 며칠됐는지 업데이트해서 화면에 뿌리는 메서드
    private fun updateBabyAgeText() {
        val currentBirthDate = birthDate
        val currentDaysSinceBirth = daysSinceBirth

        if (currentBirthDate == null || currentDaysSinceBirth == null) {
            binding.babyBirthDay.text = "아이 생년월일 입력"
            binding.babyAgeText.text =
                "생년월일을 입력하면 접종 D-day가 계산됩니다."
            return
        }
        binding.babyBirthDay.text =
            "${currentBirthDate.year}년 " +
                "${currentBirthDate.monthValue}월 " +
                "${currentBirthDate.dayOfMonth}일"

        binding.babyAgeText.text =
            "오늘은 생후 ${currentDaysSinceBirth}일입니다. " +
                "미접종 중 가장 먼저 맞아야 할 접종부터 표시합니다."
    }

    private fun setupNavigationButtons() {
        binding.recordBtn.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }

        binding.weatherBtn.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }

        binding.mapBtn.setOnClickListener {
            startActivity(Intent(this, HospitalActivity::class.java))
        }

        binding.diaryBtn.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - Firebase 접종 리스너 연결")

        // Activity가 사용자에게 보이는 동안에만 Firebase 접종 변경사항을 관찰
        observeVaccineData()
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - LocalDate.now() 기준 D-Day 재계산")

        /*
         * 다른 Activity나 캘린더 앱에 머무는 동안 날짜가 바뀔 수 있으므로
         * 화면이 다시 전면에 나타날 때 현재 날짜 기준 생후 일수와 모든 D-Day를 다시 계산합니다.
         */
        recalculateDays()
        refreshVaccineList()
    }

    override fun onPause() {
        logLifecycle("onPause")
        super.onPause()
    }

    override fun onStop() {
        // 화면이 완전히 가려진 동안에는 Firebase 변경 콜백이 필요하지 않으므로 리스너 제거
        stopObservingVaccineData()
        logLifecycle("onStop - Firebase 접종 리스너 제거")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart")
    }

    /*
     * 화면 회전처럼 시스템이 Activity를 다시 만들 때 선택한 생년월일을 유지하기 위한 Bundle 저장입니다.
     * 실제 영구 저장은 Firebase가 담당하고, Bundle은 Firebase 결과가 다시 도착하기 전의 UI 상태를 복원합니다.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_BIRTH_DATE, birthDate?.toString())
        logLifecycle("onSaveInstanceState - 선택한 생년월일 저장")
        super.onSaveInstanceState(outState)
    }

    // Fragment의 onDestroyView 대신 Activity에서는 onDestroy에서 리스너를 제거한다.
    override fun onDestroy() {
        // 보통 onStop에서 제거되지만 예외적인 종료에도 리스너가 남지 않도록 한 번 더 정리
        stopObservingVaccineData()
        binding.vaccineRc.adapter = null
        logLifecycle("onDestroy - Firebase 리스너와 RecyclerView 참조 정리")
        super.onDestroy()
    }
}
