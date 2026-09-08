package com.tassetbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.zIndex
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.Asset
import com.tassetbook.app.data.Trade
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.BuyColor
import com.tassetbook.app.ui.CardLightBlue
import com.tassetbook.app.ui.CompactTextField
import com.tassetbook.app.ui.EstimateColor
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.SellColor
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.ui.fmt3
import com.tassetbook.app.util.Money

private enum class StockFilter(val label: String) {
    HOLDING("持仓"),       // 已显示且持仓股数不为 0（默认）
    ALL("全部"),           // 所有股票
    HIDDEN("已隐藏"),      // 已隐藏的股票
    CLEARED("已清空")      // 持仓股数为 0 的股票
}

@Composable
private fun FilterTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PrimaryBlue else androidx.compose.ui.graphics.Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary
        )
    }
}

@Composable
fun AssetsScreen(
    onAddAsset: () -> Unit,
    onEditAsset: (String) -> Unit,
    onTradeOp: (assetId: String, assetName: String, status: String) -> Unit,
    onTradeRecord: (assetId: String, assetName: String) -> Unit,
    onEstimate: (assetId: String, assetName: String) -> Unit,
    onManageStocks: () -> Unit
) {
    val repo = AppRepository.get()
    val assets by repo.assetsFlow.collectAsState()
    val trades by repo.tradesFlow.collectAsState()
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StockFilter.HOLDING) }
    val expanded = remember { mutableStateOf(setOf<String>()) }

    val keyword = search.trim()
    val filtered = assets.filter {
        val matchTab = when (filter) {
            StockFilter.HOLDING -> !it.hidden && it.shares != 0
            StockFilter.ALL -> true
            StockFilter.HIDDEN -> it.hidden
            StockFilter.CLEARED -> it.shares == 0
        }
        matchTab && it.name.contains(keyword, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().background(BgGray)) {
        // 顶部
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .clickable(onClick = onManageStocks)
                    .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("股票管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            CompactTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                placeholder = "搜索股票名称"
            )
            TextButton(onClick = onAddAsset) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = PrimaryBlue)
                Text("添加", color = PrimaryBlue)
            }
        }
        // 筛选条
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StockFilter.entries.forEach { f ->
                FilterTab(
                    text = f.label,
                    selected = filter == f,
                    onClick = { filter = f },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("暂无股票", color = TextSecondary)
                    Text("点击\"添加\"开始记录", color = TextHint, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddAsset) { Text("添加股票") }
                }
            }
        } else {
            // 拖动排序
            val listState = rememberLazyListState()
            val haptics = LocalHapticFeedback.current
            val displayItems = remember { mutableStateListOf<Asset>() }
            LaunchedEffect(filtered) {
                val newIds = filtered.map { it.id }
                if (displayItems.map { it.id } != newIds) {
                    displayItems.clear()
                    displayItems.addAll(filtered)
                }
            }
            var drag by remember { mutableStateOf<CardDragState?>(null) }
            val latestFiltered = rememberUpdatedState(filtered)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayItems, key = { it.id }) { asset ->
                    val index = displayItems.indexOf(asset)
                    val dragging = drag?.index == index
                    val translationY = if (dragging) {
                        val info0 = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                        val d0 = drag
                        if (info0 != null && d0 != null) d0.fingerY - d0.pressOffsetY - info0.offset else 0f
                    } else 0f
                    Box(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .then(
                                // 手势检测器挂在 item 上而非 LazyColumn：item 是内部 scrollable
                                // 的后代，Main 传递阶段子优先，长按成立后 item 先消费 MOVE，列表不抢手势
                                if (displayItems.size > 1) Modifier.pointerInput(asset.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val startIndex = displayItems.indexOf(asset)
                                            val info = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == startIndex }
                                            if (info != null) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                // offset.y 相对当前 item；fingerY 统一换算为相对列表视口
                                                drag = CardDragState(startIndex, offset.y, info.offset + offset.y)
                                            }
                                        },
                                        onDragEnd = {
                                            val d = drag
                                            if (d != null &&
                                                displayItems.map { it.id } != latestFiltered.value.map { it.id }
                                            ) {
                                                repo.reorderAssets(displayItems.map { it.id })
                                            }
                                            drag = null
                                        },
                                        onDragCancel = { drag = null },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            val d = drag ?: return@detectDragGesturesAfterLongPress
                                            val fingerY = d.fingerY + delta.y
                                            var newIndex = d.index
                                            val target = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { vi -> fingerY < vi.offset + vi.size * 0.5f }
                                                ?.index ?: displayItems.lastIndex
                                            if (target != d.index) {
                                                val to = target.coerceIn(0, displayItems.lastIndex)
                                                val moving = displayItems.removeAt(d.index)
                                                displayItems.add(to, moving)
                                                newIndex = to
                                            }
                                            drag = d.copy(index = newIndex, fingerY = fingerY)
                                        }
                                    )
                                } else Modifier
                            )
                            .graphicsLayer {
                                this.translationY = translationY
                                if (dragging) {
                                    alpha = 0.95f
                                    scaleX = 1.015f
                                    scaleY = 1.015f
                                }
                            }
                    ) {
                        AssetCard(
                            asset = asset,
                            trades = trades,
                            expanded = expanded.value.contains(asset.id),
                            onToggleExpand = {
                                expanded.value = if (expanded.value.contains(asset.id))
                                    expanded.value - asset.id else expanded.value + asset.id
                            },
                            onEdit = { onEditAsset(asset.id) },
                            onRecord = { onTradeRecord(asset.id, asset.name) },
                            onBuy = { onTradeOp(asset.id, asset.name, "buyNoSell") },
                            onSell = { onTradeOp(asset.id, asset.name, "sellNoBuy") },
                            onEstimate = { onEstimate(asset.id, asset.name) }
                        )
                    }
                }
            }
        }
    }
}

