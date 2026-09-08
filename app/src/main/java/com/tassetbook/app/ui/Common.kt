package com.tassetbook.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tassetbook.app.util.Money
import java.time.LocalDate

// ---------------- 格式化 ----------------

fun fmt2(v: Double): String = Money.fixed(v, 2)
fun fmt3(v: Double): String = Money.fixed(v, 3)

/** 盈亏文本：正数带 +，负数自带 - */
fun signedProfit(v: Double): String = (if (v >= 0) "+" else "") + fmt2(v)

// ---------------- 紧凑型输入框样式（高度约一行文字） ----------------

/** 单行输入框内边距：压掉 Material3 默认 16dp 纵向留白 */
val CompactFieldPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/** 输入框文字样式：14sp 单行 */
val CompactFieldTextStyle = TextStyle(fontSize = 14.sp)

/**
 * 紧凑型单行输入框：高度仅一行文字（约 36dp），视觉与原 OutlinedTextField 一致。
 * Material3 1.2.x 的公开 OutlinedTextField 固定最小 56dp 且不开放 contentPadding，
 * 故用 BasicTextField 自绘边框/背景实现。
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)
    val borderColor = when {
        !enabled -> Color(0xFFD9D9D9)
        focused -> PrimaryBlue
        else -> Color(0xFFBFBFBF)
    }
    val borderWidth = if (focused) 2.dp else 1.dp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(shape)
            .background(if (enabled) Color.White else Color(0xFFF5F5F5))
            .border(borderWidth, borderColor, shape),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        textStyle = CompactFieldTextStyle.copy(color = if (enabled) TextPrimary else TextSecondary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(PrimaryBlue),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(CompactFieldPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, fontSize = 14.sp, color = TextHint)
                    }
                    innerTextField()
                }
                trailingIcon?.invoke()
            }
        }
    )
}

// ---------------- 通用组件 ----------------

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    suffix: String? = null
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            placeholder = placeholder,
            enabled = enabled,
            trailingIcon = if (suffix != null) {
                { Text(suffix, color = TextHint, fontSize = 13.sp) }
            } else null
        )
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    fontWeight: FontWeight? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, color = valueColor, fontWeight = fontWeight)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    content: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium) },
        text = { Text(content, fontSize = 14.sp, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = PrimaryBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText, color = TextSecondary) }
        }
    )
}

/**
 * 日期选择字段。以 yyyy-MM-dd 显示（DatePicker），
 * 提交业务层时用 Dates.isoToLocale 转为小程序存储格式 yyyy/M/d。
 */
@Composable
fun DateField(
    label: String,
    isoDate: String,
    onDateChange: (iso: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        CompactTextField(
            value = isoDate,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clickable {
                    val current = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> onDateChange(LocalDate.of(y, m + 1, d).toString()) },
                        current.year, current.monthValue - 1, current.dayOfMonth
                    ).show()
                },
            placeholder = "点击选择日期"
        )
    }
}
