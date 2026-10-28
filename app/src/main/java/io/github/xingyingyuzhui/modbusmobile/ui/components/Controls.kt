package io.github.xingyingyuzhui.modbusmobile.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AccentColor
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ChipRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.FieldRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.JetBrainsMono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.SmallRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.RowRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.StrokeHairline
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusType
import io.github.xingyingyuzhui.modbusmobile.ui.theme.StrokeControl
import androidx.compose.ui.semantics.error

@Composable
fun ModbusCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(LocalSpacing.current.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val shape = RoundedCornerShape(CardRadius)
    // §6.3 / §13.2：卡片内边距只有 space.card 12 一种（AL-04），内容左缘 = 卡片外缘 + 12 = K1（K0 + 12）。
    // 原来为了模仿稿面 border-box 多补的 1dp 去掉：K1 以外缘量，1dp 描边画在内边距里。
    // contentPadding 只留给通栏内容（例如卡内列表行自己带 12 水平内边距时传 0）。
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(StrokeHairline, colors.line, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalModbusColors.current
    // v2 label：12sp / 16sp，卡内间距由 ModbusCard 提供。
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("section-label").fillMaxWidth()) {
        Text(text, color = colors.muted, style = ModbusType.label, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 分段控件的单项高度（§6.7 v2）：项 36 / 小号 32（原 31）。外层仍补到 48dp 触控高度。 */
val SegItemDesign = ModbusSize.segItem
val SegItemSmall = ModbusSize.segItemSmall

/** §6.7 v2 `.seg`：外框圆角 radius.row 12、内边距 4、项间距 4（原 3）；选中块圆角 radius.small 8（= 12 − 4，同心）。 */
val SegContainerRadius = RowRadius
val SegPadding = ModbusSpacing.Comfortable.xs
val SegGap = SegPadding
val SegItemRadius = SmallRadius

const val SegContainerTag = "seg-container"
const val SegItemTag = "seg-item"

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = SegItemDesign,
    label: @Composable (T) -> Unit,
) {
    val colors = LocalModbusColors.current
    // 外层 Box 只负责 48dp 的触控占位（居中）；可见的底板就是内层 Row 本身：底板 = 项高 + 上下内边距，
    // 底板高度由项真实高度决定（双行文字的项会让底板跟着变高），不再单独按“稿面高度”另画，
    // 所以选中块与底板之间永远是均匀的 3dp，圆角同心。
    Box(modifier.fillMaxWidth().minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .testTag(SegContainerTag)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.surface2, RoundedCornerShape(SegContainerRadius))
                .padding(SegPadding),
            horizontalArrangement = Arrangement.spacedBy(SegGap),
        ) {
            options.forEach { option ->
                val on = option == selected
                val shape = RoundedCornerShape(SegItemRadius)
                // 选中块：深色 surface3 + 1dp outlineControl（§6.7，替换硬编码 #3A4658）；浅色 surface1 + 1dp line 环
                val ring = if (colors.isDark) colors.outlineControl else colors.line
                Box(
                    Modifier
                        .testTag(SegItemTag)
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = itemHeight)
                        .clip(shape)
                        .background(if (on) if (colors.isDark) colors.surface3 else colors.surface1 else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (on) Modifier.border(StrokeHairline, ring, shape) else Modifier)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(horizontal = LocalSpacing.current.xs, vertical = LocalSpacing.current.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    label(option)
                }
            }
        }
    }
}

/** 稿面尺寸的文字分段：项 14px（sm 12.5px），选中 600 + 主文字色，未选 muted。 */
@Composable
fun <T> SegmentedTextControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    text: @Composable (T) -> String,
) {
    val colors = LocalModbusColors.current
    SegmentedControl(
        options,
        selected,
        onSelect,
        modifier,
        itemHeight = if (small) SegItemSmall else SegItemDesign,
    ) { option ->
        val on = option == selected
        Text(
            text(option),
            color = if (on) colors.text else colors.muted,
            fontSize = if (small) 12.5.sp else 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            // §6.7 / G-24：分段文字允许 2 行（1.3× 时不截断）
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    xs: Boolean = false,
    design: Boolean = true,
    description: (T) -> String? = { null },
) {
    if (scroll) {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option), xs = xs, design = design, description = description(option)) { onToggle(option) }
            }
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), isSelected(option), xs = xs, design = design, description = description(option)) { onToggle(option) }
            }
        }
    }
}

