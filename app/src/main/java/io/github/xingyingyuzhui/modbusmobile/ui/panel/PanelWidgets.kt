package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.ErrorKind
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateTone
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetWrites
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusSwitchVisual
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.compositionLocalOf
import io.github.xingyingyuzhui.modbusmobile.ui.components.BoltIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.LockIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.PencilIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.TonalButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.TonalButtonHeight
import io.github.xingyingyuzhui.modbusmobile.ui.curve.LocalWallClock
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.elapsedText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * 开关 / 按钮卡能不能点：[enabled] 为 false 时控件置灰并在 meta 行写 [reason]；[busy] = 正在读整字 / 写入。
 * 传 null（编辑模式、向导预览）= 控件只显示、没有任何点击处理，不可能写设备。
 */
data class CardWrite(
    val enabled: Boolean,
    val reason: String?,
    val busy: Boolean,
    val onSwitch: (Boolean) -> Unit,
    val onButton: () -> Unit,
    /** 寄存器表某一行（行号从 0 起）。 */
    val onRow: (Int) -> Unit = {},
)

/**
 * “现在”（墙钟毫秒）：过期卡写“12 秒前的值”用。面板页在有过期卡时每秒更新一次；为 null 时直接读 [LocalWallClock]。
 */
val LocalPanelNow = compositionLocalOf<Long?> { null }

/** 卡片外观的强调：预警 = 琥珀描边，告警 = 红描边 + 红色浅底（v3 · 只有异常才出声）。 */
internal enum class CardTone { NONE, WARN, ALERT }

/**
 * 面板组件卡（v3，`docs/design/panel-v3.html` `.x`）：圆角 16、1px 边、内边距 10/12（border-box，内容离外缘 13/11）、
 * 名称 13 muted（控制卡前面有 ⚡）、值 mono 28/600 + 单位 13、底行 meta mono 10.5 dim（左地址、右阈值 / 量程 / Unit）。
 * 正常时不打标签、不写时间；只有预警 / 告警 / 过期 / 超时 / 错误才在名称行右侧打标签。
 * 质量态：过期 = 值 muted + “读取失败 · N 秒前的值”；超时 / 错误 = 值 dim + 红色删除线 + 失败说明（红）。
 */
@Composable
fun WidgetCard(
    widget: Widget,
    snapshot: PointSnapshot?,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    /** 向导里的预览卡用强调色描边（稿面 border-color:var(--amber)）。 */
    borderColor: Color? = null,
    /** 开关 / 按钮的写入入口；null = 不可操作（编辑模式 / 向导预览）。 */
    write: CardWrite? = null,
    /** 寄存器表要用：各行点位的读数（key = 行点位 id）。 */
    snapshots: Map<String, PointSnapshot> = emptyMap(),
) {
    val point = widget.point ?: return
    val colors = LocalModbusColors.current
    // 编辑模式（v3 2-1）：安静的编辑态 —— surface2 底、名称正文色、左 ⠿、右上尺寸；值灰色，没有标签；底行写“类型 · 地址”。
    val look = rememberLook(widget, point, snapshot).let {
        if (editing) it.copy(badge = null, meta = editMeta(widget.kind, addressLabel(point)), metaProblem = false, metaRight = null, tone = CardTone.NONE, valueColor = colors.muted, strike = false) else it
    }
    val name = widget.displayName(point)
    val editSize = if (editing) stringResource(R.string.panel_edit_size_short, widget.spanColumns, widget.spanRows) else null
    CompositionLocalProvider(LocalEditCardSize provides editSize) {
    CardFrame(modifier, "panel-card-${widget.id}", borderColor, look.tone, fill = if (editing) colors.surface2 else null) {
        when (widget.kind) {
            WidgetKinds.LAMP -> LampBody(widget, point, name, snapshot, look)
            WidgetKinds.STATUS_TEXT -> StatusBody(widget, point, name, snapshot, look)
            WidgetKinds.PROGRESS -> ProgressBody(widget, point, name, snapshot, look)
            WidgetKinds.GAUGE -> GaugeBody(widget, point, name, snapshot, look)
            WidgetKinds.SWITCH -> SwitchBody(widget, point, name, snapshot, look, write)
            WidgetKinds.BUTTON -> ButtonBody(widget, point, name, editing, write)
            WidgetKinds.REGISTER_TABLE -> TableBody(widget, name, editing, snapshots, write)
            else -> NumberBody(widget, point, name, snapshot, look)
        }
    }
    }
}

