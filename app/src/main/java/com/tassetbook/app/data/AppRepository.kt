package com.tassetbook.app.data

import com.tassetbook.app.domain.Backup
import com.tassetbook.app.domain.Fees
import com.tassetbook.app.domain.RealizedProfit
import com.tassetbook.app.util.Dates
import com.tassetbook.app.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate

/**
 * 全局数据仓库，对齐小程序 app.js 的 globalData 与全部业务方法。
 */
class AppRepository(private val store: LocalStore) {

    val assets = mutableListOf<Asset>()
    val trades = mutableListOf<Trade>()
    var settings: Settings = Settings()
        private set

    private val _assetsFlow = MutableStateFlow<List<Asset>>(emptyList())
    val assetsFlow: StateFlow<List<Asset>> = _assetsFlow.asStateFlow()
    private val _tradesFlow = MutableStateFlow<List<Trade>>(emptyList())
    val tradesFlow: StateFlow<List<Trade>> = _tradesFlow.asStateFlow()
    private val _settingsFlow = MutableStateFlow(settings)
    val settingsFlow: StateFlow<Settings> = _settingsFlow.asStateFlow()

    data class OpResult(val success: Boolean, val message: String = "", val trade: Trade? = null, val profit: Double? = null)

    data class Position(val totalCost: Double, val shares: Int, val costPerShare: Double, val oversold: Boolean = false)

    data class Stats(
        val totalAssets: Int,
        val totalProfit: Double,
        val totalCost: Double,
        val totalShares: Int,
        val aStock: Int,
        val hkStock: Int,
        val etf: Int,
        val totalTrades: Int,
        val buyCount: Int,
        val sellCount: Int
    )

    fun load() {
        assets.clear()
        assets.addAll(store.loadAssets().filter { it.name.isNotBlank() })
        trades.clear()
        trades.addAll(store.loadTrades().filter { it.assetId.isNotBlank() })
        settings = store.loadSettings()
        migrateLegacyBase()
        recomputeAll()
        save()   // 用当前口径校正历史版本写入的派生字段（持仓/利润）
        publish()
    }

    /**
     * 历史数据迁移：旧版归档在底仓为0时会把亏损额塞进 baseTotalCost（盈利则被 clamp 丢失），
     * 造成空仓下的幽灵成本并扭曲后续成本价。将其还原为 baseProfit（负利润）并清空底仓成本。
     */
    private fun migrateLegacyBase() {
        for (a in assets) {
            if (a.baseShares == 0 && a.baseTotalCost > 0) {
                // 旧版幽灵底仓成本：直接保留在 baseTotalCost 中（正数=亏损），
                // 后续买入自然叠加，不再走 baseProfit
                a.baseUnit = 0.0
            }
        }
    }

    fun save() {
        store.saveAssets(assets)
        store.saveTrades(trades)
        store.saveSettings(settings)
    }

    private fun publish() {
        // 必须发射元素拷贝：assets/trades 内的对象常被原地修改（如 hidden），
        // 若只发 toList()，StateFlow 逐元素 equals 比较会认为值未变化而跳过发射，
        // 导致当前页面状态（隐藏/显示徽标等）不能即时刷新。
        // 按用户拖动顺序（order）发射；未排序资产 order=0，稳定排序保持原有存储顺序
        _assetsFlow.value = assets.sortedWith(compareBy { it.order }).map { it.copy() }
        _tradesFlow.value = trades.map { it.copy() }
        _settingsFlow.value = settings.copy()
    }

    // ---------------- 资产 ----------------

    fun addAsset(name: String, market: String, exchange: String, baseTotalCost: Double, baseShares: Int): Asset {
        val now = Dates.nowString()
        val asset = Asset(
            id = System.currentTimeMillis().toString(),
            name = name,
            market = market,
            exchange = exchange,
            baseTotalCost = baseTotalCost,
            baseShares = baseShares,
            baseUnit = if (baseShares > 0 && baseTotalCost > 0) baseTotalCost / baseShares else 0.0,
            totalCost = baseTotalCost,
            shares = baseShares,
            createTime = now,
            updateTime = now,
            costPerShare = if (baseShares > 0) Money.round6(baseTotalCost / baseShares) else 0.0,
            order = (assets.minOfOrNull { it.order } ?: 0L) - 1L
        )
        assets.add(0, asset)
        recomputeAll()
        save()
        publish()
        return asset
    }

