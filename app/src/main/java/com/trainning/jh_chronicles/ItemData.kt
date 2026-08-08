package com.trainning.jh_chronicles

import kotlin.String
data class ItemData(
    val id : String = "",
    val date: String = "",
    val title: String = "",
    val content: String = ""
)

sealed class RecordData{
    data class EventData(
        val id : String = "",
        val date: String = "",
        val time: String = "",
        val eventDetail: String = "",
        val title: String = ""
    ) : RecordData()
    data class HeaderData(
        val date: String = ""
    ) : RecordData()
}

data class DailySummary(

    // 오늘 기록된 우유의 총량을 저장한다.
    val totalMilk: Int = 0,

    // 오늘 기록된 이유식의 총량을 저장한다.
    val totalMeal: Int = 0,

    // 오늘 기록된 수면시간 합계를 분 단위로 저장한다.
    val totalSleepMinutes: Int = 0,

    // 오늘 기록된 배변 횟수를 저장한다.
    val poopCount: Int = 0
)

data class AvgSummary(

    val avgMilk: Int = 0,

    val avgMeal: Int = 0,

    val avgSleepMinutes: Int = 0,

    val avgPoopCount: Double = 0.0,

    val countDay: Int = 0
)

