package com.trainning.jh_chronicles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trainning.jh_chronicles.databinding.OneRvRecordEventBinding
import com.trainning.jh_chronicles.databinding.OneRvRecordHeaderBinding

class AdapterRecord(private val itemList: MutableList<RecordData>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object { // 하나의 리사클러 뷰에 디자인이 두개(xml이 두개) 이므로 각 itemView에 대한 타입 지정 선언
        private const val TYPE_HEADER = 0
        private const val TYPE_EVENT = 1
    }
    inner class HeaderViewHolder(val bindingHeader: OneRvRecordHeaderBinding) : RecyclerView.ViewHolder(bindingHeader.root)
    inner class EventViewHolder(val bindingEvent: OneRvRecordEventBinding) : RecyclerView.ViewHolder(bindingEvent.root)
            // xml이 2개 이므로 뷰홀더도 두개 선언

    override fun getItemViewType(position: Int): Int { // 데이터를 보고 그 데이터에 맞는 xml 번호를 반환하는 메서드
        return if (itemList[position] is RecordData.HeaderData) {
            TYPE_HEADER
        } else {
            TYPE_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val layoutInflater = LayoutInflater.from(parent.context)

        if(viewType == TYPE_HEADER) {
            val binding = OneRvRecordHeaderBinding.inflate(layoutInflater, parent, false)
            return HeaderViewHolder(binding)
        } else if (viewType == TYPE_EVENT) {
            val binding = OneRvRecordEventBinding.inflate(layoutInflater, parent, false)
            return  EventViewHolder(binding)
        } else {
            error("알 수 없는 viewType: $viewType")
        }
    }

    // 클릭 이벤트 처리 인터페이스 선언
    interface ItemClick {
        fun onClick(view : View, position: Int)
    }
    var itemClick : ItemClick? = null


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = itemList[position]
        if(itemClick != null) {
            holder.itemView.setOnClickListener { v ->
                itemClick?.onClick(v, position)
            }
        }
        when (holder) {
            is HeaderViewHolder -> {
                val headerItem = item as RecordData.HeaderData
                holder.bindingHeader.dateHeader.text = headerItem.date
            }
            is EventViewHolder -> {
                val eventItem = item as RecordData.EventData
                holder.bindingEvent.eventTime.text = eventItem.time
                holder.bindingEvent.eventTitle.text = eventItem.title
                holder.bindingEvent.eventDetail.text = eventItem.eventDetail
            }
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}