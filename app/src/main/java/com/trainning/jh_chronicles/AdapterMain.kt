package com.trainning.jh_chronicles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trainning.jh_chronicles.databinding.OneRcDiaryBinding

class AdapterMain(private val itemList : MutableList<ItemData>) :
    RecyclerView.Adapter<AdapterMain.ViewHolder>(){

        // 리사이클러뷰의 아이템 항목 하나에 대한 xml 인플레이트
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = OneRcDiaryBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ViewHolder(binding)
    }

    // 리사이클러뷰 1개 아이템 클릭시 클릭이벤트를 처리하기 위한 인터페이스
    // 클릭하면 어뎁터의 onBindViewHolder에서 감지하고 이를 액티비티에서 실행한다.
    // 인터페이스는 어뎁터의 클릭 감지를 바깥의 액티비티에 전달하기 위함이다.
    interface ItemClick {
        fun onClick(view : View, position: Int)
    }

    var itemClick : ItemClick? = null

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[position]
        if(itemClick != null) { // 메인엑티비티에서 itemclick 인터페이스가 구현이 됐고 어뎁터와 연결이 됐으면 밑에 코드를 실행하라
            holder.itemView.setOnClickListener { v->
                itemClick?.onClick(v, position)
            }
        }
        holder.binding.dateArea.text = item.date
        holder.binding.titleArea.text = item.title
        holder.binding.contentArea.text = item.content
    }

    override fun getItemCount(): Int = itemList.size

    class ViewHolder(val binding: OneRcDiaryBinding) : RecyclerView.ViewHolder(binding.root)

}
