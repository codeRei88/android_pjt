package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.trainning.jh_chronicles.databinding.ActivityRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 스태틱 상수 : 통계 계산이 정상적으로 끝난 택배를 구분하는 송장 번호다.
private const val MSG_STATISTICS_COMPLETE = 1

// 스태틱 상수 : 통계 계산 중 오류가 발생한 택배를 구분하는 송장 번호다.
private const val MSG_STATISTICS_FAILED = 2

// 스태틱 상수 : 평균 계산이 정상적으로 끝난 택배를 구별하는 번호
private const val MSG_AVG_STATISTICS_COMPLETE = 3

class RecordActivity : AppCompatActivity() {

    companion object {
        // 시스템이 Activity를 다시 만들 때 통계 영역의 표시 상태를 복원하기 위한 Bundle key
        private const val STATE_STATISTICS_EXPANDED = "state_record_statistics_expanded"

        // 마지막으로 통계를 계산한 날짜를 화면 회전 전후로 기억하기 위한 Bundle key
        private const val STATE_SELECTED_STATISTICS_DATE = "state_record_selected_statistics_date"
    }

    private lateinit var binding: ActivityRecordBinding

    // 서빙 알바생 고용 (헨들러)
    private lateinit var mainHandler: Handler

    // Firebase에 등록한 리스너를 다른 화면으로 넘어갈때 꺼주기 위해 보관
    private var recordListener: ValueEventListener? = null

    // Firebase 리스너가 연결된 데이터베이스 경로를 보관한다.
    private var recordReference: DatabaseReference? = null

    // onCreate에서 연결한 Firebase 데이터베이스와 로그인 사용자 경로를 다른 생명주기에서도 사용하기 위한 변수
    private lateinit var database: FirebaseDatabase
    private lateinit var myRef: DatabaseReference
    private lateinit var uid: String

    // RecyclerView에 전달할 기록과 어댑터를 onCreate 밖의 Firebase 리스너에서도 사용하기 위한 변수
    private val dataList = mutableListOf<RecordData>()
    private lateinit var recordAdapter: AdapterRecord

    // 날짜가 바뀌었을 때 onResume에서 통계를 다시 계산할 수 있도록 최근 Firebase 기록을 보관
    private var latestEventList: List<RecordData.EventData> = emptyList()

    // 현재 통계가 펼쳐져 있는지와 어떤 날짜를 기준으로 계산했는지 기억하는 UI 상태 변수
    private var isStatisticsExpanded = true
    private var selectedStatisticsDate = ""

    /*
     * RecordEditorActivity를 실행한 뒤 작성·수정·삭제 결과를 받기 위한 Activity Result 등록입니다.
     * StartActivityForResult Contract는 Intent를 입력받아 Activity를 실행하고 ActivityResult를 반환합니다.
     * 뒤의 람다는 EditorActivity가 setResult() 후 finish() 했을 때 실행되는 사후 처리 Callback입니다.
     */
    private val recordEditorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->

        // 취소 버튼이나 시스템 뒤로가기는 RESULT_OK가 아니므로 Firebase를 변경하지 않음
        if (activityResult.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }

        // RecordEditorActivity가 결과 데이터를 넣어 돌려준 Intent를 꺼냄
        val resultIntent = activityResult.data ?: return@registerForActivityResult