    /** 添加/编辑资产页保存（编辑模式） */
    fun saveAsset(id: String?, name: String, market: String, exchange: String, baseTotalCost: Double, baseShares: Int, baseUnit: Double) {
        if (id == null) {
            addAsset(name, market, exchange, baseTotalCost, baseShares)
            return
        }
        val index = assets.indexOfFirst { it.id == id }
        if (index != -1) {
            val a = assets[index]
            a.name = name
            a.market = market
            a.exchange = exchange
            a.baseTotalCost = baseTotalCost
            a.baseShares = baseShares
            a.baseUnit = if (baseShares > 0 && baseTotalCost > 0) baseTotalCost / baseShares else 0.0
            a.updateTime = Dates.nowString()
            recomputeAll()
            save()
            publish()
        }
    }

    fun deleteAsset(id: String) {
        assets.removeAll { it.id == id }
        trades.removeAll { it.assetId == id }
        recomputeAll()
        save()
        publish()
    }

    fun toggleTop(id: String) {
        val a = assets.find { it.id == id } ?: return
        a.isTop = !a.isTop
        save()
        publish()
    }

    /**
     * 拖动排序：orderedVisibleIds 为当前可见列表的目标顺序（可能是筛选后的子集）。
     * 与全部资产做稳定归并——不可见资产保持其原有相对位置，可见资产占据它们原来的槽位，
     * 最后全局重排 order。
     */
    fun reorderAssets(orderedVisibleIds: List<String>) {
        val visibleSet = orderedVisibleIds.toSet()
        val byId = assets.associateBy { it.id }
        val visible = orderedVisibleIds.mapNotNull { byId[it] }
        if (visible.size < 2) return
        var vi = 0
        val merged = assets.sortedWith(compareBy { it.order }).map { a ->
            if (a.id in visibleSet && vi < visible.size) visible[vi++] else a
        }
        merged.forEachIndexed { i, a -> a.order = (i + 1).toLong() }
        save()
        publish()
    }

    /** 设置资产隐藏/显示（隐藏后不在股票管理主界面显示） */
    fun setAssetHidden(id: String, hidden: Boolean) {
        val a = assets.find { it.id == id } ?: return
        a.hidden = hidden
        a.updateTime = Dates.nowString()
        save()
        publish()
    }

    fun getAsset(id: String): Asset? = assets.find { it.id == id }

    // ---------------- 交易：新建正T买入 / 反T卖出 ----------------

    fun addTBuy(assetId: String, price: Double, shares: Int, remark: String, tradeDate: String): OpResult {
        val asset = assets.find { it.id == assetId } ?: return OpResult(false, "资产不存在")
        val (f, _) = Fees.buyTotal(price, shares, settings)
        val buyTotal = Money.round2(price * shares + f.commission + f.transferFee)
        val date = tradeDate.ifBlank { Dates.todayLocale() }
        val now = Dates.nowString()

        val t = Trade(
            id = System.currentTimeMillis().toString(),
            assetId = assetId,
            assetName = asset.name,
            buyPrice = price,
            buyShares = shares,
            buyCommission = f.commission,
            buyTransferFee = f.transferFee,
            buyTotalFee = Money.round2(f.commission + f.transferFee),
            buyTotal = buyTotal,
            buyTradeDate = date,
            sellPrice = 0.0, sellShares = 0, sellCommission = 0.0, sellTransferFee = 0.0,
            sellStampDuty = 0.0, sellTotalFee = 0.0, sellTotal = 0.0, sellTradeDate = "",
            price = price,
            shares = shares,
            total = buyTotal,
            commission = f.commission,
            transferFee = f.transferFee,
            stampDuty = 0.0,
            profit = 0.0,
            remark = remark,
            tradeDate = date,
            tradeTime = now,
            tradeStatus = "buyNoSell"
        )

        asset.totalCost = Money.round6(asset.totalCost + buyTotal)
        asset.shares = asset.shares + shares
        asset.costPerShare = if (asset.shares > 0) Money.round6(asset.totalCost / asset.shares) else asset.costPerShare

        trades.add(0, t)
        recomputeProfits()
        save()
        publish()
        return OpResult(true, trade = t)
    }