/**
 * 触控热区补到 [MinTouch] 高，但外扩部分不占布局：[touchPad] 量出视觉高、在上下补边距，
 * [touchOverhang] 再把补的部分从报给父布局的高度里减掉、往上挪回去，热区画在自己的上下边距之外。
 * 原来的 minimumTouchHeight 把 33dp 的 chip 撑成 48dp 布局高，上下各多出约 7.5dp 看不见的空白，
 * 标题 → chip、chip → 卡底都显得比别处松（E-20261028-ux1 A2）。两段之间夹 clickable，热区 = 补齐后的 48dp。
 * 横向不扩：窄 chip 的横向热区靠 [SelectChip] 的 minWidth 和 Compose 默认的触控目标扩展。
 */
private class TouchPad { var extra = 0 }

private fun Modifier.touchOverhang(pad: TouchPad): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val extra = pad.extra
    layout(placeable.width, placeable.height - 2 * extra) { placeable.placeRelative(0, -extra) }
}

private fun Modifier.touchPad(pad: TouchPad, min: Dp = MinTouch): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    pad.extra = maxOf(0, (min.roundToPx() - placeable.height) / 2)
    layout(placeable.width, placeable.height + 2 * pad.extra) { placeable.placeRelative(0, pad.extra) }
}

/**
 * 只占 [visible] 的布局高、内容（含 ≥48dp 的触控热区）上下居中溢出：给行内文字链接用，
 * 让「最近写入 · 清空」这类标题行和普通小标题一样矮，热区照旧 48dp。
 */
fun Modifier.overhangHeight(visible: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    val height = visible.roundToPx()
    layout(placeable.width, height) { placeable.placeRelative(0, (height - placeable.height) / 2) }
}

/**
 * [xs] = 稿面 xs 档：高 25、字 12、内边距 3/8。
 * [design] = 稿面普通 chip：高 33、字 12.5、内边距 9（G#1，只在显式开启的页面生效，全局默认仍是 48dp）。
 * [description] 给只有符号的 chip 补读屏文字。
 * 视觉保持稿面尺寸，触控高度用 [touchOverhang] 外扩到 48dp，外扩部分不占布局。
 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    xs: Boolean = false,
    design: Boolean = true,
    description: String? = null,
    /** 窄文字的芯片也要有可点的宽度（触控 ≥48dp）。 */
    minWidth: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(ChipRadius)
    val minHeight = if (xs) 25.dp else if (design) 33.dp else MinTouch
    val horizontal = if (xs) 8.dp else if (design) 9.dp else 12.dp
    val pad = remember { TouchPad() }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .touchOverhang(pad)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .touchPad(pad)
            .widthIn(min = minWidth)
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (selected) colors.wash(colors.accent) else colors.surface1)
            .border(1.dp, if (selected) colors.accent else colors.line, shape)
            .indication(interaction, LocalIndication.current)
            .padding(horizontal = horizontal, vertical = if (xs) 3.dp else if (design) 6.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) colors.accentText else colors.muted,
            fontSize = if (xs) 12.sp else if (design) 12.5.sp else 13.sp,
        )
    }
}

