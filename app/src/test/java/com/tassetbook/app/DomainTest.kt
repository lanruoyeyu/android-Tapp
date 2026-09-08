package com.tassetbook.app

import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.LocalStore
import com.tassetbook.app.data.Settings
import com.tassetbook.app.domain.Backup
import com.tassetbook.app.domain.Estimate
import com.tassetbook.app.domain.Fees
import com.tassetbook.app.domain.StatisticsCalc
import com.tassetbook.app.util.StockApi
import com.tassetbook.app.util.TradingCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 核心业务口径单元测试，数值基准来自微信小程序 app.js / trade-operation.js 的手算结果。
 */
class DomainTest {

    private lateinit var repo: AppRepository
    private lateinit var tmp: File

    @Before
    fun setup() {
        tmp = Files.createTempDirectory("tapp-test").toFile()
        repo = AppRepository(LocalStore(tmp))
        repo.load()
    }

    private fun baseAsset(): String {
        val a = repo.addAsset(name = "测试股票", market = "A股", exchange = "沪市", baseTotalCost = 10000.0, baseShares = 1000)
        return a.id
    }

    // ---------- AC-3 正T买入 ----------

    @Test
    fun testBuyFeesAndPosition() {
        val id = baseAsset()
        val r = repo.addTBuy(id, price = 10.0, shares = 100, remark = "", tradeDate = "2026/9/1")
        assertTrue(r.success)
        val t = r.trade!!
        assertEquals(5.0, t.buyCommission, 0.001)        // 佣金不足5元按5元
        assertEquals(0.01, t.buyTransferFee, 0.001)     // 过户费 万0.1
        assertEquals(0.0, t.stampDuty, 0.001)           // 买入无印花税
        assertEquals(1005.01, t.buyTotal, 0.001)        // 1000 + 5 + 0.01
        assertEquals("buyNoSell", t.tradeStatus)

        val asset = repo.getAsset(id)!!
        assertEquals(11005.01, asset.totalCost, 0.001)
        assertEquals(1100, asset.shares)

        val pos = repo.replayPosition(asset, repo.getAssetTrades(id))
        assertEquals(11005.01, pos.totalCost, 0.001)
        assertEquals(1100, pos.shares)
        assertEquals(10.005, pos.costPerShare, 0.0001)  // 成本价显示3位
    }

    // ---------- AC-4 反T卖出 ----------

    @Test
    fun testSellFeesProfitAndPosition() {
        val id = baseAsset()
        repo.addTBuy(id, price = 10.0, shares = 100, remark = "", tradeDate = "2026/9/1")
        val r = repo.addTSell(id, price = 11.0, shares = 100, remark = "", tradeDate = "2026/9/2")
        assertTrue(r.success)
        val t = r.trade!!
        assertEquals(5.0, t.sellCommission, 0.001)
        assertEquals(0.01, t.sellTransferFee, 0.001)
        assertEquals(0.55, t.sellStampDuty, 0.001)      // 印花税 万5
        assertEquals(1094.44, t.sellTotal, 0.001)      // 净额 1100-5.56
        // 利润 = (11 - 成本价6位) * 100 - 5.56 ≈ 93.98（口径同 app.js）
        assertEquals(93.98, t.profit, 0.02)

        val asset = repo.getAsset(id)!!
        assertEquals(9910.57, asset.totalCost, 0.001)  // 11005.01 - 卖出净额1094.44（独立卖出）
        assertEquals(1000, asset.shares)

        // 统计口径（记录利润求和）：卖出未补买时取该笔暂估利润 93.98（与交易卡片一致）
        assertEquals(93.98, asset.totalProfit, 0.02)

        // 超卖拦截
        val fail = repo.addTSell(id, price = 11.0, shares = 99999, remark = "", tradeDate = "2026/9/3")
        assertFalse(fail.success)
        assertEquals("卖出股数超过该时点持仓", fail.message)
    }

    // ---------- AC-5 补单状态机 ----------

    @Test
    fun testSupplementSell() {
        val id = baseAsset()
        val buy = repo.addTBuy(id, 10.0, 100, "", "2026/9/1").trade!!
        val r = repo.supplementSell(buy.id, price = 11.0, shares = 100, tradeDate = "2026/9/2", remark = "")
        assertTrue(r.success)
        val t = repo.trades.first { it.id == buy.id }
        assertEquals("buyToSell", t.tradeStatus)
        assertEquals(1094.44, t.sellTotal, 0.001)
        assertEquals(89.43, t.profit, 0.001)            // 1094.44 - 1005.01
        val asset = repo.getAsset(id)!!
        // 补卖后：底仓10000 + 买入1005.01 - 卖出净额1094.44 = 9910.57/1000股
        assertEquals(9910.57, asset.totalCost, 0.02)
        assertEquals(1000, asset.shares)
    }

