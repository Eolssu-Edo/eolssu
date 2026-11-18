package com.ssafy.eolssu.core.model

/**
 * 한국 표준시(UTC+9) 기준 날짜·시각. 연습 기록의 '10월 20일 오후 3:20' 같은 표시에 쓴다.
 * 서비스 대상이 국내라 표준시를 고정하고, 날짜 라이브러리 없이 계산한다.
 */
data class KoreanDateTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {

    fun isSameDay(other: KoreanDateTime): Boolean = year == other.year && month == other.month && day == other.day

    /** 1970-01-01부터의 날 수. */
    val epochDay: Long get() = daysFromCivil(year, month, day)

    /** 요일(월요일 0 … 일요일 6). */
    val dayOfWeek: Int get() = ((epochDay + 3).mod(7L)).toInt()

    /** 같은 주(월~일)인지. */
    fun isSameWeek(other: KoreanDateTime): Boolean = epochDay - dayOfWeek == other.epochDay - other.dayOfWeek

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

        /** 1970-01-01부터의 날 수 → 그날 0시. */
        fun ofEpochDay(epochDay: Long): KoreanDateTime {
            val (y, m, d) = civilFromDays(epochDay)
            return KoreanDateTime(y, m, d, 0, 0)
        }

        /** (연, 월, 일) → 1970-01-01부터의 날 수. Howard Hinnant의 days_from_civil. */
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = y.floorDiv(400L)
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
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