    fun addTSell(assetId: String, price: Double, shares: Int, remark: String, tradeDate: String): OpResult {
        val asset = assets.find { it.id == assetId } ?: return OpResult(false, "资产不存在")

        val (f, net) = Fees.sellNet(price, shares, settings)
        val grossProfit = (price - asset.costPerShare) * shares
        val netProfit = Money.round2(grossProfit - f.totalFee)
        val date = tradeDate.ifBlank { Dates.todayLocale() }
        val now = Dates.nowString()

        // 按日期回放校验：补录历史卖单时，当前持仓足够不代表该历史时点足够
        val probe = Trade(
            id = "__probe__", assetId = assetId, assetName = asset.name,
            sellPrice = price, sellShares = shares, sellTotal = net,
            sellTradeDate = date, tradeDate = date, tradeStatus = "sellNoBuy"
        )
        if (!validateNoOversell(asset, extra = probe)) return OpResult(false, "卖出股数超过该时点持仓")

        val t = Trade(
            id = System.currentTimeMillis().toString(),
            assetId = assetId,
            assetName = asset.name,
            buyPrice = 0.0, buyShares = 0, buyCommission = 0.0, buyTransferFee = 0.0,
            buyTotalFee = 0.0, buyTotal = 0.0, buyTradeDate = "",
            sellPrice = price,
            sellShares = shares,
            sellCommission = f.commission,
            sellTransferFee = f.transferFee,
            sellStampDuty = f.stampDuty,
            sellTotalFee = f.totalFee,
            sellTotal = net,
            sellTradeDate = date,
            price = price,
            shares = shares,
            total = net,
            commission = f.commission,
            transferFee = f.transferFee,
            stampDuty = f.stampDuty,
            profit = netProfit,
            remark = remark,
            tradeDate = date,
            tradeTime = now,
            tradeStatus = "sellNoBuy"
        )

        val sharesAfter = asset.shares - shares
        // 独立卖出（减持）：按净额冲减成本，卖出费用留在剩余持仓成本
        val costAfter = Money.round6(asset.totalCost - net)
        asset.totalCost = costAfter
        asset.shares = sharesAfter
        asset.costPerShare = if (sharesAfter > 0)
            Money.round6(costAfter / sharesAfter) else 0.0

        trades.add(0, t)
        recomputeProfits()
        save()
        publish()
        return OpResult(true, trade = t, profit = netProfit)
    }

    // ---------------- 交易：补单 / 编辑（对齐 trade-operation.js） ----------------

    /** 补充卖出：buyNoSell → buyToSell */
    fun supplementSell(tradeId: String, price: Double, shares: Int, tradeDate: String, remark: String): OpResult {
        val idx = trades.indexOfFirst { it.id == tradeId }
        if (idx == -1) return OpResult(false, "交易记录不存在")
        val t = trades[idx]
        val (f, net) = Fees.sellNet(price, shares, settings)
        val date = tradeDate.ifBlank { Dates.todayLocale() }

        val asset0 = assets.find { it.id == t.assetId }
        if (asset0 != null) {
            val candidate = t.copy(
                sellPrice = price, sellShares = shares,
                sellCommission = f.commission, sellTransferFee = f.transferFee,
                sellStampDuty = f.stampDuty, sellTotalFee = f.totalFee, sellTotal = net,
                sellTradeDate = date, tradeDate = date, tradeStatus = "buyToSell"
            )
            if (!validateNoOversell(asset0, t.id, candidate))
                return OpResult(false, "卖出股数超过该时点持仓")
        }

        t.sellPrice = price
        t.sellShares = shares
        t.sellCommission = f.commission
        t.sellTransferFee = f.transferFee
        t.sellStampDuty = f.stampDuty
        t.sellTotalFee = f.totalFee
        t.sellTotal = net
        t.sellTradeDate = date
        t.tradeStatus = "buyToSell"
        t.profit = Money.round2(net - t.buyTotal)
        t.remark = remark
        t.updateTime = Dates.nowString()

        val asset = assets.find { it.id == t.assetId }
        if (asset != null) {
            // 卖出一律按净额冲减成本（卖出费用留在剩余持仓），T利润另算
            val cost = Money.round6(asset.totalCost - net)
            val sh = asset.shares - shares
            asset.totalCost = cost
            asset.shares = sh
            asset.costPerShare = if (sh > 0) Money.round6(cost / sh) else 0.0
        }
        afterTradeChange()
        return OpResult(true, trade = t)
    }