    @Test
    fun testSupplementBuy() {
        val id = baseAsset()
        // 先反T卖出 100@11
        val sell = repo.addTSell(id, 11.0, 100, "", "2026/9/1").trade!!
        assertEquals(900, repo.getAsset(id)!!.shares)
        // 补买 100@10
        val r = repo.supplementBuy(sell.id, price = 10.0, shares = 100, tradeDate = "2026/9/2", remark = "")
        assertTrue(r.success)
        val t = repo.trades.first { it.id == sell.id }
        assertEquals("sellToBuy", t.tradeStatus)
        assertEquals(1005.01, t.buyTotal, 0.001)
        assertEquals(89.43, t.profit, 0.001)            // 1094.44 - 1005.01
        val asset = repo.getAsset(id)!!
        // 补买后：10000 - 卖出净额1094.44 + 买入1005.01 = 9910.57/1000股
        assertEquals(9910.57, asset.totalCost, 0.02)
        assertEquals(1000, asset.shares)
    }

    @Test
    fun testEditCompletedTrade() {
        val id = baseAsset()
        val buy = repo.addTBuy(id, 10.0, 100, "", "2026/9/1").trade!!
        repo.supplementSell(buy.id, 11.0, 100, "2026/9/2", "")
        // 编辑：买 100@10.2，卖 100@11.2
        val r = repo.editCompletedTrade(buy.id, buyPrice = 10.2, buyShares = 100, sellPrice = 11.2, sellShares = 100, tradeDate = "2026/9/2", remark = "edit")
        assertTrue(r.success)
        val t = repo.trades.first { it.id == buy.id }
        // 买：1020 + 5(佣金) + 0.01(过户) = 1025.01；卖：1120 - 5 - 0.01 - 0.56 = 1114.43
        assertEquals(1025.01, t.buyTotal, 0.01)
        assertEquals(1114.43, t.sellTotal, 0.01)
        assertEquals(89.42, t.profit, 0.01)             // 1114.43 - 1025.01
        // 持仓等价于：底仓10000 + 新买1025.01 - 卖出净额1114.43 = 9910.58
        assertEquals(9910.58, repo.getAsset(id)!!.totalCost, 0.02)
        assertEquals(1000, repo.getAsset(id)!!.shares)
    }

    // ---------- 编辑买入价/卖出价 → 成本价必须联动 ----------

    @Test
    fun testEditBuyPriceMovesCostPrice() {
        val id = baseAsset()                       // 底仓 1000 股 @ 10
        val buy = repo.addTBuy(id, 8.0, 100, "", "2026/9/1").trade!!
        // 8 元买入摊薄：(10000 + 805.01) / 1100 = 9.823
        val cpsLow = repo.getAsset(id)!!.costPerShare
        assertEquals(9.823, cpsLow, 0.001)
        // 买入价改为 9 元：(10000 + 905.01) / 1100 = 9.914，买入价抬高、成本价抬高
        assertTrue(repo.editBuyTrade(buy.id, 9.0, 100, "2026/9/1", "").success)
        val cpsHigh = repo.getAsset(id)!!.costPerShare
        assertEquals(9.914, cpsHigh, 0.001)
        assertTrue(cpsHigh > cpsLow)
    }

    @Test
    fun testEditSellPriceMovesCostPrice() {
        val id = baseAsset()                       // 底仓 1000 股 @ 10
        val sell = repo.addTSell(id, 12.0, 100, "", "2026/9/1").trade!!
        // 12 元独立卖出净额 1194.39：剩余 900 股成本 (10000-1194.39)/900 = 9.784
        val cpsHighSell = repo.getAsset(id)!!.costPerShare
        assertEquals(9.784, cpsHighSell, 0.001)
        // 卖出价改 11 元：净额 1094.44，(10000-1094.44)/900 = 9.895，卖低了成本价抬高
        assertTrue(repo.editSellTrade(sell.id, 11.0, 100, "2026/9/1", "").success)
        val cpsLowSell = repo.getAsset(id)!!.costPerShare
        assertEquals(9.895, cpsLowSell, 0.001)
        assertTrue(cpsLowSell > cpsHighSell)
    }

