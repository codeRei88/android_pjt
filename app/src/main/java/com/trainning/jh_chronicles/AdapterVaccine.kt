package com.trainning.jh_chronicles

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trainning.jh_chronicles.databinding.OneRcVaccinationBinding
import kotlin.math.abs

class AdapterVaccine(
    private val onCompletionChanged: (VaccineData, Boolean) -> Unit, // 체크박스 변경 시 액티비티에 저장요청하기 위한 콜백 메서드
    private val onAddToCalendar: (VaccineData) -> Unit // 캘린더 버튼 클릭 시 Activity에 암시적 Intent 실행 요청
) : RecyclerView.Adapter<AdapterVaccine.VaccineViewHolder>() {

    private val vaccineList = mutableListOf<VaccineData>() //어뎁터 가지고 있는 백신리스트 처음엔 비어있다(액티비티로부터 받아와야함)
    private var daysSinceBirth: Int? = null // 생후며칠지났는지에 대한 값도 처음엔 비어있고 액티비티로부터 받아야함

    inner class VaccineViewHolder(
        val binding: OneRcVaccinationBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VaccineViewHolder {
        val binding = OneRcVaccinationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VaccineViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: VaccineViewHolder,
        position: Int
    ) {
        val item = vaccineList[position]

        with(holder.binding) {
            vaccineName.text = item.name
            vaccineAge.text = item.recommendedAge

            // RecyclerView의 재활용떄문에 이전 체크박스 저장된 상태가 묻어올수 있음. 때문에 의도치않게 체크박스상태가 덮어씌워질수 있으므로 null시켜야함
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.complete // 현재 상태로 업데이트

            updateTextStyle(vaccineName, item.complete)
            updateTextStyle(vaccineAge, item.complete)
            showRemainingDay(remainingDay, item)

            // 생년월일이 있어 접종 권장 날짜를 계산할 수 있을 때만 캘린더 버튼 사용 가능
            addCalendarBtn.isEnabled = daysSinceBirth != null
            addCalendarBtn.setOnClickListener {
                onAddToCalendar(item)
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                updateTextStyle(vaccineName, isChecked)
                updateTextStyle(vaccineAge, isChecked)
                showRemainingDay(remainingDay, item)

                // Activity에 알려 Firebase에 저장하게 함
                onCompletionChanged(item, isChecked)
            }
        }
    }

    override fun getItemCount(): Int = vaccineList.size


     //Firebase에서 읽은 목록이나 정렬된 목록을 화면에 다시 표시합니다.
    fun submitList(newList: List<VaccineData>, newDaysSinceBirth: Int?) {
        vaccineList.clear()
        vaccineList.addAll(newList)
        daysSinceBirth = newDaysSinceBirth
        notifyDataSetChanged()
    }

    private fun showRemainingDay(textView: TextView, item: VaccineData) {
        if (item.complete) {
            textView.text = "접종 완료"
            textView.setTextColor(Color.parseColor("#2E7D32"))
            textView.paintFlags =
                    // 접종완료 즉 체크박스표시가 되면 백신이름이나 코멘트가 취소선이 그어짐 접종완료는 취소선이 없어야함
                textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            return
        }

        val currentDaysSinceBirth = daysSinceBirth

        if (currentDaysSinceBirth == null) {
            textView.text = "생일 입력" //생일입력이 안된경우 디데이 계산이 안되기떄문에 생일입력표시로 대체
            textView.setTextColor(Color.parseColor("#5F6368"))
            return
        }

        val remaining = item.dDay - currentDaysSinceBirth //아기 생일기준 백신 마감디데이 계산값
        val hasDeadline = item.deadlineMonths != null //백신마감 기한이 있음 true, 없음 false
        val prefix = if (hasDeadline) "마감 " else ""

        when {
            remaining == 0 -> {
                textView.text = "${prefix}D-Day" //마감 D-Day 표시
                textView.setTextColor(Color.parseColor("#E65100"))
            }

            remaining > 0 -> {
                textView.text = "${prefix}D-$remaining" //마감 D-?? 표시

                // 아기가 접종 가능 기간에 들어오면 주황색으로 눈에 띄게 표시
                val isInsideRecommendedRange =
                    hasDeadline && currentDaysSinceBirth >= item.startDay

                val color = if (isInsideRecommendedRange) {
                    "#E65100" //접종기간안에 있으면 주황
                } else {
                    "#1565C0" //아직 접종기간이 안됐으면 파랑색표시
                }
                textView.setTextColor(Color.parseColor(color)) //색상 hex값을 안드로이드가 알아먹게 int로 변환하여 던저줌
            }

            else -> {
                textView.text = "${prefix}D+${abs(remaining)}"
                textView.setTextColor(Color.parseColor("#C62828"))
            }
        }
    }

    private fun updateTextStyle(
        textView: TextView,
        isCompleted: Boolean
    ) {
        if (isCompleted) {
            textView.setTextColor(Color.parseColor("#9AA0A6"))
            textView.paintFlags =
                textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            textView.setTextColor(Color.parseColor("#202124"))
            textView.paintFlags =
                textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }
}
