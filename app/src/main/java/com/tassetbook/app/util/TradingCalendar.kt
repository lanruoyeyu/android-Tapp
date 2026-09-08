package com.tassetbook.app.util

import java.time.LocalDate
import java.time.DayOfWeek

/**
 * A股交易日历。
 * 正常情况下交易日以日K接口实际返回的日期为准（自动反映临时休市/调休）；
 * 日K尚未加载或离线时，用「周一至周五 - 法定节假日」兜底。
 * 注意：A股在调休上班的周末仍然休市，故周末一律不交易。
 * 节假日表来源：上交所 2026 年休市安排（上证公告〔2025〕45号）。
 */
object TradingCalendar {

    private val holidays2026: Set<LocalDate> = buildSet {
        fun range(from: LocalDate, to: LocalDate) {
            var d = from
            while (!d.isAfter(to)) { add(d); d = d.plusDays(1) }
        }
        // 元旦 1/1-1/3
        range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
        // 春节 2/15-2/23
        range(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23))
        // 清明 4/4-4/6
        range(LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6))
        // 劳动节 5/1-5/5
        range(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5))
        // 端午 6/19-6/21
        range(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21))
        // 中秋 9/25-9/27
        range(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27))
        // 国庆 10/1-10/7
        range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))
    }

    /** 兜底判断：工作日且非法定节假日 */
    fun isTradingDay(d: LocalDate): Boolean =
        (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) && d !in holidays2026

    /** 返回 today 本身（若为交易日）或往前最近的交易日 */
    fun latestTradingDay(today: LocalDate): LocalDate {
        var d = today
        while (!isTradingDay(d)) {
            d = d.minusDays(1)
        }
        return d
    }

    /**
     * 截止 today、往前 weeks 周（自然日窗口）内的交易日，按日期升序。
     * @param knownTradingDays 日K接口返回的实际交易日（最高优先级）
     */
    fun recentTradingDays(
        today: LocalDate,
        weeks: Int,
        knownTradingDays: Collection<LocalDate>
    ): List<LocalDate> {
        val from = today.minusDays(weeks * 7L - 1)
        val known = knownTradingDays
            .filter { !it.isBefore(from) && !it.isAfter(today) }
            .sorted()
        if (known.isNotEmpty()) {
            // 盘中当天日K可能尚未生成：若今天确为交易日则补上（收盘值由实时现价兜底）
            return if (known.last() != today && isTradingDay(today)) known + today else known
        }
        // 离线/未加载兜底
        val result = ArrayList<LocalDate>()
        var d = from
        while (!d.isAfter(today)) {
            if (isTradingDay(d)) result.add(d)
            d = d.plusDays(1)
        }
        return result
    }
}