/** 拖动过程状态：当前索引、按下点在卡片内的 Y 偏移、手指当前 Y（相对列表视口） */
private data class CardDragState(val index: Int, val pressOffsetY: Float, val fingerY: Float)

private data class PriceEntry(val price: String, val shares: Int, val type: String)

@Composable
private fun AssetCard(
    asset: Asset,
    trades: List<Trade>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onRecord: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit,
    onEstimate: () -> Unit
) {
    val openTrades = trades.filter { it.assetId == asset.id && !it.isArchived }
    val buyEntries = openTrades.filter { it.tradeStatus == "buyNoSell" }
        .map { PriceEntry(fmt3(it.buyPrice.takeIf { p -> p > 0 } ?: it.price), it.buyShares.takeIf { s -> s > 0 } ?: it.shares, "正T买入") }
        .sortedBy { it.price }
    val sellEntries = openTrades.filter { it.tradeStatus == "sellNoBuy" }
        .map { PriceEntry(fmt3(it.sellPrice.takeIf { p -> p > 0 } ?: it.price), it.sellShares.takeIf { s -> s > 0 } ?: it.shares, "反T卖出") }
        .sortedBy { it.price }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = CardLightBlue),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(asset.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${asset.market ?: ""} ${asset.exchange ?: ""}".trim(), fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onRecord,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProfitRed)
                ) {
                    Text("交易记录", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White)
                }
                Button(
                    onClick = onEdit,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("编辑", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            // 持仓行
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                PositionItem("总持仓成本", "%.2f元".format(Money.round3(asset.costPerShare) * asset.shares), Modifier.weight(1f))
                PositionItem("股数", "${asset.shares}股", Modifier.weight(1f))
                PositionItem("成本价", "${fmt3(asset.costPerShare)}元", Modifier.weight(1f))
            }
            // 操作按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("正T买入", BuyColor, Modifier.weight(1f), onBuy)
                ActionButton("反T卖出", SellColor, Modifier.weight(1f), onSell)
                ActionButton("预估交易", EstimateColor, Modifier.weight(1f), onEstimate)
                ActionButton(if (expanded) "收起↑" else "展开↓", EstimateColor, Modifier.weight(1f), onToggleExpand)
            }
            // 展开价格梯
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (buyEntries.isEmpty() && sellEntries.isEmpty()) {
                    Text("暂无未完成交易", fontSize = 12.sp, color = TextHint)
                } else {
                    if (buyEntries.isNotEmpty()) {
                        PriceLadderRow(buyEntries, BuyColor, "I")
                    }
                    if (sellEntries.isNotEmpty()) {
                        PriceLadderRow(sellEntries, SellColor, "O")
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp),
            color = valueColor ?: androidx.compose.ui.graphics.Color.Unspecified
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(Modifier.height(20.dp)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = androidx.compose.ui.graphics.Color.White,
            contentColor = color
        )
    ) {
        Text(
            text, fontSize = 13.sp, color = color, maxLines = 1, softWrap = false,
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

@Composable
private fun PriceLadderRow(entries: List<PriceEntry>, color: androidx.compose.ui.graphics.Color, marker: String) {
    // 与小程序一致：每行均分显示 4 个交易信息，自动换行
    entries.chunked(4).forEach { rowEntries ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rowEntries.forEach { e ->
                Box(
                    Modifier
                        .weight(1f)
                        .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${e.price}[${e.shares}]$marker", fontSize = 11.sp, color = color, maxLines = 1)
                }
            }
            // 最后一行不足 4 个时补空位，保证格子宽度一致
            repeat(4 - rowEntries.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
