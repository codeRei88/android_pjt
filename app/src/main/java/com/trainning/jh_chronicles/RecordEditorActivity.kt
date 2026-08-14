package com.trainning.jh_chronicles

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.trainning.jh_chronicles.databinding.ActivityRecordEditorBinding

/*
 * 기존 RecordActivity의 작성·수정 Dialog가 담당하던 입력 UI를 별도의 Activity로 옮긴 클래스입니다.
 * 입력만 담당하고 Firebase 저장은 하지 않으며, 저장·삭제 결과를 Intent에 담아 RecordActivity로 반환합니다.
 */
class RecordEditorActivity : AppCompatActivity() {

    companion object {
        /*
         * RecordActivity와 RecordEditorActivity가 Intent 데이터를 같은 이름으로 주고받기 위한 key입니다.
         * 문자열을 여러 곳에 직접 쓰지 않고 const val로 관리하면 오타로 데이터를 못 받는 문제를 줄일 수 있습니다.
         */
        const val EXTRA_EDITOR_MODE = "extra_record_editor_mode"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_INPUT_HINT = "input_hint"
        const val EXTRA_INPUT_UNIT = "input_unit"
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_RECORD_DATE = "extra_record_date"
        const val EXTRA_RECORD_TIME = "extra_record_time"
        const val EXTRA_RECORD_DETAIL = "extra_record_detail"
        const val EXTRA_RESULT_ACTION = "extra_record_result_action"

        // 하나의 EditorActivity에서 새 기록 작성과 기존 기록 수정을 구별하기 위한 값
        const val MODE_CREATE = "mode_create"
        const val MODE_EDIT = "mode_edit"

        // RecordActivity Callback이 저장 결과와 삭제 결과를 구별하기 위한 값
        const val RESULT_ACTION_SAVE = "result_action_save"
        const val RESULT_ACTION_DELETE = "result_action_delete"

        // 화면 회전으로 EditorActivity가 다시 만들어질 때 입력값과 수면 종류를 복원하기 위한 Bundle key
        private const val STATE_DETAIL_INPUT = "state_record_detail_input"
        private const val STATE_NAP_SELECTED = "state_record_nap_selected"
    }

    private lateinit var binding: ActivityRecordEditorBinding