@Composable
private fun editMeta(kind: String, address: String): String = stringResource(
    when (kind) {
        WidgetKinds.LAMP -> R.string.panel_wiz_kind_lamp
        WidgetKinds.STATUS_TEXT -> R.string.panel_wiz_kind_status
        WidgetKinds.PROGRESS -> R.string.panel_wiz_kind_progress
        WidgetKinds.GAUGE -> R.string.panel_wiz_kind_gauge
        WidgetKinds.SWITCH -> R.string.panel_wiz_kind_switch
        WidgetKinds.BUTTON -> R.string.panel_wiz_kind_button
        WidgetKinds.REGISTER_TABLE -> R.string.panel_wiz_kind_table
        else -> R.string.panel_wiz_kind_number
    },
) + " · " + address

/** 编辑态卡片的尺寸标签（“1×1”）；非 null 时名称行左边画 ⠿、右边画尺寸，名称用正文色。 */
internal val LocalEditCardSize = staticCompositionLocalOf<String?> { null }

@Composable
internal fun CardFrame(
    modifier: Modifier,
    tag: String,
    borderColor: Color? = null,
    tone: CardTone = CardTone.NONE,
    fill: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val border = borderColor ?: when (tone) {
        CardTone.WARN -> colors.txText.copy(alpha = 0.6f)
        CardTone.ALERT -> colors.error.copy(alpha = 0.65f)
        CardTone.NONE -> colors.line
    }
    Column(
        modifier
            .fillMaxSize()
            .testTag(tag)
            .background(fill ?: colors.surface1, shape)
            .then(if (tone == CardTone.ALERT) Modifier.background(colors.wash(colors.error), shape) else Modifier)
            .border(1.dp, border, shape)
            // 稿面 border-box：边框在内边距之外占位，Compose 的 border 不占位，补 1dp。
            .padding(1.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** 一张卡的“怎么画”：标签（只有异常才有）、值的颜色、是否划线、底行左右两段、卡片强调。 */
internal data class Look(
    val badge: Pair<String, Color>?,
    val valueColor: Color,
    val strike: Boolean,
    val meta: String,
    val metaProblem: Boolean,
    val hasValue: Boolean,
    val metaRight: String? = null,
    val tone: CardTone = CardTone.NONE,
)

@Composable
internal fun rememberLook(widget: Widget, point: PointDefinition, snapshot: PointSnapshot?): Look {
    val colors = LocalModbusColors.current
    val quality = snapshot?.quality ?: Quality.NO_DATA
    val hasValue = snapshot?.raw != null
    val addr = addressLabel(point)
    val level = if (quality == Quality.OK) snapshot?.level ?: ThresholdLevel.NORMAL else ThresholdLevel.NORMAL
    // 正常不打标签：只有预警 / 告警 / 过期 / 超时 / 错误才说话
    val badge: Pair<String, Color>? = when (quality) {
        Quality.OK -> when (level) {
            ThresholdLevel.ALERT -> stringResource(R.string.panel_badge_alert) to colors.error
            ThresholdLevel.WARN -> stringResource(R.string.panel_badge_warn) to colors.txText
            else -> null
        }
        Quality.STALE -> stringResource(R.string.panel_badge_stale) to colors.dropped
        Quality.TIMEOUT -> stringResource(R.string.panel_badge_timeout) to colors.error
        Quality.ERROR -> stringResource(R.string.panel_badge_error) to colors.error
        Quality.NO_DATA -> null
    }
    val valueColor = when (quality) {
        Quality.OK -> when (level) {
            ThresholdLevel.ALERT -> colors.error
            ThresholdLevel.WARN -> colors.txText
            else -> colors.text
        }
        Quality.STALE -> colors.muted
        else -> colors.dim
    }
    val failures = snapshot?.failures ?: 0
    val now = LocalPanelNow.current ?: LocalWallClock.current()
    val problem = when (quality) {
        Quality.OK -> null
        Quality.NO_DATA -> when (snapshot?.error?.kind) {
            null -> null
            else -> stringResource(R.string.panel_meta_failed, failures)
        }
        Quality.STALE -> snapshot?.updatedWallMs
            ?.let { stringResource(R.string.panel_meta_stale_age, elapsedText((now - it).coerceAtLeast(0))) }
            ?: stringResource(R.string.panel_meta_stale)
        Quality.TIMEOUT -> stringResource(R.string.panel_meta_timeout, failures)
        Quality.ERROR -> when (snapshot?.error?.kind) {
            ErrorKind.EXCEPTION -> stringResource(R.string.panel_meta_exception, snapshot.error.code ?: 0)
            ErrorKind.BAD_VALUE -> stringResource(R.string.panel_meta_bad_value)
            else -> stringResource(R.string.panel_meta_invalid)
        }
    }
    val meta = when {
        problem != null -> problem
        quality == Quality.NO_DATA -> stringResource(R.string.panel_meta_waiting, addr)
        else -> addr
    }
    // 底行右侧：进度条写量程；预警 / 告警写命中的阈值（“≥ 0.80”）
    val right = when {
        problem != null || quality == Quality.NO_DATA -> null
        widget.kind == WidgetKinds.PROGRESS -> rangeLabel(point)
        level != ThresholdLevel.NORMAL -> thresholdLabel(point, level, snapshot?.engineering)
        else -> null
    }
    val tone = when (level) {
        ThresholdLevel.ALERT -> CardTone.ALERT
        ThresholdLevel.WARN -> CardTone.WARN
        else -> CardTone.NONE
    }
    val strike = (quality == Quality.TIMEOUT || quality == Quality.ERROR) && hasValue
    return Look(badge, valueColor, strike, meta, problem != null && quality != Quality.NO_DATA && quality != Quality.STALE, hasValue, right, tone)
}

/** 命中的阈值区间写成“≥ 0.80”/“< 10”/“10 – 20”。 */
@Composable
private fun thresholdLabel(point: PointDefinition, level: ThresholdLevel, value: Double?): String? {
    val band = point.thresholds.firstOrNull { it.level == level && (value == null || it.contains(value)) } ?: return null
    val min = band.min?.let { ValueCodec.format(it, point.conversion.decimals) }
    val max = band.max?.let { ValueCodec.format(it, point.conversion.decimals) }
    return when {
        min != null && max != null -> stringResource(R.string.panel_threshold_range, min, max)
        min != null -> stringResource(R.string.panel_threshold_ge, min)
        max != null -> stringResource(R.string.panel_threshold_lt, max)
        else -> null
    }
}

// ---- 各类型卡片 ----

/** 名称行：[leading]（⚡ / 🔒）+ 名称 13 muted + 右侧标签或 [trailing]。 */
@Composable
private fun NameRow(
    title: String,
    badge: Pair<String, Color>?,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    val editSize = LocalEditCardSize.current
    Row(
        // 固定最小高 22：有标签（高 18 + 内边距）和没标签的卡，名称在同一条线上
        Modifier.fillMaxWidth().heightIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (editSize != null) GripDots(colors.dim)
        leading?.invoke()
        Text(
            title, color = if (editSize != null) colors.text else colors.muted, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1,
            fontWeight = if (editSize != null) FontWeight.SemiBold else FontWeight.Normal,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("panel-name"),
        )
        when {
            // 编辑态：右侧读数（进度条）照样画，后面跟尺寸标签
            editSize != null -> { trailing?.invoke(); EditSizeTag(editSize) }
            trailing != null -> trailing()
            badge != null -> ToneBadge(badge.first, badge.second, Modifier.testTag("panel-badge"))
        }
    }
}

/** 编辑态右上角的尺寸标签：mono 10.5、1dp 描边、圆角 5。 */
@Composable
internal fun EditSizeTag(text: String) {
    val colors = LocalModbusColors.current
    Text(
        text, style = Mono.copy(fontSize = 10.5.sp, lineHeight = 16.sp), color = colors.dim,
        // 纯文字，不画描边：描边小框像按钮，但尺寸是在卡片设置里改的，这里点不了（E-20261028-ux1 B5）。
        modifier = Modifier.testTag("edit-size-tag"),
    )
}

/** ⠿ 拖动提示：2 × 3 个小圆点。 */
@Composable
internal fun GripDots(color: Color) {
    Canvas(Modifier.size(width = 8.dp, height = 12.dp).testTag("edit-grip")) {
        val r = 1.5.dp.toPx()
        for (cx in listOf(r, size.width - r)) for (cy in listOf(r, size.height / 2, size.height - r)) drawCircle(color, r, Offset(cx, cy))
    }
}

/** ⚡：这张卡会写设备（开关 / 按钮 / 可修改的寄存器表）。广播用红色。 */
@Composable
private fun WritesDeviceMark(danger: Boolean = false) {
    val colors = LocalModbusColors.current
    val desc = stringResource(R.string.panel_writes_device)
    BoltIcon(
        if (danger) colors.error else colors.dim,
        Modifier.testTag("panel-writes-mark").semantics { contentDescription = desc },
    )
}

/** 底行：左 [Look.meta]（出问题时红色）、右 [Look.metaRight]；推到卡片底部，同行卡片底行对齐。 */
@Composable
private fun ColumnScope.MetaRow(look: Look, topPadding: Int = 4, right: String? = look.metaRight) {
    if (look.meta.isEmpty() && right == null) return
    val colors = LocalModbusColors.current
    val style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp)
    Spacer(Modifier.weight(1f))
    Row(
        Modifier.fillMaxWidth().padding(top = topPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            look.meta, style = style, color = if (look.metaProblem) colors.error else colors.dim,
            modifier = Modifier.weight(1f).testTag("panel-meta"),
        )
        if (right != null) {
            Text(right, style = style, color = colors.dim, maxLines = 1, modifier = Modifier.testTag("panel-meta-right"))
        }
    }
}

@Composable
private fun ColumnScope.NumberBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val text = if (snapshot?.raw == null) "—" else snapshot.text ?: flagText(snapshot)
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text,
            style = Mono.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            color = look.valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .alignByBaseline()
                .testTag("panel-value-${widget.id}")
                .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
        )
        if (point.unitLabel.isNotBlank() && snapshot?.raw != null) {
            Text(point.unitLabel, color = colors.muted, fontSize = 13.sp, lineHeight = 16.sp, modifier = Modifier.alignByBaseline())
        }
    }
    MetaRow(look)
}

