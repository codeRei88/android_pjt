package com.trainning.jh_chronicles

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Firebase에서 읽으려면 모든 값에 기본값이 필요
 *
 * startDay의 뜻:
 * "아기가 접종가능한 날짜가 되기까지 생후 며칠이 걸리는가?"(ex: 아이생일 2025 1월1일 생후6개월부터 맞을수 있는 접종 사이를 day로 치환한 값)
 *
 * dDay의 뜻:
 * "아기가 태어난 날부터 접종 권장 기간이 끝날 때까지 며칠인가?"
 * 마감일이 따로 없는 접종은 시작일과 dDay가 같습니다.
 *
 * 화면에 표시할 남은 날짜:
 * vaccine.dDay - daysSinceBirth
 */
data class VaccineData(
    val id: String = "",
    val name: String = "",
    val recommendedAge: String = "",
    // 접종을 시작하는 생후 개월/추가 일수입니다.
    val targetMonths: Int = 0, // 접종가능 개월수 이 순서로 목록정리
    val extraDays: Int = 0, //접종까지 남은 일수
    val deadlineMonths: Int? = null, //접종 마감 기한 month단위
    val deadlineExtraDays: Int = 0,//접종 마감 기한 day단위
    var startDay: Int = 0, //출생 후 접종가능한 날짜가 되는 시기를 day로 치환
    var dDay: Int = 0, // 아기 출생날짜에서 접종 마감날짜를 뺀 값
    var complete: Boolean = false // 접종여부
) {

    //한 달을 무조건 30일로 계산하지 않고 실제 달력을 사용
    // 생일을 넣으면 각 접종주사를 언제부터 맞을수있는지 startDay를 리턴
    fun calculateStartDayFromBirth(birthDate: LocalDate): Int {
        val startDate = birthDate
            .plusMonths(targetMonths.toLong())
            .plusDays(extraDays.toLong())

        return ChronoUnit.DAYS.between(birthDate, startDate).toInt()
    }

    //생후기준 기한이 없는 접종이면 접종시작가능 일이 기한날짜가 되고 기한이 있다면 그 날짜가 기한날짜
    fun calculateDayFromBirth(birthDate: LocalDate): Int {
        val dueDate = if (deadlineMonths == null) {
            birthDate
                .plusMonths(targetMonths.toLong())
                .plusDays(extraDays.toLong())
        } else {
            birthDate
                .plusMonths(deadlineMonths.toLong())
                .plusDays(deadlineExtraDays.toLong())
        }

        return ChronoUnit.DAYS.between(birthDate, dueDate).toInt()
    }

    companion object {

        /*
         * 대한민국 질병관리청 표준 예방접종 일정입니다.
         *
         * 접종 가능 기간이 범위인 경우:
         * - 범위 시작일은 목록 정렬에 사용합니다.
         * - 범위 마지막 날은 화면의 D-day에 사용합니다.
         */
        fun standardSchedule(): List<VaccineData> = listOf(
            VaccineData(
                id = "hepb_1",
                name = "B형간염 1차",
                recommendedAge = "출생 직후 · 가능하면 24시간 이내",
                targetMonths = 0
            ),
            VaccineData(
                id = "bcg_1",
                name = "결핵 BCG",
                recommendedAge = "생후 4주 이내",
                targetMonths = 0,
                deadlineMonths = 0,
                deadlineExtraDays = 28
            ),
            VaccineData(
                id = "hepb_2",
                name = "B형간염 2차",
                recommendedAge = "생후 1개월",
                targetMonths = 1
            ),

            VaccineData(
                id = "dtap_1",
                name = "DTaP 1차",
                recommendedAge = "생후 2개월 · 디프테리아/파상풍/백일해",
                targetMonths = 2
            ),
            VaccineData(
                id = "ipv_1",
                name = "폴리오 IPV 1차",
                recommendedAge = "생후 2개월",
                targetMonths = 2
            ),
            VaccineData(
                id = "hib_1",
                name = "Hib 1차",
                recommendedAge = "생후 2개월 · b형 헤모필루스 인플루엔자",
                targetMonths = 2
            ),
            VaccineData(
                id = "pcv_1",
                name = "폐렴구균 PCV 1차",
                recommendedAge = "생후 2개월",
                targetMonths = 2
            ),
            VaccineData(
                id = "rotavirus_1",
                name = "로타바이러스 1차",
                recommendedAge = "생후 2개월 · 늦어도 생후 15주 전 시작",
                targetMonths = 2,
                deadlineMonths = 0,
                deadlineExtraDays = 104
            ),

            VaccineData(
                id = "dtap_2",
                name = "DTaP 2차",
                recommendedAge = "생후 4개월",
                targetMonths = 4
            ),
            VaccineData(
                id = "ipv_2",
                name = "폴리오 IPV 2차",
                recommendedAge = "생후 4개월",
                targetMonths = 4
            ),
            VaccineData(
                id = "hib_2",
                name = "Hib 2차",
                recommendedAge = "생후 4개월",
                targetMonths = 4
            ),
            VaccineData(
                id = "pcv_2",
                name = "폐렴구균 PCV 2차",
                recommendedAge = "생후 4개월",
                targetMonths = 4
            ),
            VaccineData(
                id = "rotavirus_2",
                name = "로타바이러스 2차",
                recommendedAge = "생후 4개월 · 모든 접종은 8개월 안에 완료",
                targetMonths = 4,
                deadlineMonths = 8
            ),

            VaccineData(
                id = "hepb_3",
                name = "B형간염 3차",
                recommendedAge = "생후 6개월",
                targetMonths = 6
            ),
            VaccineData(
                id = "dtap_3",
                name = "DTaP 3차",
                recommendedAge = "생후 6개월",
                targetMonths = 6
            ),
            VaccineData(
                id = "ipv_3",
                name = "폴리오 IPV 3차",
                recommendedAge = "생후 6~18개월",
                targetMonths = 6,
                deadlineMonths = 18
            ),
            VaccineData(
                id = "hib_3",
                name = "Hib 3차",
                recommendedAge = "생후 6개월",
                targetMonths = 6
            ),
            VaccineData(
                id = "pcv_3",
                name = "폐렴구균 PCV 3차",
                recommendedAge = "생후 6개월",
                targetMonths = 6
            ),
            VaccineData(
                id = "rotavirus_3",
                name = "로타바이러스 3차",
                recommendedAge = "생후 6개월 · 5가(로타텍), 8개월 안에 완료",
                targetMonths = 6,
                deadlineMonths = 8
            ),
            VaccineData(
                id = "influenza_first_1",
                name = "인플루엔자 첫해 1차",
                recommendedAge = "생후 6개월부터 · 이후 매년 접종",
                targetMonths = 6
            ),
            VaccineData(
                id = "influenza_first_2",
                name = "인플루엔자 첫해 2차",
                recommendedAge = "첫 접종 4주 후 · 첫해 2회 대상인 경우",
                targetMonths = 6,
                extraDays = 28
            ),

            VaccineData(
                id = "hib_4",
                name = "Hib 4차",
                recommendedAge = "생후 12~15개월",
                targetMonths = 12,
                deadlineMonths = 15
            ),
            VaccineData(
                id = "pcv_4",
                name = "폐렴구균 PCV 4차",
                recommendedAge = "생후 12~15개월",
                targetMonths = 12,
                deadlineMonths = 15
            ),
            VaccineData(
                id = "mmr_1",
                name = "MMR 1차",
                recommendedAge = "생후 12~15개월 · 홍역/유행성이하선염/풍진",
                targetMonths = 12,
                deadlineMonths = 15
            ),
            VaccineData(
                id = "varicella_1",
                name = "수두",
                recommendedAge = "생후 12~15개월 · 1회",
                targetMonths = 12,
                deadlineMonths = 15
            ),
            VaccineData(
                id = "hepa_1",
                name = "A형간염 1차",
                recommendedAge = "생후 12~23개월",
                targetMonths = 12,
                deadlineMonths = 23
            ),

            /*
             * 일본뇌염은 불활성화 또는 생백신 중 한 종류를 선택합니다.
             * 두 종류를 모두 맞는 일정이 아닙니다.
             */
            VaccineData(
                id = "jev_inactivated_1",
                name = "일본뇌염 1차 [불활성화 선택]",
                recommendedAge = "생후 12~23개월 · 생백신 일정과 둘 중 하나",
                targetMonths = 12,
                deadlineMonths = 23
            ),
            VaccineData(
                id = "jev_live_1",
                name = "일본뇌염 1차 [생백신 선택]",
                recommendedAge = "생후 12~23개월 · 불활성화 일정과 둘 중 하나",
                targetMonths = 12,
                deadlineMonths = 23
            ),
            VaccineData(
                id = "jev_inactivated_2",
                name = "일본뇌염 2차 [불활성화]",
                recommendedAge = "1차 약 1개월 후",
                targetMonths = 13
            ),

            VaccineData(
                id = "dtap_4",
                name = "DTaP 4차",
                recommendedAge = "생후 15~18개월",
                targetMonths = 15,
                deadlineMonths = 18
            ),
            VaccineData(
                id = "hepa_2",
                name = "A형간염 2차",
                recommendedAge = "1차 접종 후 최소 6개월",
                targetMonths = 18
            ),
            VaccineData(
                id = "jev_inactivated_3",
                name = "일본뇌염 3차 [불활성화]",
                recommendedAge = "불활성화 2차 약 11개월 후",
                targetMonths = 24
            ),
            VaccineData(
                id = "jev_live_2",
                name = "일본뇌염 2차 [생백신]",
                recommendedAge = "생백신 1차 12개월 후",
                targetMonths = 24
            ),

            VaccineData(
                id = "dtap_5",
                name = "DTaP 5차",
                recommendedAge = "만 4~6세",
                targetMonths = 48,
                deadlineMonths = 72
            ),
            VaccineData(
                id = "ipv_4",
                name = "폴리오 IPV 4차",
                recommendedAge = "만 4~6세",
                targetMonths = 48,
                deadlineMonths = 72
            ),
            VaccineData(
                id = "mmr_2",
                name = "MMR 2차",
                recommendedAge = "만 4~6세",
                targetMonths = 48,
                deadlineMonths = 72
            ),
            VaccineData(
                id = "jev_inactivated_4",
                name = "일본뇌염 4차 [불활성화]",
                recommendedAge = "만 6세",
                targetMonths = 72
            ),
            VaccineData(
                id = "tdap_6",
                name = "Tdap 6차",
                recommendedAge = "만 11~12세",
                targetMonths = 132,
                deadlineMonths = 144
            ),
            VaccineData(
                id = "jev_inactivated_5",
                name = "일본뇌염 5차 [불활성화]",
                recommendedAge = "만 12세",
                targetMonths = 144
            ),
            VaccineData(
                id = "hpv_1",
                name = "HPV 1차",
                recommendedAge = "만 12세 전후 · 국가 지원 대상 확인",
                targetMonths = 144
            ),
            VaccineData(
                id = "hpv_2",
                name = "HPV 2차",
                recommendedAge = "1차 접종 6~12개월 후",
                targetMonths = 150,
                deadlineMonths = 156
            )
        )
    }
}
