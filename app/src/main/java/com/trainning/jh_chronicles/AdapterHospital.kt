package com.trainning.jh_chronicles

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trainning.jh_chronicles.databinding.OneRcHospitalBinding
class AdapterHospital(
    private val hospitalList: MutableList<Place>,
    private val onDialClick: (Place) -> Unit,
    private val onMapClick: (Place) -> Unit
) :
    RecyclerView.Adapter<AdapterHospital.HospitalViewHolder>() {

    inner class HospitalViewHolder(val binding: OneRcHospitalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val binding = OneRcHospitalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HospitalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = hospitalList[position]
        holder.binding.hospitalName.text = hospital.place_name

        // 도로명 주소가 비어 있으면 지번 주소를 대신 보여줍니다.
        holder.binding.hospitalAddress.text =
            hospital.road_address_name.ifBlank { hospital.address_name }

        // 1km 미만은 m, 1km 이상은 km로 바꿔 읽기 쉽게 보여줍니다.
        holder.binding.hospitalDistance.text = formatDistance(hospital.distance)

        // 카카오에 전화번호가 등록되지 않은 병원도 있으므로 빈칸 대신 안내 문구를 보여줍니다.
        holder.binding.hospitalPhone.text =
            hospital.phone.ifBlank { "전화번호 정보 없음" }

        // RecyclerView는 ViewHolder를 재사용하므로 항목을 묶을 때마다 현재 병원으로 클릭 이벤트를 다시 연결
        holder.binding.dialBtn.isEnabled = hospital.phone.isNotBlank()
        holder.binding.dialBtn.setOnClickListener {
            onDialClick(hospital)
        }
        holder.binding.openMapBtn.setOnClickListener {
            onMapClick(hospital)
        }

        if (hospital.place_name.contains("24") || hospital.place_name.contains("야간") || hospital.place_name.contains("365")) {
            // 조건 만족 시: 글씨를 두껍게(BOLD) 하고, 색상을 빨간색으로 변경
            holder.binding.hospitalName.setTypeface(null, Typeface.BOLD)
            holder.binding.hospitalName.setTextColor(Color.BLUE)
        } else {
            // 조건 불만족 시: 글씨를 원래대로(NORMAL) 돌리고, 색상을 검은색으로 복구
            holder.binding.hospitalName.setTypeface(null, Typeface.NORMAL)
            holder.binding.hospitalName.setTextColor(Color.BLACK)
        }
    }

    private fun formatDistance(distance: String): String {
        val distanceInMeters = distance.toIntOrNull() ?: return "거리 정보 없음"

        return if (distanceInMeters < 1000) {
            "${distanceInMeters}m"
        } else {
            String.format("%.1fkm", distanceInMeters / 1000.0)
        }
    }

    override fun getItemCount(): Int = hospitalList.size

    fun updateData(newList: List<Place>) {
        hospitalList.clear()
        hospitalList.addAll(newList)
        notifyDataSetChanged()
    }
}
