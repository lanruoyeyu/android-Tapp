package com.tassetbook.app.domain

import com.tassetbook.app.data.Asset
import com.tassetbook.app.data.Settings
import com.tassetbook.app.data.Trade
import com.tassetbook.app.util.Dates
import com.tassetbook.app.util.Money

/**
 * 数据备份/导入，逐行对齐 app.js jsonToCsv / csvToJson / parseCsvLine / convertRowToObject。
 * CSV 分段：===资产数据=== / ===交易记录=== / ===交易明细表===；导出带 UTF-8 BOM。
 * JSON 备份格式：{version, exportTime, exportSettings, assets, trades[, settings]}。
 */
object Backup {

    class BackupException(message: String) : Exception(message)

    data class BackupResult(
        val assets: List<Asset>,
        val trades: List<Trade>,
        val settings: Settings?
    )

    // ---------------- 导出 ----------------

    fun jsonToCsv(assets: List<Asset>, trades: List<Trade>): String {
        val sb = StringBuilder()

        // === 资产数据 ===
        if (assets.isNotEmpty()) {
            sb.append("===资产数据===\n")
            sb.append(
                listOf(
                    "ID", "名称", "市场", "交易所", "底仓股数", "底仓成本", "底仓单价",
                    "总持仓成本", "持仓股数", "成本价", "现价", "市值", "收益", "备注", "创建时间"
                ).joinToString(",")
            ).append("\n")
            for (a in assets) {
                val baseTotalCost = a.baseTotalCost
                val baseUnit = a.baseUnit
                val totalCost = a.totalCost
                val costPerShare = a.costPerShare
                val currentPrice = a.currentPrice
                val shares = a.shares
                val currentProfit = shares * (currentPrice - costPerShare)
                sb.append(
                    listOf(
                        a.id,
                        "\"${a.name}\"",
                        a.market ?: "",
                        a.exchange ?: "",
                        a.baseShares.toString(),
                        Money.fixed(baseTotalCost, 2),
                        Money.fixed(baseUnit, 2),
                        Money.fixed(totalCost, 2),
                        shares.toString(),
                        Money.fixed(costPerShare, 3),
                        Money.fixed(currentPrice, 2),
                        Money.fixed(currentPrice * shares, 2),
                        Money.fixed(currentProfit, 2),
                        "\"${a.remark ?: ""}\"",
                        a.createTime
                    ).joinToString(",")
                ).append("\n")
            }
        }

        // === 交易记录 ===
        if (trades.isNotEmpty()) {
            sb.append("\n===交易记录===\n")
            sb.append(
                listOf(
                    "ID", "资产ID", "资产名称", "交易状态",
                    "买入价", "买入股数", "买入佣金", "买入过户费", "买入手续费", "买入总金额", "买入日期",
                    "卖出价", "卖出股数", "卖出佣金", "卖出过户费", "卖出印花税", "卖出手续费", "卖出总金额", "卖出日期",
                    "价格", "股数", "总金额", "佣金", "过户费", "印花税", "利润", "备注", "交易日期", "交易时间", "是否归档"
                ).joinToString(",")
            ).append("\n")
            for (t in trades) {
                val buyPrice = t.buyPrice
                val buyCommission = t.buyCommission
                val buyTransferFee = t.buyTransferFee
                val buyTotalFee = if (t.buyTotalFee != 0.0) t.buyTotalFee else buyCommission + buyTransferFee
                val buyTotal = if (t.isArchived && t.buyTotal != 0.0) t.buyTotal
                else if (t.buyTotal != 0.0) t.buyTotal else buyPrice * t.buyShares
                val sellPrice = t.sellPrice
                val sellCommission = t.sellCommission
                val sellTransferFee = t.sellTransferFee
                val sellStampDuty = t.sellStampDuty
                val sellTotalFee = if (t.sellTotalFee != 0.0) t.sellTotalFee
                else sellCommission + sellTransferFee + sellStampDuty
                val sellTotal = if (t.isArchived && t.sellTotal != 0.0) t.sellTotal
                else if (t.sellTotal != 0.0) t.sellTotal else sellPrice * t.sellShares - sellTotalFee
                val price = if (t.price != 0.0) t.price else (if (buyPrice != 0.0) buyPrice else sellPrice)
                val shares = if (t.shares != 0) t.shares else (if (t.buyShares != 0) t.buyShares else t.sellShares)
                val total = if (t.total != 0.0) t.total else (if (buyTotal != 0.0) buyTotal else sellTotal)
                val commission = if (t.commission != 0.0) t.commission else (if (buyCommission != 0.0) buyCommission else sellCommission)
                val transferFee = if (t.transferFee != 0.0) t.transferFee else (if (buyTransferFee != 0.0) buyTransferFee else sellTransferFee)
                val stampDuty = if (t.stampDuty != 0.0) t.stampDuty else sellStampDuty
                val profit = t.profit
                val tradeDate = formatCsvDate(t.tradeDate)
                sb.append(
                    listOf(
                        t.id,
                        t.assetId,
                        "\"${t.assetName}\"",
                        t.tradeStatus,
                        Money.fixed(buyPrice, 2),
                        t.buyShares.toString(),
                        Money.fixed(buyCommission, 2),
                        Money.fixed(buyTransferFee, 2),
                        Money.fixed(buyTotalFee, 2),
                        Money.fixed(buyTotal, 2),
                        formatCsvDate(t.buyTradeDate),
                        Money.fixed(sellPrice, 2),
                        t.sellShares.toString(),
                        Money.fixed(sellCommission, 2),
                        Money.fixed(sellTransferFee, 2),
                        Money.fixed(sellStampDuty, 2),
                        Money.fixed(sellTotalFee, 2),
                        Money.fixed(sellTotal, 2),
                        formatCsvDate(t.sellTradeDate),
                        Money.fixed(price, 2),
                        shares.toString(),
                        Money.fixed(total, 2),
                        Money.fixed(commission, 2),
                        Money.fixed(transferFee, 2),
                        Money.fixed(stampDuty, 2),
                        Money.fixed(profit, 2),
                        "\"${t.remark}\"",
                        tradeDate,
                        t.tradeTime,
                        if (t.isArchived) "是" else "否"
                    ).joinToString(",")
                ).append("\n")
            }
        }

        // === 交易明细表 ===
        if (assets.isNotEmpty()) {
            sb.append("\n===交易明细表===\n")
            sb.append("日期,成本单价,股数,日交易类型,日交易单价,日交易股数\n")
            for (a in assets) {
                val assetTrades = trades.filter { it.assetId == a.id }.sortedWith(compareBy(
                    { Dates.parseDate(it.tradeDate.ifBlank { it.buyTradeDate.ifBlank { it.sellTradeDate } }) }
                ))
                var currentCost = a.totalCost
                var currentShares = a.shares
                var currentCostPerShare = if (currentShares > 0) currentCost / currentShares else 0.0
                for (t in assetTrades) {
                    val d = Dates.parseDate(t.tradeDate.ifBlank { t.buyTradeDate.ifBlank { t.sellTradeDate } })
                    val tradeDate = d?.let { Dates.formatCn(it) } ?: Dates.todayLocale()

                    if (t.tradeStatus == "buyNoSell" || t.tradeStatus == "buyToSell") {
                        val bp = t.buyPrice
                        val bs = t.buyShares
                        val bt = if (t.buyTotal != 0.0) t.buyTotal else bp * bs
                        currentCost += bt
                        currentShares += bs
                        currentCostPerShare = if (currentShares > 0) currentCost / currentShares else 0.0
                        sb.append("$tradeDate,${Money.fixed(currentCostPerShare, 3)},$currentShares,买入,${Money.fixed(bp, 3)},$bs\n")
                    }
                    if (t.tradeStatus == "sellNoBuy" || t.tradeStatus == "sellToBuy") {
                        val sp = t.sellPrice
                        val ss = t.sellShares
                        val sellAmount = sp * ss
                        currentCost -= sellAmount
                        currentShares -= ss
                        currentCostPerShare = if (currentShares > 0) currentCost / currentShares else 0.0
                        sb.append("$tradeDate,${Money.fixed(currentCostPerShare, 3)},$currentShares,卖出,${Money.fixed(sp, 3)},$ss\n")
                    }
                    if (t.tradeStatus == "buyToSell" || t.tradeStatus == "sellToBuy") {
                        val vol = if (t.buyShares != 0) t.buyShares else t.sellShares
                        sb.append("$tradeDate,${Money.fixed(currentCostPerShare, 3)},$currentShares,T完,0.000,$vol\n")
                    }
                }
            }
        }

        return sb.toString()
    }

