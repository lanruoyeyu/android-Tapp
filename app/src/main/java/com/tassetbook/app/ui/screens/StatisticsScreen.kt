package com.tassetbook.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.domain.StatisticsCalc
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.LossGreen
import com.tassetbook.app.ui.PriceOrange
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextPrimary
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.ui.fmt2
import com.tassetbook.app.ui.fmt3
import com.tassetbook.app.ui.profitColor
import com.tassetbook.app.util.Money
import com.tassetbook.app.util.StockApi
import com.tassetbook.app.util.TradingCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun StatisticsScreen() {
    val repo = AppRepository.get()
    val assets by repo.assetsFlow.collectAsState()
    val trades by repo.tradesFlow.collectAsState()

    var year by remember { mutableStateOf(LocalDate.now().year) }
    var month by remember { mutableStateOf(LocalDate.now().monthValue) }
    var selectedDay by remember { mutableStateOf<StatisticsCalc.CalendarDay?>(null) }
    var chartView by remember { mutableStateOf(true) }  // true=折线图, false=表格

    // 顶部利润口径与交易记录页一致：仅 T完成 + 归档（不含未T完的暂估盈亏）
    val now = LocalDate.now()
    val totalProfit = remember(assets, trades) { StatisticsCalc.totalRealizedProfit(assets, trades) }
    val monthProfit = remember(assets, trades) { StatisticsCalc.monthlyProfit(assets, trades, now.year, now.monthValue) }
    val todayProfit = remember(assets, trades) { StatisticsCalc.dailyRealizedProfit(assets, trades, now) }
    val totalCost = assets.sumOf { it.totalCost }
    val calendar = remember(year, month, trades, assets) { StatisticsCalc.profitCalendar(assets, trades, year, month) }
    val monthProfitCalendar = remember(year, month, trades, assets) { StatisticsCalc.monthlyProfit(assets, trades, year, month) }

    fun shiftMonth(delta: Int) {
        var m = month + delta
        var y = year
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        month = m; year = y
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgGray)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text("统计", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        // 按屏宽比例缩放汇总数字，避免长数字错行
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val sumValueSize = (screenWidthDp * 0.041f).coerceIn(13f, 17f).sp
        val sumLabelSize = (screenWidthDp * 0.030f).coerceIn(10f, 12f).sp
        val smallValueSize = (screenWidthDp * 0.035f).coerceIn(11f, 14f).sp

        // 汇总卡
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SumItem("总做T利润", totalProfit, Modifier.weight(1f), sumLabelSize, sumValueSize)
                    SumItem("本月利润", monthProfit, Modifier.weight(1f), sumLabelSize, sumValueSize)
                    SumItem("今日利润", todayProfit, Modifier.weight(1f), sumLabelSize, sumValueSize)
                }
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SumItemSmall("持仓总成本", fmt2(totalCost), Modifier.weight(1f), sumLabelSize, smallValueSize)
                    SumItemSmall("资产数", "${assets.size}", Modifier.weight(1f), sumLabelSize, smallValueSize)
                }
            }
        }

        // 利润日历
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("做T利润日历", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { shiftMonth(-1) }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上月") }
                    Text("${year}年${month}月", fontSize = 14.sp)
                    IconButton(onClick = { shiftMonth(1) }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下月") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("当月合计", fontSize = 13.sp, color = TextSecondary)
                    Text(
                        (if (monthProfitCalendar >= 0) "+" else "") + fmt2(monthProfitCalendar),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = profitColor(monthProfitCalendar)
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 周标题（仅周一至周五）
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五").forEach {
                        Text(it, fontSize = 12.sp, color = TextHint, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(4.dp))
                calendar.chunked(5).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).padding(2.dp)) {
                                if (day.date != null) {
                                    val bg = when {
                                        day.hasProfit && day.profit > 0 -> ProfitRed.copy(alpha = 0.10f)
                                        day.hasProfit && day.profit < 0 -> LossGreen.copy(alpha = 0.10f)
                                        day.isToday -> PrimaryBlue.copy(alpha = 0.08f)
                                        else -> Color(0xFFF7F8FA)
                                    }
                                    // 固定等高 + 垂直居中，避免有/无利润的格子高度不一致导致整行错位
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .background(bg, RoundedCornerShape(6.dp))
                                            .clickable(enabled = day.hasProfit) { selectedDay = day },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("${day.day}", fontSize = 12.sp, color = if (day.isToday) PrimaryBlue else TextSecondary)
                                        if (day.hasProfit) {
                                            Text(
                                                compactProfit(day.profit),
                                                fontSize = 8.sp,
                                                color = profitColor(day.profit),
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 各资产成本价走势
        Text("持仓成本价走势", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        val visible = assets.filter { it.shares > 0 }

        // 联网获取现价：交易时段（9:00-15:00）每 10s 全量刷新；其他时段若仍有资产
        // 没拿到价（首次请求失败/弱网），每 5s 只补缺失的，最多重试 20 轮（约 100s）
        var chartPrices by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
        LaunchedEffect(visible.map { it.id }) {
            var retryCount = 0
            while (true) {
                for (a in visible) {
                    if (StockApi.inTradingHours() || chartPrices[a.id] == null) {
                        StockApi.fetchPrice(a.name)?.let { p -> chartPrices = chartPrices + (a.id to p) }
                    }
                }
                if (!StockApi.inTradingHours() && visible.all { chartPrices[it.id] != null }) break
                if (!StockApi.inTradingHours()) {
                    retryCount++
                    if (retryCount >= 20) break
                }
                delay(5_000)
            }
        }

        // 联网获取历史日K收盘价（多取 45 个自然日，覆盖国庆等长假，保证 4 周窗口交易日齐全）。
        // 移动网络偶发失败：未成功的资产每 3s 重试下一轮，最多 10 轮，避免冷启动弱网后曲线永久缺失
        var chartCloses by remember { mutableStateOf<Map<String, Map<LocalDate, Double>>>(emptyMap()) }
        LaunchedEffect(visible.map { it.id }) {
            val to = LocalDate.now()
            val from = to.minusDays(45)
            val pending = visible.associate { it.id to it.name }.toMutableMap()
            repeat(10) {
                if (pending.isEmpty()) return@LaunchedEffect
                for ((aid, name) in pending.toList()) {
                    val list = StockApi.fetchDailyCloses(name, from, to)
                    if (list != null) {
                        chartCloses = chartCloses + (aid to list.associate { it.date to it.close })
                        pending.remove(aid)
                    }
                }
                if (pending.isNotEmpty()) delay(3000)
            }
        }

        if (visible.isEmpty()) {
            Text("暂无持仓数据", fontSize = 13.sp, color = TextHint, modifier = Modifier.padding(24.dp))
        }
        visible.forEach { asset ->
            val currentPrice = chartPrices[asset.id]
            val closes = chartCloses[asset.id].orEmpty()
            // 横轴只含交易日（日K实际日期，离线时工作日-法定节假日兜底），窗口为往前 4 周
            val tradingDays = remember(closes.keys) {
                TradingCalendar.recentTradingDays(LocalDate.now(), 4, closes.keys)
            }
            val history = remember(trades, asset.id, tradingDays) {
                StatisticsCalc.recentCostHistory(
                    asset,
                    trades.filter { it.assetId == asset.id && !it.isArchived },
                    tradingDays
                )
            }
            val costPerShare = Money.round3(asset.costPerShare)
            val profitVal = currentPrice?.let { (it - costPerShare) * asset.shares }
            val profitPct = currentPrice?.let { (it - costPerShare) / costPerShare * 100 }
            val marketValue = currentPrice?.let { it * asset.shares }
            val profitRate = profitVal?.let { it / asset.totalCost * 100 }
            // 今日T利润：当日做T（T完成/归档）已实现利润，不是浮动盈亏
            val todayTProfit = remember(trades, asset.id) {
                StatisticsCalc.assetDailyProfit(asset, trades, LocalDate.now())
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    // 第一行：股票名称 / 今日T利润 / 现价 / 切换按钮
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            asset.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        StatCell(
                            "今日T利润",
                            (if (todayTProfit >= 0) "+" else "") + fmt2(todayTProfit),
                            if (todayTProfit >= 0) ProfitRed else LossGreen,
                            Modifier.weight(1.1f)
                        )
                        StatCell(
                            "现价",
                            currentPrice?.let { fmt2(it) } ?: "--",
                            PriceOrange,
                            Modifier.weight(0.9f)
                        )
                        // 切换按钮
                        Row(
                            Modifier
                                .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                .clickable { chartView = !chartView }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (chartView) "折线图" else "表格",
                                fontSize = 12.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(5.dp))

                    // 图例
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendItem(PrimaryBlue, "成本价")
                        LegendItem(PriceOrange, "收盘价")
                    }

                    Spacer(Modifier.height(4.dp))

                    // 折线图或表格
                    if (chartView) {
                        CostHistoryChart(history, currentPrice, closes)
                    } else {
                        CostHistoryTable(history, currentPrice, closes)
                    }

                    Spacer(Modifier.height(5.dp))

                    // 第三行：市值 / 股数 / 成本价
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCell("市值", marketValue?.let { fmt2(it) } ?: "--", TextPrimary, Modifier.weight(1f))
                        StatCell("股数", "${asset.shares}", TextPrimary, Modifier.weight(1f))
                        StatCell("成本价", fmt3(costPerShare), PrimaryBlue, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(5.dp))

                    // 第四行：收益 / 收益率 / 成本金额
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCell(
                            "收益",
                            profitVal?.let { (if (it >= 0) "+" else "") + fmt2(it) } ?: "--",
                            profitVal?.let { if (it >= 0) ProfitRed else LossGreen } ?: TextSecondary,
                            Modifier.weight(1f)
                        )
                        StatCell(
                            "收益率",
                            profitRate?.let { (if (it >= 0) "+" else "") + "%.2f".format(it) + "%" } ?: "--",
                            profitRate?.let { if (it >= 0) ProfitRed else LossGreen } ?: TextSecondary,
                            Modifier.weight(1f)
                        )
                        StatCell("成本金额", fmt2(asset.totalCost), TextPrimary, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // 当日利润明细
    selectedDay?.let { day ->
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = { Text("${day.date} 做T利润") },
            text = {
                Column {
                    day.assetProfits.forEach { ap ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${ap.assetName}（${ap.tradeCount}笔）", fontSize = 13.sp)
                            Text(
                                (if (ap.profit >= 0) "+" else "") + fmt2(ap.profit),
                                fontSize = 13.sp,
                                color = profitColor(ap.profit),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("合计", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            (if (day.profit >= 0) "+" else "") + fmt2(day.profit),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = profitColor(day.profit)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedDay = null }) { Text("关闭", color = PrimaryBlue) } }
        )
    }
}

/** 日历格内紧凑利润：万元以上显示为 x.xx万，避免长数字撑破格子；精确值见当日明细弹窗 */
private fun compactProfit(v: Double): String {
    val sign = when {
        v > 0 -> "+"
        v < 0 -> "-"
        else -> ""
    }
    val a = kotlin.math.abs(v)
    return if (a >= 10000) "$sign%.2f万".format(a / 10000) else sign + fmt2(a)
}

@Composable
private fun SumItem(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    labelSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    valueSize: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    Column(modifier) {
        Text(label, fontSize = labelSize, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        Text(
            (if (value >= 0) "+" else "") + fmt2(value),
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SumItemSmall(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    valueSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    Column(modifier) {
        Text(label, fontSize = labelSize, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        Text(
            value,
            fontSize = valueSize,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** 统计卡片信息格：标签在上、数值在下（市值/股数/成本价等） */
@Composable
private fun StatCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** 走势图图例：一段彩色短线 + 文字 */
@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp).height(3.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun CostHistoryTable(
    history: List<StatisticsCalc.DayPoint>,
    currentPrice: Double?,
    closes: Map<LocalDate, Double>
) {
    val today = LocalDate.now()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 表头
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgGray)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("日期", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Medium)
            Text("收盘", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("成本价", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("股数", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        // 数据行（倒序：最新在上）
        history.reversed().forEach { p ->
            val closeVal = closes[p.date] ?: if (p.date == today) currentPrice else null
            val isToday = p.date == today
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${p.date.monthValue}/${p.date.dayOfMonth}",
                    fontSize = 11.sp,
                    color = if (isToday) PrimaryBlue else TextPrimary,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    closeVal?.let { "%.2f".format(it) } ?: "--",
                    fontSize = 11.sp,
                    color = PriceOrange,
                    modifier = Modifier.weight(0.9f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Text(
                    if (p.hasPosition) "%.3f".format(p.costPerShare) else "--",
                    fontSize = 11.sp,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(0.9f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Text(
                    if (p.hasPosition) "${p.shares}" else "--",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun CostHistoryChart(
    history: List<StatisticsCalc.DayPoint>,
    currentPrice: Double?,
    closes: Map<LocalDate, Double>
) {
    val lineColor = PrimaryBlue
    val maxColor = ProfitRed
    val minColor = LossGreen
    val gridColor = Color(0xFFE8E8E8)
    // 触摸十字线当前定位到的日期索引（仿东方财富：按下/滑动显示竖线与弹窗，松手后保留）
    var touchIndex by remember { mutableStateOf<Int?>(null) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            // 触摸十字线：水平拖动跟随、点击定位；垂直移动让给父级滚动，互不干扰
            .pointerInput(history.size) {
                val pLeft = 78f
                val pRight = 8f
                val plotW = size.width - pLeft - pRight
                fun indexAt(px: Float): Int {
                    val f = (px - pLeft) / plotW * (history.size - 1)
                    return f.roundToInt().coerceIn(0, history.size - 1)
                }
                val slop = viewConfiguration.touchSlop
                // 外层循环：手势被父级滚动竞争取消时（CancellationException 但协程仍活），重启监听
                while (coroutineContext.isActive) {
                    try {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            // 0=未决 1=水平拖动(跟十字线) 2=垂直移动(让给父级滚动)
                            var mode = 0
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                val dx = change.position.x - downX
                                val dy = change.position.y - down.position.y
                                if (mode == 0) {
                                    when {
                                        kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy) -> {
                                            mode = 1
                                            touchIndex = indexAt(change.position.x)
                                        }
                                        kotlin.math.abs(dy) > slop -> mode = 2
                                        !change.pressed -> {
                                            // 松手时未形成拖动 → 点击定位
                                            touchIndex = indexAt(downX)
                                            mode = 3
                                        }
                                    }
                                } else if (mode == 1) {
                                    touchIndex = indexAt(change.position.x)
                                }
                                if (!change.pressed) break
                            }
                        }
                    } catch (ce: CancellationException) {
                        if (!coroutineContext.isActive) throw ce
                    }
                }
            }
    ) {
        if (history.isEmpty()) {
            return@Canvas
        }
        // 仅用有持仓的日期参与成本线与量程；空仓日折线断开
        val costs = history.filter { it.hasPosition }.map { it.costPerShare }
        // 收盘价点：只取图上区间内的交易日（周末/节假日无点，线段自然跨过间隔）
        val closePts = history.mapIndexedNotNull { i, p ->
            closes[p.date]?.let { i to it }
        }
        var minV = costs.minOrNull() ?: (closePts.firstOrNull()?.second ?: 0.0)
        var maxV = costs.maxOrNull() ?: (closePts.firstOrNull()?.second ?: 1.0)
        // 收盘价纳入取值范围，保证橙色收盘曲线始终在图内
        for ((_, v) in closePts) {
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        // 无收盘价时，现价纳入量程（回落的水平虚线）
        if (closePts.isEmpty()) currentPrice?.let { cp ->
            if (cp < minV) minV = cp
            if (cp > maxV) maxV = cp
        }
        val range = maxV - minV
        if (range > 0.01) {
            minV = maxOf(0.0, minV - range * 0.1)
            maxV = maxV + range * 0.1
        } else {
            minV = maxOf(0.0, minV - 0.5)
            maxV = maxV + 0.5
        }
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val padLeft = 78f   // 左侧留出纵轴价格刻度空间
        val padRight = 8f
        val padTop = 16f
        val padBottom = 30f   // 拉开最底刻度与日期标签，避免文字相挤
        val w = size.width - padLeft - padRight
        val h = size.height - padTop - padBottom
        fun x(i: Int): Float = padLeft + if (history.size <= 1) w / 2 else i.toFloat() / (history.size - 1) * w
        fun y(v: Double): Float = padTop + h - ((v - minV) / span).toFloat() * h

        val nativeCanvas = drawContext.canvas.nativeCanvas

        // 网格 + 纵轴价格刻度（5 档，成本价与收盘价共用一个量程）
        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#999999")
            textSize = 19f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val axisFm = axisPaint.fontMetrics
        for (j in 0..4) {
            val yy = padTop + h * j / 4
            drawLine(gridColor, Offset(padLeft, yy), Offset(size.width - padRight, yy), strokeWidth = 1f)
            val v = maxV - span * j / 4
            nativeCanvas.drawText(
                "%.2f".format(v),
                padLeft - 8f,
                yy - (axisFm.ascent + axisFm.descent) / 2f,
                axisPaint
            )
        }
        // 最高/最低成本虚线
        costs.maxOrNull()?.let { maxCost ->
            drawLine(
                maxColor, Offset(padLeft, y(maxCost)), Offset(size.width - padRight, y(maxCost)),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
        costs.minOrNull()?.let { minCost ->
            drawLine(
                minColor, Offset(padLeft, y(minCost)), Offset(size.width - padRight, y(minCost)),
                strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
        // 成本价折线：相邻两个有持仓的日期才连线，空仓区间断开
        if (costs.isNotEmpty()) {
            val linePath = Path()
            var prev = -1
            history.forEachIndexed { i, p ->
                if (p.hasPosition) {
                    val px = x(i)
                    val py = y(p.costPerShare)
                    if (prev >= 0 && i - prev == 1) linePath.lineTo(px, py) else linePath.moveTo(px, py)
                    prev = i
                }
            }
            drawPath(linePath, color = lineColor, style = Stroke(width = 3f))
            // 成本价数据点
            history.forEachIndexed { i, p ->
                if (p.hasPosition) {
                    drawCircle(lineColor, radius = 4f, center = Offset(x(i), y(p.costPerShare)))
                }
            }
        }
        // 收盘价折线（日K历史）：连接区间内各交易日收盘，跨周末直接连线
        if (closePts.isNotEmpty()) {
            val closePath = Path()
            closePts.forEachIndexed { k, (i, v) ->
                val px = x(i)
                val py = y(v)
                if (k == 0) closePath.moveTo(px, py) else closePath.lineTo(px, py)
            }
            drawPath(closePath, color = PriceOrange, style = Stroke(width = 2.5f))
            closePts.forEach { (i, v) ->
                drawCircle(PriceOrange, radius = 3f, center = Offset(x(i), y(v)))
            }
        } else if (currentPrice != null) {
            // 日K拉取失败的回落：现价水平虚线
            val cy = y(currentPrice)
            drawLine(
                PriceOrange, Offset(padLeft, cy), Offset(size.width - padRight, cy),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
        }
        // 末端标签：成本价（蓝，默认画在点下方）/ 收盘价（橙，默认画在点上方），两线贴近时上下错开
        val labelPaint = android.graphics.Paint().apply {
            textSize = 24f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val costAnchor = costs.lastOrNull()?.let {
            val idx = history.indexOfLast { p -> p.hasPosition }
            Triple((x(idx) - 6f).coerceAtLeast(120f), y(it), it)
        }
        // 日K曲线：标签居中于最后一个数据点上方（末点贴近右缘，避免压住文字）
        val closeAnchor = closePts.lastOrNull()?.let { (i, v) ->
            val cx = x(i).coerceIn(padLeft + 60f, size.width - padRight - 60f)
            Triple(cx, y(v), v)
        }
        // 回落（无日K）时把现价标签当作收盘标签，靠右显示
        val closeAnchor2 = closeAnchor ?: if (currentPrice != null)
            Triple(size.width - padRight - 4f, y(currentPrice), currentPrice) else null

        var costTextY = costAnchor?.second?.let { if (it + 26f > size.height - padBottom) it - 10f else it + 26f }
        var closeTextY = closeAnchor2?.second?.let { if (it - 14f < 26f) it + 28f else it - 14f }
        // 两标签纵向贴在一起时：收盘价上移、成本价下移，强制错开
        if (costTextY != null && closeTextY != null && kotlin.math.abs(costTextY - closeTextY) < 28f) {
            val mid = (costAnchor!!.second + closeAnchor2!!.second) / 2f
            closeTextY = mid - 6f
            costTextY = (mid + 22f).coerceAtMost(size.height - padBottom)
        }
        costAnchor?.let { (lx, _, v) ->
            labelPaint.color = android.graphics.Color.parseColor("#1565C0")
            nativeCanvas.drawText("成本 %.3f".format(v), lx, costTextY!!, labelPaint)
        }
        closeAnchor2?.let { (lx, _, v) ->
            labelPaint.color = android.graphics.Color.parseColor("#FF4500")
            // 日K曲线标签居中于末点；回落的现价标签保持右对齐
            labelPaint.textAlign = if (closePts.isNotEmpty())
                android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.RIGHT
            nativeCanvas.drawText("收盘 %.2f".format(v), lx, closeTextY!!, labelPaint)
        }
        // 日期标签（约每 3 天一个，避免拥挤）
        val step = if (history.size <= 7) 1 else (history.size / 7).coerceAtLeast(2)
        val datePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#999999")
            textSize = 22f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val lastI = history.size - 1
        history.forEachIndexed { i, p ->
            // 步长标签与末端标签至少隔开一个步长，避免最后两个日期文字叠在一起
            val show = (i % step == 0 && lastI - i >= step) || i == lastI
            if (show) {
                nativeCanvas.drawText("${p.date.monthValue}/${p.day}", x(i), size.height - 4f, datePaint)
            }
        }

        // ---------- 触摸十字线 + 半透明弹窗（仿东方财富） ----------
        touchIndex?.let { ti ->
            val p = history[ti]
            val vx = x(ti)
            // 该日收盘价：日K数据；当天盘中日K未生成时回落实时现价
            val closeVal = closes[p.date]
                ?: if (p.date == LocalDate.now()) currentPrice else null
            val costVal = if (p.hasPosition) p.costPerShare else null

            // 纵向虚线
            drawLine(
                Color(0x99333333),
                Offset(vx, padTop), Offset(vx, size.height - padBottom),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            // 曲线上高亮光晕（白环 + 彩色实心）
            fun highlight(px: Float, py: Float, c: Color) {
                drawCircle(Color.White, radius = 7f, center = Offset(px, py))
                drawCircle(c, radius = 4.5f, center = Offset(px, py))
            }
            closeVal?.let { highlight(vx, y(it), PriceOrange) }
            costVal?.let { highlight(vx, y(it), lineColor) }

            // 半透明深色圆角弹窗
            val boxW = 218f
            val boxH = 138f
            var boxLeft = vx + 14f
            if (boxLeft + boxW > size.width - padRight) boxLeft = vx - 14f - boxW
            boxLeft = boxLeft.coerceIn(padLeft, size.width - padRight - boxW)
            val boxTop = padTop + 2f
            drawRoundRect(
                color = Color(0xE63A3A3A),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(18f, 18f)
            )

            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 20f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val valuePaint = android.graphics.Paint().apply {
                textSize = 20f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val tx = boxLeft + 16f
            val vx2 = boxLeft + 74f
            // 行1：完整日期
            nativeCanvas.drawText(
                "${p.date.year}/${p.date.monthValue}/${p.date.dayOfMonth}",
                tx, boxTop + 32f, labelPaint
            )
            // 行2：收盘价
            nativeCanvas.drawText("收盘", tx, boxTop + 64f, labelPaint)
            valuePaint.color = android.graphics.Color.parseColor("#FF7A1A")
            nativeCanvas.drawText(
                closeVal?.let { "%.2f".format(it) } ?: "--",
                vx2, boxTop + 64f, valuePaint
            )
            // 行3：成本价
            nativeCanvas.drawText("成本", tx, boxTop + 94f, labelPaint)
            valuePaint.color = android.graphics.Color.parseColor("#4A90D9")
            nativeCanvas.drawText(
                costVal?.let { "%.3f".format(it) } ?: "--",
                vx2, boxTop + 94f, valuePaint
            )
            // 行4：股数
            nativeCanvas.drawText("股数", tx, boxTop + 124f, labelPaint)
            valuePaint.color = android.graphics.Color.WHITE
            nativeCanvas.drawText(
                if (p.hasPosition) "${p.shares}" else "--",
                vx2, boxTop + 124f, valuePaint
            )
        }
    }
}
