package com.tassetbook.app.data

/**
 * 资产（股票）。字段与微信小程序 app.js globalData.assets 对齐。
 * 注意：小程序 addAsset 漏存 market（编辑时才补），Android 版在新增时即保存 market。
 */
data class Asset(
    var id: String = "",
    var name: String = "",
    var market: String? = null,        // A股 / 港股 / ETF
    var exchange: String? = null,      // 沪市 / 深市 / 港交所
    // 底仓
    var baseTotalCost: Double = 0.0,
    var baseShares: Int = 0,
    var baseUnit: Double = 0.0,        // 底仓单价
    // 底仓为0时，归档交易的利润累计保留于此（元），并计入后续持仓回放的总体成本价
    var baseProfit: Double = 0.0,
    // 当前持仓（由交易回放维护）
    var totalCost: Double = 0.0,
    var shares: Int = 0,
    var costPerShare: Double = 0.0,    // 成本价（内部数值，显示 3 位小数）
    // 利润统计
    var totalProfit: Double = 0.0,
    var monthProfit: Double = 0.0,
    var todayProfit: Double = 0.0,
    var currentPrice: Double = 0.0,
    var remark: String? = null,
    var isTop: Boolean = false,
    var hidden: Boolean = false,          // 隐藏后不在股票管理主界面显示
    var order: Long = 0L,                 // 主界面手动排序（越小越靠前；0 为未排序，按存储顺序）
    var createTime: String = "",
    var updateTime: String = ""
)

/**
 * 交易记录。字段与小程序 Trade 对齐；buyXxx、sellXxx 为两侧明细，
 * price、shares、total、commission、transferFee、stampDuty 为兼容旧字段。
 * tradeStatus: buyNoSell(正T买入未卖) / sellNoBuy(反T卖出未补) / buyToSell(买后补卖) / sellToBuy(卖后补买)
 */
data class Trade(
    var id: String = "",
    var assetId: String = "",
    var assetName: String = "",
    var tradeStatus: String = "buyNoSell",
    // 买入侧
    var buyPrice: Double = 0.0,
    var buyShares: Int = 0,
    var buyCommission: Double = 0.0,
    var buyTransferFee: Double = 0.0,
    var buyTotalFee: Double = 0.0,
    var buyTotal: Double = 0.0,
    var buyTradeDate: String = "",
    // 卖出侧
    var sellPrice: Double = 0.0,
    var sellShares: Int = 0,
    var sellCommission: Double = 0.0,
    var sellTransferFee: Double = 0.0,
    var sellStampDuty: Double = 0.0,
    var sellTotalFee: Double = 0.0,
    var sellTotal: Double = 0.0,        // 卖出净额（扣费后）
    var sellTradeDate: String = "",
    // 兼容旧字段
    var price: Double = 0.0,
    var shares: Int = 0,
    var total: Double = 0.0,
    var commission: Double = 0.0,
    var transferFee: Double = 0.0,
    var stampDuty: Double = 0.0,
    // 通用
    var profit: Double = 0.0,
    var remark: String = "",
    var tradeDate: String = "",
    var tradeTime: String = "",
    var isArchived: Boolean = false,
    var archiveTime: String = "",
    var relatedBuyTradeId: String? = null,
    var relatedSellTradeId: String? = null,
    var updateTime: String = ""
)

/**
 * 设置。费率单位为万分之一（‱）：tradeRateA=0.85 表示 0.85‱=0.0085%。
 */
data class Settings(
    var theme: String = "light",
    var defaultMarket: String = "A股",
    var defaultExchange: String = "沪市",
    var tradeRateTransfer: String = "0.1",   // 过户费 ‱
    var tradeRateStampDuty: String = "5",    // 印花税 ‱（仅卖出）
    var tradeRateA: Double? = 0.85,          // A股佣金 ‱；null 时按 0.000085
    var tradeRateHK: Double? = 1.00,
    var tradeRateETF: Double? = 0.20
)
