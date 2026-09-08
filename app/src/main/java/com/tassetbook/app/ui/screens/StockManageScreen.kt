package com.tassetbook.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.data.AppRepository
import com.tassetbook.app.data.Asset
import com.tassetbook.app.ui.BgGray
import com.tassetbook.app.ui.CardWhite
import com.tassetbook.app.ui.ConfirmDialog
import com.tassetbook.app.ui.PrimaryBlue
import com.tassetbook.app.ui.ProfitRed
import com.tassetbook.app.ui.TextHint
import com.tassetbook.app.ui.TextSecondary
import com.tassetbook.app.ui.fmt3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockManageScreen(onBack: () -> Unit) {
    val repo = AppRepository.get()
    val context = LocalContext.current
    val assets by repo.assetsFlow.collectAsState()
    var pendingDelete by remember { mutableStateOf<Asset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("股票管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (assets.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).background(BgGray),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无股票", color = TextSecondary)
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(BgGray)
            ) {
                items(assets, key = { it.id }) { asset ->
                    StockManageRow(
                        asset = asset,
                        onToggleHidden = {
                            repo.setAssetHidden(asset.id, !asset.hidden)
                            Toast.makeText(
                                context,
                                if (asset.hidden) "已显示" else "已隐藏",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onDelete = { pendingDelete = asset }
                    )
                }
            }
        }
    }

    pendingDelete?.let { asset ->
        ConfirmDialog(
            title = "确认删除",
            content = "确定要删除「${asset.name}」吗？该股票下的所有交易记录将一并删除。",
            confirmText = "删除",
            onConfirm = {
                repo.deleteAsset(asset.id)
                pendingDelete = null
                Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun StockManageRow(
    asset: Asset,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).then(
                if (asset.hidden) Modifier.alpha(0.55f) else Modifier
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(asset.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (asset.hidden) "已隐藏" else "已显示",
                        fontSize = 11.sp,
                        color = if (asset.hidden) TextHint else PrimaryBlue
                    )
                }
                Spacer(Modifier.height(3.dp))
                val meta = buildString {
                    append("${asset.market ?: ""} ${asset.exchange ?: ""}".trim())
                    append(" · ${asset.shares}股")
                    append(" · 成本价${fmt3(asset.costPerShare)}元")
                }
                Text(meta, fontSize = 12.sp, color = TextSecondary)
            }
            TextButton(onClick = onToggleHidden) {
                Text(if (asset.hidden) "显示" else "隐藏", fontSize = 13.sp, color = PrimaryBlue)
            }
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 13.sp, color = ProfitRed)
            }
        }
    }
}