@Composable
private fun ColumnScope.LampBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val known = snapshot?.raw != null
    val on = known && (snapshot?.engineering ?: 0.0) != 0.0
    val label = when {
        !known -> "—"
        on -> widget.onLabel.ifBlank { stringResource(R.string.panel_flag_on) }
        else -> widget.offLabel.ifBlank { stringResource(R.string.panel_flag_off) }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // 编辑态安静：灯不发光（亮的灯只画减淡的绿），文字灰
        val editing = LocalEditCardSize.current != null
        Lamp(on = on && snapshot?.quality == Quality.OK && !editing, dim = on)
        Text(
            label,
            fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
            color = if (editing) colors.muted else if (snapshot?.quality == Quality.OK) (if (on) colors.text else colors.muted) else look.valueColor,
            modifier = Modifier.testTag("panel-value-${widget.id}"),
        )
    }
    MetaRow(look, topPadding = 6)
}

/**
 * 开关卡：⚡ 名称 + 标签、当前状态文字 17 粗、右侧 42×24 开关（稿面 .tg，点击区 ≥48×48）、底行“地址 · U1”。
 * 点开关**不会**直接写：它只是请求“切到相反状态”，确认弹窗里才发（见 [PanelWriteController]）。
 * 状态未知 / 过期、非主站、正在读写时置灰，原因写在底行；编辑模式 / 向导预览（[write] 为 null）只画不响应。
 */
