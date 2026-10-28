package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

/** 值变化后的高亮时长。来自 ui-plan：2 秒内淡出。 */
const val FlashDurationMs = 2_000L

/**
 * @param hex HEX 列文字，null 表示这一行没有 HEX（例如线圈）。
 * @param flashAt 值发生变化的时刻（[SystemClock.elapsedRealtime]）；2 秒内按剩余时间淡出，重新进入页面不会重播整段动画。
 * @param editable 能不能从这一行打开写入。
 */
data class RegisterRow(
    val address: Int,
    /** 地址列主行文字；null = 直接显示 [address]（“参考号”模式下传 40001 这样的参考号）。 */
    val addressText: String? = null,
    val value: String,
    val sub: String? = null,
    val hex: String? = null,
    val flashAt: Long? = null,
    val editable: Boolean = false,
    /** 从站表“最近来源”列的徽标；表格传了 `badgeHeader` 时，这一列代替 HEX 列。 */
    val badge: RowBadge? = null,
)

/** “最近来源”徽标：文字 + 语义色（外部写 / 广播琥珀、模拟蓝、本地编辑灰）。 */
data class RowBadge(val text: String, val kind: SemanticKind?)

/**
 * 稿面寄存器表：列 地址(70) | 值(1fr) | HEX(84) | ✎(26)；表头高 30，行高 40（横屏 36）。
 * 横屏稿面没有 ✎ 列，地址列 62、HEX 列 70。
 */
@Composable
fun RegisterTable(
    rows: List<RegisterRow>,
    modifier: Modifier = Modifier,
    valueHeader: String = stringResource(R.string.table_value),
    showEdit: Boolean = true,
    rowHeight: Dp = 40.dp,
    addressWidth: Dp = 70.dp,
    hexWidth: Dp = 84.dp,
    onHeaderValueClick: (() -> Unit)? = null,
    onRowClick: ((RegisterRow) -> Unit)? = null,
    /** 非空 = 最后一列显示徽标（稿面 b1-5“最近来源”，宽 [hexWidth]、右对齐），不显示 HEX。 */
    badgeHeader: String? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().height(if (onHeaderValueClick != null) MinTouch else 30.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.table_address), color = colors.dim, fontSize = 11.sp, modifier = Modifier.width(addressWidth))
            Text(
                valueHeader,
                color = if (onHeaderValueClick != null) colors.muted else colors.dim,
                fontSize = 11.sp,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onHeaderValueClick != null) {
                            Modifier.semantics { role = Role.Button }.clickable(onClick = onHeaderValueClick)
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                badgeHeader ?: stringResource(R.string.table_hex),
                color = colors.dim,
                fontSize = 11.sp,
                textAlign = if (badgeHeader != null) androidx.compose.ui.text.style.TextAlign.End else null,
                modifier = Modifier.width(hexWidth),
            )
            if (showEdit) Box(Modifier.width(26.dp))
        }
        HorizontalDivider(color = colors.line)
        rows.forEachIndexed { index, row ->
            RegisterRowView(row, showEdit, rowHeight, addressWidth, hexWidth, onRowClick, badgeColumn = badgeHeader != null)
            if (index != rows.lastIndex) HorizontalDivider(color = colors.rowLine)
        }
    }
}

@Composable
private fun RegisterRowView(
    row: RegisterRow,
    showEdit: Boolean,
    rowHeight: Dp,
    addressWidth: Dp,
    hexWidth: Dp,
    onRowClick: ((RegisterRow) -> Unit)?,
    badgeColumn: Boolean = false,
) {
    val colors = LocalModbusColors.current
    val flash = remember(row.flashAt) { Animatable(flashFraction(row.flashAt)) }
    LaunchedEffect(row.flashAt) {
        val at = row.flashAt
        val remaining = if (at == null) 0L else FlashDurationMs - (SystemClock.elapsedRealtime() - at)
        if (remaining > 0) {
            flash.snapTo(remaining / FlashDurationMs.toFloat())
            flash.animateTo(0f, tween(remaining.toInt(), easing = LinearEasing))
        } else {
            flash.snapTo(0f)
        }
    }
    val flashColor = colors.tx
    val clickable = row.editable && onRowClick != null
    Row(
        Modifier
            .fillMaxWidth()
            // 能点的行（✎ 写入）按触控优先补到 48dp，比稿面的 40 / 36 高；不能点的行保持稿面行高。
            // 用最小高度而不是固定高度：系统 1.3x + App 特大时「行号 + 参考号」超过 48dp 就撑高，不截字（qa-03 B）。
            .heightIn(min = if (clickable) maxOf(rowHeight, MinTouch) else rowHeight)
            .drawBehind {
                val fraction = flash.value
                if (fraction > 0f) {
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(flashColor.copy(alpha = 0.16f * fraction), Color.Transparent),
                            startX = 0f,
                            endX = size.width * 0.8f,
                        ),
                    )
                }
            }
            .then(if (clickable) Modifier.semantics { role = Role.Button }.clickable { onRowClick?.invoke(row) } else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(addressWidth)) {
            Text(row.addressText ?: row.address.toString(), style = Mono, color = colors.text)
            if (row.sub != null) Text(row.sub, color = colors.dim, style = Mono.copy(fontSize = 10.sp, lineHeight = 16.sp))
        }
        Text(
            row.value,
            style = Mono.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = if (flash.value > 0.02f) colors.txText else colors.text,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (badgeColumn) {
            Box(Modifier.width(hexWidth), contentAlignment = Alignment.CenterEnd) {
                val badge = row.badge
                // 来源只是说明，不可点：用带语义色的小字，不画底色徽标（徽标像按钮，E-20261028-ux1 C1）。
                Text(
                    badge?.text ?: "—",
                    color = when {
                        badge == null -> colors.dim
                        badge.kind == null -> colors.muted
                        else -> colors.tone(badge.kind)
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                row.hex.orEmpty(),
                style = Mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.muted,
                maxLines = 1,
                modifier = Modifier.width(hexWidth),
            )
        }
        if (showEdit) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterEnd) {
                if (row.editable) PencilIcon(colors.dim)
            }
        }
    }
}

private fun flashFraction(flashAt: Long?): Float {
    if (flashAt == null) return 0f
    val remaining = FlashDurationMs - (SystemClock.elapsedRealtime() - flashAt)
    return if (remaining > 0) remaining / FlashDurationMs.toFloat() else 0f
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TableDark() = TablePreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TableLight() = TablePreview(false)

@Composable
private fun TablePreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        RegisterTable(
            listOf(
                RegisterRow(0, "0", "40001", "0x0000", editable = true),
                RegisterRow(1, "10", "40002", "0x000A", editable = true),
                RegisterRow(2, "20", "40003", "0x0014", editable = true),
            ),
        )
    }
}
