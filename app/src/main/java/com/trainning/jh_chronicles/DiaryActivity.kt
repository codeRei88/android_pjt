package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.trainning.jh_chronicles.databinding.ActivityDiaryBinding

class DiaryActivity : AppCompatActivity() {

    companion object {
        // 시스템에 의해 Activity가 파괴되고 나중에 복원될 때 스크롤위치를 저장하기 위한 Bundle key
        private const val STATE_SCROLL_POSITION = "state_diary_scroll_position"

        // (시스템에 의해 Activity가 파괴되고 나중에 복원될 때) 사용자가 수정하려고 선택한 일기의 id를 저장하기 위한 Bundle key
        private const val STATE_SELECTED_DIARY_ID = "state_selected_diary_id"
    }

    private lateinit var binding: ActivityDiaryBinding

    // Firebase에 저장된 현재 사용자의 일기 폴더 경로를 보관하는 변수
    private lateinit var myRef: DatabaseReference

    // Firebase에서 가져온 일기들을 RecyclerView에 전달하기 위한 리스트
    private val dataList = mutableListOf<ItemData>()

    // RecyclerView에 일기 목록을 실제로 그려주는 어댑터
    private lateinit var diaryAdapter: AdapterMain

    // onStart에서 등록한 Firebase 리스너를 onStop에서 제거하려면 객체를 기억하고 있어야 함
    private var diaryValueListener: ValueEventListener? = null

    // 화면 회전 전에 보고 있던 RecyclerView 위치를 Firebase 데이터가 도착한 뒤 복원하기 위한 변수(번들의 벨류)
    private var savedScrollPosition = 0

    // 현재 사용자가 선택한 일기 id를 화면 회전 전후로 기억하기 위한 변수(번들의 벨류)
    private var selectedDiaryId: String? = null


      //DiaryEditorActivity를 실행한 뒤 결과를 돌려받기 위한 Activity Result 계약
      //계약을 위해 ActivityResultLauncher객체를 담을 변수그릇 선언
    private val diaryEditorLauncher = registerForActivityResult( //Contract, Callback 두 인수를 받아 ActivityResultLauncher를 반환하는 함수
        ActivityResultContracts.StartActivityForResult() //Intent를 입력받아 Activity를 실행하고, 결과를 ActivityResult 형태로 반환하는 Contract
    ) { activityResult -> // 실행된 activity에서 결과값을 받아 실행하는 Callback

        // 편집 화면에서 취소하거나 시스템 뒤로가기를 눌렀다면 RESULT_OK가 아니므로 아무것도 저장하지 않음
        if (activityResult.resultCode != RESULT_OK) {
            return@registerForActivityResult
        }

        // DiaryEditorActivity가 setResult()로 돌려준 결과Intent를 그릇에 담음
        val resultIntent = activityResult.data ?: return@registerForActivityResult

        // 같은 편집 Activity가 저장과 삭제 결과를 모두 보내므로 action 문자열로 할 일을 구분함
         when (resultIntent.getStringExtra(DiaryEditorActivity.EXTRA_RESULT_ACTION)) {
            DiaryEditorActivity.RESULT_ACTION_SAVE -> saveDiaryResult(resultIntent)
            DiaryEditorActivity.RESULT_ACTION_DELETE -> deleteDiaryResult(resultIntent)
        }

        // 편집 화면의 결과 처리가 끝났으므로 현재 선택된 일기 id를 비움
        selectedDiaryId = null
    }

    // Fragment의 onCreateView 대신 Activity는 onCreate에서 화면을 생성한다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - RecyclerView와 버튼을 최초로 준비")

        binding = ActivityDiaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면 회전 전에 저장한 RecyclerView 위치가 있다면 가져옴. 처음 실행이면 0번 위치 사용
        savedScrollPosition = savedInstanceState?.getInt(STATE_SCROLL_POSITION) ?: 0

        // 화면 회전 전에 수정하려고 선택했던 일기 id가 있다면 다시 가져옴
        selectedDiaryId = savedInstanceState?.getString(STATE_SELECTED_DIARY_ID)