@Composable
private fun ColumnScope.SwitchBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look, write: CardWrite?) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge, leading = { WritesDeviceMark() })
    val known = snapshot?.raw != null && snapshot.quality == Quality.OK
    val on = snapshot?.raw != null && (snapshot.engineering ?: 0.0) != 0.0
    val label = when {
        snapshot?.raw == null -> "—"
        on -> widget.onLabel.ifBlank { stringResource(R.string.panel_flag_on) }
        else -> widget.offLabel.ifBlank { stringResource(R.string.panel_flag_off) }
    }
    val usable = write != null && write.enabled && !write.busy && known
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold,
            color = if (known) (if (on) colors.text else colors.muted) else look.valueColor,
            modifier = Modifier.weight(1f).testTag("panel-value-${widget.id}"),
        )
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .alpha(if (usable || write == null) 1f else 0.45f)
                .testTag("panel-switch-${widget.id}")
                .then(
                    if (write != null) {
                        Modifier.toggleable(value = on, enabled = usable, role = Role.Switch, onValueChange = { write.onSwitch(it) })
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) { ModbusSwitchVisual(on) }
    }
    val note = when {
        write == null -> null
        write.busy -> stringResource(R.string.panel_write_busy)
        !write.enabled -> write.reason
        !known -> stringResource(R.string.panel_write_unknown_state)
        else -> null
    }
    if (note != null) {
        Spacer(Modifier.weight(1f))
        Text(
            note, style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp), color = colors.dim,
            modifier = Modifier.padding(top = 2.dp).testTag("panel-write-note"),
        )
    } else {
        MetaRow(look, right = if (look.metaProblem || look.meta.isEmpty()) look.metaRight else stringResource(R.string.panel_unit_label, point.unitId))
    }
}

/**
 * 按钮卡：⚡ 名称（广播：红 ⚡ + 红标签“广播 · 所有从站”）+ 整宽浅底描边按钮（视觉 44、触控 ≥48；广播红色）
 * + 底行“C5 ← ON”与“U1”。按钮的点位不轮询；点按钮只是请求写预设值，确认弹窗里才发。
 */