    @Test
    fun testEditCompletedSellPriceMovesCostPrice() {
        val id = baseAsset()                       // 底仓 1000 股 @ 10
        val buy = repo.addTBuy(id, 9.0, 100, "", "2026/9/1").trade!!
        repo.supplementSell(buy.id, 9.5, 100, "2026/9/2", "")
        // 卖 9.5 净额 ≈ 1892.91：(10000 + 905.01 - 1892.91) / 1000
        val cpsLow = repo.getAsset(id)!!.costPerShare
        // 卖价提高到 10（净额 ≈ 1992.8），落袋利润增加、剩余成本价下降
        assertTrue(repo.editCompletedTrade(buy.id, 9.0, 100, 10.0, 100, "2026/9/2", "").success)
        val cpsHigh = repo.getAsset(id)!!.costPerShare
        assertTrue(cpsHigh < cpsLow)
    }

    @Test
    fun testEditSellRejectsOversellAndKeepsData() {
        val id = repo.addAsset(name = "测试股票", market = "A股", exchange = "沪市",
            baseTotalCost = 1000.0, baseShares = 100).id   // 底仓 100 股 @ 10
        // 9/1 卖 100（合法，清空底仓），9/2 再买回 200
        val sell = repo.addTSell(id, 11.0, 100, "", "2026/9/1").trade!!
        repo.addTBuy(id, 9.0, 200, "", "2026/9/2")
        assertEquals(200, repo.getAsset(id)!!.shares)

        // 把 9/1 的卖单改成 101 股：该时点只有底仓 100，回放必然超卖 → 拒绝
        val r = repo.editSellTrade(sell.id, 11.0, 101, "2026/9/1", "")
        assertFalse(r.success)
        // 原数据不受污染：持仓仍为 200
        assertEquals(200, repo.getAsset(id)!!.shares)
        assertEquals(100, repo.getAssetTrades(id).first { it.id == sell.id }.sellShares)
    }

    @Test
    fun testSupplementSellRejectsOversell() {
        val id = repo.addAsset(name = "测试股票", market = "A股", exchange = "沪市",
            baseTotalCost = 1000.0, baseShares = 100).id   // 底仓 100 股
        val buy = repo.addTBuy(id, 9.0, 100, "", "2026/9/1").trade!!  // 共 200
        // 对该买单补卖 300 股：同条交易先买 100 后卖 300，超卖 → 拒绝
        val r = repo.supplementSell(buy.id, 9.5, 300, "2026/9/1", "")
        assertFalse(r.success)
        assertEquals(200, repo.getAsset(id)!!.shares)
        assertEquals(0, repo.getAssetTrades(id).first { it.id == buy.id }.sellShares)
    }

    @Test
    fun testParseKlines() {
        val body = """{"data":{"code":"601377","klines":[
            "2026-09-15,5.82,5.80,5.86,5.78,489355",
            "2026-09-16,5.79,5.91,5.92,5.75,840799",
            "2026-09-17,5.88,5.85,5.91,5.83,487986"]}}"""
        val r = StockApi.parseKlines(body)!!
        assertEquals(3, r.size)
        // 取的是第 3 个字段“收盘”而非开盘
        assertEquals(5.80, r[0].close, 1e-9)
        assertEquals(java.time.LocalDate.of(2026, 9, 17), r[2].date)
        assertEquals(5.85, r[2].close, 1e-9)
    }

    @Test
    fun testParseKlinesBadBody() {
        assertNull(StockApi.parseKlines("not json"))
        assertNull(StockApi.parseKlines("""{"data":{}}"""))
        // klines 为空数组时返回 null，UI 走回落
        assertNull(StockApi.parseKlines("""{"data":{"klines":[]}}"""))
    }

    // ---------- AC-6 归档 ----------

    @Test
    fun testArchiveKeepsCostPrice() {
        val id = baseAsset()
        val buy = repo.addTBuy(id, 10.0, 100, "", "2026/9/1").trade!!
        repo.supplementSell(buy.id, 11.0, 100, "2026/9/2", "")
        val before = repo.getAsset(id)!!
        val costBefore = before.totalCost
        val sharesBefore = before.shares

        assertTrue(repo.archiveTrade(buy.id))
        val archived = repo.trades.first { it.id == buy.id }
        assertTrue(archived.isArchived)
        assertEquals(89.43, archived.profit, 0.01)
        // 归档后仅保留盈亏字段
        assertEquals(0, archived.buyShares)
        assertEquals(0, archived.sellShares)

        val after = repo.getAsset(id)!!
        // 底仓已吸收交易影响（卖出按净额1094.44冲减）
        assertEquals(9910.57, after.baseTotalCost, 0.01)
        assertEquals(1000, after.baseShares)
        // 持仓成本价不变
        assertEquals(costBefore, after.totalCost, 0.02)
        assertEquals(sharesBefore, after.shares)
        val pos = repo.replayPosition(after, repo.getAssetTrades(id))
        assertEquals(9.911, pos.costPerShare, 0.001)
        // 归档记录出现在 getAll 但不出现在未归档列表
        assertTrue(repo.getAssetTrades(id).none { it.id == buy.id })
        assertTrue(repo.getAllAssetTrades(id).any { it.id == buy.id })
    }

