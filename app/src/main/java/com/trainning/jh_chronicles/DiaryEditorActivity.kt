package com.trainning.jh_chronicles

import android.app.DatePickerDialog
import android.content.Intent
import android.icu.util.GregorianCalendar
import android.os.Bundle
import android.text.InputFilter
import android.text.Layout
import android.text.StaticLayout
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.trainning.jh_chronicles.databinding.ActivityDiaryEditorBinding

class DiaryEditorActivity : AppCompatActivity() {

    companion object {
        /* 
         * DiaryActivity와 DiaryEditorActivity가 같은 이름으로 Intent 데이터를 주고받기 위한 key
         * 문자열을 여러 위치에 직접 쓰면 오타가 나도 컴파일러가 찾지 못하므로 const val로 한곳에서 관리
         */
        const val EXTRA_EDITOR_MODE = "extra_diary_editor_mode"
        const val EXTRA_DIARY_ID = "extra_diary_id"
        const val EXTRA_DIARY_DATE = "extra_diary_date"
        const val EXTRA_DIARY_TITLE = "extra_diary_title"
        const val EXTRA_DIARY_CONTENT = "extra_diary_content"
        const val EXTRA_RESULT_ACTION = "extra_diary_result_action"

        // 같은 편집 화면에서 새 일기 작성과 기존 일기 수정을 구별하기 위한 값
        const val MODE_CREATE = "mode_create"
        const val MODE_EDIT = "mode_edit"

        // DiaryActivity가 돌아온 결과를 저장 결과인지 삭제 결과인지 구별하기 위한 값
        const val RESULT_ACTION_SAVE = "result_action_save"
        const val RESULT_ACTION_DELETE = "result_action_delete"

        //로컬DB sharedprefer 관련
        const val DIARY_PREFERENCES_NAME = "diary_prefs"
        const val DRAFT_DATE_KEY = "draft_date"
        const val DRAFT_TITLE_KEY = "draft_title"
        const val DRAFT_CONTENT_KEY = "draft_content"

        // 화면 회전 시 현재 입력값을 Bundle에 저장하기 위한 key
        private const val STATE_DATE = "state_editor_date"
        private const val STATE_TITLE = "state_editor_title"
        private const val STATE_CONTENT = "state_editor_content"
    }

    private lateinit var binding: ActivityDiaryEditorBinding

    // Intent로 받은 모드가 없다면 안전하게 새 일기 작성 모드로 사용
    private val editorMode: String by lazy {
        intent.getStringExtra(EXTRA_EDITOR_MODE) ?: MODE_CREATE
    }

    /*
     * setResult()를 보낸 뒤 finish()가 실행되면 onPause()도 호출됩니다.
     * 이때 저장을 완료한 내용을 다시 초안으로 저장하지 않도록 결과 전송 여부를 기억합니다.
     */
    private var isResultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - Intent Extra를 읽어 일기 편집 화면 생성")

        binding = ActivityDiaryEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3줄 제한 적용
        applyThreeLineLimit(binding.contentEdit)

        // 새 작성인지 수정인지와 화면 회전 여부를 확인해서 날짜, 제목, 내용을 입력창에 넣음
        restoreEditorContent(savedInstanceState)

        // 수정 모드일 때만 Firebase에 이미 저장된 일기가 있으므로 삭제 버튼을 보여줌
        binding.deleteBtn.visibility = if (editorMode == MODE_EDIT) View.VISIBLE else View.GONE

        // 사용자가 현재 작성인지 수정인지 쉽게 알 수 있도록 저장 버튼 글자를 구분
        binding.saveBtn.text = if (editorMode == MODE_EDIT) "수정 및 저장" else "저장"

        // 날짜 버튼을 누르면 기존 코드와 같은 DatePickerDialog를 실행
        binding.dateSelectBtn.setOnClickListener {
            showDatePicker()
        }

        // 저장 버튼을 누르면 Firebase에 직접 저장하지 않고 결과 Intent를 DiaryActivity로 반환
        binding.saveBtn.setOnClickListener {
            returnSaveResult()
        }

        // 삭제 버튼을 누르면 삭제할 일기 id를 결과 Intent로 DiaryActivity에 반환
        binding.deleteBtn.setOnClickListener {
            returnDeleteResult()
        }