    /** 补充买入：sellNoBuy → sellToBuy */
    fun supplementBuy(tradeId: String, price: Double, shares: Int, tradeDate: String, remark: String): OpResult {
        val idx = trades.indexOfFirst { it.id == tradeId }
        if (idx == -1) return OpResult(false, "交易记录不存在")
        val t = trades[idx]
        val (f, _) = Fees.buyTotal(price, shares, settings)
        val buyTotal = Money.round2(price * shares + f.commission + f.transferFee)
        val date = tradeDate.ifBlank { Dates.todayLocale() }

        t.buyPrice = price
        t.buyShares = shares
        t.buyCommission = f.commission
        t.buyTransferFee = f.transferFee
        t.buyTotalFee = Money.round2(f.commission + f.transferFee)
        t.buyTotal = buyTotal
        t.buyTradeDate = date
        t.tradeStatus = "sellToBuy"
        t.profit = Money.round2(t.sellTotal - buyTotal)
        t.remark = remark
        t.updateTime = Dates.nowString()

        val asset = assets.find { it.id == t.assetId }
        if (asset != null) {
            // 补买：只加买入总额，卖出净额已在原独立卖出时冲减过，无需调整
            val cost = Money.round6(asset.totalCost + buyTotal)
            val sh = asset.shares + shares
            asset.totalCost = cost
            asset.shares = sh
            asset.costPerShare = Money.round6(cost / sh)
        }
        afterTradeChange()
        return OpResult(true, trade = t)
    }

    /** 编辑正T买入记录（buyNoSell） */
    fun editBuyTrade(tradeId: String, price: Double, shares: Int, tradeDate: String, remark: String): OpResult {
        val idx = trades.indexOfFirst { it.id == tradeId }
        if (idx == -1) return OpResult(false, "交易记录不存在")
        val t = trades[idx]
        val oldShares = if (t.buyShares != 0) t.buyShares else t.shares
        val oldTotal = if (t.buyTotal != 0.0) t.buyTotal else t.total

        val (f, _) = Fees.buyTotal(price, shares, settings)
        val buyTotalFee = Money.round2(f.commission + f.transferFee)
        val buyTotal = Money.round2(price * shares + buyTotalFee)
        val date = tradeDate.ifBlank { Dates.todayLocale() }

        val asset0 = assets.find { it.id == t.assetId }
        if (asset0 != null) {
            val candidate = t.copy(
                buyPrice = price, buyShares = shares,
                buyCommission = f.commission, buyTransferFee = f.transferFee,
                buyTotalFee = buyTotalFee, buyTotal = buyTotal, buyTradeDate = date,
                price = price, shares = shares, total = buyTotal,
                commission = f.commission, transferFee = f.transferFee, tradeDate = date
            )
            if (!validateNoOversell(asset0, t.id, candidate))
                return OpResult(false, "修改后卖出股数将超过该时点持仓")
        }

        t.buyPrice = price; t.buyShares = shares
        t.buyCommission = f.commission; t.buyTransferFee = f.transferFee
        t.buyTotalFee = buyTotalFee; t.buyTotal = buyTotal; t.buyTradeDate = date
        t.price = price; t.shares = shares; t.total = buyTotal
        t.commission = f.commission; t.transferFee = f.transferFee
        t.remark = remark; t.tradeDate = date; t.updateTime = Dates.nowString()

        val asset = assets.find { it.id == t.assetId }
        if (asset != null) {
            val cost = Money.round6(asset.totalCost - oldTotal + buyTotal)
            val sh = asset.shares - oldShares + shares
            asset.totalCost = cost
            asset.shares = sh
            asset.costPerShare = if (sh > 0) Money.round6(cost / sh) else 0.0
        }
        afterTradeChange()
        return OpResult(true, trade = t)
    }

