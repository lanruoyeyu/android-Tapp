package com.tassetbook.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.Trade
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.BuyColor
import com.tassetbook.app.ui.CardDarkBlue
import com.tassetbook.app.ui.ConfirmDialog
import com.tassetbook.app.ui.LossGreen
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.PrimaryBlueLight
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.SellColor
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.ui.fmt2
import com.tassetbook.app.ui.fmt3
import com.tassetbook.app.ui.profitColor
import com.tassetbook.app.util.Dates
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeRecordScreen(
    assetId: String,
    assetName: String,
    onBack: () -> Unit,
    onEditTrade: (assetId: String, assetName: String, tradeId: String, status: String) -> Unit,
    onSupplement: (assetId: String, assetName: String, tradeId: String, status: String) -> Unit
) {
    val repo = AppRepository.get()
    val context = LocalContext.current
    val trades by repo.tradesFlow.collectAsState()
    val assets by repo.assetsFlow.collectAsState()
    val asset = assets.find { it.id == assetId }

    var filter by remember { mutableStateOf("incomplete") }
    var sort by remember { mutableStateOf("completeTime") }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmText by remember { mutableStateOf("") }
    var feeOpen by remember { mutableStateOf(setOf<String>()) }

    val all = trades.filter { it.assetId == assetId }

    // 利润统计（T完成 + 归档）
    fun profitDate(t: Trade): LocalDate? {
        if (t.isArchived) return Dates.parseDate(t.tradeDate)
        return Dates.profitDate(t.tradeDate, t.buyTradeDate, t.sellTradeDate)
    }
    fun tradeProfit(t: Trade): Double = if (t.isArchived) t.profit else (t.sellTotal - t.buyTotal)
    val profitTrades = all.filter { it.isArchived || t_status(it) }
    val now = LocalDate.now()
    var totalProfit = 0.0; var monthProfit = 0.0; var todayProfit = 0.0
    for (t in profitTrades) {
        val p = tradeProfit(t)
        totalProfit += p
        val d = profitDate(t)
        if (d != null) {
            if (d.year == now.year && d.monthValue == now.monthValue) monthProfit += p
            if (d == now) todayProfit += p
        }
    }

    val list = all.filter {
        when (filter) {
            "incomplete" -> !it.isArchived && (it.tradeStatus == "buyNoSell" || it.tradeStatus == "sellNoBuy")
            "completed" -> !it.isArchived && (it.tradeStatus == "buyToSell" || it.tradeStatus == "sellToBuy")
            "archived" -> it.isArchived
            else -> !it.isArchived
        }
    }.let { l ->
        if (filter == "incomplete") {
            l.sortedByDescending { (it_buyPrice(it)).takeIf { v -> v > 0 } ?: it_sellPrice(it) }
        } else if (sort == "buyPrice") {
            l.sortedByDescending { it.buyPrice.takeIf { v -> v > 0 } ?: it.sellPrice }
        } else {
            l.sortedByDescending { profitDate(it) ?: LocalDate.MIN }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(assetName, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
                modifier = Modifier.height(44.dp),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                actions = {
                    if (all.isNotEmpty()) {
                        TextButton(onClick = {
                            confirmText = "确定要清空该资产的所有交易记录吗？"
                            confirm = {
                                repo.clearTradesForAsset(assetId)
                                Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("清空", color = ProfitRed) }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(BgGray)) {
            // 概览
            Card(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ProfitItem("总利润", totalProfit)
                        ProfitItem("本月利润", monthProfit)
                        ProfitItem("今日利润", todayProfit)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HoldingItem("持仓股数", "${asset?.shares ?: 0}股")
                        HoldingItem("总持仓成本", "%.2f".format(asset?.totalCost ?: 0.0))
                        HoldingItem("成本价", fmt3(asset?.costPerShare ?: 0.0))
                    }
                }
            }

            // 筛选
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("incomplete" to "未T完", "completed" to "T完成", "archived" to "归档", "all" to "全部").forEach { (v, label) ->
                    FilterChip(
                        selected = filter == v,
                        onClick = { filter = v },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (filter == "completed" || filter == "archived") {
                    listOf("completeTime" to "完成时间", "buyPrice" to "买入价").forEach { (v, label) ->
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (sort == v) PrimaryBlue else TextHint,
                            fontWeight = if (sort == v) FontWeight.Medium else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { sort = v }.padding(4.dp)
                        )
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
                items(list, key = { it.id }) { t ->
                    TradeCard(
                        t = t,
                        feeOpen = feeOpen.contains(t.id),
                        onToggleFee = {
                            feeOpen = if (feeOpen.contains(t.id)) feeOpen - t.id else feeOpen + t.id
                        },
                        onEdit = { onEditTrade(t.assetId, t.assetName, t.id, t.tradeStatus) },
                        onSupplementSell = { onSupplement(t.assetId, t.assetName, t.id, "buyNoSell") },
                        onSupplementBuy = { onSupplement(t.assetId, t.assetName, t.id, "sellNoBuy") },
                        onArchive = {
                            confirmText = "归档后将只保留盈亏数据用于统计，详细交易信息将被移除。确定归档吗？"
                            confirm = {
                                repo.archiveTrade(t.id)
                                Toast.makeText(context, "已归档", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            confirmText = "确定要永久删除这条交易记录吗？删除后无法恢复！"
                            confirm = {
                                repo.deleteTrade(t.id)
                                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                if (list.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无交易记录", color = TextSecondary)
                            Text("快去做T赚取利润吧~", color = TextHint, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { action ->
        ConfirmDialog(
            title = "请确认",
            content = confirmText,
            onConfirm = { action(); confirm = null },
            onDismiss = { confirm = null }
        )
    }
}

private fun t_status(t: Trade) = t.tradeStatus == "buyToSell" || t.tradeStatus == "sellToBuy"
private fun it_buyPrice(t: Trade) = if (t.buyPrice > 0) t.buyPrice else t.price
private fun it_sellPrice(t: Trade) = if (t.sellPrice > 0) t.sellPrice else t.price

@Composable
private fun ProfitItem(label: String, value: Double) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
        Text(
            (if (value >= 0) "+" else "") + fmt2(value),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun HoldingItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun TradeCard(
    t: Trade,
    feeOpen: Boolean,
    onToggleFee: () -> Unit,
    onEdit: () -> Unit,
    onSupplementSell: () -> Unit,
    onSupplementBuy: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkBlue),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(1.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (badge, badgeColor) = when {
                    t.isArchived -> "已归档" to TextSecondary
                    t.tradeStatus == "buyNoSell" -> "正T买入（未T完）" to BuyColor
                    t.tradeStatus == "sellNoBuy" -> "反T卖出（未T完）" to SellColor
                    else -> "T完成" to PrimaryBlueLight
                }
                Text(
                    badge,
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                val date = when {
                    t.isArchived -> t.tradeDate
                    t.tradeStatus == "buyToSell" || t.tradeStatus == "sellToBuy" ->
                        (Dates.profitDate(t.tradeDate, t.buyTradeDate, t.sellTradeDate)?.let { Dates.formatCn(it) }) ?: t.tradeDate
                    else -> t.tradeDate
                }
                Text(date, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(2.dp))

            when {
                t.tradeStatus == "buyNoSell" && !t.isArchived -> {
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("买入价", "${fmt3(t.buyPrice)}元", Modifier.weight(1f))
                        InfoItem("买入股数", "${t.buyShares}股", Modifier.weight(1f))
                        InfoItem("买入金额", "${fmt2(t.buyTotal)}元", Modifier.weight(1.4f))
                    }
                    CardActions(feeOpen, onToggleFee, onEdit, onDelete,
                        middleLabel = "补卖", middleColor = SellColor, onMiddle = onSupplementSell)
                }
                t.tradeStatus == "sellNoBuy" && !t.isArchived -> {
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("卖出价", "${fmt3(t.sellPrice)}元", Modifier.weight(1f))
                        InfoItem("卖出股数", "${t.sellShares}股", Modifier.weight(1f))
                        InfoItem("卖出净额", "${fmt2(t.sellTotal)}元", Modifier.weight(1.4f))
                    }
                    CardActions(feeOpen, onToggleFee, onEdit, onDelete,
                        middleLabel = "补买", middleColor = BuyColor, onMiddle = onSupplementBuy)
                }
                t.isArchived -> {
                    // 归档仅保留金额与利润，同样用未T完的三列信息行展示
                    val p = t.profit
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("买入金额", "${fmt2(t.buyTotal)}元", Modifier.weight(1f))
                        InfoItem("卖出净额", "${fmt2(t.sellTotal)}元", Modifier.weight(1f))
                        InfoItem(
                            "利润", "${if (p >= 0) "+" else ""}${fmt2(p)}元",
                            Modifier.weight(1f), valueColor = profitColor(p)
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                        SmallActionButton("删除", ProfitRed, onDelete)
                    }
                }
                else -> {
                    // T完成：与未T完一致的三列信息行，买入、卖出各一行
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("买入价", "${fmt3(t.buyPrice)}元", Modifier.weight(1f))
                        InfoItem("买入股数", "${t.buyShares}股", Modifier.weight(1f))
                        InfoItem("买入金额", "${fmt2(t.buyTotal)}元", Modifier.weight(1.4f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("卖出价", "${fmt3(t.sellPrice)}元", Modifier.weight(1f))
                        InfoItem("卖出股数", "${t.sellShares}股", Modifier.weight(1f))
                        InfoItem("卖出净额", "${fmt2(t.sellTotal)}元", Modifier.weight(1.4f))
                    }
                    Spacer(Modifier.height(2.dp))
                    val p = t.sellTotal - t.buyTotal
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Text("单次利润：", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("${if (p >= 0) "+" else ""}${fmt2(p)}元", fontSize = 13.sp, color = profitColor(p), fontWeight = FontWeight.Bold)
                    }
                    CardActions(feeOpen, onToggleFee, onEdit, onDelete,
                        middleLabel = "归档", middleColor = TextSecondary, onMiddle = onArchive)
                }
            }

            if (feeOpen) {
                Spacer(Modifier.height(1.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(1.dp)) {
                    if (t.sellShares > 0 && (t.tradeStatus == "buyToSell" || t.tradeStatus == "sellToBuy") && !t.isArchived) {
                            Row(Modifier.fillMaxWidth()) {
                                FeeCell("买入佣金", fmt2(t.commission), Modifier.weight(1f))
                                FeeCell("买入过户费", fmt2(t.transferFee), Modifier.weight(1f))
                                FeeCell("买入手续费", fmt2(t.buyTotalFee), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                FeeCell("卖出佣金", fmt2(t.commission), Modifier.weight(1f))
                                FeeCell("卖出过户费", fmt2(t.transferFee), Modifier.weight(1f))
                                FeeCell("卖出印花税", fmt2(t.stampDuty), Modifier.weight(1f))
                                FeeCell("卖出手续费", fmt2(t.sellTotalFee), Modifier.weight(1f))
                            }
                        }
                        if (t.tradeStatus == "buyNoSell" && !t.isArchived) {
                            // 两行布局：上行标签（佣金/过户费/总计），下行金额
                            Row(Modifier.fillMaxWidth()) {
                                FeeCell("佣金", fmt2(t.commission), Modifier.weight(1f))
                                FeeCell("过户费", fmt2(t.transferFee), Modifier.weight(1f))
                                FeeCell("总计", fmt2(t.buyTotalFee), Modifier.weight(1f))
                            }
                        }
                        if (t.tradeStatus == "sellNoBuy" && !t.isArchived) {
                            Row(Modifier.fillMaxWidth()) {
                                FeeCell("佣金", fmt2(t.commission), Modifier.weight(1f))
                                FeeCell("过户费", fmt2(t.transferFee), Modifier.weight(1f))
                                FeeCell("印花税", fmt2(t.stampDuty), Modifier.weight(1f))
                                FeeCell("总计", fmt2(t.sellTotalFee), Modifier.weight(1f))
                            }
                        }
                        if (t.remark.isNotBlank()) {
                            Text("备注：${t.remark}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** 并排信息列：标签与数值同一行（买入价 6.280元） */
@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: Color.White,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** 手续费单元格：上行标签、下行金额（元） */
@Composable
private fun FeeCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text("${value}元", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun CardActions(
    feeOpen: Boolean,
    onToggleFee: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    middleLabel: String,
    middleColor: Color,
    onMiddle: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallActionButton(if (feeOpen) "收起费用" else "手续费", TextSecondary, onToggleFee, Modifier.weight(1f))
        SmallActionButton(middleLabel, middleColor, onMiddle, Modifier.weight(1f))
        SmallActionButton("编辑", PrimaryBlue, onEdit, Modifier.weight(1f))
        SmallActionButton("删除", ProfitRed, onDelete, Modifier.weight(1f))
    }
}

@Composable
private fun SmallActionButton(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(Modifier.height(20.dp)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = color
        )
    ) {
        Text(
            text, fontSize = 12.sp, color = color, maxLines = 1, softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                    androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                    androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                )
            )
        )
    }
}