@Composable
fun FieldTile(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    minHeight: Dp = ModbusSize.fieldTile,
    open: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(FieldRadius)
    // §6.2 v2：label caption 11/16、值 monoValueM 17/24；内边距 12×8，最小高 56（8 + 16 + 24 + 8）。原 10×7 / 50。
    // 传了 [onClick] 就是下拉格：整格可点、值后画 ⌄，菜单展开（[open]）时 1.5dp accent.border 描边，同输入格聚焦态。
    Column(
        modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(colors.surface2, shape)
            .then(if (open) Modifier.border(StrokeControl, colors.accentBorder, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = label, role = Role.DropdownList, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(fieldPadding()),
    ) {
        Text(label, color = colors.muted, style = ModbusType.caption, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                color = colors.text,
                style = if (mono) ModbusType.monoValueM else ModbusType.titleS,
                maxLines = 1,
                softWrap = false,
                modifier = if (onClick != null) Modifier.weight(1f) else Modifier,
            )
            if (sub != null) {
                Text(sub, color = colors.dim, style = ModbusType.monoCaption, modifier = Modifier.padding(start = LocalSpacing.current.xs))
            }
            if (onClick != null) {
                ChevronIcon(
                    colors.dim,
                    expanded = true,
                    size = 12.dp,
                    modifier = Modifier.padding(start = LocalSpacing.current.xs).then(if (open) Modifier.rotate(180f) else Modifier),
                )
            }
        }
    }
}

/**
 * 输入格。[sub] 是右侧小字（例如 40001 参考号）；获得焦点时画 1.5dp accent.border 描边（§6.2）。
 *
 * - 整个格子都能点（不只是文字那一行）：点格子任何位置都聚焦输入框。
 * - 聚焦时全选，键入的新数字直接替换旧值（123 不会变成 0123）。
 * - 值正好是“0”时再键入数字，丢掉前导 0（外部改值、软键盘把光标放在 0 后面的情况）。
 * - 出错（[isError]，默认 = 有 [error] 文字）：1.5dp error 描边 + label 变 error + 下方 12sp error 文字 + semantics error（§6.0，G-02）。
 * - 只读（[readOnly]）：底 surface2、值 muted、不能聚焦（§6.2，S1-03）。
 */
@Composable
fun NumberInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    sub: String? = null,
    minHeight: Dp = ModbusSize.fieldTile,
    keyboardType: KeyboardType = KeyboardType.Number,
    isError: Boolean = error != null,
    readOnly: Boolean = false,
    onValue: (String) -> Unit,
) {
    val colors = LocalModbusColors.current
    InputField(
        label = label,
        value = value,
        modifier = modifier,
        errorMessage = error,
        isError = isError,
        readOnly = readOnly,
        sub = sub,
        minHeight = minHeight,
        keyboardType = keyboardType,
        textStyle = ModbusType.monoValueM.copy(fontWeight = Mono.fontWeight, color = colors.text),
        normalize = ::dropLeadingZero,
        onValue = onValue,
    )
}

/** 文本输入（§6.2 新增，S5-06）：外观同 [NumberInput]，值用 body 14 比例字体，文本键盘，不做前导 0 处理。 */
@Composable
fun TextInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    isError: Boolean = error != null,
    readOnly: Boolean = false,
    minHeight: Dp = ModbusSize.fieldTile,
    onValue: (String) -> Unit,
) {
    val colors = LocalModbusColors.current
    InputField(
        label = label,
        value = value,
        modifier = modifier,
        errorMessage = error,
        isError = isError,
        readOnly = readOnly,
        sub = null,
        minHeight = minHeight,
        keyboardType = KeyboardType.Text,
        textStyle = ModbusType.body.copy(color = colors.text),
        normalize = { _, next -> next },
        selectAllOnFocus = false,
        onValue = onValue,
    )
}

@Composable
private fun InputField(
    label: String,
    value: String,
    modifier: Modifier,
    errorMessage: String?,
    isError: Boolean,
    readOnly: Boolean,
    sub: String?,
    minHeight: Dp,
    keyboardType: KeyboardType,
    textStyle: androidx.compose.ui.text.TextStyle,
    normalize: (previous: String, next: String) -> String,
    selectAllOnFocus: Boolean = true,
    onValue: (String) -> Unit,
) {
    val colors = LocalModbusColors.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val requester = remember { FocusRequester() }
    val shape = RoundedCornerShape(FieldRadius)
    LaunchedEffect(focused) {
        if (focused && selectAllOnFocus) {
            // 点击落点会在聚焦之后才把光标放下，等下一帧再全选，才不会被覆盖。
            withFrameNanos { }
            field = field.copy(selection = TextRange(0, field.text.length))
        }
    }
    val ring = when {
        isError -> Modifier.border(StrokeControl, colors.error, shape)
        focused && !readOnly -> Modifier.border(StrokeControl, colors.accentBorder, shape)
        else -> Modifier
    }
    val errorText = errorMessage
    Column(modifier.revealOnError(isError), verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(colors.surface2, shape)
                .then(ring)
                .then(if (readOnly) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { requester.requestFocus() } })
                .padding(fieldPadding()),
        ) {
            Text(label, color = if (isError) colors.error else colors.muted, style = ModbusType.caption, maxLines = 1)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = field,
                    onValueChange = { next ->
                        val text = normalize(field.text, next.text)
                        field = if (text == next.text) next else TextFieldValue(text, TextRange(text.length))
                        if (text != value) onValue(text)
                    },
                    textStyle = if (readOnly) textStyle.copy(color = colors.muted) else textStyle,
                    singleLine = true,
                    readOnly = readOnly,
                    enabled = !readOnly,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(requester)
                        .semantics {
                            contentDescription = label
                            if (isError) error(errorText ?: label)
                        }
                        .onFocusChanged { focused = it.isFocused },
                )
                if (sub != null) {
                    Text(sub, color = colors.dim, style = ModbusType.monoCaption, maxLines = 1)
                }
            }
        }
        if (errorText != null) {
            Text(errorText, color = colors.error, style = ModbusType.label)
        }
    }
}