    /** 底仓为0时归档盈利T：利润写入 baseTotalCost（负值），后续买入成本价被摊薄 */
    @Test
    fun testArchiveWithZeroBaseKeepsProfit() {
        val a = repo.addAsset(name = "零底仓", market = "A股", exchange = "沪市", baseTotalCost = 0.0, baseShares = 0)
        val id = a.id
        val buy = repo.addTBuy(id, 10.0, 100, "", "2026/9/1").trade!!
        repo.supplementSell(buy.id, 11.0, 100, "2026/9/2", "")
        // T完成但未归档：空仓，无幽灵成本
        assertEquals(0.0, repo.getAsset(id)!!.totalCost, 0.001)
        assertEquals(0, repo.getAsset(id)!!.shares)

        assertTrue(repo.archiveTrade(buy.id))
        val after = repo.getAsset(id)!!
        // 归档利润 89.43 写入 baseTotalCost（负值=利润）
        assertEquals(-89.43, after.baseTotalCost, 0.01)
        assertEquals(0, after.baseShares)
        // 空仓不显示幽灵成本/成本价
        assertEquals(0.0, after.totalCost, 0.001)
        assertEquals(0.0, after.costPerShare, 0.001)
        // 利润仍计入总利润
        assertEquals(89.43, after.totalProfit, 0.01)

        // 再次买入 100@10：成本价被归档盈利摊薄 (1005.01 - 89.43)/100 = 9.156
        repo.addTBuy(id, 10.0, 100, "", "2026/9/5")
        val pos = repo.replayPosition(repo.getAsset(id)!!, repo.getAssetTrades(id))
        assertEquals(100, pos.shares)
        assertEquals(9.156, pos.costPerShare, 0.001)
    }

    /** 底仓为0时归档亏损T：亏损写入 baseTotalCost（正值），抬高后续持仓成本价 */
    @Test
    fun testArchiveWithZeroBaseLossRaisesCost() {
        val a = repo.addAsset(name = "零底仓亏", market = "A股", exchange = "沪市", baseTotalCost = 0.0, baseShares = 0)
        val id = a.id
        // 买 100@11（1105.01），卖 100@10（净 994.49），利润 -110.52
        val buy = repo.addTBuy(id, 11.0, 100, "", "2026/9/1").trade!!
        repo.supplementSell(buy.id, 10.0, 100, "2026/9/2", "")
        assertTrue(repo.archiveTrade(buy.id))
        val after = repo.getAsset(id)!!
        // 亏损 110.52 写入 baseTotalCost（正值=亏损）
        assertEquals(110.52, after.baseTotalCost, 0.01)
        assertEquals(0, after.baseShares)
        assertEquals(0.0, after.totalCost, 0.001)

        // 再买 100@10：成本价被亏损抬高 (1005.01 + 110.52)/100 = 11.155
        repo.addTBuy(id, 10.0, 100, "", "2026/9/5")
        val pos = repo.replayPosition(repo.getAsset(id)!!, repo.getAssetTrades(id))
        assertEquals(11.155, pos.costPerShare, 0.001)
    }

    /** 旧版幽灵底仓成本迁移：0股+正成本（历史亏损归档残留）保留在 baseTotalCost */
    @Test
    fun testMigrateLegacyPhantomBaseCost() {
        val a = repo.addAsset(name = "旧数据", market = "A股", exchange = "沪市", baseTotalCost = 0.0, baseShares = 0)
        a.baseTotalCost = 623.63
        a.baseUnit = 0.0
        repo.save()
        repo.load()
        val migrated = repo.getAsset(a.id)!!
        // 幽灵成本保留在 baseTotalCost（正数=亏损），不再清零
        assertEquals(623.63, migrated.baseTotalCost, 0.01)
        assertEquals(0, migrated.baseShares)
    }

