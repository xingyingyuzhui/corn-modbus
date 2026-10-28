package io.github.xingyingyuzhui.modbusmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

/** ⋮ 菜单的容器：设计令牌里的 surface1 底 + 1dp line 描边 + 圆角 14，不用 Material 默认的浅紫色底。稿面没有画菜单，样式取自令牌。 */
@Composable
fun ModbusMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        containerColor = colors.surface1,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.line),
        content = content,
    )
}

/** 菜单项：文字用 text 色，当前项（[selected]）用强调色文字 + 半粗。 */
@Composable
fun ModbusMenuItem(text: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    DropdownMenuItem(
        modifier = modifier,
        text = {
            Text(
                text,
                color = if (selected) colors.accentText else colors.text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = colors.text),
    )
}

private val MenuItemDense = 36.dp

/**
 * 点格子弹菜单选值（连接页串口四格）：菜单锚在格子下方，当前值强调色 + wash 底，选中即关。
 * 项高 36（紧凑菜单，Material 默认 48）：8 档波特率 48 高放不进格子下方，菜单会被挤到盖住格子本身。
 */
@Composable
fun <T> FieldTileMenu(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    minHeight: Dp = ModbusSize.fieldTile,
    /** 菜单项的 testTag（测试按项点选用）；null = 不打。 */
    optionTag: (T) -> String? = { null },
) {
    val colors = LocalModbusColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldTile(label, optionLabel(value), modifier = Modifier.fillMaxWidth(), mono = mono, minHeight = minHeight, open = open, onClick = { open = true })
        ModbusMenu(expanded = open, onDismiss = { open = false }, modifier = Modifier.testTag("field-menu")) {
            options.forEach { option ->
                val selected = option == value
                ModbusMenuItem(
                    optionLabel(option),
                    onClick = { open = false; onPick(option) },
                    selected = selected,
                    modifier = Modifier
                        .then(optionTag(option)?.let { Modifier.testTag(it) } ?: Modifier)
                        .height(MenuItemDense)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (selected) Modifier.background(colors.wash(colors.accent)) else Modifier),
                )
            }
        }
    }
}
