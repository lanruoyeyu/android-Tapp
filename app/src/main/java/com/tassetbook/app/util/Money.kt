package com.tassetbook.app.util

import kotlin.math.roundToLong

/** 数值/金额工具，口径对齐小程序 Math.round(x*10^n)/10^n 与 toFixed。 */
object Money {
    fun round(v: Double, decimals: Int): Double {
        val f = Math.pow(10.0, decimals.toDouble())
        return (Math.round(v * f).toDouble()) / f
    }

    fun round2(v: Double): Double = round(v, 2)
    fun round3(v: Double): Double = round(v, 3)
    fun round6(v: Double): Double = round(v, 6)

    /** 对应 JS toFixed(n)，负数不补正号 */
    fun fixed(v: Double, decimals: Int): String = String.format("%.${decimals}f", v)

    /** 小程序利润展示：正值前缀 '+'，负值自带 '-'；对应 formatProfit */
    fun signed(v: Double, decimals: Int = 2): String {
        val s = fixed(v, decimals)
        return if (v >= 0) "+$s" else s
    }

    /** 千分位金额，如 12,345.67 */
    fun currency(v: Double, decimals: Int = 2): String {
        val neg = v < 0
        val abs = if (neg) -v else v
        val s = fixed(abs, decimals)
        val parts = s.split(".")
        val intPart = parts[0].reversed().chunked(3).joinToString(",").reversed()
        val result = if (parts.size > 1) "$intPart.${parts[1]}" else intPart
        return if (neg) "-$result" else result
    }
}
