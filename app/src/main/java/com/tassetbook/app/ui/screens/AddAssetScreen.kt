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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.CompactTextField
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssetScreen(assetId: String?, onBack: () -> Unit) {
    val repo = AppRepository.get()
    val context = LocalContext.current
    val existing = assetId?.let { id -> repo.getAsset(id) }
    val isEdit = existing != null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var market by remember { mutableStateOf(existing?.market ?: "A股") }
    var exchange by remember { mutableStateOf(existing?.exchange ?: "沪市") }
    var shares by remember { mutableStateOf(existing?.baseShares?.takeIf { it > 0 }?.toString() ?: "") }
    var unit by remember { mutableStateOf(existing?.baseUnit?.takeIf { it > 0 }?.let { "%.3f".format(it).trimEnd('0').trimEnd('.') } ?: "") }

    val sharesNum = shares.toIntOrNull() ?: 0
    val unitNum = unit.toDoubleOrNull() ?: 0.0
    val totalCost = if (sharesNum > 0 && unitNum > 0) sharesNum * unitNum else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑资产" else "添加资产") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgGray)
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("资产名称", fontSize = 13.sp, color = TextSecondary)
                CompactTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = "请输入资产名称"
                )

                Spacer(Modifier.height(12.dp))
                Text("资产类型", fontSize = 13.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("A股", "港股", "ETF").forEach { m ->
                        FilterChip(
                            selected = market == m,
                            onClick = { market = m },
                            label = { Text(m) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("交易所", fontSize = 13.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("沪市", "深市", "港交所").forEach { e ->
                        FilterChip(
                            selected = exchange == e,
                            onClick = { exchange = e },
                            label = { Text(e) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("持有数量(股)", fontSize = 13.sp, color = TextSecondary)
                CompactTextField(
                    value = shares,
                    onValueChange = { v -> shares = v.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = "非必填",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(12.dp))
                Text("成本价(元)", fontSize = 13.sp, color = TextSecondary)
                CompactTextField(
                    value = unit,
                    onValueChange = { v -> unit = v.filter { it.isDigit() || it == '.' } },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = "非必填",
                    keyboardType = KeyboardType.Decimal
                )

                Spacer(Modifier.height(12.dp))
                Text("持有总成本(元)", fontSize = 13.sp, color = TextSecondary)
                CompactTextField(
                    value = if (totalCost > 0) "%.2f".format(totalCost) else "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = "自动计算",
                    enabled = false
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "请输入资产名称", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        repo.saveAsset(
                            id = assetId,
                            name = name.trim(),
                            market = market,
                            exchange = exchange,
                            baseTotalCost = totalCost,
                            baseShares = sharesNum,
                            baseUnit = unitNum
                        )
                        Toast.makeText(context, if (isEdit) "保存成功" else "添加成功", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text(if (isEdit) "保存修改" else "添加资产") }

                Spacer(Modifier.height(20.dp))
                Text("说明", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Text("1. 持有总成本 = 持有数量 × 成本价（自动计算）\n2. 添加后可以记录做T操作（正T买入、反T卖出）", fontSize = 12.sp, color = TextHint)
            }
        }
    }
}
