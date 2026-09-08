package com.tassetbook.app.domain

import com.tassetbook.app.data.Asset
import com.tassetbook.app.data.Trade
import com.tassetbook.app.util.Dates
import com.tassetbook.app.util.Money
import java.time.LocalDate

/**
 * 做T利润（记录口径，对齐微信小程序 app.js calculateAssetProfit）：
 *
 * 总/本月/今日利润 = 该资产全部交易（含已归档）记录的 t.profit 之和。
 * - 新建正T买入：profit = 0；
 * - 新建反T卖出：profit 按当时持仓成本价暂估 = (卖价 - 成本价) * 股数 - 卖出费用；
 * - 补单形成 T完成（buyToSell / sellToBuy）：profit = 卖出净额 - 买入总额；
 * - 已归档交易：取归档时固化的 profit。
 *
 * 即统计页顶部数字与每笔交易卡片上显示的盈亏严格可对账（逐笔相加）。
 * 利润归属日：tradeDate 优先，其次买入日、卖出日。
 *
 * 注意：利润日历另有限定——只统计 T完成/归档交易（见 StatisticsCalc）。
 */
object RealizedProfit {

    data class Event(
        val assetId: String,
        val assetName: String,
        val profit: Double,
        val date: LocalDate?
    )

    /** 该资产全部交易的已记录利润事件（profit 为 0 的不产出事件） */
    fun events(asset: Asset, allTrades: List<Trade>): List<Event> =
        allTrades.mapNotNull { t ->
            val p = Money.round2(t.profit)
            if (p == 0.0) return@mapNotNull null
            Event(
                assetId = asset.id,
                assetName = asset.name,
                profit = p,
                date = Dates.parseDate(t.tradeDate)
                    ?: Dates.parseDate(t.buyTradeDate)
                    ?: Dates.parseDate(t.sellTradeDate)
            )
        }

    fun total(asset: Asset, allTrades: List<Trade>): Double =
        Money.round2(events(asset, allTrades).sumOf { it.profit })
}