    /** 编辑反T卖出记录（sellNoBuy） */
    fun editSellTrade(tradeId: String, price: Double, shares: Int, tradeDate: String, remark: String): OpResult {
        val idx = trades.indexOfFirst { it.id == tradeId }
        if (idx == -1) return OpResult(false, "交易记录不存在")
        val t = trades[idx]
        val oldShares = if (t.sellShares != 0) t.sellShares else t.shares
        val oldNet = Money.round2(t.sellPrice * oldShares - t.sellTotalFee)

        val (f, net) = Fees.sellNet(price, shares, settings)
        val date = tradeDate.ifBlank { Dates.todayLocale() }

        val asset0 = assets.find { it.id == t.assetId }
        if (asset0 != null) {
            val candidate = t.copy(
                sellPrice = price, sellShares = shares,
                sellCommission = f.commission, sellTransferFee = f.transferFee,
                sellStampDuty = f.stampDuty, sellTotalFee = f.totalFee, sellTotal = net,
                sellTradeDate = date, price = price, shares = shares, total = net,
                commission = f.commission, transferFee = f.transferFee, stampDuty = f.stampDuty,
                tradeDate = date
            )
            if (!validateNoOversell(asset0, t.id, candidate))
                return OpResult(false, "卖出股数超过该时点持仓")
        }

        t.sellPrice = price; t.sellShares = shares
        t.sellCommission = f.commission; t.sellTransferFee = f.transferFee; t.sellStampDuty = f.stampDuty
        t.sellTotalFee = f.totalFee; t.sellTotal = net; t.sellTradeDate = date
        t.price = price; t.shares = shares; t.total = net
        t.commission = f.commission; t.transferFee = f.transferFee; t.stampDuty = f.stampDuty
        t.remark = remark; t.tradeDate = date; t.updateTime = Dates.nowString()

        val asset = assets.find { it.id == t.assetId }
        if (asset != null) {
            // 独立卖出：按新旧卖出净额差额调整成本（卖出费用留在剩余持仓）
            val cost = Money.round6(asset.totalCost + oldNet - net)
            val sh = asset.shares + oldShares - shares
            asset.totalCost = cost
            asset.shares = sh
            asset.costPerShare = if (sh > 0) Money.round6(cost / sh) else 0.0
        }
        afterTradeChange()
        return OpResult(true, trade = t)
    }

    /** 编辑 T完成记录（buyToSell / sellToBuy） */
    fun editCompletedTrade(
        tradeId: String, buyPrice: Double, buyShares: Int,
        sellPrice: Double, sellShares: Int, tradeDate: String, remark: String
    ): OpResult {
        val idx = trades.indexOfFirst { it.id == tradeId }
        if (idx == -1) return OpResult(false, "交易记录不存在")
        val t = trades[idx]
        val oldBuyTotal = t.buyTotal
        val oldBuyShares = t.buyShares
        val oldSellNet = t.sellTotal
        val oldSellShares = t.sellShares

        val (bf, _) = Fees.buyTotal(buyPrice, buyShares, settings)
        val buyTotalFee = Money.round2(bf.commission + bf.transferFee)
        val buyTotal = Money.round2(buyPrice * buyShares + buyTotalFee)
        val (sf, sellNet) = Fees.sellNet(sellPrice, sellShares, settings)
        val date = tradeDate.ifBlank { Dates.todayLocale() }

        val asset0 = assets.find { it.id == t.assetId }
        if (asset0 != null) {
            val candidate = t.copy(
                buyPrice = buyPrice, buyShares = buyShares,
                buyCommission = bf.commission, buyTransferFee = bf.transferFee,
                buyTotalFee = buyTotalFee, buyTotal = buyTotal, buyTradeDate = date,
                sellPrice = sellPrice, sellShares = sellShares,
                sellCommission = sf.commission, sellTransferFee = sf.transferFee,
                sellStampDuty = sf.stampDuty, sellTotalFee = sf.totalFee, sellTotal = sellNet,
                sellTradeDate = date, tradeDate = date
            )
            if (!validateNoOversell(asset0, t.id, candidate))
                return OpResult(false, "修改后卖出股数将超过该时点持仓")
        }

        t.buyPrice = buyPrice; t.buyShares = buyShares
        t.buyCommission = bf.commission; t.buyTransferFee = bf.transferFee
        t.buyTotalFee = buyTotalFee; t.buyTotal = buyTotal; t.buyTradeDate = date
        t.sellPrice = sellPrice; t.sellShares = sellShares
        t.sellCommission = sf.commission; t.sellTransferFee = sf.transferFee; t.sellStampDuty = sf.stampDuty
        t.sellTotalFee = sf.totalFee; t.sellTotal = sellNet; t.sellTradeDate = date
        t.tradeDate = date
        t.profit = Money.round2(sellNet - buyTotal)
        t.remark = remark
        t.updateTime = Dates.nowString()

        val asset = assets.find { it.id == t.assetId }
        if (asset != null) {
            // 卖出一律按净额冲减成本（卖出费用留在剩余持仓）
            var cost = asset.totalCost - oldBuyTotal + oldSellNet + buyTotal - sellNet
            var sh = asset.shares - oldBuyShares + oldSellShares + buyShares - sellShares
            if (sh < 0) sh = 0
            if (cost < 0) cost = 0.0
            cost = Money.round6(cost)
            asset.totalCost = cost
            asset.shares = sh
            asset.costPerShare = if (sh > 0) Money.round6(cost / sh) else 0.0
        }
        afterTradeChange()
        return OpResult(true, trade = t)
    }

