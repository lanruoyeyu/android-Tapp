package com.tassetbook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.domain.Estimate
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.CompactTextField
import com.tassetbook.app.ui.LossGreen
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.ui.profitColor
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateTradeScreen(assetId: String, assetName: String, onBack: () -> Unit) {
    val repo = AppRepository.get()
    val asset = repo.getAsset(assetId)

    var estPrice by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf(asset?.costPerShare?.let { "%.3f".format(it).trimEnd('0').trimEnd('.') } ?: "") }
    var shares by remember { mutableStateOf(asset?.shares?.takeIf { it > 0 }?.toString() ?: "") }
    var tradeShares by remember { mutableStateOf("") }
    var ratio by remember { mutableStateOf("1.017") }
    var rows by remember { mutableStateOf<List<Estimate.Row>>(emptyList()) }

    val costNum = costPrice.toDoubleOrNull() ?: 0.0
    val sharesNum = shares.toIntOrNull() ?: 0
    val totalCost = if (costNum > 0 && sharesNum > 0) costNum * sharesNum else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预估交易 - $assetName") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(BgGray)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    EstField("预估股价(元)", estPrice, onChange = { estPrice = it })
                    EstField("成本价(元)", costPrice, onChange = { costPrice = it })
                    EstField("总股数", shares, integer = true, onChange = { shares = it })
                    Row {
                        EstField("持有总成本(元)", if (totalCost > 0) "%.2f".format(totalCost) else "", onChange = {}, modifier = Modifier.weight(1f), enabled = false)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        EstField("单次卖出股数", tradeShares, integer = true, modifier = Modifier.weight(1f), onChange = { tradeShares = it })
                    }
                    EstField("阶梯比例(%)", ratio, onChange = { ratio = it })
                    Button(
                        onClick = {
                            rows = Estimate.calculate(
                                estimatedPrice = estPrice.toDoubleOrNull() ?: 0.0,
                                costPrice = costNum,
                                shares = sharesNum,
                                tradeShares = tradeShares.toIntOrNull() ?: 0,
                                ratio = ratio.toDoubleOrNull() ?: 0.0
                            )
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) { Text("开始计算") }
                }
            }

            if (rows.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("阶梯价格", fontSize = 12.sp, color = TextHint, fontWeight = FontWeight.Medium)
                    Text("本次卖出", fontSize = 12.sp, color = TextHint, fontWeight = FontWeight.Medium)
                    Text("剩余股数", fontSize = 12.sp, color = TextHint, fontWeight = FontWeight.Medium)
                    Text("持仓市值", fontSize = 12.sp, color = TextHint, fontWeight = FontWeight.Medium)
                    Text("累计收入", fontSize = 12.sp, color = TextHint, fontWeight = FontWeight.Medium)
                    Text("盈亏", fontSize = 12.sp, color = TextHint, fontWeight = FontWeight.Medium)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows) { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(r.price, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(r.sellPrice, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(r.remainingShares, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(r.totalPrice, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(r.totalRevenue, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                r.profit,
                                fontSize = 13.sp,
                                color = profitColor(r.profitNum),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("填写参数后点击开始计算", color = TextHint, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EstField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    integer: Boolean = false,
    enabled: Boolean = true
) {
    Column(modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        CompactTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || (!integer && it == '.') }) },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            enabled = enabled,
            keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal
        )
    }
}