/** 旧值正好是“0”、新值是“0”后面又跟了数字（“01”“07B”）时，去掉那个前导 0。其它情况原样返回。 */
internal fun dropLeadingZero(previous: String, next: String): String =
    if (previous == "0" && next.length > 1 && next[0] == '0' && next[1] != 'x' && next[1] != 'X' &&
        next.all { it.isLetterOrDigit() }
    ) {
        val rest = next.drop(1)
        if (rest.all { it == '0' }) "0" else rest
    } else {
        next
    }

/** 与输入格同形的可点击格（稿面“格式 十进制”、线圈值格）。 */
@Composable
fun ChoiceTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = ModbusSize.fieldTile,
    mono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = LocalModbusColors.current.text,
) {
    val colors = LocalModbusColors.current
    Column(
        modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(FieldRadius))
            .background(colors.surface2)
            .semantics { role = androidx.compose.ui.semantics.Role.Button }
            .clickable(onClick = onClick)
            .padding(fieldPadding()),
    ) {
        Text(label, color = colors.muted, style = ModbusType.caption, maxLines = 1)
        Text(value, color = valueColor, style = if (mono) ModbusType.monoValueM else ModbusType.titleS, maxLines = 1)
    }
}

const val SwitchVisualTag = "switch-visual"

enum class SettingTrailing { SWITCH, CHEVRON, LOCKED, NONE }

/** 开关视觉尺寸（§6.7 v2）：轨道 size.switch 44×24（原 42×24），滑块 20 = 稿面，四周留 (24 − 20) ÷ 2。 */
private val SwitchTrackWidth = ModbusSize.switchWidth
private val SwitchTrackHeight = ModbusSize.switchHeight
private val SwitchThumb = ModbusSize.iconL
private val SwitchThumbInset = (SwitchTrackHeight - SwitchThumb) / 2

/**
 * 开关（§6.7，G-08）：开 = accent.fill 轨道 + 白滑块；关 = surface3 轨道 + 1.5dp outlineControl 描边 + outlineControl 滑块；
 * 禁用 = surface3 轨道 + dim 滑块（不用 alpha）。只负责画，点击由所在行处理（整行 ≥48dp 可点）。
 */
@Composable
fun ModbusSwitchVisual(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = LocalModbusColors.current
    val track = when {
        !enabled -> colors.surface3
        checked -> colors.accent
        else -> colors.surface3
    }
    val thumb = when {
        !enabled -> colors.dim
        checked -> androidx.compose.ui.graphics.Color.White
        else -> colors.outlineControl
    }
    Box(
        modifier
            .testTag(SwitchVisualTag)
            .size(width = SwitchTrackWidth, height = SwitchTrackHeight)
            .clip(CircleShape)
            .background(track)
            .then(if (!checked || !enabled) Modifier.border(StrokeControl, if (enabled) colors.outlineControl else colors.line, CircleShape) else Modifier),
    ) {
        Box(
            Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(SwitchThumbInset)
                .size(SwitchThumb)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}

/** 行首 30×30 图标块（稿面 b3-1 列表行）。 */
@Composable
fun SettingIconBlock(content: @Composable () -> Unit) {
    val colors = LocalModbusColors.current
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface2),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: SettingTrailing = SettingTrailing.CHEVRON,
    checked: Boolean = false,
    selected: Boolean = false,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onChecked: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val colors = LocalModbusColors.current
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = MinTouch)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) colors.wash(colors.accent) else androidx.compose.ui.graphics.Color.Transparent)
    val interactive = when (trailing) {
        SettingTrailing.SWITCH -> base.toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
        SettingTrailing.LOCKED, SettingTrailing.NONE -> base
        SettingTrailing.CHEVRON -> base.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        interactive.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) SettingIconBlock(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = if (selected) colors.accentText else colors.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) Text(subtitle, color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (value != null) {
            Text(value, color = colors.dim, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
        }
        when (trailing) {
            SettingTrailing.SWITCH -> ModbusSwitchVisual(checked)
            SettingTrailing.CHEVRON -> Text("›", color = colors.dim, fontSize = 18.sp)
            SettingTrailing.LOCKED -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.setting_always), color = colors.dim, fontSize = 13.sp)
                LockIcon(colors.dim)
            }
            SettingTrailing.NONE -> Unit
        }
    }
}