    private fun afterTradeChange() {
        // 交易变更后：按交易回放重算持仓（与小程序页面 loadAssets/loadTrades 一致），再算利润
        recomputePositions()
        recomputeProfits()
        save()
        publish()
    }

    // ---------------- 归档 / 删除 / 清空 ----------------

    fun archiveTrade(tradeId: String): Boolean {
        val idx = trades.indexOfFirst { it.id == tradeId }
        if (idx == -1) return false
        val t = trades[idx]

        val buyTotal = t.buyTotal
        val sellTotal = t.sellTotal
        val profit = Money.round2(sellTotal - buyTotal)
        var profitDate = t.tradeDate
        val bd = Dates.parseDate(t.buyTradeDate)
        val sd = Dates.parseDate(t.sellTradeDate)
        if (bd != null && sd != null) {
            profitDate = if (bd.isAfter(sd)) t.buyTradeDate else t.sellTradeDate
        }

        val asset = assets.find { it.id == t.assetId }
        if (asset != null) adjustBaseUnitAfterArchive(asset, t)

        val archived = Trade(
            id = t.id,
            assetId = t.assetId,
            assetName = t.assetName,
            tradeStatus = t.tradeStatus,
            tradeDate = profitDate,
            buyTotal = buyTotal,
            sellTotal = sellTotal,
            profit = profit,
            isArchived = true,
            archiveTime = Dates.nowString()
        )
        if (asset != null) {
            // 底仓为0时 baseTotalCost 承载归档盈亏（负=利润/正=亏损），
            // 但空仓 totalCost/costPerShare 必须为0，盈亏在下次买入时通过回放叠加
            asset.shares = asset.baseShares
            asset.totalCost = if (asset.baseShares > 0) asset.baseTotalCost else 0.0
            asset.costPerShare = if (asset.baseShares > 0) asset.baseUnit else 0.0
        }
        trades[idx] = archived
        recomputeAll()
        save()
        publish()
        return true
    }

    /**
     * 归档后把交易对成本/股数的净影响转移进底仓，保证成本价不变。
     * 特殊情形：底仓股数为0时（无底仓），不能把净成本塞进底仓造成幽灵成本、
     * 扭曲后续交易的成本价；改为把利润累计进 baseProfit，由回放计入总体成本价。
     */
    private fun adjustBaseUnitAfterArchive(asset: Asset, t: Trade) {
        val baseShares = asset.baseShares
        val baseTotalCost = asset.baseTotalCost
        val buyShares = t.buyShares
        val sellShares = t.sellShares
        val buyTotal = t.buyTotal
        val sellTotal = t.sellTotal

        if (baseShares == 0) {
            // 底仓为0：归档盈亏直接写入 baseTotalCost（利润为负、亏损为正），
            // 后续买入时自然叠加到成本价，与券商口径一致
            val profit = Money.round2(sellTotal - buyTotal)
            asset.baseTotalCost = Money.round2(asset.baseTotalCost - profit)
            return
        }

        val newBaseShares = maxOf(0, baseShares + buyShares - sellShares)
        // 卖出按净额冲减（卖出费用留在底仓成本）
        val newBaseTotalCost = maxOf(0.0, baseTotalCost + buyTotal - sellTotal)

        asset.baseTotalCost = Money.round2(newBaseTotalCost)
        asset.baseShares = newBaseShares
        asset.baseUnit = if (newBaseShares > 0) Money.round3(newBaseTotalCost / newBaseShares) else 0.0
    }

