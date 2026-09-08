package com.tassetbook.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期工具。小程序用 new Date().toLocaleDateString()（中文环境为 yyyy/M/d）存储 tradeDate，
 * 解析需兼容 yyyy/M/d、yyyy-M-d、ISO 等多种格式。
 */
object Dates {
    private val DATE_REGEX = Regex("""(\d{4})[/\-年.](\d{1,2})[/\-月.](\d{1,2})""")

    /** 宽松解析日期字符串为 LocalDate；失败返回 null */
    fun parseDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        val m = DATE_REGEX.find(s) ?: return null
        return try {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } catch (e: Exception) {
            null
        }
    }

    /** 小程序默认交易日期格式：yyyy/M/d（如 2026/9/4） */
    fun todayLocale(): String {
        val d = LocalDate.now()
        return "${d.year}/${d.monthValue}/${d.dayOfMonth}"
    }

    /** DatePicker 使用的 yyyy-MM-dd */
    fun todayIso(): String = LocalDate.now().toString()

    /** yyyy-MM-dd -> yyyy/M/d（小程序存储/CSV 习惯） */
    fun isoToLocale(iso: String): String {
        val d = parseDate(iso) ?: return iso
        return "${d.year}/${d.monthValue}/${d.dayOfMonth}"
    }

    /** yyyy/M/d -> yyyy-MM-dd（DatePicker 初始值） */
    fun localeToIso(locale: String?): String {
        val d = parseDate(locale) ?: return todayIso()
        return d.toString()
    }

    fun nowString(): String = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss", Locale.CHINA))

    /** 利润归属日期：取买入/卖出日期中较晚者（对齐 getCompleteTime） */
    fun profitDate(tradeDate: String?, buyDate: String?, sellDate: String?): LocalDate? {
        val bt = parseDate(buyDate ?: tradeDate)
        val st = parseDate(sellDate ?: tradeDate)
        return when {
            bt == null -> st
            st == null -> bt
            else -> if (bt.isAfter(st)) bt else st
        }
    }

    fun formatCn(d: LocalDate): String = "${d.year}/${d.monthValue}/${d.dayOfMonth}"
}
