package com.tassetbook.app.domain

import com.tassetbook.app.util.Money
import kotlin.math.min

/**
 * 预估交易阶梯测算，对齐 estimate-trade.js calculateEstimates。
 */
object Estimate {
    data class Row(
        val index: Int,
        val price: String,
        val sellPrice: String,      // 本次卖出额
        val remainingShares: String,
        val totalPrice: String,     // 单价×剩余股数
        val totalRevenue: String,   // 累计卖出收入
        val profit: String,
        val profitNum: Double
    )

    /**
     * @param estimatedPrice 预估股价
     * @param costPrice 成本价
     * @param shares 总股数
     * @param tradeShares 单次卖出股数
     * @param ratio 阶梯比例（%，如 1.017）
     */
    fun calculate(
        estimatedPrice: Double,
        costPrice: Double,
        shares: Int,
        tradeShares: Int,
        ratio: Double
    ): List<Row> {
        if (estimatedPrice <= 0 || costPrice <= 0 || shares <= 0 || tradeShares <= 0 || ratio <= 0) {
            return emptyList()
        }
        val rows = mutableListOf<Row>()
        val priceStep = estimatedPrice * (ratio / 100.0)
        val totalCost = costPrice * shares
        var totalRevenue = 0.0
        var cumulativeSoldShares = 0

        for (i in 1..100) {
            val price = estimatedPrice + ((i - 1) * priceStep)
            val remainingShares = shares - cumulativeSoldShares
            if (remainingShares <= 0) break

            val currentSellShares = min(tradeShares, remainingShares)
            val totalPrice = price * remainingShares
            val sellPrice = price * currentSellShares
            totalRevenue += sellPrice
            cumulativeSoldShares += currentSellShares

            val profit = totalPrice + totalRevenue - totalCost

            rows.add(
                Row(
                    index = i,
                    price = Money.fixed(price, 3),
                    sellPrice = Money.fixed(sellPrice, 2),
                    remainingShares = remainingShares.toString(),
                    totalPrice = Money.fixed(totalPrice, 2),
                    totalRevenue = Money.fixed(totalRevenue, 2),
                    profit = Money.fixed(profit, 2),
                    profitNum = Money.round2(profit)
                )
            )
        }
        return rows
    }
}