@Composable
private fun ColumnScope.ButtonBody(widget: Widget, point: PointDefinition, name: String, editing: Boolean, write: CardWrite?) {
    val colors = LocalModbusColors.current
    val broadcast = point.unitId == 0
    NameRow(name, null, leading = { WritesDeviceMark(danger = broadcast) })
    if (broadcast) {
        // 一列宽的卡里名称和标签挤不下：广播标签单独一行（规范 54：卡上红字写明广播）
        ToneBadge(stringResource(R.string.panel_broadcast_tag), colors.error, Modifier.padding(top = 4.dp).testTag("panel-broadcast-${widget.id}"))
    }
    val label = widget.buttonLabel.ifBlank { stringResource(R.string.panel_button_default) }
    val usable = write != null && write.enabled && !write.busy
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("panel-button-${widget.id}")) {
        if (write != null) {
            TonalButton(label, onClick = write.onButton, Modifier.fillMaxWidth(), danger = broadcast, enabled = usable)
        } else {
            // 编辑模式 / 向导预览：只画样子（尺寸和真按钮一致），没有任何点击处理，也不挡住“点卡片打开向导”
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().height(TonalButtonHeight).clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2).border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .testTag("panel-button-visual-${widget.id}"),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = colors.dim, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
    }
    val value = widget.writeValue?.let { v ->
        if (point.area == RegisterArea.COIL) (if (v != 0.0) "ON" else "OFF") else ValueCodec.format(v, null).orEmpty()
    }.orEmpty()
    val target = stringResource(
        if (broadcast) R.string.panel_button_target_broadcast else R.string.panel_button_target,
        addressLabel(point), value,
    )
    val note = when {
        editing || write == null -> target
        write.busy -> stringResource(R.string.panel_write_busy)
        !write.enabled -> write.reason ?: target
        else -> target
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp)
        Text(note, style = style, color = colors.dim, modifier = Modifier.weight(1f).testTag("panel-button-note-${widget.id}"))
        if (!broadcast && note == target) Text(stringResource(R.string.panel_unit_label, point.unitId), style = style, color = colors.dim, maxLines = 1)
    }
}

/**
 * 寄存器表卡（v3）：表头一行 = ⚡（可修改）/ 🔒（只读）+ 名称 + 异常标签 + “U1 · U16 · ℃”；
 * 每行 = 左地址（mono 13.5 + 参考号 10 dim）、右值（mono 17 粗，HEX 小字在值下面）、可修改时右侧一个灰色 ✎ 提示。
 * 行高 ≥48；可修改时整行可点（只是请求：先看写入帧、确认才写，见 [PanelWriteController]）。
 * 底行只在有问题 / 有原因时出现（读取失败、正在写、不能写的原因、等待数据）。
 * 编辑模式只画摘要；向导预览（[write] 为 null）画出 ✎ 但不响应。
 */
