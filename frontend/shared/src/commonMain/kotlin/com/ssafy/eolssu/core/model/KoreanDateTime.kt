package com.ssafy.eolssu.core.model

/**
 * 한국 표준시(UTC+9) 기준 날짜·시각. 연습 기록의 '10월 20일 오후 3:20' 같은 표시에 쓴다.
 * 서비스 대상이 국내라 표준시를 고정하고, 날짜 라이브러리 없이 계산한다.
 */
data class KoreanDateTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {

    fun isSameDay(other: KoreanDateTime): Boolean = year == other.year && month == other.month && day == other.day

    companion object {
        private const val OFFSET_MILLIS = 9 * 60 * 60 * 1000L

        /** 유닉스 시각(ms) → 한국 날짜·시각. */
        fun fromEpochMillis(epochMillis: Long): KoreanDateTime {
            val local = epochMillis + OFFSET_MILLIS
            val days = local.floorDiv(86_400_000L)
            val millisOfDay = local.mod(86_400_000L)
            val (y, m, d) = civilFromDays(days)
            return KoreanDateTime(y, m, d, (millisOfDay / 3_600_000L).toInt(), ((millisOfDay / 60_000L) % 60).toInt())
        }

        /** 1970-01-01부터의 날 수 → (연, 월, 일). Howard Hinnant의 civil_from_days. */
        private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
            val z = days + 719_468
            val era = z.floorDiv(146_097L)
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return Triple(y, m, d)
        }
    }
}