        // 같은 EditorActivity가 저장과 삭제 결과를 모두 보내므로 action 값으로 사후 처리를 구분함
        when (resultIntent.getStringExtra(RecordEditorActivity.EXTRA_RESULT_ACTION)) {
            RecordEditorActivity.RESULT_ACTION_SAVE -> saveRecordResult(resultIntent)
            RecordEditorActivity.RESULT_ACTION_DELETE -> deleteRecordResult(resultIntent)
        }
    }

    // 기록데이터에서 숫자만 추출하는 메서드
    private fun extractNumber(value: String): Int {

        // 전달받은 문자열의 문자들을 하나씩 떼어 괄호안 코드를 실행한다.
        val numberText = value.filter { character -> // 하나씩 땐 문자

            // 현재 문자가 숫자인지 확인 후 숫자만 남긴 값을 numberText에 저장
            character.isDigit()
        }
        // 숫자로 변환할 수 있으면 Int를 반환한다.
        // 숫자가 없다면 앱이 종료되지 않도록 null이 아닌 0을 반환한다.
        return numberText.toIntOrNull() ?: 0
    }

    // 전체 기록을 받아 전달받은 날짜의 육아 통계를 계산한다.
    // 이 함수는 나중에 백그라운드 Thread에서 실행한다.
    private fun calculateTodaySummary(
        events: List<RecordData.EventData>,
        statisticsDate: String
    ): DailySummary {

        // 오늘 우유 총량을 누적할 그릇이다.
        var totalMilk = 0
        // 오늘 이유식 총량을 누적할 그릇이다.
        var totalMeal = 0
        // 오늘 수면 총시간을 누적할 그릇이다.
        var totalSleepMinutes = 0
        // 오늘 배변 횟수를 누적할 그릇이다.
        var poopCount = 0
        // 전체 육아 기록을 하나씩 꺼내 확인한다.
        for (event in events) {
            // 기록 날짜가 통계를 계산할 날짜와 다르면 통계에 포함하지 않는다.
            if (event.date != statisticsDate) {
                // 현재 기록을 건너뛰고 다음 기록으로 이동한다.
                continue
            }
            // 오늘 기록의 제목을 확인하여 종류를 구분한다.
            // 자바의 if-else if 와 같은 기능 --> 인자없는 when
            when {
                // 우유 기록인 경우다.
                event.title == "우유" -> {
                    // "120(ml)"에서 120을 추출하여 우유 총량에 더한다.
                    totalMilk += extractNumber(event.eventDetail)
                }
                // 이유식 기록인 경우다.
                event.title == "맘마" -> {
                    // 이유식 양을 추출하여 이유식 총량에 더한다.
                    totalMeal += extractNumber(event.eventDetail)
                }

                // "수면(낮잠)"과 "수면(밤잠)"을 모두 찾는다.
                event.title.startsWith("수면") -> {

                    // 수면시간을 추출하여 전체 수면시간에 더한다.
                    totalSleepMinutes += extractNumber(event.eventDetail)
                }

                // 배변 기록인 경우다.
                event.title == "배변" -> {
                    // 배변 기록 한 개당 횟수를 1 증가시킨다.
                    poopCount += 1
                }
            }
        }
        // 계산 결과를 하나의 DailySummary 물건으로 포장한다.
        return DailySummary(
            // 계산된 우유 총량을 담는다.
            totalMilk = totalMilk,

            // 계산된 이유식 총량을 담는다.
            totalMeal = totalMeal,

            // 계산된 수면 총시간을 담는다.
            totalSleepMinutes = totalSleepMinutes,

            // 계산된 배변 횟수를 담는다.
            poopCount = poopCount
        )
    }

    // Fragment의 onCreateView 대신 Activity는 onCreate에서 화면을 생성한다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - RecyclerView, Handler와 버튼을 최초로 준비")

        // 화면 생성(식당 인테리어 끝)
        binding = ActivityRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*
         * 화면 회전처럼 Activity가 다시 생성된 경우 Bundle에 저장해 둔 UI 상태를 복원합니다.
         * 현재 화면에는 별도의 날짜 선택기가 없으므로 selectedStatisticsDate는 오늘 통계의 기준 날짜입니다.
         */
        isStatisticsExpanded =
            savedInstanceState?.getBoolean(STATE_STATISTICS_EXPANDED) ?: true
        selectedStatisticsDate =
            savedInstanceState?.getString(STATE_SELECTED_STATISTICS_DATE)
                ?: getCurrentRecordDate()
        binding.statisticsLayout.visibility =
            if (isStatisticsExpanded) View.VISIBLE else View.GONE

        // 알바생인 헨들러가 백그라운드 쓰레드에서 받은 메세지를 가지고 카운터(화면)에서 할일을 학습 시킴(Handler.CallBack인터페이스)
        mainHandler = Handler(Looper.getMainLooper()) { message ->

            // Activity가 이미 종료 중이라면 UI를 변경하지 않는다.
            if (isFinishing || isDestroyed) {
                true
            } else {
                // 배달물건이 어떤 종류인지 물품번호인 message.what을 확인한다.
                when (message.what) {

                    // 물품 번호가 1인 통계 계산 완료인 경우다.
                    MSG_STATISTICS_COMPLETE -> {

                        // 통계 계산이 끝났으므로 로딩 표시를 숨긴다.
                        binding.statisticsProgress.visibility = View.GONE

                        // 택배의 obj에서 DailySummary 물건을 꺼낸다.
                        val summary = message.obj as? DailySummary //DailySummary로 형변환하고 안되면 null반환해라

                        // 내용물이 정상적인 DailySummary일 때만 화면에 표시한다.
                        if (summary != null) {

                            // 우유 총량을 TextView에 표시한다.
                            binding.totalMilkText.text =
                                "오늘 우유: ${summary.totalMilk}ml"

                            // 이유식 총량을 TextView에 표시한다.
                            binding.totalMealText.text =
                                "오늘 이유식: ${summary.totalMeal}ml"

                            // 수면 총시간을 TextView에 표시한다.
                            binding.totalSleepText.text =
                                "오늘 수면: ${summary.totalSleepMinutes}분"

                            // 배변 횟수를 TextView에 표시한다.
                            binding.poopCountText.text =
                                "오늘 배변: ${summary.poopCount}회"
                        }
                    }

                    // 물품 번호가 3인 평균 계산 완료인 경우다.
                    MSG_AVG_STATISTICS_COMPLETE -> {
                        binding.statisticsProgress.visibility = View.GONE

                        //메세지 택배 상자를 까서 AvgSummary데이터를 꺼내 변수그릇에 담는다.
                        val avg = message.obj as? AvgSummary
                        if (avg != null) {
                            binding.avgMilkText.text = "평균 우유량 : ${avg.avgMilk}ml"
                            binding.avgMealText.text = "평균 이유식 : ${avg.avgMeal}ml"
                            binding.avgSleepText.text = "평균 수면 : ${avg.avgSleepMinutes / 60}시간 ${avg.avgSleepMinutes % 60}분"
                            binding.avgPoopCountText.text = "평균 배변 : ${String.format("%.1f", avg.avgPoopCount)}회"
                            binding.calculationDaysText.text = "계산일 수 : ${avg.countDay}일"
                        }
                    }
                    // 송장 번호가 통계 계산 실패인 경우다.
                    MSG_STATISTICS_FAILED -> {

                        // 계산 작업이 끝났으므로 로딩 표시를 숨긴다.
                        binding.statisticsProgress.visibility = View.GONE

                        // 택배의 obj에서 오류 문장을 꺼낸다. 문장이 아니거나 비어있으면 null을 뱉고 null일경우 "통계 계산에 실패했습니다." 출력
                        val errorMessage =
                            message.obj as? String ?: "통계 계산에 실패했습니다."

                        // 오류 문장을 사용자에게 표시한다.
                        Toast.makeText(
                            this,
                            errorMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                // Handler가 해당 Message를 처리했으므로 true반환(Looper에게) 후 메모리에서 삭제 진행
                true
            }
        }

        // 파이어베이스 데이터베이스 연결
        database = Firebase.database // 파이어베이스 저장공간을 코드로 가져오기
        uid = Firebase.auth.currentUser!!.uid // 파이어베이스에 접속한 유저의 uid를 가져다 저장함
        myRef = database.getReference("record_entries").child(uid) // 가져온 저장공간에 폴더 이름설정 및 생성 해주기

        //리사이클러뷰를 어뎁터와 레이아웃 메니저와 연결
        recordAdapter = AdapterRecord(dataList)// 어뎁터에 쥐어줄 데이터를 리스트화해서 변수그룻에 담기
        binding.recordRc.adapter = recordAdapter
        binding.recordRc.layoutManager = LinearLayoutManager(this)

        //리사이클러뷰 클릭 이벤트 처리
        recordAdapter.itemClick = object : AdapterRecord.ItemClick {
            override fun onClick(view: View, position: Int) {
                val clickedRV = dataList[position]
                if (clickedRV is RecordData.EventData) { // clickedRV가 RecordData.EventData 객체 타입인지 확인
                    // 기존 수정 Dialog 대신 클릭한 기록을 Extra에 담아 수정 전용 Activity 실행
                    openRecordEditorForEdit(clickedRV)
                }
            }
        }

        //평균계산 버튼 누를경우 지난날 모든 데이터 평균을 계산 후 헨들러에게 전달
        binding.avgBtn.setOnClickListener {
            calculateAverageStatistics()
        }

        /*
         * 기존 showRecordDialog() 호출 대신 명시적 Intent로 RecordEditorActivity를 실행합니다.
         * event type, 입력 힌트와 단위를 Extra로 전달하므로 하나의 EditorActivity가 네 기록을 모두 처리합니다.
         */
        binding.milk.setOnClickListener {
            openRecordEditorForCreate("우유", "수유량(ml)", "(ml)")
        }
        binding.meal.setOnClickListener {
            openRecordEditorForCreate("맘마", "이유식 양(ml)", "(ml)")
        }
        binding.sleep.setOnClickListener {
            openRecordEditorForCreate("수면", "수면 시간입력", "(분)")
        }
        binding.poop.setOnClickListener {
            openRecordEditorForCreate("배변", "보통,설사", "")
        }

        // 액티비티 화면 이동 부분은 명시적 Intent 이동으로만 변경
        binding.weatherBtn.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }

        binding.diaryBtn.setOnClickListener {
            startActivity(Intent(this, DiaryActivity::class.java))
        }

        binding.dDayBtn.setOnClickListener {
            startActivity(Intent(this, VaccinationActivity::class.java))
        }

        binding.mapBtn.setOnClickListener {
            startActivity(Intent(this, HospitalActivity::class.java))
        }
    }

    /*
     * 새 기록 작성 화면을 여는 명시적 Intent입니다.
     * 어떤 버튼을 눌렀는지 RecordEditorActivity가 알 수 있도록 종류·힌트·단위를 Extra로 전달합니다.
     */
    private fun openRecordEditorForCreate(
        eventTitle: String,
        inputHint: String,
        unit: String
    ) {
        // 기존 showRecordDialog()처럼 기록 버튼을 누른 순간의 날짜와 시간을 먼저 만듦
        val clickedAt = Date()
        val clickedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(clickedAt)
        val clickedDate = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN).format(clickedAt)

        val editorIntent = Intent(this, RecordEditorActivity::class.java).apply {
            // 같은 편집 Activity가 작성과 수정을 모두 담당하므로 새 기록 작성 모드 전달
            putExtra(RecordEditorActivity.EXTRA_EDITOR_MODE, RecordEditorActivity.MODE_CREATE)

            // 우유·맘마·수면·배변 중 어떤 입력 화면인지 알려주는 실제 값
            putExtra(RecordEditorActivity.EXTRA_EVENT_TYPE, eventTitle)

            // 기존 Dialog의 EditText 안내 문구와 저장 단위를 그대로 전달
            putExtra(RecordEditorActivity.EXTRA_INPUT_HINT, inputHint)
            putExtra(RecordEditorActivity.EXTRA_INPUT_UNIT, unit)

            // EditorActivity에서 입력하는 동안 시간이 지나도 기존 기능처럼 버튼 클릭 시점을 저장하도록 전달
            putExtra(RecordEditorActivity.EXTRA_RECORD_TIME, clickedTime)
            putExtra(RecordEditorActivity.EXTRA_RECORD_DATE, clickedDate)
        }

        // EditorActivity의 setResult() 결과가 필요하므로 startActivity가 아닌 Launcher로 실행
        recordEditorLauncher.launch(editorIntent)
    }

    /*
     * 기존 기록 수정 화면을 여는 명시적 Intent입니다.
     * 기존 Dialog에 넣었던 id, 날짜, 시간, 제목, 상세 내용을 putExtra()로 그대로 전달합니다.
     */
    private fun openRecordEditorForEdit(clickedRV: RecordData.EventData) {
        val editorIntent = Intent(this, RecordEditorActivity::class.java).apply {
            putExtra(RecordEditorActivity.EXTRA_EDITOR_MODE, RecordEditorActivity.MODE_EDIT)
            putExtra(RecordEditorActivity.EXTRA_RECORD_ID, clickedRV.id)
            putExtra(RecordEditorActivity.EXTRA_RECORD_DATE, clickedRV.date)
            putExtra(RecordEditorActivity.EXTRA_RECORD_TIME, clickedRV.time)
            putExtra(RecordEditorActivity.EXTRA_EVENT_TYPE, clickedRV.title)
            putExtra(RecordEditorActivity.EXTRA_RECORD_DETAIL, clickedRV.eventDetail)
        }

        // 수정 또는 삭제 결과를 Activity Result Callback에서 받기 위해 Launcher로 실행
        recordEditorLauncher.launch(editorIntent)
    }

    /*
     * RecordEditorActivity가 RESULT_OK와 함께 돌려준 작성·수정 결과를 Firebase에 반영합니다.
     * id가 없으면 push()로 새 기록을 만들고, id가 있으면 기존 기록 경로를 선택합니다.
     */
    private fun saveRecordResult(resultIntent: Intent) {
        val returnedRecordId =
            resultIntent.getStringExtra(RecordEditorActivity.EXTRA_RECORD_ID).orEmpty()

        val targetRef = if (returnedRecordId.isBlank()) {
            myRef.push()
        } else {
            myRef.child(returnedRecordId)
        }

        val finalRecordId = targetRef.key ?: return

        // 결과 Intent의 값들을 기존 RecordData.EventData 그릇에 다시 담음
        val returnedRecord = RecordData.EventData(
            id = finalRecordId,
            time = resultIntent.getStringExtra(RecordEditorActivity.EXTRA_RECORD_TIME).orEmpty(),
            date = resultIntent.getStringExtra(RecordEditorActivity.EXTRA_RECORD_DATE).orEmpty(),
            eventDetail = resultIntent.getStringExtra(RecordEditorActivity.EXTRA_RECORD_DETAIL).orEmpty(),
            title = resultIntent.getStringExtra(RecordEditorActivity.EXTRA_EVENT_TYPE).orEmpty()
        )

        // 기존 Dialog에서 하던 것과 똑같이 선택한 Firebase 경로에 기록 저장
        targetRef.setValue(returnedRecord)
    }

    // RecordEditorActivity가 돌려준 삭제 결과에서 id를 꺼내 Firebase의 해당 기록만 삭제
    private fun deleteRecordResult(resultIntent: Intent) {
        val recordId =
            resultIntent.getStringExtra(RecordEditorActivity.EXTRA_RECORD_ID).orEmpty()

        if (recordId.isNotBlank()) {
            myRef.child(recordId).removeValue()
        }
    }

    /*
     * Firebase 기록 리스너를 연결하는 함수입니다.
     * onStart에서 호출하여 Activity가 사용자에게 보이는 동안에만 기록 변경을 관찰합니다.
     */
    private fun attachRecordListener() {
        // onStart가 다시 호출돼도 같은 리스너가 중복 등록되지 않도록 확인
        if (recordListener != null) return

        // 데이터베이스에 업데이트사항이 있으면 데이터베이스의 데이터를 하나하나 dataList(어뎁터가 받을)에 추가 시킨다
        // Firebase에서 기록을 받고 어뎁터에게 줄 데이터리스트에 데이터를 추가시킬 리스너 객체를 만든 후 변수에 저장한다, 추후 리스너 해제를 위해
        val listener = object : ValueEventListener {

            // Firebase 기록을 처음 가져오거나 기록이 변경되면 호출된다.
            override fun onDataChange(snapshot: DataSnapshot) {

                // 곧 통계 계산을 시작하므로 메인 화면에서 로딩 표시를 켠다.
                binding.statisticsProgress.visibility = View.VISIBLE

                // RecyclerView에 있던 기존 기록을 제거한다.
                dataList.clear()

                // 통계 계산에 사용할 실제 EventData만 담을 리스트를 만든다.
                val eventList = mutableListOf<RecordData.EventData>()

                // RecyclerView 날짜 헤더를 구분하기 위한 이전 날짜 변수다.
                var previousDate: String? = null

                // Firebase 기록을 최신 순서부터 하나씩 확인한다.
                // 파이어베이스 데이터를 다 가져와서 리스트화 시킨다 그다음에 리버스로 배열을 바꾼다
                for (oneData in snapshot.children.toList().asReversed()) {

                    // Firebase 데이터를 EventData 객체로 변환한다.
                    val eventData = oneData.getValue(
                        RecordData.EventData::class.java
                    )

                    // 변환할 수 없는 데이터라면 다음 기록으로 넘어간다.
                        ?: continue

                    // 날짜 헤더를 제외한 실제 기록을 통계용 리스트에 담는다.
                    eventList.add(eventData)

                    // 현재 기록 날짜가 이전 기록 날짜와 다르면 새 날짜가 시작된 것이다.
                    if (eventData.date != previousDate) {

                        // RecyclerView에 날짜 헤더를 추가한다.
                        dataList.add(
                            RecordData.HeaderData(eventData.date)
                        )

                        // 다음 기록과 비교하기 위해 현재 날짜를 저장한다.
                        previousDate = eventData.date
                    }

                    // RecyclerView에 실제 육아 기록을 추가한다.
                    dataList.add(eventData)
                }

                // 변경된 기록 목록을 RecyclerView에 다시 표시한다.
                recordAdapter.notifyDataSetChanged()

                // 백그라운드 Thread가 안전하게 사용할 수 있도록 리스트를 복사한다.
                // toList() 수정이 안되는 복사본을 만드는것, 원본을 주지 않는이유는 계산 중 데이터가 수정될 경우 에러를 막기 위함.
                latestEventList = eventList.toList()

                // 데이터가 도착한 시점의 오늘 날짜를 기준으로 기존 통계 계산을 실행
                startTodayStatisticsCalculation(latestEventList, getCurrentRecordDate())
            }

            // Firebase 데이터를 가져오지 못했을 때 호출된다.
            override fun onCancelled(error: DatabaseError) {

                // Firebase 오류 내용을 사용자용 문장으로 만든다.
                val errorText = "데이터 로드 실패: ${error.message}"

                // 실패 송장 번호와 오류 문장을 담은 택배를 만든다.
                val failedMessage = mainHandler.obtainMessage(
                    MSG_STATISTICS_FAILED,
                    errorText
                )

                // 실패 택배를 메인 우편 담당자에게 발송한다.
                failedMessage.sendToTarget()
            }
        }

        // onStop에서 정확히 같은 리스너 객체를 제거할 수 있도록 필드에 저장
        recordListener = listener

        // 리스너를 제거할 때 필요한 Firebase 경로를 보관한다.
        recordReference = myRef

        // Firebase 경로에 기록 변경 리스너를 등록한다.
        myRef.addValueEventListener(listener)
    }

    // onStart에서 연결했던 Firebase 리스너를 화면이 완전히 가려지는 onStop에서 제거
    private fun detachRecordListener() {
        recordListener?.let { listener ->
            recordReference?.removeEventListener(listener)
        }
        recordListener = null
        recordReference = null
    }

    /*
     * 오늘 통계 계산과 daily_summaries 저장을 기존 코드 그대로 한곳에 모은 함수입니다.
     * Firebase 변경 시에도 호출하고, 날짜가 바뀐 뒤 onResume될 때도 호출합니다.
     */
    private fun startTodayStatisticsCalculation(
        eventsForCalculation: List<RecordData.EventData>,
        statisticsDate: String
    ) {
        // 현재 어떤 날짜를 기준으로 계산했는지 onResume과 Bundle 저장에서 확인할 수 있도록 기억
        selectedStatisticsDate = statisticsDate
        binding.statisticsProgress.visibility = View.VISIBLE

        // 새로운 백그라운드 통계 작업자를 만든다.
        // 데이터가 완전히 도착했고 리스트에 추가가 됐으므로 그걸 토대로 작업을 하는 쓰레드를 이 위치에 선언한다.
        Thread {

            // 계산 과정에서 발생할 수 있는 오류를 처리한다.
            try {

                // 백그라운드 Thread가 오늘의 통계를 계산한다.
                val summary = calculateTodaySummary(eventsForCalculation, statisticsDate)

                // 2. 카운터로 보내기 전에 데이터베이스(창고)에 먼저 저장하기
                // 오늘 섬머리한 데이터의 날짜를 변수에 저장
                val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).format(Date())

                // 기존 myRef(record_entries)가 아닌, 'daily_summaries'라는 완전히 새로운 폴더를 만듬
                // 기존 record_entries 폴더에 저장하면 섬머리가 저장되면서 리스너가 데이터베이스 갱신으로 방금저장한 값을 또 던저줘 무한루프에 빠짐
                val summaryRef = database.getReference("daily_summaries").child(uid).child(dateKey)

                // 새로 판 창고에 계산된 summary 물건을 쏙 집어넣는다. (무한루프 걱정 없음)
                summaryRef.setValue(summary)

                // 계산끝냈고 데이터베이스에 저장했으니 메인쓰레드에게 줄 택배를 포장한다.
                // 3. 메인 우편 담당자(알바생)에게 택배 상자를 요청하고 내용물을 담는다.
                val completeMessage = mainHandler.obtainMessage(
                    MSG_STATISTICS_COMPLETE,
                    summary
                )
                // 완성된 택배를 target인 mainHandler에게 실제 발송한다.
                completeMessage.sendToTarget()

            } catch (exception: Exception) {

                // 계산 중 발생할 수 있는 오류 내용을 문자열로 만든다.
                val errorText =
                    exception.message ?: "통계 계산 중 오류가 발생했습니다."

                // 실패 송장 번호와 오류 문장을 담은 택배를 만든다.
                val failedMessage = mainHandler.obtainMessage(
                    MSG_STATISTICS_FAILED,
                    errorText
                )
                // 실패 택배도 mainHandler에게 실제 발송한다.
                failedMessage.sendToTarget()
            }

        }.start()
    }

    //평균계산 버튼의 기존 계산 코드를 함수로 옮겨 onCreate에는 버튼 연결 역할만 남김
    private fun calculateAverageStatistics() {
        val avgRef = database.getReference("daily_summaries").child(uid)

        // 평균 계산 시작 시 로딩 표시를 보여준다.
        binding.statisticsProgress.visibility = View.VISIBLE

        avgRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var totalMilkAmount = 0
                var totalMealAmount = 0
                var totalSleepMinutes = 0
                var totalPoopCount = 0
                var dayCount = 0

                // 계산은 백그라운드 쓰레드가 담당
                Thread {

                    try {

                        for (dateSnapshot in snapshot.children) {
                            val dailySummary = dateSnapshot.getValue(DailySummary::class.java) ?: continue
                            totalMilkAmount += dailySummary.totalMilk
                            totalMealAmount += dailySummary.totalMeal
                            totalSleepMinutes += dailySummary.totalSleepMinutes
                            totalPoopCount += dailySummary.poopCount
                            dayCount++
                        }
                        if (dayCount > 0) {
                            val averageMilk = totalMilkAmount / dayCount
                            val averageMeal = totalMealAmount / dayCount
                            val averageSleep = totalSleepMinutes / dayCount
                            val averagePoop = totalPoopCount.toDouble() / dayCount

                            val avgData = AvgSummary(
                                avgMilk = averageMilk,
                                avgMeal = averageMeal,
                                avgSleepMinutes = averageSleep,
                                avgPoopCount = averagePoop,
                                countDay = dayCount
                            )
                            val completeMsg = mainHandler.obtainMessage(MSG_AVG_STATISTICS_COMPLETE, avgData)
                            completeMsg.sendToTarget()
                        }

                    } catch (exception: Exception) {
                        // 계산 중 발생한 오류 내용을 문자열로 만든다.
                        val errorText =
                            exception.message ?: "통계 계산 중 오류가 발생했습니다."

                        // 실패 송장 번호와 오류 문장을 담은 택배를 만든다.
                        val failedMessage = mainHandler.obtainMessage(
                            MSG_STATISTICS_FAILED,
                            errorText
                        )
                        // 실패 택배도 mainHandler에게 실제 발송한다.
                        failedMessage.sendToTarget()
                    }
                }.start()
            }

            override fun onCancelled(error: DatabaseError) {
                // Firebase 오류 내용을 사용자용 문장으로 만든다.
                val errorText = "데이터 로드 실패: ${error.message}"

                // 실패 송장 번호와 오류 문장을 담은 택배를 만든다.
                val failedMessage = mainHandler.obtainMessage(
                    MSG_STATISTICS_FAILED,
                    errorText
                )

                // 실패 택배를 메인 우편 담당자에게 발송한다.
                failedMessage.sendToTarget()
            }
        })
    }

    // Firebase 기록의 날짜 형식과 같은 오늘 날짜 문자열을 만드는 함수
    private fun getCurrentRecordDate(): String {
        return SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN).format(Date())
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - Firebase 기록 리스너 연결")

        // Activity가 화면에 보이는 동안에만 Firebase 기록 변경을 받도록 리스너 연결
        attachRecordListener()
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - 오늘 날짜 변경 여부 확인")

        /*
         * 홈 화면이나 EditorActivity에 머무는 사이 날짜가 바뀌었는지 확인합니다.
         * 날짜가 바뀌었다면 최근에 받은 기록을 새 날짜 기준으로 다시 계산합니다.
         */
        val currentDate = getCurrentRecordDate()
        if (selectedStatisticsDate != currentDate && latestEventList.isNotEmpty()) {
            startTodayStatisticsCalculation(latestEventList, currentDate)
        }
    }

    override fun onPause() {
        logLifecycle("onPause - EditorActivity 또는 다른 화면이 앞에 나타남")
        super.onPause()
    }

    override fun onStop() {
        // 화면이 완전히 가려진 동안 Firebase 콜백을 계속 받을 필요가 없으므로 리스너 제거
        detachRecordListener()
        logLifecycle("onStop - Firebase 기록 리스너 제거")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart - 다른 Activity에서 기록 화면으로 돌아옴")
    }

    /*
     * 시스템이 Activity를 파괴하고 다시 만들 때 통계 UI 상태와 기준 날짜를 복원하기 위한 Bundle 저장입니다.
     * Firebase에 저장할 실제 기록이 아니라 잠깐 유지할 화면 상태이므로 onSaveInstanceState를 사용합니다.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        // 현재 통계 영역이 보이는 상태인지 Boolean 값으로 저장
        isStatisticsExpanded = binding.statisticsLayout.visibility == View.VISIBLE
        outState.putBoolean(STATE_STATISTICS_EXPANDED, isStatisticsExpanded)

        // 마지막 통계 기준 날짜를 저장하여 onResume에서 날짜 변경 여부를 다시 비교할 수 있게 함
        outState.putString(STATE_SELECTED_STATISTICS_DATE, selectedStatisticsDate)

        logLifecycle("onSaveInstanceState - 통계 영역 상태와 통계 기준 날짜 저장")
        super.onSaveInstanceState(outState)
    }

    // RecordFragment의 onDestroyView가 Activity에서는 onDestroy로 바뀐다.
    override fun onDestroy() {

        // 보통 onStop에서 제거하지만 예외적인 종료에서도 리스너가 남지 않도록 한 번 더 정리
        detachRecordListener()

        // mainHandler가 초기화됐었는지 확인한다.
        if (::mainHandler.isInitialized) { // 그냥 ::없이 했다면 mainHandler의 상자안 객체를 열어 .뒤 명령어를 실행할것이다 :: 있는경우 변수상자를 열지 않고 초기화된 적이 있는지만 확인

            // 메인 우편 보관함에 아직 남아 있는 이 Handler의 택배들을 제거한다.
            mainHandler.removeCallbacksAndMessages(null)
        }

        logLifecycle("onDestroy - Handler 메시지와 콜백 제거")
        super.onDestroy()
    }
}