@Composable
private fun ColumnScope.TableBody(widget: Widget, name: String, editing: Boolean, snapshots: Map<String, PointSnapshot>, write: CardWrite?) {
    val colors = LocalModbusColors.current
    val rows = widget.tablePoints
    if (rows.isEmpty()) return
    val first = rows.first()
    val last = rows.last()
    // 范围写到最后一个寄存器（32 位行占 2 个）：HR100–HR107，而不是最后一行的起始地址
    val endLabel = addressLabel(last.copy(address = last.address + last.quantity - 1))
    val range = addressLabel(first) + if (endLabel != addressLabel(first)) "–$endLabel" else ""
    val writable = widget.tableWritable
    val mark: @Composable () -> Unit = {
        if (writable) {
            WritesDeviceMark()
        } else {
            val desc = stringResource(R.string.panel_read_only)
            LockIcon(colors.dim, Modifier.testTag("panel-readonly-mark").semantics { contentDescription = desc })
        }
    }
    if (editing) {
        NameRow(name, null, leading = mark)
        Text(
            stringResource(
                R.string.panel_table_edit_summary, rows.size, addressLabel(first), endLabel,
                stringResource(if (writable) R.string.panel_table_mode_rw else R.string.panel_table_mode_ro),
            ),
            color = colors.dim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp).testTag("panel-table-summary-${widget.id}"),
        )
        return
    }
    val snaps = rows.map { snapshots[it.id] }
    val worst = snaps.withIndex().maxByOrNull { (_, s) -> qualityRank(s?.quality) }
    val look = rememberLook(widget, rows[worst?.index ?: 0], worst?.value)
    val badge = if (snaps.all { it?.raw == null && (it?.quality ?: Quality.NO_DATA) == Quality.NO_DATA }) null else look.badge
    val isBits = first.isBitArea
    val usable = write != null && write.enabled && !write.busy
    val unit = first.unitLabel.takeIf { it.isNotBlank() }
    val info = stringResource(R.string.panel_table_head_info, first.unitId, first.type.name) + (unit?.let { " · $it" } ?: "")
    NameRow(name, null, leading = mark) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (badge != null) ToneBadge(badge.first, badge.second, Modifier.testTag("panel-badge"))
            Text(info, style = Mono.copy(fontSize = 11.sp, lineHeight = 14.sp), color = colors.dim, maxLines = 1, modifier = Modifier.testTag("panel-table-info-${widget.id}"))
        }
    }
    Spacer(Modifier.height(4.dp))
    rows.forEachIndexed { i, row ->
        val snap = snaps[i]
        val desc = stringResource(R.string.panel_table_edit_desc, addressLabel(row))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TABLE_ROW_MIN)
                .alpha(if (!writable || usable || write == null) 1f else 0.6f)
                .then(
                    if (writable && write != null) {
                        Modifier.semantics { contentDescription = desc }
                            .clickable(enabled = usable, role = Role.Button) { write.onRow(i) }
                    } else {
                        Modifier
                    },
                )
                .testTag("panel-row-${widget.id}-$i"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(addressLabel(row), style = Mono.copy(fontSize = 13.5.sp, lineHeight = 17.sp), color = colors.text, maxLines = 1)
                Text(modiconLabel(row.area, row.address), style = Mono.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.dim, maxLines = 1)
            }
            val quality = snap?.quality ?: Quality.NO_DATA
            val has = snap?.raw != null
            val color = when (quality) {
                Quality.OK -> when (snap?.level) {
                    ThresholdLevel.ALERT -> colors.error
                    ThresholdLevel.WARN -> colors.txText
                    else -> colors.text
                }
                Quality.STALE -> colors.muted
                else -> colors.dim
            }
            val text = when {
                !has -> "—"
                isBits -> if (snap?.engineering == 1.0) "ON" else "OFF"
                else -> snap?.text ?: "—"
            }
            val strike = (quality == Quality.TIMEOUT || quality == Quality.ERROR) && has
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text, style = Mono.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("panel-row-value-${widget.id}-$i").then(if (strike) Modifier.redStrike(colors.error) else Modifier),
                )
                if (!isBits) {
                    val words = WidgetWrites.wordsOf(row, snap?.raw)
                    Text(
                        words?.joinToString(" ") { "%04X".format(it) } ?: "—",
                        style = Mono.copy(fontSize = 10.sp, lineHeight = 12.sp), color = colors.dim, maxLines = 1,
                        modifier = Modifier.testTag("panel-row-hex-${widget.id}-$i"),
                    )
                }
            }
            if (writable) {
                // 灰色 ✎ 只是提示整行可点；点的是整行（≥48dp）
                Box(Modifier.width(TABLE_EDIT_W).testTag("panel-row-edit-${widget.id}-$i"), contentAlignment = Alignment.CenterEnd) {
                    PencilIcon(colors.dim, size = 13.dp)
                }
            }
        }
    }
    val anyData = snaps.any { it?.raw != null }
    val note = when {
        write == null || !writable -> null
        write.busy -> stringResource(R.string.panel_write_busy)
        !write.enabled -> write.reason
        else -> null
    }
    val footer = when {
        note != null -> note
        look.meta.isNotEmpty() && (look.metaProblem || look.badge?.first == stringResource(R.string.panel_badge_stale)) -> look.meta
        !anyData -> stringResource(R.string.panel_meta_waiting, range)
        else -> null
    }
    if (footer != null) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
        Text(
            footer, style = Mono.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
            color = if (note == null && look.metaProblem) colors.error else colors.dim,
            modifier = Modifier.padding(top = 6.dp).testTag(if (note != null) "panel-table-note-${widget.id}" else "panel-table-meta-${widget.id}"),
        )
    }
}

private val TABLE_ROW_MIN = 48.dp
private val TABLE_EDIT_W = 26.dp

/** 越大越“糟”：错误 > 超时 > 过期 > 正常 > 没数据，卡片标签 / 说明跟最糟的那一行走。 */
private fun qualityRank(q: Quality?): Int = when (q) {
    Quality.ERROR -> 4
    Quality.TIMEOUT -> 3
    Quality.STALE -> 2
    Quality.OK -> 1
    else -> 0
}