        // 파이어베이스 데이터베이스 연결
        val database = Firebase.database // 파이어베이스 저장공간을 코드로 가져오기
        val uid = Firebase.auth.currentUser!!.uid // 파이어베이스에 접속한 유저의 uid를 가져다 저장함
        myRef = database.getReference("diary_entries").child(uid) // 가져온 저장공간에 폴더 이름설정 및 생성 해주기

        /// 리사이클러뷰를 어뎁터와 레이아웃 메니저와 연결
        diaryAdapter = AdapterMain(dataList) // 어뎁터에 일기리스트를 전달하여 전용 그릇에 담기
        binding.diaryRc.adapter = diaryAdapter // 리사이클러뷰에 어뎁터 연결
        binding.diaryRc.layoutManager = LinearLayoutManager(this) // 리사이클러뷰에 레이아웃 메니저 연결

        //리사이클러뷰 클릭 이벤트 처리
        diaryAdapter.itemClick = object : AdapterMain.ItemClick {
            override fun onClick(view: View, position: Int) {
                val clickedDiary = dataList[position]

                // 어떤 일기를 수정하고 있었는지 onSaveInstanceState에서 저장할 수 있도록 id를 보관
                selectedDiaryId = clickedDiary.id

                // 기존 Dialog 대신 수정할 일기 정보를 Intent Extra에 담아 DiaryEditorActivity 실행
                openDiaryEditorForEdit(clickedDiary)
            }
        }

        // 쓰기 버튼을 누르면 새 일기 작성 모드라는 정보만 Intent에 담아 DiaryEditorActivity 실행
        binding.writeImg.setOnClickListener {
            selectedDiaryId = null
            openDiaryEditorForCreate()
        }

