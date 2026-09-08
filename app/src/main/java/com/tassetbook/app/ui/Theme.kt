package com.tassetbook.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 与小程序一致的配色：主题蓝 #1565C0；盈红亏绿（A股习惯）
val PrimaryBlue = Color(0xFF1565C0)
val PrimaryBlueLight = Color(0xFF1E88E5)
val ProfitRed = Color(0xFFB22222)
val LossGreen = Color(0xFF008000)
val BgGray = Color(0xFFF5F6F8)
val CardWhite = Color(0xFFFFFFFF)
val CardLightBlue = Color(0xFFECF4FC) // 股票卡片特别浅的蓝
val TextPrimary = Color(0xFF222222)
val TextSecondary = Color(0xFF666666)
val TextHint = Color(0xFF999999)
val DividerGray = Color(0xFFEEEEEE)
val BuyColor = Color(0xFFB22222)
val SellColor = Color(0xFF008000)
val EstimateColor = Color(0xFF1565C0)
val PriceOrange = Color(0xFFFF4500)   // 现价（走势图/卡片）专用橙色
val CardDarkBlue = Color(0xFF091629) // 交易记录卡片深藏蓝（偏黑）

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = PrimaryBlueLight,
    background = BgGray,
    surface = CardWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TAssetBookTheme(content: @Composable () -> Unit) {
    // 小程序仅浅色主题
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}

/** 盈亏颜色：红涨绿跌（0 用灰色） */
fun profitColor(value: Double): Color = when {
    value > 0.005 -> ProfitRed
    value < -0.005 -> LossGreen
    else -> TextSecondary
}
