package com.tassetbook.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.Trade
import com.tassetbook.app.domain.Fees
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.BuyColor
import com.tassetbook.app.ui.CompactTextField
import com.tassetbook.app.ui.DateField
import com.tassetbook.app.ui.KeyValueRow
import com.tassetbook.app.ui.LossGreen
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.SellColor
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.ui.fmt2
import com.tassetbook.app.util.Dates
import com.tassetbook.app.util.TradingCalendar
import androidx.compose.foundation.text.KeyboardOptions
import java.time.LocalDate

/**
 * 交易操作页，7 种形态对齐 trade-operation.js：
 * 1. 资产页-正T买入   2. 资产页-反T卖出
 * 3. 记录页-编辑买入  4. 记录页-编辑卖出  5. 记录页-编辑T完成
 * 6. 买入未卖-补充卖出 7. 卖出未补-补充买入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeOperationScreen(
    assetId: String,
    assetName: String,
    tradeStatusArg: String,
    tradeId: String?,
    source: String,
    isEdit: Boolean,
    onBack: () -> Unit
) {
    val repo = AppRepository.get()
    val context = LocalContext.current
    val settings = repo.settings

    val related: Trade? = if (source == "tradeRecord" && tradeId != null)
        repo.trades.find { it.id == tradeId } else null

    val title = when {
        source == "tradeRecord" && isEdit && tradeStatusArg == "buyNoSell" -> "编辑买入操作"
        source == "tradeRecord" && isEdit && tradeStatusArg == "sellNoBuy" -> "编辑卖出操作"
        source == "tradeRecord" && isEdit -> "编辑T完成记录"
        source == "tradeRecord" && related?.tradeStatus == "sellNoBuy" -> "补充买入操作"
        source == "tradeRecord" && related?.tradeStatus == "buyNoSell" -> "补充卖出操作"
        tradeStatusArg == "sellNoBuy" -> "反T卖出"
        else -> "正T买入"
    }

    // 形态判定
    val isNewSell = tradeStatusArg == "sellNoBuy" && source == "asset"
    val isNewBuy = tradeStatusArg == "buyNoSell" && source == "asset"
    val isSupplementSell = related?.tradeStatus == "buyNoSell" && source == "tradeRecord" && !isEdit
    val isSupplementBuy = related?.tradeStatus == "sellNoBuy" && source == "tradeRecord" && !isEdit
    val isEditBuy = related?.tradeStatus == "buyNoSell" && isEdit
    val isEditSell = related?.tradeStatus == "sellNoBuy" && isEdit
    val isEditDone = (related?.tradeStatus == "buyToSell" || related?.tradeStatus == "sellToBuy") && isEdit

    val showBuyForm = isNewBuy || isSupplementBuy || isEditBuy || isEditDone
    val showSellForm = isNewSell || isSupplementSell || isEditSell || isEditDone
    val showOrigBuy = isSupplementSell
    val showOrigSell = isSupplementBuy

    // 表单状态
    var buyPrice by remember {
        mutableStateOf(
            when {
                isEditBuy || isEditDone -> (related!!.buyPrice).toString().trimEnd('0').trimEnd('.')
                else -> ""
            }
        )
    }
    var buyShares by remember {
        mutableStateOf(
            when {
                isEditBuy || isEditDone -> related!!.buyShares.toString()
                isSupplementBuy -> (related!!.sellShares.takeIf { it > 0 } ?: related.shares).toString()
                else -> ""
            }
        )
    }
    var sellPrice by remember {
        mutableStateOf(
            when {
                isEditSell || isEditDone -> (related!!.sellPrice).toString().trimEnd('0').trimEnd('.')
                else -> ""
            }
        )
    }
    var sellShares by remember {
        mutableStateOf(
            when {
                isEditSell -> related!!.sellShares.toString()
                isEditDone -> related.sellShares.toString()
                isSupplementSell -> (related!!.buyShares.takeIf { it > 0 } ?: related.shares).toString()
                else -> ""
            }
        )
    }
    var remark by remember { mutableStateOf(if (isEdit || isEditDone) related?.remark ?: "" else "") }
    var dateIso by remember {
        mutableStateOf(
            when {
                related != null && isEdit -> Dates.localeToIso(related.tradeDate.ifBlank { related.buyTradeDate.ifBlank { related.sellTradeDate } })
                else -> TradingCalendar.latestTradingDay(LocalDate.now()).toString()
            }
        )
    }

    // 实时费用试算
    val bp = buyPrice.toDoubleOrNull() ?: 0.0
    val bs = buyShares.toIntOrNull() ?: 0
    val sp = sellPrice.toDoubleOrNull() ?: 0.0
    val ss = sellShares.toIntOrNull() ?: 0
    val buyFees = if (bp > 0 && bs > 0) Fees.calc(bp, bs, false, settings) else null
    val sellFees = if (sp > 0 && ss > 0) Fees.calc(sp, ss, true, settings) else null

    val canSubmit = when {
        isNewSell || isEditSell -> sp > 0 && ss > 0
        isSupplementSell -> sp > 0 && ss > 0
        isNewBuy || isEditBuy -> bp > 0 && bs > 0
        isSupplementBuy -> bp > 0 && bs > 0
        isEditDone -> bp > 0 && bs > 0 && sp > 0 && ss > 0
        else -> false
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
                modifier = Modifier.height(44.dp),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgGray)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(assetName, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))

            // 原买入信息（补充卖出时）
            if (showOrigBuy && related != null) {
                InfoCard("原买入信息") {
                    KeyValueRow("买入价", "%.3f".format(related.buyPrice.takeIf { it > 0 } ?: related.price))
                    KeyValueRow("买入股数", "${related.buyShares.takeIf { it > 0 } ?: related.shares} 股")
                    KeyValueRow("买入总额", "%.2f 元".format(related.buyTotal))
                }
            }
            // 原卖出信息（补充买入时）
            if (showOrigSell && related != null) {
                InfoCard("原卖出信息") {
                    KeyValueRow("卖出价", "%.3f".format(related.sellPrice.takeIf { it > 0 } ?: related.price))
                    KeyValueRow("卖出股数", "${related.sellShares.takeIf { it > 0 } ?: related.shares} 股")
                    KeyValueRow("卖出净额", "%.2f 元".format(related.sellTotal))
                }
            }

            if (showBuyForm) {
                InfoCard(if (isNewBuy) "正T买入" else "买入信息", accent = BuyColor) {
                    NumField("买入价(元)", buyPrice) { buyPrice = it }
                    NumField("买入股数", buyShares, integer = true) { buyShares = it }
                    buyFees?.let { f ->
                        Spacer(Modifier.height(6.dp))
                        KeyValueRow("成交金额", "${fmt2(f.grossAmount)} 元")
                        KeyValueRow("佣金", "${fmt2(f.commission)} 元")
                        KeyValueRow("过户费", "${fmt2(f.transferFee)} 元")
                        KeyValueRow("买入总额", "${fmt2(f.grossAmount + f.commission + f.transferFee)} 元", valueColor = BuyColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showSellForm) {
                InfoCard(if (isNewSell) "反T卖出" else "卖出信息", accent = SellColor) {
                    NumField("卖出价(元)", sellPrice) { sellPrice = it }
                    NumField("卖出股数", sellShares, integer = true) { sellShares = it }
                    sellFees?.let { f ->
                        Spacer(Modifier.height(6.dp))
                        KeyValueRow("成交金额", "${fmt2(f.grossAmount)} 元")
                        KeyValueRow("佣金", "${fmt2(f.commission)} 元")
                        KeyValueRow("过户费", "${fmt2(f.transferFee)} 元")
                        KeyValueRow("印花税", "${fmt2(f.stampDuty)} 元")
                        val net = f.grossAmount - f.totalFee
                        KeyValueRow("卖出净额", "${fmt2(net)} 元", valueColor = SellColor, fontWeight = FontWeight.Bold)
                        if (isSupplementSell || isSupplementBuy || isEditDone) {
                            val buyTotal = buyFees?.let { it.grossAmount + it.commission + it.transferFee }
                                ?: related?.buyTotal ?: 0.0
                            val profit = net - buyTotal
                            KeyValueRow(
                                "预估盈亏",
                                (if (profit >= 0) "+" else "") + "${fmt2(profit)} 元",
                                valueColor = if (profit >= 0) ProfitRed else LossGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            InfoCard("其他信息") {
                DateField("交易日期", dateIso, onDateChange = { dateIso = it })
                Spacer(Modifier.height(4.dp))
                // Text("备注", fontSize = 13.sp, color = TextSecondary)
                // CompactTextField(
                //     value = remark,
                //     onValueChange = { remark = it },
                //     modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                //     placeholder = "选填"
                // )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val dateLocale = Dates.isoToLocale(dateIso)
                    val result = when {
                        isNewBuy -> repo.addTBuy(assetId, bp, bs, remark, dateLocale)
                        isNewSell -> repo.addTSell(assetId, sp, ss, remark, dateLocale)
                        isSupplementSell -> repo.supplementSell(related!!.id, sp, ss, dateLocale, remark)
                        isSupplementBuy -> repo.supplementBuy(related!!.id, bp, bs, dateLocale, remark)
                        isEditBuy -> repo.editBuyTrade(related!!.id, bp, bs, dateLocale, remark)
                        isEditSell -> repo.editSellTrade(related!!.id, sp, ss, dateLocale, remark)
                        isEditDone -> repo.editCompletedTrade(related!!.id, bp, bs, sp, ss, dateLocale, remark)
                        else -> null
                    }
                    if (result == null) {
                        toast("操作失败"); return@Button
                    }
                    if (result.success) {
                        toast(
                            when {
                                isNewBuy -> "正T买入成功"
                                isNewSell -> "反T卖出成功"
                                isSupplementSell -> "补充卖出成功"
                                isSupplementBuy -> "补充买入成功"
                                isEditDone -> "T完成记录更新成功"
                                isEditBuy -> "正T买入记录更新成功"
                                isEditSell -> "反T卖出记录更新成功"
                                else -> "操作成功"
                            }
                        )
                        onBack()
                    } else {
                        toast(result.message)
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        showBuyForm && !showSellForm -> BuyColor
                        showSellForm && !showBuyForm -> SellColor
                        else -> PrimaryBlue
                    }
                )
            ) { Text(if (isEdit || isEditDone) "保存修改" else "确认提交", fontSize = 16.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, accent: Color = PrimaryBlue, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = accent, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
private fun NumField(label: String, value: String, integer: Boolean = false, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        CompactTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || (!integer && it == '.') }) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal
        )
    }
}