/** 18dp 灯，亮 = 绿 + 4dp 浅圈 + 光晕；灭 / 无数据 = surface3；有值但读取失败（过期）= 绿色减淡，无光晕。 */
@Composable
private fun Lamp(on: Boolean, dim: Boolean) {
    val colors = LocalModbusColors.current
    val base = when {
        on -> colors.rxLocal
        dim -> colors.rxLocal.copy(alpha = 0.45f)
        else -> colors.surface3
    }
    Box(
        Modifier
            .size(LAMP_SIZE)
            .drawBehind {
                if (on) {
                    drawCircle(
                        Brush.radialGradient(
                            0.5f to colors.rxLocal.copy(alpha = 0.5f),
                            1f to Color.Transparent,
                            center = center,
                            radius = 16.dp.toPx(),
                        ),
                        radius = 16.dp.toPx(),
                    )
                    drawCircle(colors.wash(colors.rxLocal), radius = size.minDimension / 2f + 4.dp.toPx())
                }
            }
            .clip(CircleShape)
            .background(base)
            .testTag("panel-lamp"),
    )
}

internal val LAMP_SIZE = 18.dp

@Composable
private fun ColumnScope.StatusBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val key: Long? = when (val raw = snapshot?.raw) {
        is RawValue.Integer -> raw.bits
        is RawValue.Flag -> if (raw.value) 1L else 0L
        else -> null
    }
    val mapping = key?.let { k -> widget.states.firstOrNull { it.value == k } }
    val ok = snapshot?.quality == Quality.OK
    val (text, color) = when {
        snapshot?.raw == null -> "—" to look.valueColor
        mapping != null -> mapping.label to (if (ok && LocalEditCardSize.current == null) tone(colors, mapping.tone) else look.valueColor)
        key != null -> stringResource(R.string.panel_state_undefined, key.toInt()) to (if (ok) colors.muted else look.valueColor)
        else -> (snapshot.text ?: "—") to look.valueColor
    }
    Text(
        text,
        fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp).testTag("panel-value-${widget.id}")
            .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
    )
    val meta = if (ok && key != null) stringResource(R.string.panel_meta_state_value, addressLabel(point), key.toInt()) else look.meta
    if (look.meta.isNotEmpty()) MetaRow(look.copy(meta = meta))
}

@Composable
private fun ColumnScope.ProgressBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    val min = point.rangeMin ?: 0.0
    val max = point.rangeMax ?: 100.0
    val value = snapshot?.engineering
    val fraction = if (value == null || snapshot.raw == null) 0f else ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val valueText = if (snapshot?.raw == null) "—" else (snapshot.text ?: "") + point.unitLabel.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    NameRow(name, look.badge) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (look.badge != null) ToneBadge(look.badge.first, look.badge.second, Modifier.testTag("panel-badge"))
            Text(
                valueText,
                style = Mono.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = look.valueColor,
                modifier = Modifier.testTag("panel-value-${widget.id}")
                    .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
            )
        }
    }
    val fill = when {
        snapshot?.quality != Quality.OK -> colors.dim
        snapshot.level == ThresholdLevel.ALERT -> colors.error
        else -> colors.tx
    }
    val barShape = RoundedCornerShape(5.dp)
    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(PROGRESS_BAR_H).clip(barShape).background(colors.surface2).testTag("panel-bar-${widget.id}")) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(PROGRESS_BAR_H).clip(barShape).background(fill))
        }
    }
    MetaRow(look, topPadding = 7)
}

internal val PROGRESS_BAR_H = 10.dp

/**
 * 仪表（半圆）：8dp 底轨 + 值弧（正常 = tx、预警 = 琥珀、告警 = 红、非正常数据 = 灰）；
 * 阈值分段画在底轨外侧一圈细带上（预警 / 告警区间，随量程换算，不被值弧盖住）；中间下方是当前值，两端是量程。
 * 值在量程外：弧停在端点（读数仍显示真实值）。没有数据时只画底轨和分段。
 */
