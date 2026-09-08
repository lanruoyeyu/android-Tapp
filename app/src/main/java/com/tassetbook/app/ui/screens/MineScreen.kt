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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.Settings
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.CompactTextField
import com.tassetbook.app.ui.ConfirmDialog
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun MineScreen(onExport: () -> Unit) {
    val repo = AppRepository.get()
    val context = LocalContext.current
    val assets by repo.assetsFlow.collectAsState()
    val trades by repo.tradesFlow.collectAsState()
    val settings by repo.settingsFlow.collectAsState()

    var editingRate by remember { mutableStateOf<String?>(null) }
    var rateInput by remember { mutableStateOf("") }
    var showClear1 by remember { mutableStateOf(false) }
    var showClear2 by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val stats = repo.getStatistics()

    Column(
        Modifier
            .fillMaxSize()
            .background(BgGray)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text("我的", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        // 数据统计
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("数据统计", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("资产数量", "${stats.totalAssets}")
                    StatItem("记录总数", "${trades.size}")
                    StatItem("A股/港股/ETF", "${stats.aStock}/${stats.hkStock}/${stats.etf}")
                }
            }
        }

        // 费率设置
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("交易费率设置", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("单位：万分之一（‱）", fontSize = 12.sp, color = TextHint)
                Spacer(Modifier.height(6.dp))
                RateRow("A股佣金", settings.tradeRateA ?: 0.85) { editingRate = "A"; rateInput = (settings.tradeRateA ?: 0.85).toString() }
                RateRow("港股佣金", settings.tradeRateHK ?: 1.00) { editingRate = "HK"; rateInput = (settings.tradeRateHK ?: 1.00).toString() }
                RateRow("ETF佣金", settings.tradeRateETF ?: 0.20) { editingRate = "ETF"; rateInput = (settings.tradeRateETF ?: 0.20).toString() }
                Spacer(Modifier.height(6.dp))
                Text("过户费：${settings.tradeRateTransfer}‱（默认万0.1）", fontSize = 12.sp, color = TextSecondary)
                Text("印花税：${settings.tradeRateStampDuty}‱（仅卖出，默认万5）", fontSize = 12.sp, color = TextSecondary)
            }
        }

        // 功能入口
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column {
                MenuRow("数据备份与导入", "导出/导入 CSV、JSON 备份", onExport)
                Spacer(Modifier.height(0.dp))
                MenuRow("清空所有数据", "删除全部资产与交易记录（不可恢复）", { showClear1 = true }, danger = true)
                MenuRow("关于", "T-account book 版本说明", { showAbout = true })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("T-account book · 专业做T记账工具", fontSize = 12.sp, color = TextHint, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    // 费率编辑
    if (editingRate != null) {
        val title = when (editingRate) {
            "A" -> "A股佣金费率"
            "HK" -> "港股佣金费率"
            else -> "ETF佣金费率"
        }
        AlertDialog(
            onDismissRequest = { editingRate = null },
            title = { Text(title) },
            text = {
                Column {
                    Text("单位：万分之一（‱），如 0.854 表示万0.854", fontSize = 12.sp, color = TextHint)
                    CompactTextField(
                        value = rateInput,
                        onValueChange = { v -> rateInput = v.filter { it.isDigit() || it == '.' } },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = rateInput.toDoubleOrNull() ?: return@TextButton
                    repo.updateSettings { s ->
                        when (editingRate) {
                            "A" -> s.copy(tradeRateA = v)
                            "HK" -> s.copy(tradeRateHK = v)
                            else -> s.copy(tradeRateETF = v)
                        }
                    }
                    editingRate = null
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                }) { Text("保存", color = PrimaryBlue) }
            },
            dismissButton = { TextButton(onClick = { editingRate = null }) { Text("取消") } }
        )
    }

    if (showClear1) {
        ConfirmDialog(
            title = "清空数据",
            content = "确定要清空所有数据吗？此操作不可恢复！",
            confirmText = "确定",
            onConfirm = { showClear1 = false; showClear2 = true },
            onDismiss = { showClear1 = false }
        )
    }
    if (showClear2) {
        ConfirmDialog(
            title = "再次确认",
            content = "真的要清空所有数据吗？",
            confirmText = "确认清空",
            onConfirm = {
                showClear2 = false
                repo.clearAllData()
                Toast.makeText(context, "已清空所有数据", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClear2 = false }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于 T-account book") },
            text = {
                Text(
                    "T-account book - 专业做T记账工具\n\n版本：1.0.0（Android 版）\n\n功能特点：\n- 记录做T操作（正T买入、反T卖出）\n- 自动计算做T利润\n- 支持 A股/港股/ETF\n- 数据本地存储\n- CSV/JSON 备份互通\n\n使用说明：\n1. 先添加资产（输入持仓成本和股数）\n2. 进行做T操作记录\n3. 查看利润统计",
                    fontSize = 13.sp
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("知道了", color = PrimaryBlue) } }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RateRow(label: String, value: Double, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text("%.3f‱（每万元成交额 %.3f 元）".format(value, value), fontSize = 13.sp, color = TextSecondary)
        TextButton(onClick = onEdit) { Text("编辑", color = PrimaryBlue) }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (danger) ProfitRed else Color.Unspecified)
            Text(subtitle, fontSize = 12.sp, color = TextHint, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextHint)
    }
}
