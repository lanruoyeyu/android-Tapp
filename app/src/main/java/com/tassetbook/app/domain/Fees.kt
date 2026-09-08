package com.tassetbook.app.domain

import com.tassetbook.app.data.Settings
import com.tassetbook.app.util.Money
import kotlin.math.max

/**
 * 交易费用计算，口径对齐 app.js addTBuy/addTSell 与 trade-operation.js _calcFees。
 * 费率单位为万分之一（‱）：rate/10000。佣金不足 5 元按 5 元；印花税仅卖出。
 */
object Fees {
    data class Breakdown(
        val grossAmount: Double,   // 成交金额 = 价×股数
        val commission: Double,    // 佣金
        val transferFee: Double,   // 过户费
        val stampDuty: Double,     // 印花税
        val totalFee: Double       // 手续费合计
    )

    fun calc(price: Double, shares: Int, isSell: Boolean, s: Settings): Breakdown {
        val gross = Money.round2(price * shares)

        // 佣金：tradeRateA 未配置时 0.000085（万0.85）
        val commissionRate = s.tradeRateA?.let { it / 10000.0 } ?: 0.000085
        var commission = max(gross * commissionRate, 5.0)
        commission = Money.round2(commission)

        // 过户费：tradeRateTransfer 缺省 '0.1'（万0.1）
        val transferRate = (s.tradeRateTransfer.toDoubleOrNull() ?: 0.1) / 10000.0
        val transferFee = Money.round2(gross * transferRate)

        // 印花税：tradeRateStampDuty 缺省 '5'（万5），仅卖出
        val stampRate = (s.tradeRateStampDuty.toDoubleOrNull() ?: 5.0) / 10000.0
        val stampDuty = if (isSell) Money.round2(gross * stampRate) else 0.0

        val totalFee = Money.round2(commission + transferFee + stampDuty)
        return Breakdown(gross, commission, transferFee, stampDuty, totalFee)
    }

    /** 买入总额 = 成交金额 + 佣金 + 过户费（无印花税） */
    fun buyTotal(price: Double, shares: Int, s: Settings): Pair<Breakdown, Double> {
        val f = calc(price, shares, false, s)
        val total = Money.round2(f.grossAmount + f.commission + f.transferFee)
        return f to total
    }

    /** 卖出净额 = 成交金额 - 佣金 - 过户费 - 印花税 */
    fun sellNet(price: Double, shares: Int, s: Settings): Pair<Breakdown, Double> {
        val f = calc(price, shares, true, s)
        val net = Money.round2(f.grossAmount - f.totalFee)
        return f to net
    }
}