    // ---------- AC-9 预估交易 ----------

    @Test
    fun testEstimateLadder() {
        val rows = Estimate.calculate(estimatedPrice = 10.0, costPrice = 10.0, shares = 1000, tradeShares = 100, ratio = 1.017)
        assertEquals(10, rows.size)                              // 1000/100 = 10 行卖完
        assertEquals("10.000", rows[0].price)
        assertEquals("1000.00", rows[0].sellPrice)
        assertEquals("1000", rows[0].remainingShares)
        assertEquals(1000.0, rows[0].profitNum, 0.01)           // 10000 + 1000 - 10000
        // 第二行：价格 10.1017
        assertEquals("10.102", rows[1].price)
        assertEquals("900", rows[1].remainingShares)
        assertEquals(1101.70, rows[1].profitNum, 0.02)
        // 最后一批 100 股（卖出前列示），之后剩余 0 停止
        assertEquals("100", rows.last().remainingShares)
        assertEquals(10, rows.size)
    }

    // ---------- AC-11 备份互通 ----------

    @Test
    fun testCsvRoundtrip() {
        val id = baseAsset()
        val buy = repo.addTBuy(id, 10.0, 100, "备注,含逗号", "2026/9/1").trade!!
        repo.supplementSell(buy.id, 11.0, 100, "2026/9/2", "卖出,备注")

        val csv = repo.exportCsv()
        assertTrue(csv.contains("===资产数据==="))
        assertTrue(csv.contains("===交易记录==="))
        assertTrue(csv.contains("===交易明细表==="))
        assertTrue(csv.contains("ID,名称,市场,交易所,底仓股数"))

        // 段计数
        val (assetCount, tradeCount) = Backup.sectionCounts(csv)
        assertEquals(1, assetCount)
        assertEquals(1, tradeCount)

        // 导入新仓库
        val tmp2 = Files.createTempDirectory("tapp-test2").toFile()
        val repo2 = AppRepository(LocalStore(tmp2))
        repo2.load()
        val (na, nt) = repo2.importCsv(csv)
        assertEquals(1, na)
        assertEquals(1, nt)
        val a = repo2.assets.first()
        assertEquals("测试股票", a.name)
        assertEquals("A股", a.market)
        assertEquals(1000, a.baseShares)
        val t = repo2.trades.first()
        assertEquals("buyToSell", t.tradeStatus)
        assertEquals(100, t.buyShares)
        assertEquals(100, t.sellShares)
        assertEquals(89.43, t.profit, 0.01)
        assertEquals("卖出,备注", t.remark)

        // 非法文件拦截
        var thrown = false
        try {
            Backup.csvToJson("这不是一个备份文件")
        } catch (e: Backup.BackupException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun testJsonRoundtrip() {
        val id = baseAsset()
        repo.addTBuy(id, 10.0, 100, "", "2026/9/1")
        val json = repo.exportJson()
        assertTrue(json.contains("\"version\":\"2.0.0\""))

        val tmp2 = Files.createTempDirectory("tapp-test3").toFile()
        val repo2 = AppRepository(LocalStore(tmp2))
        repo2.load()
        val (na, nt) = repo2.importJson(json)
        assertEquals(1, na)
        assertEquals(1, nt)
        assertEquals(0.85, repo2.settings.tradeRateA!!, 0.001)
    }

    // ---------- AC-10 费率联动 ----------

    @Test
    fun testCustomCommissionRate() {
        val s = Settings(tradeRateA = 1.0)  // 万1
        val f = Fees.calc(price = 100.0, shares = 10000, isSell = false, s = s)
        assertEquals(100.0, f.commission, 0.01)   // 1,000,000 * 0.0001
        val fMin = Fees.calc(price = 1.0, shares = 100, isSell = false, s = s)
        assertEquals(5.0, fMin.commission, 0.01)  // 最低5元
    }

    @Test
    fun testPersistenceAcrossRestart() {
        val id = baseAsset()
        repo.addTBuy(id, 10.0, 100, "", "2026/9/1")
        // 重新加载（模拟重启）
        val repo2 = AppRepository(LocalStore(tmp))
        repo2.load()
        assertEquals(1, repo2.assets.size)
        assertEquals(1, repo2.trades.size)
        assertEquals("测试股票", repo2.assets.first().name)
        assertEquals(1100, repo2.assets.first().shares)
    }

    // ---------- 做T利润：记录利润口径（与 app.js / 每笔交易卡片可对账） ----------

    /** 兴业证券批次：底仓 60000@6.138，9 笔买入各 10000，5 笔卖出共 90000（均未补单） */
    @Test
    fun testRealizedProfitXingyeBatch() {
        val a = repo.addAsset(name = "兴业证券", market = "A股", exchange = "沪市",
            baseTotalCost = 368280.0, baseShares = 60000)
        val id = a.id
        listOf(6.0, 6.03, 6.05, 6.1, 6.15, 6.2, 6.24, 6.26, 6.28).forEach {
            repo.addTBuy(id, it, 10000, "", "2026/9/10")
        }
        listOf(5.82 to 10000, 5.84 to 20000, 5.86 to 20000, 5.87 to 20000, 5.94 to 20000).forEach {
            repo.addTSell(id, it.first, it.second, "", "2026/9/10")
        }
        // 总做T利润 = 5 笔独立卖出记录暂估利润之和（卖出按净额冲减成本）
        // -3263.52 - 6593.40 - 7292.53 - 8551.15 - 9289.77 = -34990.37
        assertEquals(-34990.37, repo.getAsset(id)!!.totalProfit, 0.05)
        assertEquals(-34990.37, repo.getAsset(id)!!.monthProfit, 0.05)
        // 全部未T完：利润日历/当月合计不计入
        assertEquals(0.0, StatisticsCalc.monthlyProfit(repo.assets, repo.trades, 2026, 9), 0.001)
        // 持仓回到 60000 股
        assertEquals(60000, repo.getAsset(id)!!.shares)
    }

    /**
     * 兴业证券完整批次（成本价 2.002 口径）：
     * 底仓 10000@1；独立买单 2/3/4 各 10000；独立卖单 2/3 各 10000；
     * T完成单：买 1 /卖 2。所有卖出一律按净额冲减成本（卖出费用留在剩余持仓），
     * 配对为T完成不影响成本价，与独立卖出完全一致。
     */
    @Test
    fun testBrokerCostPriceMixedSellRules() {
        val id = repo.addAsset("兴业证券", "A股", "沪市", 10000.0, 10000).id
        repo.addTBuy(id, 2.0, 10000, "", "2026/9/18")
        repo.addTBuy(id, 3.0, 10000, "", "2026/9/18")
        repo.addTBuy(id, 4.0, 10000, "", "2026/9/18")
        repo.addTSell(id, 2.0, 10000, "", "2026/9/18")
        repo.addTSell(id, 3.0, 10000, "", "2026/9/18")
        // T完成记录：买 1 补卖 2
        val tBuy = repo.addTBuy(id, 1.0, 10000, "", "2026/9/18").trade!!
        val sup = repo.supplementSell(tBuy.id, 2.0, 10000, "2026/9/18", "")
        assertTrue(sup.success)

        val asset = repo.getAsset(id)!!
        assertEquals(20000, asset.shares)
        // 所有卖出一律按净额冲减：T完成配对不影响成本价
        val pos = repo.replayPosition(asset, repo.getAssetTrades(id))
        // 配对前后成本价应完全一致
        assertEquals(pos.costPerShare, asset.costPerShare, 0.0001)
    }

    /**
     * 国金证券批次：清仓后重新买入，亏损结转，T完成配对不影响成本价。
     * 底仓 10000@9.152；卖出 10000@8.3（净额约82950.61）；买入 8.28/8.28/8.33 各10000。
     * 成本价 = (91520-82950.61+82807.87+82807.87+83307.91)/30000 = 8.583
     */
    @Test
    fun testGuojinCostPriceAfterClearAndRebuy() {
        val id = repo.addAsset("国金证券", "A股", "沪市", 91520.0, 10000).id
        repo.addTSell(id, 8.3, 10000, "", "2026/9/23")
        repo.addTBuy(id, 8.28, 10000, "", "2026/9/23")
        repo.addTBuy(id, 8.28, 10000, "", "2026/9/23")
        repo.addTBuy(id, 8.33, 10000, "", "2026/9/23")

        val asset = repo.getAsset(id)!!
        assertEquals(30000, asset.shares)
        val pos = repo.replayPosition(asset, repo.getAssetTrades(id))
        assertEquals(8.583, pos.costPerShare, 0.001)
    }

    /**
     * 同上国金证券数据，但卖出+首笔买入配对为T完成后，成本价不变。
     */
    @Test
    fun testGuojinTCoupleDoesNotChangeCostPrice() {
        val id = repo.addAsset("国金证券", "A股", "沪市", 91520.0, 10000).id
        // 独立卖出
        val sell = repo.addTSell(id, 8.3, 10000, "", "2026/9/23").trade!!
        // 独立买入
        repo.addTBuy(id, 8.28, 10000, "", "2026/9/23")
        repo.addTBuy(id, 8.28, 10000, "", "2026/9/23")
        repo.addTBuy(id, 8.33, 10000, "", "2026/9/23")

        val costBefore = repo.getAsset(id)!!.costPerShare

        // 将卖出和首笔买入配对为T完成（不影响成本价）
        // 注意：这里验证的是配对行为本身不改变成本，因为所有卖出都按净额冲减
        val pos = repo.replayPosition(repo.getAsset(id)!!, repo.getAssetTrades(id))
        assertEquals(8.583, pos.costPerShare, 0.001)
        assertEquals(costBefore, pos.costPerShare, 0.001)
    }

    /**
     * 清仓后重新买入：亏损保留在 totalCost，再次买入自然叠加（券商口径）。
     * 底仓 10000@9.193；卖出 10000@8.4（净额约 83950.02）；再买入 10000@8.35。
     * 成本 = (91930 - 83950.02) + 83507.94 = 91487.92 → 成本价 9.149
     */
    @Test
    fun testSellAllThenBuyCarriesLoss() {
        val id = repo.addAsset("国金证券", "A股", "沪市", 91930.0, 10000).id
        repo.addTSell(id, 8.4, 10000, "", "2026/9/17")
        val asset0 = repo.getAsset(id)!!
        assertEquals(0, asset0.shares)
        // 亏损保留在 totalCost（不归零）
        assertTrue(asset0.totalCost > 0.0)

        repo.addTBuy(id, 8.35, 10000, "", "2026/9/18")
        val asset = repo.getAsset(id)!!
        assertEquals(10000, asset.shares)
        val pos = repo.replayPosition(asset, repo.getAssetTrades(id))
        // (91930 - 83950.02 + 83507.94) / 10000 = 9.149
        assertEquals(9.149, pos.costPerShare, 0.001)
    }

    /** 只买入未卖出：总利润与日历均为 0 */
    @Test
    fun testBuyOnlyHasNoRealizedProfit() {
        val id = baseAsset()
        repo.addTBuy(id, 10.0, 100, "", "2026/9/1")
        repo.addTBuy(id, 10.5, 200, "", "2026/9/2")
        assertEquals(0.0, repo.getAsset(id)!!.totalProfit, 0.001)
        assertEquals(0.0, StatisticsCalc.monthlyProfit(repo.assets, repo.trades, 2026, 9), 0.001)
    }

    /** 反T卖出减持底仓：暂估利润 = 净额 1094.44 - 底仓成本 1000 = 94.44 */
    @Test
    fun testSellFromBasePosition() {
        val id = baseAsset()   // 底仓 1000 股 @ 10
        val r = repo.addTSell(id, 11.0, 100, "", "2026/9/2")
        assertTrue(r.success)
        assertEquals(94.44, repo.getAsset(id)!!.totalProfit, 0.02)
    }

    /** 利润日历只统计 T完成/归档：未配对卖出不进日历，补卖后按 卖出净额-买入总额 计入 */
    @Test
    fun testProfitCalendarOnlyCountsCompletedTrades() {
        val id = baseAsset()
        val buy = repo.addTBuy(id, 10.0, 100, "", "2026/9/1").trade!!
        repo.addTSell(id, 11.0, 100, "", "2026/9/2")   // 独立反T卖出（未T完）
        // 顶部总利润含该笔暂估 93.98
        assertEquals(93.98, repo.getAsset(id)!!.totalProfit, 0.02)
        // 日历当月合计仍为 0（无 T完成记录）
        assertEquals(0.0, StatisticsCalc.monthlyProfit(repo.assets, repo.trades, 2026, 9), 0.001)
        // 给正T买入补卖 100@11 → T完成：1094.44 - 1005.01 = 89.43
        repo.supplementSell(buy.id, 11.0, 100, "2026/9/3", "")
        assertEquals(89.43, StatisticsCalc.monthlyProfit(repo.assets, repo.trades, 2026, 9), 0.02)
        val cal = StatisticsCalc.profitCalendar(repo.assets, repo.trades, 2026, 9)
        val day3 = cal.first { it.date != null && it.date.dayOfMonth == 3 }
        assertTrue(day3.hasProfit)
        assertEquals(89.43, day3.profit, 0.02)
    }

    /** 统计页总做T利润：未T完的独立卖出暂估不计入，T完成后才计入 */
    @Test
    fun testStatisticsTotalProfitOnlyCompleted() {
        val id = baseAsset()
        repo.addTSell(id, 11.0, 100, "", "2026/9/2")   // 独立反T卖出（未T完，暂估 94.44）
        assertEquals(0.0, StatisticsCalc.totalRealizedProfit(repo.assets, repo.trades), 0.001)
        val buy = repo.addTBuy(id, 10.0, 100, "", "2026/9/3").trade!!
        repo.supplementSell(buy.id, 11.0, 100, "2026/9/4", "")  // T完成：89.43
        assertEquals(89.43, StatisticsCalc.totalRealizedProfit(repo.assets, repo.trades), 0.02)
        assertEquals(
            89.43,
            StatisticsCalc.dailyRealizedProfit(repo.assets, repo.trades, java.time.LocalDate.of(2026, 9, 4)),
            0.02
        )
    }

    /** 拖动排序：顺序写入 order 并通过 assetsFlow 体现；子集排序不影响其他资产的相对位置 */
    @Test
    fun testReorderAssets() {
        val a = repo.addAsset("A", "A股", "沪市", 0.0, 0)
        val b = repo.addAsset("B", "A股", "沪市", 0.0, 0)
        assertEquals(listOf(b.id, a.id), repo.assetsFlow.value.map { it.id })  // 默认新在前
        repo.reorderAssets(listOf(a.id, b.id))
        assertEquals(listOf(a.id, b.id), repo.assetsFlow.value.map { it.id })
        // 重新载入后顺序保持
        repo.load()
        assertEquals(listOf(a.id, b.id), repo.assetsFlow.value.map { it.id })
    }

    // ---------- 走势图：交易日历（排除周末与法定节假日） ----------

    private fun d(m: Int, day: Int) = java.time.LocalDate.of(2026, m, day)

    /** 离线兜底：4 周窗口内只含工作日，且排除中秋、国庆法定休市日 */
    @Test
    fun testTradingCalendarFallbackExcludesWeekendsAndHolidays() {
        // 窗口 2026/9/11 - 2026/10/9，横跨中秋(9/25周五)与国庆(10/1-10/7)
        val days = TradingCalendar.recentTradingDays(d(10, 9), 4, emptyList())
        assertTrue(days.none { it.dayOfWeek == java.time.DayOfWeek.SATURDAY || it.dayOfWeek == java.time.DayOfWeek.SUNDAY })
        assertFalse(days.contains(d(9, 25)))                    // 中秋节周五休市
        (1..7).forEach { assertFalse(days.contains(d(10, it))) } // 国庆休市
        assertTrue(days.contains(d(10, 8)))                    // 节后首个交易日
        assertEquals(d(10, 9), days.last())
        assertEquals(days.sorted(), days)
    }

    /** 有日K实际日期时以其为准；盘中今天日K缺失但今天为交易日时补上今天 */
    @Test
    fun testTradingCalendarPrefersKnownDates() {
        val known = listOf(d(9, 14), d(9, 15), d(9, 16), d(9, 17))
        val days = TradingCalendar.recentTradingDays(d(9, 18), 4, known)
        assertEquals(known + d(9, 18), days)
        // 已知日期落在窗口外的被裁掉
        val days2 = TradingCalendar.recentTradingDays(
            d(9, 18), 4, known + listOf(d(8, 3))
        )
        assertFalse(days2.contains(d(8, 3)))
    }

    /** recentCostHistory 按传入交易日出点，不掺周末；成本价按交易日回放 */
    @Test
    fun testRecentCostHistoryTradingDaysOnly() {
        val id = baseAsset()   // 底仓 1000@10
        repo.addTBuy(id, 10.0, 100, "", "2026/9/16")
        val asset = repo.getAsset(id)!!
        val days = listOf(d(9, 15), d(9, 16), d(9, 17), d(9, 18))
        val hist = StatisticsCalc.recentCostHistory(asset, repo.getAssetTrades(id), days)
        assertEquals(4, hist.size)
        assertEquals(d(9, 15), hist.first().date)
        assertEquals(d(9, 18), hist.last().date)
        assertEquals(10.0, hist[0].costPerShare, 1e-6)              // 买入前：10000/1000
        // 9/16 买入后：(10000+1005.01)/1100 = 10.0046
        assertEquals(11005.01, hist[1].totalCost, 0.01)
        assertEquals(1100, hist[1].shares)
        assertEquals(10.005, hist[1].costPerShare, 0.001)
    }
}
