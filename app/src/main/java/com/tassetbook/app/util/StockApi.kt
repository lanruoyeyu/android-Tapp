package com.tassetbook.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 股票现价联网获取（东方财富公开行情接口，无需鉴权）。
 *
 * 支持按股票代码（6位数字）或名称/拼音搜索获取现价：
 * 1. 名称/关键词 → searchapi 搜索 → QuoteID（如 1.601377）
 * 2. QuoteID → push2 行情接口 → 现价（f43 / 10^f59）
 *
 * 网络策略：先公网直连；若设备无外网，则回落宿主机中转
 * （开发调试用，需 adb reverse tcp:18080 tcp:18080）。
 */
object StockApi {

    /** 日K收盘价数据点 */
    data class DayClose(val date: LocalDate, val close: Double)

    suspend fun fetchPrice(keyword: String): Double? = withContext(Dispatchers.IO) {
        fetchPriceInternal(keyword, viaProxy = false)
            ?: fetchPriceInternal(keyword, viaProxy = true)
    }

    /**
     * 拉取 [from, to] 区间的日K收盘价（前复权），按日期升序。
     * 用于统计页走势图收盘价曲线；失败（无外网等）返回 null，UI 回落到现价水平线。
     */
    suspend fun fetchDailyCloses(keyword: String, from: LocalDate, to: LocalDate): List<DayClose>? =
        withContext(Dispatchers.IO) {
            fetchDailyClosesInternal(keyword, from, to, viaProxy = false)
                ?: fetchDailyClosesInternal(keyword, from, to, viaProxy = true)
        }

    /** 是否处于交易时段（9:00-15:00），此时段内每 10s 刷新，其他时段只取一次 */
    fun inTradingHours(now: java.time.LocalTime = java.time.LocalTime.now()): Boolean {
        val start = java.time.LocalTime.of(9, 0)
        val end = java.time.LocalTime.of(15, 0)
        return !now.isBefore(start) && !now.isAfter(end)
    }

    private fun fetchPriceInternal(keyword: String, viaProxy: Boolean): Double? {
        return try {
            val quoteId = resolveQuoteId(keyword, viaProxy) ?: run {
                android.util.Log.w("StockApi", "resolveQuoteId returned null for '$keyword' (proxy=$viaProxy)")
                return null
            }
            fetchByQuoteId(quoteId, viaProxy)
        } catch (e: Exception) {
            android.util.Log.w("StockApi", "fetchPrice failed for '$keyword' (proxy=$viaProxy): ${e.message}")
            null
        }
    }

    private fun fetchDailyClosesInternal(keyword: String, from: LocalDate, to: LocalDate, viaProxy: Boolean): List<DayClose>? {
        return try {
            val quoteId = resolveQuoteId(keyword, viaProxy) ?: return null
            val beg = from.format(DateTimeFormatter.BASIC_ISO_DATE)
            val end = to.format(DateTimeFormatter.BASIC_ISO_DATE)
            val urlStr = if (viaProxy) {
                "http://127.0.0.1:18080/kline?secid=$quoteId&beg=$beg&end=$end"
            } else {
                "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=$quoteId" +
                    "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56" +
                    "&klt=101&fqt=1&beg=$beg&end=$end"
            }
            val conn = openConn(urlStr, readMs = 8000)
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseKlines(body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w("StockApi", "fetchDailyCloses failed (proxy=$viaProxy): ${e.message}")
            null
        }
    }

    /** 名称/代码 → 东财 QuoteID（如 1.601377） */
    private fun resolveQuoteId(keyword: String, viaProxy: Boolean): String? {
        val kw = keyword.trim()
        return if (kw.matches(Regex("\\d{6}"))) guessQuoteId(kw) else searchQuoteId(kw, viaProxy)
    }

    /**
     * 解析日K响应：klines 元素形如 "2026-09-17,5.88,5.85,5.91,5.83,487986"
     * （日期,开盘,收盘,最高,最低,成交量）。抽出便于单测。
     */
    // 用正则而非 org.json：东财响应格式固定，且本地 JVM 单测里 Android 的 JSONObject 是空壳
    private val klinesBlockRegex = Regex("\"klines\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
    private val quotedItemRegex = Regex("\"([^\"]+)\"")

    internal fun parseKlines(body: String): List<DayClose>? {
        val block = klinesBlockRegex.find(body)?.groupValues?.get(1) ?: return null
        val out = ArrayList<DayClose>()
        for (m in quotedItemRegex.findAll(block)) {
            val parts = m.groupValues[1].split(",")
            if (parts.size < 3) continue
            val d = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: continue
            val c = parts[2].toDoubleOrNull() ?: continue
            if (c > 0) out.add(DayClose(d, c))
        }
        return out.ifEmpty { null }
    }

    private fun openConn(urlStr: String, connectMs: Int = 8000, readMs: Int = 8000): HttpURLConnection {
        return (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
    }

    /** 用 QuoteID（如 1.601377）查现价 */
    private fun fetchByQuoteId(quoteId: String, viaProxy: Boolean): Double? {
        val urlStr = if (viaProxy) {
            "http://127.0.0.1:18080/quote?secid=$quoteId"
        } else {
            "https://push2.eastmoney.com/api/qt/stock/get?secid=$quoteId&fields=f43,f57,f58,f59&invt=2"
        }
        val conn = openConn(urlStr)
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("StockApi", "quote response for $quoteId: ${body.take(200)}")
            val data = JSONObject(body).optJSONObject("data") ?: run {
                android.util.Log.w("StockApi", "no data object in quote response for $quoteId")
                return null
            }
            val price = data.optInt("f43", Int.MIN_VALUE)
            val scale = data.optInt("f59", 2)
            if (price == Int.MIN_VALUE || price <= 0) {
                android.util.Log.w("StockApi", "invalid price f43=$price for $quoteId")
                return null
            }
            return price / Math.pow(10.0, scale.toDouble())
        } finally {
            conn.disconnect()
        }
    }

    /** 名称/拼音搜索 → QuoteID */
    private fun searchQuoteId(keyword: String, viaProxy: Boolean): String? {
        val enc = URLEncoder.encode(keyword, "UTF-8")
        val urlStr = if (viaProxy) {
            "http://127.0.0.1:18080/search?input=$enc"
        } else {
            "https://searchapi.eastmoney.com/api/suggest/get?input=$enc&type=14&token=D43BF722C8E33BDC906FB84D85E326E8&count=5"
        }
        val conn = openConn(urlStr)
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("StockApi", "search response for '$keyword': ${body.take(200)}")
            val arr = JSONObject(body)
                .optJSONObject("QuotationCodeTable")
                ?.optJSONArray("Data") ?: run {
                android.util.Log.w("StockApi", "search returned no data for '$keyword'")
                return null
            }
            // 优先沪深 A 股
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val qid = o.optString("QuoteID", "")
                if ((qid.startsWith("0.") || qid.startsWith("1.")) &&
                    o.optString("Classify", "") == "AStock") return qid
            }
            for (i in 0 until arr.length()) {
                val qid = arr.optJSONObject(i)?.optString("QuoteID", "") ?: continue
                if (qid.startsWith("0.") || qid.startsWith("1.")) return qid
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** 6 位代码推断市场：6 开头沪市，0/3 开头深市 */
    private fun guessQuoteId(code: String): String? = when (code.first()) {
        '6' -> "1.$code"
        '0', '3' -> "0.$code"
        else -> null
    }
}