@Composable
private fun ColumnScope.GaugeBody(widget: Widget, point: PointDefinition, name: String, snapshot: PointSnapshot?, look: Look) {
    val colors = LocalModbusColors.current
    NameRow(name, look.badge)
    val min = point.rangeMin ?: 0.0
    val max = point.rangeMax ?: 100.0
    val span = if (max > min) max - min else 1.0
    val value = snapshot?.engineering
    val hasValue = value != null && snapshot?.raw != null
    val fraction = if (!hasValue) 0f else ((value!! - min) / span).coerceIn(0.0, 1.0).toFloat()
    val ok = snapshot?.quality == Quality.OK
    val arcColor = when {
        !ok -> colors.dim
        snapshot?.level == ThresholdLevel.ALERT -> colors.error
        snapshot?.level == ThresholdLevel.WARN -> colors.txText
        else -> colors.tx
    }
    val track = colors.surface3
    val warn = colors.txText
    val alert = colors.error
    val bands = point.thresholds.filter { it.level != ThresholdLevel.NORMAL }.sortedBy { it.level.ordinal }
    val valueText = if (!hasValue) "—" else snapshot?.text ?: ""
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = Mono.copy(fontSize = 10.sp, lineHeight = 13.sp, color = colors.dim)
    val minLabel = ValueCodec.format(min, null).orEmpty()
    val maxLabel = ValueCodec.format(max, null).orEmpty()
    val labelBand = 15.dp
    val gaugeDesc = stringResource(R.string.panel_gauge_desc, minLabel, maxLabel)
    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(78.dp).testTag("panel-gauge-${widget.id}").semantics { contentDescription = gaugeDesc }) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val bandStroke = 3.dp.toPx()
            val bandGap = 4.dp.toPx()
            val outer = stroke / 2 + bandGap + bandStroke
            val baseY = size.height - labelBand.toPx() - stroke / 2f
            val radius = minOf(size.width / 2f - outer, baseY - bandStroke - bandGap - stroke / 2f).coerceAtLeast(1f)
            val center = Offset(size.width / 2f, baseY)
            fun arc(color: Color, from: Float, to: Float, r: Float, width: Float, round: Boolean) {
                if (to <= from) return
                drawArc(
                    color, startAngle = 180f + 180f * from, sweepAngle = 180f * (to - from), useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width, cap = if (round) StrokeCap.Round else StrokeCap.Butt),
                )
            }
            arc(track, 0f, 1f, radius, stroke, round = true)
            bands.forEach { b ->
                val from = ((b.min ?: min) - min).div(span).coerceIn(0.0, 1.0).toFloat()
                val to = ((b.max ?: max) - min).div(span).coerceIn(0.0, 1.0).toFloat()
                arc(if (b.level == ThresholdLevel.ALERT) alert else warn, from, to, radius + stroke / 2 + bandGap + bandStroke / 2, bandStroke, round = false)
            }
            if (fraction > 0f) arc(arcColor, 0f, fraction, radius, stroke, round = true)
            // 量程两端：画在底轨两端的正下方
            val y = size.height - labelBand.toPx() + 2.dp.toPx()
            val l = measurer.measure(minLabel, labelStyle)
            val r = measurer.measure(maxLabel, labelStyle)
            drawText(l, topLeft = Offset((center.x - radius - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), y))
            drawText(r, topLeft = Offset((center.x + radius - r.size.width / 2f).coerceIn(0f, size.width - r.size.width), y))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = labelBand + 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                valueText,
                style = Mono.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                color = look.valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().testTag("panel-value-${widget.id}")
                    .then(if (look.strike) Modifier.redStrike(colors.error) else Modifier),
            )
            if (point.unitLabel.isNotBlank() && hasValue) {
                Text(point.unitLabel, color = colors.muted, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.alignByBaseline())
            }
        }
    }
    MetaRow(look, topPadding = 4)
}

// ---- 工具 ----

internal fun Widget.displayName(point: PointDefinition) = title.ifBlank { point.name }

private fun tone(colors: ModbusColors, tone: StateTone): Color = when (tone) {
    StateTone.GREEN -> colors.rxLocal
    StateTone.AMBER -> colors.txText
    StateTone.RED -> colors.error
    StateTone.BLUE -> colors.foreign
    StateTone.GRAY -> colors.muted
}

@Composable
private fun flagText(snapshot: PointSnapshot): String =
    if (snapshot.engineering == 1.0) stringResource(R.string.panel_flag_on) else stringResource(R.string.panel_flag_off)

/** 稿面“超时”卡：值 dim，删除线是红色（Compose 的 TextDecoration 不能单独着色，自己画）。 */
private fun Modifier.redStrike(color: Color): Modifier = drawWithContent {
    drawContent()
    val y = size.height * 0.56f
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
}

internal fun addressLabel(point: PointDefinition): String {
    val prefix = when (point.area) {
        RegisterArea.COIL -> "C"
        RegisterArea.DISCRETE_INPUT -> "DI"
        RegisterArea.INPUT_REGISTER -> "IR"
        RegisterArea.HOLDING_REGISTER -> "HR"
    }
    return "$prefix${point.address}"
}

private fun rangeLabel(point: PointDefinition): String {
    val min = ValueCodec.format(point.rangeMin ?: 0.0, null)
    val max = ValueCodec.format(point.rangeMax ?: 100.0, null)
    return "$min–$max"
}

private val ClockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

internal fun clock(wallMs: Long): String = ClockFormat.format(Instant.ofEpochMilli(wallMs).atZone(ZoneId.systemDefault()))