    private fun formatCsvDate(dateVal: String): String {
        if (dateVal.isBlank()) return ""
        val d = Dates.parseDate(dateVal) ?: return dateVal
        return "${d.year}/${String.format("%02d", d.monthValue)}/${String.format("%02d", d.dayOfMonth)}"
    }

    // ---------------- 导入 ----------------

    /** 解析 CSV 备份；非法格式抛 BackupException */
    fun csvToJson(csv: String): BackupResult {
        if (!csv.contains("===资产数据===") && !csv.contains("===交易记录===")) {
            throw BackupException("文件格式错误：不是有效的 T-account book 备份文件（缺少数据段标记）")
        }
        val lines = csv.split("\n")
        var section = ""
        var headers: List<String> = emptyList()
        val assets = mutableListOf<Asset>()
        val trades = mutableListOf<Trade>()

        for (raw in lines) {
            val line = raw.trim().removeSuffix("\r")
            if (line.isEmpty()) continue
            if (line.startsWith("===")) {
                section = when {
                    line.contains("资产数据") -> "assets"
                    line.contains("交易记录") -> "trades"   // 交易明细表也含“交易记录”，先匹配明细
                    else -> ""
                }
                if (line.contains("交易明细表")) section = ""
                headers = emptyList()
                continue
            }
            if (section.isEmpty()) continue
            if (headers.isEmpty()) {
                headers = line.split(",").map { it.trim() }
                continue
            }
            val values = parseCsvLine(line)
            val row = HashMap<String, String>()
            headers.forEachIndexed { i, h -> row[h] = values.getOrNull(i) ?: "" }
            val obj = convertRow(section, row) ?: continue
            when (section) {
                "assets" -> assets.add(obj as Asset)
                "trades" -> trades.add(obj as Trade)
            }
        }

        val validAssets = assets.filter { it.name.isNotBlank() }
        val validTrades = trades.filter { it.assetId.isNotBlank() }
        return BackupResult(validAssets, validTrades, null)
    }

