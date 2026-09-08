package com.tassetbook.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.Trade
import com.tassetbook.app.domain.Backup
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.ConfirmDialog
import com.tassetbook.app.ui.DateField
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.util.Dates
import java.time.LocalDate

/**
 * 数据备份与导入页。
 * 导出使用 SAF CreateDocument；导入使用 SAF OpenDocument。
 * CSV 导出带 UTF-8 BOM，格式与微信小程序完全一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportConfigScreen(onBack: () -> Unit) {
    val repo = AppRepository.get()
    val context = LocalContext.current
    val assets by repo.assetsFlow.collectAsState()
    val trades by repo.tradesFlow.collectAsState()

    var format by remember { mutableStateOf("csv") }
    var scope by remember { mutableStateOf("all") }
    var pendingImport by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    // 时间范围（对齐 export-config.js：仅过滤交易，按 tradeDate）
    var timeRange by remember { mutableStateOf("all") }
    var startIso by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }
    var endIso by remember { mutableStateOf(LocalDate.now().toString()) }
    var showPreview by remember { mutableStateOf(false) }

    fun applyRange(v: String) {
        timeRange = v
        val today = LocalDate.now()
        endIso = today.toString()
        startIso = when (v) {
            "today" -> today.toString()
            "week" -> today.minusDays(7).toString()
            "month" -> today.withDayOfMonth(1).toString()
            "custom" -> today.withDayOfMonth(1).toString()
            else -> ""
        }
        if (v == "all") endIso = ""
    }

    fun filteredTrades(): List<Trade> {
        if (scope == "assets" || timeRange == "all") return repo.trades.toList()
        val start = startIso.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = endIso.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return repo.trades.filter { t ->
            // 与小程序一致：无 tradeDate 的记录保留
            val d = Dates.parseDate(t.tradeDate) ?: return@filter true
            (start == null || !d.isBefore(start)) && (end == null || !d.isAfter(end))
        }
    }

    val defaultName = "tassetbook_export_${LocalDate.now().toString().replace("-", "")}"

    // 导出：创建文档
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = buildExport(repo, format, scope, filteredTrades())
                val withBom = if (format == "csv") "\uFEFF" + content else content
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(withBom.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 导入：打开文档
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                } ?: ""
                val cleaned = if (text.startsWith("\uFEFF")) text.substring(1) else text
                if (!cleaned.contains("===资产数据===") && !cleaned.contains("===交易记录===") &&
                    !cleaned.trimStart().startsWith("{")
                ) {
                    Toast.makeText(context, "文件格式错误：不是有效的备份文件", Toast.LENGTH_LONG).show()
                } else {
                    pendingImport = uri to cleaned
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据备份与导入") },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前数据", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("资产 ${assets.size} 条 · 交易 ${trades.size} 条", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("导出格式", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        FilterChip(
                            selected = format == "csv",
                            onClick = { format = "csv" },
                            label = { Text("CSV（Excel 兼容）") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                        )
                        FilterChip(
                            selected = format == "json",
                            onClick = { format = "json" },
                            label = { Text("JSON（数据备份）") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("导出范围", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf("all" to "全部数据", "assets" to "仅资产", "trades" to "仅交易").forEach { (v, label) ->
                            FilterChip(
                                selected = scope == v,
                                onClick = { scope = v },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPreview = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("预览") }
                        Button(
                            onClick = {
                                val ext = if (format == "csv") "csv" else "json"
                                createLauncher.launch("$defaultName.$ext")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) { Text("导出到文件") }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("时间范围（仅筛选交易记录）", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        listOf(
                            "all" to "全部时间", "today" to "今日", "week" to "近7天",
                            "month" to "本月", "custom" to "自定义"
                        ).forEach { (v, label) ->
                            FilterChip(
                                selected = timeRange == v,
                                onClick = { applyRange(v) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White)
                            )
                        }
                    }
                    if (timeRange == "custom") {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            DateField("开始日期", startIso, onDateChange = { startIso = it }, modifier = Modifier.weight(1f))
                            Text(" 至 ", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                            DateField("结束日期", endIso, onDateChange = { endIso = it }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("导入备份", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "支持从微信小程序导出的 CSV/JSON 备份文件导入，导入将覆盖当前数据。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { openLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel", "text/csv", "application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("选择备份文件导入") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("提示：CSV 与微信小程序备份格式完全一致，可双向互通。", fontSize = 12.sp, color = TextHint)
        }
    }

    if (showPreview) {
        val content = buildExport(repo, format, scope, filteredTrades())
        val lines = content.split("\n")
        val preview = lines.take(20).joinToString("\n") +
            if (lines.size > 20) "\n...（共 ${lines.size} 行，已省略其余）" else ""
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("导出预览（前 20 行）", fontSize = 16.sp) },
            text = {
                Text(
                    preview.ifBlank { "（无数据）" },
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("关闭", color = PrimaryBlue) } }
        )
    }

    pendingImport?.let { (_, content) ->
        val isJson = content.trimStart().startsWith("{")
        var info = ""
        var (aCount, tCount) = 0 to 0
        try {
            if (isJson) {
                val gson = com.google.gson.Gson()
                val obj = gson.fromJson(content, com.google.gson.JsonObject::class.java)
                val aArr = obj?.getAsJsonArray("assets")
                val tArr = obj?.getAsJsonArray("trades")
                aCount = aArr?.size() ?: 0
                tCount = tArr?.size() ?: 0
            } else {
                val (ac, tc) = Backup.sectionCounts(content)
                aCount = ac; tCount = tc
            }
            info = "即将导入：资产 $aCount 条，交易 $tCount 条。\n\n导入将覆盖当前数据，确定继续吗？"
        } catch (e: Exception) {
            info = "无法解析文件：${e.message}"
        }
        ConfirmDialog(
            title = "确认导入",
            content = info,
            confirmText = "确认导入",
            onConfirm = {
                try {
                    val (na, nt) = if (isJson) repo.importJson(content) else repo.importCsv(content)
                    Toast.makeText(context, "导入成功：资产 $na 条，交易 $nt 条", Toast.LENGTH_LONG).show()
                } catch (e: Backup.BackupException) {
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
                pendingImport = null
            },
            onDismiss = { pendingImport = null }
        )
    }
}

private fun buildExport(repo: AppRepository, format: String, scope: String, tradesInRange: List<com.tassetbook.app.data.Trade>): String {
    return if (format == "json") {
        // JSON 导出（按范围裁剪）
        val gson = com.google.gson.Gson()
        val assets = if (scope != "trades") repo.assets.toList() else emptyList()
        val trades = if (scope != "assets") tradesInRange else emptyList()
        val payload = mapOf(
            "version" to "2.0.0",
            "exportTime" to com.tassetbook.app.util.Dates.nowString(),
            "exportSettings" to mapOf("format" to "json", "scope" to scope),
            "assets" to assets,
            "trades" to trades,
            "settings" to repo.settings
        )
        gson.toJson(payload)
    } else {
        val assets = if (scope != "trades") repo.assets.toList() else emptyList()
        val trades = if (scope != "assets") tradesInRange else emptyList()
        Backup.jsonToCsv(assets, trades)
    }
}