/** 设置行的容器卡片：无内边距、行间不留空隙，行与行之间自己放 [RowDivider]。 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .clip(shape)
            .padding(1.dp),
        content = content,
    )
}

/** 卡片内行与行之间的分隔线。 */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalModbusColors.current.line))
}

@Composable
fun ColorSwatchRow(
    selected: AccentColor,
    modifier: Modifier = Modifier,
    onSelect: (AccentColor) -> Unit,
) {
    val colors = LocalModbusColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentColor.entries.forEach { accent ->
            val label = stringResource(R.string.cd_accent, accent.name)
            Box(
                Modifier
                    .size(MinTouch)
                    .semantics { contentDescription = label }
                    .clickable { onSelect(accent) },
                contentAlignment = Alignment.Center,
            ) {
                // 选中：外圈描边 + 2dp 间隙（稿面 b3-2 强调色圆点），圆点本身 26dp。
                Box(
                    Modifier
                        .size(34.dp)
                        .then(if (selected == accent) Modifier.border(2.dp, colors.text, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(accent.tones(colors.isDark).fill.toInt())),
                    )
                }
            }
        }
    }
}

@Composable
fun BitPicker16(
    bits: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val colors = LocalModbusColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(8) { column ->
                    val bit = 15 - (row * 8 + column)
                    val on = ((bits shr bit) and 1) == 1
                    val shape = RoundedCornerShape(8.dp)
                    val bitLabel = stringResource(R.string.cd_bit, bit)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = MinTouch)
                            .clip(shape)
                            .background(if (on) colors.wash(colors.accent) else colors.surface2)
                            .border(1.dp, if (on) colors.accent else colors.line, shape)
                            .semantics { contentDescription = bitLabel }
                            .clickable { onChange(bits xor (1 shl bit)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (on) "1" else "0",
                            color = if (on) colors.accentText else colors.muted,
                            fontFamily = JetBrainsMono,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "深色", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ControlsDark() = ControlsPreview(true)

@Preview(name = "浅色", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun ControlsLight() = ControlsPreview(false)

@Preview(name = "浅色 1.3x", fontScale = 1.3f)
@Composable
private fun ControlsLarge() = ControlsPreview(false)

@Composable
private fun ControlsPreview(dark: Boolean) {
    ModbusTheme(UiSettings(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedControl(listOf("主站", "从站"), "主站", {}) { Text(it, fontWeight = FontWeight.SemiBold) }
            ChipRow(listOf("9600 8N1", "19200 8N1"), { it.startsWith("9600") }, { it }, {})
            FieldTile("起始地址", "0", "40001")
            NumberInput(stringResource(R.string.input_preview_address), "0", sub = "40001") {}
            NumberInput(stringResource(R.string.input_preview_address), "70000", error = stringResource(R.string.input_preview_address_error)) {}
            NumberInput(stringResource(R.string.input_preview_address), "0", readOnly = true) {}
            TextInput(stringResource(R.string.input_preview_name), stringResource(R.string.input_preview_name_value)) {}
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModbusSwitchVisual(true)
                ModbusSwitchVisual(false)
                ModbusSwitchVisual(true, enabled = false)
                ModbusSwitchVisual(false, enabled = false)
            }
            SettingRow("屏幕常亮", trailing = SettingTrailing.SWITCH, checked = true)
            SettingRow("主题", trailing = SettingTrailing.CHEVRON)
            SettingRow("写入前确认", trailing = SettingTrailing.LOCKED)
            ColorSwatchRow(AccentColor.AMBER) {}
            BitPicker16(0x00A5) {}
        }
    }
}

/** 输入格 / FieldTile / ChoiceTile 的内边距（§6.2 v2）：水平 space.card 12、竖直 8（= (56 − 16 − 24) ÷ 2，不随密度变）。 */
@Composable
private fun fieldPadding(): PaddingValues =
    PaddingValues(horizontal = LocalSpacing.current.card, vertical = ModbusSpacing.Comfortable.s)