    /** 解析 CSV 行（处理双引号包裹的含逗号字段），对齐 parseCsvLine */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun isValidRow(section: String, row: Map<String, String>): Boolean {
        val isEmpty = row.values.all { it.isBlank() }
        if (isEmpty) return false
        if (section == "assets") {
            val hasName = row["名称"]?.isNotBlank() == true
            val hasCode = row["代码"]?.isNotBlank() == true
            if (!hasName && !hasCode) return false
        }
        if (section == "trades") {
            val hasAssetId = row["资产ID"]?.isNotBlank() == true
            val hasAssetName = row["资产名称"]?.trim('"')?.isNotBlank() == true
            if (!hasAssetId && !hasAssetName) return false
        }
        return true
    }

    private fun convertRow(section: String, row: Map<String, String>): Any? {
        if (!isValidRow(section, row)) return null
        fun s(key: String) = row[key]?.trim('"')?.trim() ?: ""
        fun d(key: String) = s(key).toDoubleOrNull() ?: 0.0
        fun i(key: String) = s(key).toIntOrNull() ?: (d(key).toInt())

        return when (section) {
            "assets" -> Asset(
                id = s("ID").ifBlank { System.currentTimeMillis().toString() },
                name = s("名称"),
                market = s("市场").ifBlank { null },
                exchange = s("交易所").ifBlank { null },
                baseShares = i("底仓股数"),
                baseTotalCost = d("底仓成本"),
                baseUnit = d("底仓单价"),
                totalCost = d("总持仓成本"),
                shares = i("持仓股数"),
                costPerShare = d("成本价"),
                currentPrice = d("现价"),
                remark = s("备注").ifBlank { null },
                createTime = s("创建时间").ifBlank { Dates.nowString() },
                updateTime = Dates.nowString(),
                baseProfit = d("底仓利润")
            )
            "trades" -> Trade(
                id = s("ID").ifBlank { System.currentTimeMillis().toString() },
                assetId = s("资产ID"),
                assetName = s("资产名称"),
                tradeStatus = s("交易状态").ifBlank { "buyNoSell" },
                buyPrice = d("买入价"),
                buyShares = i("买入股数"),
                buyCommission = d("买入佣金"),
                buyTransferFee = d("买入过户费"),
                buyTotalFee = d("买入手续费"),
                buyTotal = d("买入总金额"),
                buyTradeDate = s("买入日期"),
                sellPrice = d("卖出价"),
                sellShares = i("卖出股数"),
                sellCommission = d("卖出佣金"),
                sellTransferFee = d("卖出过户费"),
                sellStampDuty = d("卖出印花税"),
                sellTotalFee = d("卖出手续费"),
                sellTotal = d("卖出总金额"),
                sellTradeDate = s("卖出日期"),
                price = d("价格").let { if (it != 0.0) it else (d("买入价").let { b -> if (b != 0.0) b else d("卖出价") }) },
                shares = i("股数").let { if (it != 0) it else (i("买入股数").let { b -> if (b != 0) b else i("卖出股数") }) },
                total = d("总金额").let { if (it != 0.0) it else (d("买入总金额").let { b -> if (b != 0.0) b else d("卖出总金额") }) },
                commission = d("佣金").let { if (it != 0.0) it else (d("买入佣金").let { b -> if (b != 0.0) b else d("卖出佣金") }) },
                transferFee = d("过户费").let { if (it != 0.0) it else (d("买入过户费").let { b -> if (b != 0.0) b else d("卖出过户费") }) },
                stampDuty = d("印花税").let { if (it != 0.0) it else d("卖出印花税") },
                profit = d("利润"),
                remark = s("备注"),
                tradeDate = s("交易日期").ifBlank { Dates.todayLocale() },
                tradeTime = s("交易时间").ifBlank { Dates.nowString() },
                updateTime = Dates.nowString(),
                isArchived = s("是否归档") == "是"
            )
            else -> null
        }
    }

    // ---------------- 统计辅助 ----------------

    /** 统计 CSV 中各段数据条数（减去表头），对齐 mine.js parseSectionCount */
    fun sectionCounts(csv: String): Pair<Int, Int> {
        fun count(marker: String): Int {
            val regex = Regex("\n===$marker===\n([\\s\\S]*?)(?=\n===|$)")
            val m = regex.find("\n" + csv.trim()) ?: return 0
            val lines = m.groupValues[1].split("\n").filter { it.trim().isNotEmpty() && !it.trim().startsWith("===") }
            return maxOf(0, lines.size - 1)
        }
        return count("资产数据") to count("交易记录")
    }
}