    fun deleteTrade(tradeId: String): Boolean {
        val removed = trades.removeAll { it.id == tradeId }
        if (removed) {
            recomputeAll()
            save()
            publish()
        }
        return removed
    }

    fun clearTradesForAsset(assetId: String) {
        trades.removeAll { it.assetId == assetId }
        val asset = assets.find { it.id == assetId }
        if (asset != null) {
            asset.totalCost = 0.0
            asset.shares = 0
            asset.costPerShare = 0.0
        }
        recomputeProfits()
        save()
        publish()
    }

    fun clearAllData() {
        assets.clear()
        trades.clear()
        save()
        publish()
    }

    // ---------------- 查询 ----------------

    fun getAssetTrades(assetId: String): List<Trade> =
        trades.filter { it.assetId == assetId && !it.isArchived }

    fun getAllAssetTrades(assetId: String): List<Trade> =
        trades.filter { it.assetId == assetId }

    // ---------------- 持仓回放 / 利润 / 统计 ----------------

    /**
     * 持仓回放：底仓起，按日期升序（同日先买后卖）重放未归档交易。
     * 对齐 assets.js / trade-record.js loadXxx 中的回放逻辑。
     * baseProfit（底仓为0时的归档利润累计）计入总体成本价：盈利摊薄、亏损抬高；
     * totalCost 仍为实际投入资金，空仓（shares=0）时为0，不出现幽灵成本。
     */
    fun replayPosition(asset: Asset, nonArchivedTrades: List<Trade>): Position {
        var cost = asset.baseTotalCost
        var sh = asset.baseShares
        var oversold = false
        val sorted = nonArchivedTrades.sortedWith(
            compareBy<Trade> { Dates.parseDate(it.tradeDate) ?: LocalDate.MAX }
                .thenBy { -(if (it.buyShares > 0) 1 else 0) }
        )
        for (t in sorted) {
            val bs = t.buyShares
            val bt = t.buyTotal
            if (bs > 0 && bt > 0) {
                cost += bt
                sh += bs
            }
            val ss = t.sellShares
            if (ss > 0 && t.sellTotal > 0) {
                // 所有卖出一律按净额（成交金额-卖出费用）冲减成本，
                // 卖出费用留在剩余持仓成本中；T利润单独按 卖出净额-买入总额 计算
                cost -= t.sellTotal
                sh -= ss
                if (sh < 0) {
                    // 卖出时点持仓不足：回放只能钳制为 0，会扭曲后续成本价与利润。
                    // 正常写入路径应通过 validateNoOversell 拦截，这里仅作历史脏数据容错。
                    oversold = true
                    sh = 0
                }
                if (cost < 0) cost = 0.0
            }
        }
        sh = maxOf(0, sh)
        // 空仓时 totalCost 归零（baseTotalCost 承载的归档盈亏在下次买入时通过回放叠加）
        if (sh == 0) cost = 0.0
        cost = Money.round6(cost)
        val cps = if (sh > 0) Money.round3(cost / sh) else 0.0
        return Position(cost, sh, cps, oversold)
    }

    /**
     * 提交前校验：把目标交易替换为候选版本后回放，
     * 若任一时点卖出股数超过当时持仓（超卖），返回 false。
     * 超卖会被回放钳制为 0 仓，造成成本价变 0、利润虚增等错误联动。
     */
    private fun validateNoOversell(
        asset: Asset,
        targetTradeId: String? = null,
        candidate: Trade? = null,
        extra: Trade? = null
    ): Boolean {
        val probe = getAssetTrades(asset.id).mapNotNull { t ->
            if (t.id == targetTradeId) candidate else t   // candidate=null 表示删除该交易
        }
        val full = if (extra != null) probe + extra else probe
        return !replayPosition(asset, full).oversold
    }

    fun recomputePositions() {
        for (a in assets) {
            val p = replayPosition(a, getAssetTrades(a.id))
            a.totalCost = p.totalCost
            a.shares = p.shares
            a.costPerShare = p.costPerShare
        }
    }