    // Intent에 편집 모드가 없다면 안전하게 새 기록 작성 모드로 사용
    private val editorMode: String by lazy {
        intent.getStringExtra(EXTRA_EDITOR_MODE) ?: MODE_CREATE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifecycle("onCreate - Intent Extra를 읽어 기록 입력 화면 생성")

        binding = ActivityRecordEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 제목, 입력값, 수면 종류, 버튼 상태를 설정
        restoreEditorContent(savedInstanceState)

        // 저장 버튼을 누르면 Firebase에 직접 저장하지 않고 결과 Intent를 RecordActivity로 반환
        binding.saveBtn.setOnClickListener {
            returnSaveResult()
        }

        // 수정 모드에서 삭제 버튼을 누르면 삭제할 기록 id만 결과 Intent로 반환
        binding.deleteBtn.setOnClickListener {
            returnDeleteResult()
        }

        // 취소 시 RESULT_CANCELED를 반환하므로 RecordActivity Callback은 Firebase를 변경하지 않음
        binding.cancelBtn.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    /*
     * 편집화면 복원시 우선순위에 맞춰 데이터를 복원해 오는 메서드
     * 우선순위는 화면 회전 Bundle -> 수정 Intent Extra -> 새 작성 Intent Extra 순서
     */
    private fun restoreEditorContent(savedInstanceState: Bundle?) {
        // 작성 버튼에서 보낸 우유·맘마·수면·배변 또는 수정할 기존 제목을 화면 제목으로 표시
        val eventTitle = intent.getStringExtra(EXTRA_EVENT_TYPE).orEmpty()
        binding.title.text = eventTitle

        // 새 작성 화면에는 RecordActivity가 전달한 기존 입력 안내 문구를 사용
        binding.detailInput.hint =
            intent.getStringExtra(EXTRA_INPUT_HINT) ?: "내용 입력"

        // 수정 모드에서만 Firebase에 이미 존재하는 기록이 있으므로 삭제 버튼을 표시
        binding.deleteBtn.visibility = if (editorMode == MODE_EDIT) View.VISIBLE else View.GONE
        binding.saveBtn.text = if (editorMode == MODE_EDIT) "수정" else "저장"

        // 수면 기록에만 낮잠·밤잠 RadioGroup을 표시
        val isSleepRecord = eventTitle.startsWith("수면")
        binding.sleepTypeGroup.visibility = if (isSleepRecord) View.VISIBLE else View.GONE

        if (savedInstanceState != null) {
            // 화면 회전 직전의 사용자가 입력한 상세 내용을 Bundle에서 가장 먼저 복원
            binding.detailInput.setText(
                savedInstanceState.getString(STATE_DETAIL_INPUT).orEmpty()
            )

            // 수면 기록이면 회전 직전에 선택한 낮잠·밤잠 상태도 복원
            if (isSleepRecord) {
                binding.napRadioBtn.isChecked =
                    savedInstanceState.getBoolean(STATE_NAP_SELECTED, true)
                binding.nightSleepRadioBtn.isChecked = !binding.napRadioBtn.isChecked
            }
            return
        }

        if (editorMode == MODE_EDIT) {
            // 기록화면에서 보낸 데이터를 그대로 표시
            binding.detailInput.setText(
                intent.getStringExtra(EXTRA_RECORD_DETAIL).orEmpty()
            )

            // 기존 수면 기록 제목에 들어 있는 낮잠·밤잠 값을 RadioButton에 표시
            if (isSleepRecord) {
                if (eventTitle.contains("낮잠")) {
                    binding.napRadioBtn.isChecked = true
                } else if (eventTitle.contains("밤잠")) {
                    binding.nightSleepRadioBtn.isChecked = true
                }
            }
        }
    }

    /*
     * 이 Activity에서 Firebase에 저장하지 않고 결과 Intent를 만들어 돌려줌
     */
    private fun returnSaveResult() {
        val inputValue = binding.detailInput.text.toString()

        // 아무 내용도 입력하지 않으면 저장하지 않고 EditText에 오류 표시
        if (inputValue.isBlank()) {
            binding.detailInput.error = "내용을 입력해주세요"
            return
        }

        val originalTitle = intent.getStringExtra(EXTRA_EVENT_TYPE).orEmpty()

        // 수면 기록이면 기존 코드처럼 RadioButton 선택 결과를 제목에 붙임
        val finalTitle = if (originalTitle.startsWith("수면")) {
            val selectedType =
                if (binding.napRadioBtn.isChecked) "낮잠" else "밤잠"
            "수면($selectedType)"
        } else {
            originalTitle
        }

        /*
         * 새 작성은 입력값 뒤에 (ml), (분) 단위를 붙임
         * 수정은 기존 showEditDialog()처럼 입력창에 보이는 문자열 자체를 그대로 반환
         */
        val finalDetail = if (editorMode == MODE_CREATE) {
            inputValue + intent.getStringExtra(EXTRA_INPUT_UNIT).orEmpty()
        } else {
            inputValue
        }

        /*
         * 새 작성과 수정 모두 RecordActivity가 보낸 날짜·시간을 그대로 유지
         * 새 작성은 기록 버튼을 누른 순간, 수정은 기존 Firebase 기록의 날짜·시간
         */
        val finalTime = intent.getStringExtra(EXTRA_RECORD_TIME).orEmpty()
        val finalDate = intent.getStringExtra(EXTRA_RECORD_DATE).orEmpty()

        // RecordActivity로 돌려보낼 결과 데이터 상자 역할의 Intent 생성
        val resultIntent = Intent().apply {
            // Callback이 저장 사후 처리를 선택할 수 있도록 저장 결과라는 값을 전달
            putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_SAVE)

            // 새 기록은 빈 id, 수정 기록은 기존 Firebase id를 그대로 반환
            putExtra(EXTRA_RECORD_ID, intent.getStringExtra(EXTRA_RECORD_ID).orEmpty())

            // Firebase EventData를 다시 만들 수 있도록 날짜·시간·제목·상세 값을 모두 반환
            putExtra(EXTRA_RECORD_DATE, finalDate)
            putExtra(EXTRA_RECORD_TIME, finalTime)
            putExtra(EXTRA_EVENT_TYPE, finalTitle)
            putExtra(EXTRA_RECORD_DETAIL, finalDetail)
        }

        // 반환할 성공 결과를 먼저 등록하고 현재 EditorActivity를 종료해야 Callback으로 전달됨
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    // 기록 삭제 버튼을 눌렀을 때 RecordActivity가 삭제할 Firebase id를 결과로 반환
    private fun returnDeleteResult() {
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID).orEmpty()

        // 새 작성 화면이거나 id가 없다면 Firebase에서 삭제할 대상이 없으므로 중단
        if (editorMode != MODE_EDIT || recordId.isBlank()) return

        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_ACTION, RESULT_ACTION_DELETE)
            putExtra(EXTRA_RECORD_ID, recordId)
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onStart() {
        super.onStart()
        logLifecycle("onStart - 기록 편집 화면이 보이기 시작")
    }

    override fun onResume() {
        super.onResume()
        logLifecycle("onResume - 기록 입력 가능")
    }

    override fun onPause() {
        logLifecycle("onPause - 기록 편집 화면이 일부 가려짐")
        super.onPause()
    }

    override fun onStop() {
        logLifecycle("onStop - 기록 편집 화면이 완전히 가려짐")
        super.onStop()
    }

    /*
     * 화면 회전처럼 시스템이 EditorActivity를 다시 만들 때 작성 중인 값을 잃지 않도록 Bundle에 저장
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DETAIL_INPUT, binding.detailInput.text.toString())
        outState.putBoolean(STATE_NAP_SELECTED, binding.napRadioBtn.isChecked)
        logLifecycle("onSaveInstanceState - 입력값과 수면 종류 저장")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        logLifecycle("onDestroy - RecordEditorActivity 제거")
        super.onDestroy()
    }
}