        // 취소 시 RESULT_CANCELED를 반환하므로 DiaryActivity는 저장이나 삭제를 실행하지 않음
        binding.cancelBtn.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        /*
         * 기존 코드의 "입력 할때 마다 저장하기" 기능을 그대로 유지합니다.
         * 기존 일기 수정 내용이 새 일기 초안을 덮어쓰면 안 되므로 새 작성 모드에서만 실행합니다.
         */
        if (editorMode == MODE_CREATE) {
            binding.titleEdit.doAfterTextChanged {
                saveDraftToSharedPreferences()
            }

            binding.contentEdit.doAfterTextChanged {
                saveDraftToSharedPreferences()
            }
        }
    }

    /*
     * 편집 화면에 처음 표시할 내용을 정하는 메서드입니다.
     * 우선순위는 화면 회전 Bundle → 수정 Intent Extra → 새 일기 SharedPreferences 초안 순서입니다.
     */
    private fun restoreEditorContent(savedInstanceState: Bundle?) {
        // 화면 회전으로 Activity가 다시 생성된 경우에는 회전 직전 입력값을 가장 먼저 복원
        if (savedInstanceState != null) {
            binding.dateSelectBtn.text = savedInstanceState.getString(STATE_DATE, "날짜기입")
            binding.titleEdit.setText(savedInstanceState.getString(STATE_TITLE).orEmpty())
            binding.contentEdit.setText(savedInstanceState.getString(STATE_CONTENT).orEmpty())
            return
        }

        if (editorMode == MODE_EDIT) {
            /*
             * 수정 모드에서는 DiaryActivity가 putExtra()로 보낸 기존 일기 값을 사용합니다.
             * 기존 일기 수정은 새 일기 초안과 관계없으므로 SharedPreferences를 읽지 않습니다.
             */
            binding.dateSelectBtn.text =
                intent.getStringExtra(EXTRA_DIARY_DATE) ?: "날짜기입"
            binding.titleEdit.setText(
                intent.getStringExtra(EXTRA_DIARY_TITLE).orEmpty()
            )
            binding.contentEdit.setText(
                intent.getStringExtra(EXTRA_DIARY_CONTENT).orEmpty()
            )
        } else {
            /*
             * 새 일기 작성 모드에서는 기존 Dialog 코드와 똑같이 diary_prefs에서 초안을 불러옵니다.
             * 앱을 나갔다 돌아오거나 작성 화면을 취소했다 다시 열어도 작성 중인 내용이 복원됩니다.
             */
            val sharedPreferences =
                getSharedPreferences(DIARY_PREFERENCES_NAME, MODE_PRIVATE)

            binding.dateSelectBtn.text =
                sharedPreferences.getString(DRAFT_DATE_KEY, "날짜기입")
            binding.titleEdit.setText(
                sharedPreferences.getString(DRAFT_TITLE_KEY, "")
            )
            binding.contentEdit.setText(
                sharedPreferences.getString(DRAFT_CONTENT_KEY, "")
            )
        }
    }

    // 날짜 선택버튼이 눌렸을때 이벤트
    private fun showDatePicker() {
        val today = GregorianCalendar()
        val year = today.get(GregorianCalendar.YEAR)
        val month = today.get(GregorianCalendar.MONTH)
        val day = today.get(GregorianCalendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, dayOfMonth ->
            val selectedDate = "${selectedYear}년 ${selectedMonth + 1}월 ${dayOfMonth}일"
            binding.dateSelectBtn.text = selectedDate

            // 기존 코드처럼 날짜를 고른 순간에도 SharedPreferences 초안을 바로 갱신
            if (editorMode == MODE_CREATE) {
                saveDraftToSharedPreferences()
            }

        }, year, month, day).show()
    }

    /*
     * 사용자가 저장 버튼을 눌렀을 때 호출됩니다.
     * 이 Activity는 Firebase 경로를 소유하지 않으므로 setValue()를 하지 않고,
     * 날짜·제목·내용을 결과 Intent에 담아 DiaryActivity에 반환합니다.
     */
    private fun returnSaveResult() {
        // 화면 입력값들을 앞뒤 공백을 정리하여 각각 변수에 저장
        val date = binding.dateSelectBtn.text.toString()
        val title = binding.titleEdit.text.toString().trim()
        val content = binding.contentEdit.text.toString().trim()

        // 기존 코드와 동일하게 날짜, 제목, 내용 중 하나라도 없으면 저장 중단
        if (title.isEmpty() || content.isEmpty() || date == "날짜기입") {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        //3줄 이상이라면 워닝 띄우는 메서드 실행
        if (!isDiaryContentWithinThreeLines(binding.contentEdit)) {
            showThreeLineWarning()
            return
        }

        // DiaryActivity로 돌려보낼 비어 있는 결과 Intent 생성
        val resultIntent = Intent().apply {
            // DiaryActivity가 저장 결과라는 것을 알 수 있도록 action 값 전달
            putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_SAVE)

            // 새 일기는 빈 id, 수정 일기는 기존 Firebase id를 그대로 반환
            putExtra(EXTRA_DIARY_ID, intent.getStringExtra(EXTRA_DIARY_ID).orEmpty())

            // 사용자가 선택하거나 수정한 날짜를 반환
            putExtra(EXTRA_DIARY_DATE, date)

            // 사용자가 작성하거나 수정한 제목을 반환
            putExtra(EXTRA_DIARY_TITLE, title)

            // 사용자가 작성하거나 수정한 내용을 반환
            putExtra(EXTRA_DIARY_CONTENT, content)
        }

        // 이제 onPause가 호출되어도 저장 완료 내용을 다시 초안으로 쓰지 않도록 표시
        isResultSent = true

        // RESULT_OK와 결과 Intent를 등록하면 DiaryActivity의 Activity Result 콜백으로 전달됨
        setResult(RESULT_OK, resultIntent)

        // 결과를 등록한 뒤 현재 편집 Activity를 닫아 DiaryActivity로 돌아감
        finish()
    }

    // 수정 중인 일기의 삭제 버튼을 눌렀을 때 삭제할 id를 DiaryActivity로 반환
    private fun returnDeleteResult() {
        val diaryId = intent.getStringExtra(EXTRA_DIARY_ID).orEmpty()

        // 수정 모드가 아니거나 id가 없다면 Firebase에서 삭제할 대상이 없으므로 중단
        if (editorMode != MODE_EDIT || diaryId.isBlank()) return

        val resultIntent = Intent().apply {
            // DiaryActivity가 저장 결과와 삭제 결과를 구별하도록 삭제 action 전달
            putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_DELETE)

            // DiaryActivity가 Firebase의 정확한 자식을 삭제할 수 있도록 일기 id 전달
            putExtra(EXTRA_DIARY_ID, diaryId)
        }

        // 삭제 결과를 보낸 뒤 onPause에서 초안 저장 코드가 실행되지 않도록 표시
        isResultSent = true
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    /*
     * 기존 DiaryActivity Dialog에서 사용하던 SharedPreferences 초안 저장 기능입니다.
     * 입력할 때마다 호출하고 onPause에서도 호출하여 예상하지 못한 화면 전환에도 내용을 보관합니다.
     */
    private fun saveDraftToSharedPreferences() {
        // 기존 일기 수정값은 새 일기 초안이 아니므로 새 작성 모드가 아니면 저장하지 않음
        if (editorMode != MODE_CREATE || !::binding.isInitialized || isResultSent) return

        getSharedPreferences(DIARY_PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putString(DRAFT_DATE_KEY, binding.dateSelectBtn.text.toString())
            .putString(DRAFT_TITLE_KEY, binding.titleEdit.text.toString())
            .putString(DRAFT_CONTENT_KEY, binding.contentEdit.text.toString())
            .apply()
    }

    //3줄 리미트 거는 메서드
    private fun applyThreeLineLimit(editText: EditText) {
        val threeLineFilter = InputFilter { source, start, end, dest, dstart, dend ->
            // source: 새로 입력되는 문자열, dest: 기존에 입력되어 있던 문자열 start, end = 새로입련된 문자열의 처음과 끝 위치
            // dstart(사용자의 커서위치or블록지정시작점) dend(커서뒤 기존 문자열시작위치)

            // 글자를 지우는 동작은 항상 허용
            if (source.isEmpty()) {
                return@InputFilter null
            }
            //사용자가 지정한 커서위치를 기준으로 앞과 뒤를 자르고 사용자가 새로 입력한 텍스부분을 합쳐서 newText에 저장
            val newText = buildString {
                append(dest.subSequence(0, dstart)) //기존 문자열 첫번째에서부터 사용자가 지정한 커서위치까지 문자열을 긁는다.
                append(source.subSequence(start, end)) // 이제 막 새로 입력문자열 처음과 끝을 긁어옴
                append(dest.subSequence(dend, dest.length)) // 새로입력된 문자열의 끝부분 바로뒤 문자열 부터 마지막 문자열까지 긁어옴
            }

            // 사용자가 Enter를 눌러 만든 줄이 3줄을 넘으면 입력을 막음
            val manualLineCount = newText.count { it == '\n' } + 1 //최종 가져온 텍스트에서 엔터가 들어간 수를 카운트
            if (manualLineCount > 3) { //엔터 3번을 넘어가면 입력을 빈공간을 리턴하면서 막음
                return@InputFilter ""
            }

            /*
             * 긴 문장이 자동으로 다음 줄로 넘어가는 경우도 계산
             * EditText의 실제 너비가 만들어진 뒤에 정확하게 검사할 수 있음
             */
            val availableWidth = //왼쪽 오른쪽 여백 빼고 순수 글자를 쓸수있는 공간을 저장함
                editText.width - editText.paddingLeft - editText.paddingRight

            if (availableWidth > 0 && newText.isNotEmpty()) {
                val textLayout = StaticLayout.Builder.obtain( //엔터없이 쭉 문자열입력할때 자동으로 줄이 바뀌는데 그게 현 newText를 넣었을때 3줄이 넘어가는지 체크하는 메서드
                    newText, //어떤 문자열을 넣을지
                    0, //문자열 어디서부터 시작할지
                    newText.length, //문자열의 끝은어딘지
                    editText.paint, //어떻게 그릴지 문자열을 (글자 크기 , 폰트)
                    availableWidth // 문자열을 쓸 너비여백은 얼만큼인지
                )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL) //정렬방식 보통 왼쪽부터 써내려감
                    .setIncludePad(false)// 폰트 자체의 위아래 기본 여백 포함 여부
                    .build()// 위 설정대로 레이아웃을 생성

                if (textLayout.lineCount > 3) {
                    return@InputFilter ""
                }
            }
            // 3줄 안쪽이면 원래 입력을 그대로 허용합니다.
            null // InputFilter에서 null을 반환하는 것은 방금 입력한 글자에 아무런 태클도 걸지 않을 테니 원래대로 화면에 띄워라 라는 뜻
        }
        //안드로이드 EditText에는 기본적으로 설정된 필터들(예: 최대 글자 수 제한 등)이 배열(리스트) 형태로 들어있음 이 배열에 새로운 필터를 추가함
        editText.filters = editText.filters + threeLineFilter
    }

    /*
     * 기존에 이미 3줄을 넘겨 저장된 일기가 있을 수 있으므로
     * 저장 버튼을 누를 때도 마지막으로 한 번 더 검사
     */
    private fun isDiaryContentWithinThreeLines(editText: EditText): Boolean {
        val content = editText.text.toString()
        val manualLineCount = content.count { it == '\n' } + 1 //수동으로 친 엔터가 몇번인지 카운트
        val displayedLineCount = editText.layout?.lineCount ?: manualLineCount //lineCount(자동으로 넘어가는 줄수)를 저장하거나 없다면 수동엔터 카운터를 저장

        return manualLineCount <= 3 && displayedLineCount <= 3 //줄바꿈이 3이하일떄만 true
    }

    private fun showThreeLineWarning() {
        Toast.makeText(
            this,
            "3줄 일기는 내용도 3줄까지만 작성할 수 있어요.",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - 편집 화면이 보이기 시작")
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - 일기 입력 가능")
    }

    override fun onPause() {
        /*
         * 홈 버튼, 공유 앱 실행, 시스템 뒤로가기 등으로 편집 Activity가 가려질 때 호출됩니다.
         * 기존의 입력할 때마다 저장하는 기능에 더해 마지막 상태를 한 번 더 SharedPreferences에 저장합니다.
         */
        saveDraftToSharedPreferences()
        logLifecycle("onPause - 작성 중인 새 일기 초안을 SharedPreferences에 저장")
        super.onPause()
    }

    /*
     * 화면 회전으로 편집 Activity가 재생성될 때 입력 중인 내용을 복원하기 위한 Bundle 저장입니다.
     * SharedPreferences는 장기 초안, Bundle은 현재 화면의 일시적인 UI 상태라는 차이가 있습니다.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DATE, binding.dateSelectBtn.text.toString())
        outState.putString(STATE_TITLE, binding.titleEdit.text.toString())
        outState.putString(STATE_CONTENT, binding.contentEdit.text.toString())
        logLifecycle("onSaveInstanceState - 날짜, 제목, 내용 입력 상태 저장")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        logLifecycle("onDestroy - DiaryEditorActivity 제거")
        super.onDestroy()
    }
}