    /**
     * app.js calculateAssetProfit 的记录利润口径：
     * 总利润 = 全部交易（含归档）t.profit 之和；月/今日按 tradeDate→买日→卖日归属。
     * 与每笔交易卡片显示的盈亏逐笔可对账；利润日历（T完成/归档）见 StatisticsCalc。
     */
    fun calculateAssetProfit(assetId: String) {
        val asset = assets.find { it.id == assetId } ?: return
        val now = LocalDate.now()
        var total = 0.0
        var month = 0.0
        var today = 0.0
        for (e in RealizedProfit.events(asset, getAllAssetTrades(assetId))) {
            total += e.profit
            val d = e.date
            if (d != null) {
                if (d.year == now.year && d.monthValue == now.monthValue) month += e.profit
                if (d == now) today += e.profit
            }
        }
        asset.totalProfit = Money.round2(total)
        asset.monthProfit = Money.round2(month)
        asset.todayProfit = Money.round2(today)
    }

    fun recomputeProfits() {
        for (a in assets) calculateAssetProfit(a.id)
    }

    fun recomputeAll() {
        recomputePositions()
        recomputeProfits()
    }

    fun getStatistics(): Stats {
        var totalProfit = 0.0
        var totalCost = 0.0
        var totalShares = 0
        var aStock = 0
        var hkStock = 0
        var etf = 0
        for (a in assets) {
            totalProfit += a.totalProfit
            totalCost += a.totalCost
            totalShares += a.shares
            when (a.market) {
                "A股" -> aStock++
                "港股" -> hkStock++
                "ETF" -> etf++
            }
        }
        return Stats(
            totalAssets = assets.size,
            totalProfit = Money.round2(totalProfit),
            totalCost = Money.round2(totalCost),
            totalShares = totalShares,
            aStock = aStock,
            hkStock = hkStock,
            etf = etf,
            totalTrades = trades.size,
            buyCount = trades.count { it.tradeStatus.startsWith("buy") },
            sellCount = trades.count { it.tradeStatus.startsWith("sell") }
        )
    }

    // ---------------- 设置 ----------------

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        store.saveSettings(settings)
        publish()
    }

    // ---------------- 备份 ----------------

    fun exportCsv(): String = Backup.jsonToCsv(assets, trades)

    fun exportJson(): String {
        val gson = com.google.gson.Gson()
        val payload = mapOf(
            "version" to "2.0.0",
            "exportTime" to Dates.nowString(),
            "exportSettings" to mapOf("format" to "json"),
            "assets" to assets,
            "trades" to trades,
            "settings" to settings
        )
        return gson.toJson(payload)
    }

    /** CSV 导入（覆盖式），返回 (资产数, 交易数) */
    fun importCsv(content: String): Pair<Int, Int> {
        val result = Backup.csvToJson(content)
        assets.clear()
        assets.addAll(result.assets)
        trades.clear()
        trades.addAll(result.trades)
        recomputeAll()
        save()
        publish()
        return assets.size to trades.size
    }

    /** JSON 备份导入（覆盖式） */
    fun importJson(content: String): Pair<Int, Int> {
        val gson = com.google.gson.Gson()
        val obj = gson.fromJson(content, com.google.gson.JsonObject::class.java)
            ?: throw Backup.BackupException("文件格式错误：不是有效的 JSON 备份")
        val importedAssets: MutableList<Asset> = if (obj.has("assets") && !obj.get("assets").isJsonNull)
            gson.fromJson(obj.get("assets"), object : com.google.gson.reflect.TypeToken<MutableList<Asset>>() {}.type)
        else mutableListOf()
        val importedTrades: MutableList<Trade> = if (obj.has("trades") && !obj.get("trades").isJsonNull)
            gson.fromJson(obj.get("trades"), object : com.google.gson.reflect.TypeToken<MutableList<Trade>>() {}.type)
        else mutableListOf()
        if (obj.has("settings") && !obj.get("settings").isJsonNull) {
            runCatching {
                val s = gson.fromJson(obj.get("settings"), Settings::class.java)
                if (s != null) settings = s
            }
        }
        assets.clear()
        assets.addAll(importedAssets.filter { it.name.isNotBlank() })
        trades.clear()
        trades.addAll(importedTrades.filter { it.assetId.isNotBlank() })
        recomputeAll()
        save()
        publish()
        return assets.size to trades.size
    }

    companion object {
        @Volatile
        private var instance: AppRepository? = null

        fun init(dataDir: File): AppRepository {
            val dir = File(dataDir, "tassetbook")
            val repo = AppRepository(LocalStore(dir))
            repo.load()
            instance = repo
            return repo
        }

        fun get(): AppRepository =
            instance ?: error("AppRepository not initialized")
    }
}