        // 액티비티 화면 이동관련 코드는 명시적 Intent 이동으로만 변경
        binding.recordBtn.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }

        binding.weatherBtn.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }

        binding.mapBtn.setOnClickListener {
            startActivity(Intent(this, HospitalActivity::class.java))
        }

        binding.dDayBtn.setOnClickListener {
            startActivity(Intent(this, VaccinationActivity::class.java))
        }
    }


    //새 일기 작성 화면을 여는 명시적 Intent
    private fun openDiaryEditorForCreate() {
        val editorIntent = Intent(this, DiaryEditorActivity::class.java).apply {
            // 같은 편집 화면이 작성과 수정을 모두 담당하므로 새 일기 작성 모드임을 Extra로 전달
            putExtra(DiaryEditorActivity.EXTRA_EDITOR_MODE, DiaryEditorActivity.MODE_CREATE)
        }
        // startActivity()가 아니라 launcher.launch()로 실행해야 편집 Activity의 결과를 돌려받을 수 있음(사후처리)
        diaryEditorLauncher.launch(editorIntent)
    }


     //기존 일기 수정 화면을 여는 명시적 Intent
     //과제에서 확인할 수 있도록 id, 날짜, 제목, 내용을 각각 putExtra()로 전달
    private fun openDiaryEditorForEdit(clickedDiary: ItemData) {
        val editorIntent = Intent(this, DiaryEditorActivity::class.java).apply {
            // 편집 화면에게 새 글 작성이 아니라 기존 글 수정이라는 것을 알려줌
            putExtra(DiaryEditorActivity.EXTRA_EDITOR_MODE, DiaryEditorActivity.MODE_EDIT)

            // Firebase에서 수정하거나 삭제할 정확한 일기를 찾기 위해 고유 id 전달
            putExtra(DiaryEditorActivity.EXTRA_DIARY_ID, clickedDiary.id)

            // 수정 화면에 기존 날짜를 표시하기 위해 날짜 전달
            putExtra(DiaryEditorActivity.EXTRA_DIARY_DATE, clickedDiary.date)

            // 수정 화면의 제목 EditText에 기존 제목을 표시하기 위해 제목 전달
            putExtra(DiaryEditorActivity.EXTRA_DIARY_TITLE, clickedDiary.title)

            // 수정 화면의 내용 EditText에 기존 내용을 표시하기 위해 내용 전달
            putExtra(DiaryEditorActivity.EXTRA_DIARY_CONTENT, clickedDiary.content)
        }

        // 편집 Activity가 setResult()로 보내는 수정 또는 삭제 결과를 받기 위해 launcher로 실행
        diaryEditorLauncher.launch(editorIntent)
    }

    /*
     * DiaryEditorActivity가 RESULT_OK와 함께 돌려준 저장 결과를 Firebase에 반영합니다.
     * id가 비어 있으면 새 일기이고, id가 있으면 기존 일기를 수정하는 것입니다.
     */
    private fun saveDiaryResult(resultIntent: Intent) {
        // 편집 화면이 돌려준 일기 id를 가져옴. 새 일기는 아직 id가 없으므로 빈 문자열임
        val returnedDiaryId = resultIntent.getStringExtra(DiaryEditorActivity.EXTRA_DIARY_ID).orEmpty()

        // id가 비어 있으면 push()로 새 Firebase 경로를 만들고, id가 있으면 기존 경로를 선택함
        val targetRef = if (returnedDiaryId.isBlank()) {
            myRef.push()
        } else {
            myRef.child(returnedDiaryId)
        }

        // 새 일기일 경우 push()가 만든 key를 사용하고 수정일 경우 기존 id를 그대로 사용함
        val finalDiaryId = targetRef.key ?: return

        // 결과 Intent에 담겨 돌아온 날짜, 제목, 내용을 ItemData 한 개로 다시 포장함
        val returnedDiary = ItemData(
            id = finalDiaryId,
            date = resultIntent.getStringExtra(DiaryEditorActivity.EXTRA_DIARY_DATE).orEmpty(),
            title = resultIntent.getStringExtra(DiaryEditorActivity.EXTRA_DIARY_TITLE).orEmpty(),
            content = resultIntent.getStringExtra(DiaryEditorActivity.EXTRA_DIARY_CONTENT).orEmpty()
        )

        // 새 일기인지 판단한 결과는 Firebase 저장 성공 후 초안을 지울지 결정할 때 사용함
        val isNewDiary = returnedDiaryId.isBlank()

        // 새 일기 또는 수정된 일기를 선택한 Firebase 경로에 저장
        targetRef.setValue(returnedDiary)
            .addOnSuccessListener {
                /*
                 * 새 일기가 Firebase에 정상 저장된 뒤에만 SharedPreferences 초안을 삭제
                 * 저장에 실패했는데 초안을 먼저 지우면 사용자가 작성한 내용을 잃을 수 있기 때문
                 */
                if (isNewDiary) {
                    clearDiaryDraft()
                }

                Toast.makeText(this, "저장 완료", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                // 저장 실패 시 초안은 지우지 않으므로 다시 작성 화면을 열면 기존 내용을 복구할 수 있음
                Toast.makeText(this, "저장 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // DiaryEditorActivity가 돌려준 삭제 결과에서 id를 꺼내 Firebase의 해당 일기만 삭제
    private fun deleteDiaryResult(resultIntent: Intent) {
        val diaryId =
            resultIntent.getStringExtra(DiaryEditorActivity.EXTRA_DIARY_ID).orEmpty()

        // 새 일기에는 삭제할 Firebase id가 없으므로 id가 있을 때만 삭제 요청
        if (diaryId.isNotBlank()) {
            myRef.child(diaryId).removeValue()
        }
    }

    // 새 일기가 Firebase에 저장된 뒤 기존 세 개의 초안 key를 한 번에 제거
    private fun clearDiaryDraft() {
        getSharedPreferences(DiaryEditorActivity.DIARY_PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .remove(DiaryEditorActivity.DRAFT_DATE_KEY)
            .remove(DiaryEditorActivity.DRAFT_TITLE_KEY)
            .remove(DiaryEditorActivity.DRAFT_CONTENT_KEY)
            .apply()
    }

    /*
     * Firebase 일기 리스너를 연결하는 함수입니다.
     * onStart는 Activity가 사용자에게 보이기 시작할 때 호출되므로 이 시점부터 일기 변경을 관찰합니다.
     */
    private fun attachDiaryListener() {
        // 이미 리스너가 연결된 상태에서 중복으로 등록되는 것을 방지
        if (diaryValueListener != null) return

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                dataList.clear()// 기존 dataList에 있던 값 리셋
                for (oneData in snapshot.children) {// 내가지정한 경로의 파이어베이스에 있는 모든 데이터 하나씩 가져와라
                    oneData.getValue(ItemData::class.java)?.let { dataList.add(it) }
                        ?: continue// 가져온 데이터 하나를 ItemDate 타입의 객체 타입으로 바꿔라, 그리고 dataList 그거를 추가해라
                }
                diaryAdapter.notifyDataSetChanged() // 리스트에 추가된 것을 어뎁터에게 알려라

                // Firebase 데이터가 RecyclerView에 들어온 다음 화면 회전 전에 보고 있던 위치로 이동
                if (dataList.isNotEmpty()) {
                    val safePosition = savedScrollPosition.coerceIn(0, dataList.lastIndex)
                    binding.diaryRc.scrollToPosition(safePosition)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DiaryActivity, "데이터 로드 실패", Toast.LENGTH_SHORT).show()
            }
        }

        // onStop에서 정확히 같은 리스너 객체를 제거할 수 있도록 필드에 저장
        diaryValueListener = listener

        // 현재 로그인 사용자의 일기 경로에 실시간 데이터 변경 리스너 등록
        myRef.addValueEventListener(listener)
    }

    // onStart에서 연결했던 Firebase 리스너를 화면이 보이지 않는 onStop에서 제거
    private fun detachDiaryListener() {
        diaryValueListener?.let { listener ->
            myRef.removeEventListener(listener)
        }
        diaryValueListener = null
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - Firebase 일기 리스너 연결")

        // Activity가 화면에 보이는 동안에만 Firebase 변경사항을 받기 위해 리스너 연결
        attachDiaryListener()
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - 사용자가 일기 화면과 상호작용 가능")
    }

    override fun onPause() {
        /*
         * 작성 입력창은 이제 DiaryEditorActivity가 가지고 있음
         * 따라서 초안 저장은 DiaryEditorActivity.onPause()에서 수행
         */
        logLifecycle("onPause - 편집 Activity 또는 다른 화면이 앞에 나타남")
        super.onPause()
    }

    override fun onStop() {
        // Activity가 완전히 가려진 동안에는 Firebase 콜백이 필요하지 않으므로 리스너 제거
        detachDiaryListener()
        logLifecycle("onStop - Firebase 일기 리스너 제거")
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        logLifecycle("onRestart - 다른 Activity에서 일기 목록으로 돌아옴")
    }

    /*
     * 화면 회전처럼 Activity가 파괴됐다 다시 만들어질 때도 사용자가 보던 위치를 복원하기 위한 메서드
     * Firebase에 저장할 데이터가 아니라 잠깐 유지할 UI 상태이므로 Bundle을 사용
     */
    override fun onSaveInstanceState(outState: Bundle) {
        // 현재 RecyclerView 화면에서 가장 위에 보이는 항목의 위치를 가져옴
        val currentScrollPosition =
            (binding.diaryRc.layoutManager as? LinearLayoutManager)
                ?.findFirstVisibleItemPosition()
                ?: 0

        // 화면 회전 후 onCreate에서 다시 읽을 수 있도록 스크롤 위치 저장
        outState.putInt(STATE_SCROLL_POSITION, currentScrollPosition)

        // 수정 중 선택했던 일기 id가 있다면 함께 저장
        outState.putString(STATE_SELECTED_DIARY_ID, selectedDiaryId)

        logLifecycle("onSaveInstanceState - 선택한 일기와 목록 위치 저장")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        // 보통 onStop에서 이미 제거되지만 예외적인 종료 상황에서도 리스너가 남지 않도록 한 번 더 정리
        detachDiaryListener()
        logLifecycle("onDestroy - DiaryActivity 제거")
        super.onDestroy()
    }
}
