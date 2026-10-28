package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveField
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveMath
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveResult
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries
import io.github.xingyingyuzhui.modbusmobile.application.panel.FormError
import io.github.xingyingyuzhui.modbusmobile.application.panel.GapStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.LegendPosition
import io.github.xingyingyuzhui.modbusmobile.application.panel.LineStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.SamplingMode
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.SeriesForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.YAxis
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChoiceTile
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.FieldTileMenu
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.RowDivider
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingTrailing
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingsCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge
import io.github.xingyingyuzhui.modbusmobile.ui.curve.CurveCardBody
import io.github.xingyingyuzhui.modbusmobile.ui.curve.rememberCurveNow
import io.github.xingyingyuzhui.modbusmobile.ui.curve.seriesColor
import io.github.xingyingyuzhui.modbusmobile.ui.theme.JetBrainsMono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors

private val WindowChoices = listOf(60, 300, 600, 3600)

// ---- 选类型卡里的小图示（稿面 b2-3：橙 + 蓝两条折线）----

// ---- 2/3 曲线配置（b2-5）----

@Composable
internal fun CurveConfigStep(
    wizard: WizardState,
    result: CurveResult,
    history: SampleHistory?,
    live: Boolean,
    onCurve: (CurveForm) -> Unit,
    onAddSeries: () -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    val curve = wizard.curve ?: return
    // v3 3-4：第 2 步只管“画哪些点、多长时间”：预览、系列、时间窗口、采样与内存（折叠）；坐标轴 / 图例 / 缺测 / 游标在第 3 步
    CurvePreviewCard(curve, history, live)
    SeriesList(wizard, result, onAddSeries, onOpenSeries)
    ModbusCard(Modifier.fillMaxWidth()) { WindowSection(wizard, result, onCurve) }
    val memoryError = wizard.showErrors && (result.errors[CurveField.RETENTION] != null || result.errors[CurveField.MAX_POINTS] != null)
    CollapsibleBlock(
        stringResource(R.string.panel_wiz_curve_sampling_memory),
        stringResource(
            R.string.panel_wiz_curve_sampling_summary,
            stringResource(if (curve.sampling == SamplingMode.EVERY_SAMPLE) R.string.panel_wiz_curve_sampling_every else R.string.panel_wiz_curve_sampling_snapshot),
            curve.retentionHours.ifBlank { "—" },
        ),
        tag = "wiz-sampling-block", forceOpen = memoryError,
    ) { SamplingSection(wizard, result, onCurve) }
}

/** 预览：真实的曲线卡（强调色描边）；还没有能成立的系列时画只有标题的占位。 */
@Composable
internal fun CurvePreviewCard(curve: CurveForm, history: SampleHistory?, live: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val config = curve.previewConfig()
    Column(modifier.fillMaxWidth().testTag("wiz-preview"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (config != null) {
            val now = rememberCurveNow()
            val frame = remember(config, now, history, live) {
                CurveMath.frame(config, now) { id, from -> if (live) history?.read(id, from) ?: SampleHistory.Slice.EMPTY else SampleHistory.Slice.EMPTY }
            }
            CurveCardBody(
                // 稿面 3-4：预览卡标题是“实时预览 · 1 分钟”，系列名在图例里
                stringResource(R.string.panel_wiz_curve_preview_title), "preview", config, frame,
                Modifier.fillMaxWidth().height(CurvePreviewHeight), borderColor = colors.accent,
                // 规范 65：预览卡要一眼看出是预览
                previewBadge = stringResource(R.string.panel_wiz_preview_badge),
            )
        } else {
            CardFrame(Modifier.fillMaxWidth().height(CurvePreviewHeight), "wiz-preview-placeholder", borderColor = colors.accent) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.panel_wiz_kind_curve), color = colors.muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                    ToneBadge(stringResource(R.string.panel_wiz_preview_badge), colors.txText, Modifier.testTag("wiz-preview-badge"))
                }
                Text(
                    stringResource(R.string.panel_wiz_curve_preview_empty), color = colors.dim, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (!live) {
            Text(
                stringResource(R.string.panel_wiz_preview_none), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.testTag("wiz-preview-none"),
            )
        }
    }
}

private val CurvePreviewHeight = 150.dp

@Composable
private fun SeriesList(wizard: WizardState, result: CurveResult, onAdd: () -> Unit, onOpen: (Int) -> Unit) {
    val colors = LocalModbusColors.current
    val curve = wizard.curve ?: return
    val title: @Composable () -> Unit = {
        // 稿面 v3 3-4：系列数量是块内标题（“系列 2 / 8”），不是卡片外面的小标题
        Text(
            stringResource(R.string.panel_wiz_curve_series, curve.series.size, CurveConfig.MAX_SERIES),
            color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).testTag("wiz-series-title"),
        )
    }
    val canAdd = curve.series.size < CurveConfig.MAX_SERIES
    val add = stringResource(R.string.panel_wiz_curve_add_series)
    val addRow: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = add; role = Role.Button }
                .clickable(onClick = onAdd)
                .padding(horizontal = 12.dp)
                .testTag("wiz-series-add"),
            contentAlignment = Alignment.CenterStart,
        ) { Text("＋ $add", color = colors.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
    if (curve.series.isEmpty()) {
        val need = wizard.showErrors && result.errors[CurveField.SERIES] != null
        // 空列表也先写“系列 0 / 8”，再写原因，最后是“＋ 添加系列”
        SettingsCard {
            title()
            Text(
                stringResource(if (need) R.string.panel_wiz_curve_series_need else R.string.panel_wiz_curve_series_empty),
                color = if (need) colors.error else colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("wiz-series-empty"),
            )
            if (canAdd) { RowDivider(); addRow() }
        }
        return
    }
    SettingsCard {
        title()
        curve.series.forEachIndexed { index, s ->
            if (index > 0) RowDivider()
            SeriesRow(index, s, wizard.showErrors && index in result.badSeries) { onOpen(index) }
        }
        if (canAdd) { RowDivider(); addRow() }
    }
}

@Composable
private fun SeriesRow(index: Int, s: SeriesForm, bad: Boolean, onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    val axisText = stringResource(if (s.axis == YAxis.LEFT) R.string.panel_wiz_curve_axis_left else R.string.panel_wiz_curve_axis_right)
    val sub = remember(s) { seriesSubline(s) }
    val shown = s.name.ifBlank { stringResource(R.string.panel_wiz_series_unnamed) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("wiz-series-row-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(seriesColor(s.colorIndex, colors.isDark)))
        Column(Modifier.weight(1f)) {
            Text(shown, color = if (bad) colors.error else colors.text, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 1)
            Text(
                if (bad) stringResource(R.string.panel_wiz_curve_series_bad) else sub,
                color = if (bad) colors.error else colors.muted, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = JetBrainsMono, maxLines = 1,
                modifier = Modifier.testTag("wiz-series-sub-$index"),
            )
        }
        // v3 3-4：行内不放线宽 / 线型样例（颜色圆点已经能区分系列），轴标签是独立小标签；
        // 线型 / 粗细只在系列浮层里改。
        Box(
            Modifier
                .border(1.dp, colors.line, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(axisText, color = colors.muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.testTag("wiz-series-axis-$index"))
        }
        Text("›", color = colors.dim, fontSize = 18.sp)
    }
}

/** “U1 HR100 · S16 ×0.1 ℃”：这个系列读什么、怎么换算（表单没填完整时尽量照填的写）。 */
internal fun seriesSubline(s: SeriesForm): String {
    val f = s.point
    val prefix = when (f.area) {
        io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea.COIL -> "C"
        io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea.DISCRETE_INPUT -> "DI"
        io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea.INPUT_REGISTER -> "IR"
        io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea.HOLDING_REGISTER -> "HR"
    }
    val scale = f.scale.trim()
    val scaleText = if (f.usesConversion && scale.isNotEmpty() && scale != "1" && f.type != io.github.xingyingyuzhui.modbusmobile.application.panel.DataType.ASCII) " ×$scale" else ""
    val unit = if (f.unitLabel.isNotBlank()) " ${f.unitLabel}" else ""
    return "U${f.unitId} $prefix${f.address} · ${f.type.name}$scaleText$unit"
}

@Composable
private fun WindowSection(wizard: WizardState, result: CurveResult, onCurve: (CurveForm) -> Unit) {
    val curve = wizard.curve ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 预设和「自定义…」都在一个下拉格里（同连接页串口格）；选自定义才出分钟输入框。
        FieldTileMenu(
            stringResource(R.string.panel_wiz_curve_window),
            curve.windowSec,
            WindowChoices + listOf<Int?>(null),
            { sec -> stringResource(if (sec == null) R.string.panel_wiz_curve_w_custom else windowChipLabel(sec)) },
            { sec ->
                onCurve(
                    if (sec != null) {
                        curve.copy(windowSec = sec)
                    } else {
                        curve.copy(
                            windowSec = null,
                            customMinutes = curve.customMinutes.ifBlank { ((curve.windowSec ?: 60) / 60).coerceAtLeast(1).toString() },
                        )
                    },
                )
            },
            Modifier.fillMaxWidth().testTag("wiz-window"),
            mono = false,
            optionTag = { sec -> if (sec == null) "wiz-window-custom" else "wiz-window-$sec" },
        )
        if (curve.windowSec == null) {
            NumberInput(
                stringResource(R.string.panel_wiz_curve_window_minutes), curve.customMinutes, Modifier.fillMaxWidth().testTag("wiz-window-minutes"),
                sub = stringResource(R.string.panel_wiz_curve_minutes_unit), error = curveError(wizard, result, CurveField.WINDOW),
            ) { onCurve(curve.copy(customMinutes = it.filter(Char::isDigit).take(4))) }
        }
    }
}

@Composable
private fun SamplingSection(wizard: WizardState, result: CurveResult, onCurve: (CurveForm) -> Unit) {
    val colors = LocalModbusColors.current
    val curve = wizard.curve ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.panel_wiz_curve_sampling), color = colors.muted, fontSize = 12.sp)
        SegmentedTextControl(
            SamplingMode.entries, curve.sampling, { onCurve(curve.copy(sampling = it)) }, Modifier.testTag("wiz-sampling"), small = true,
        ) { stringResource(if (it == SamplingMode.EVERY_SAMPLE) R.string.panel_wiz_curve_sampling_every else R.string.panel_wiz_curve_sampling_snapshot) }
        Text(
            stringResource(R.string.panel_wiz_curve_sampling_note), color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp,
            modifier = Modifier.testTag("wiz-sampling-note"),
        )
        if (curve.sampling == SamplingMode.SNAPSHOT) {
            val step = remember(curve.series) { snapshotStepText(curve) }
            Text(
                stringResource(R.string.panel_wiz_curve_snapshot_note, step), color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp,
                modifier = Modifier.testTag("wiz-snapshot-note"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput(
                stringResource(R.string.panel_wiz_curve_retention), curve.retentionHours, Modifier.weight(1f).testTag("wiz-retention"),
                sub = stringResource(R.string.panel_wiz_curve_hours_unit), keyboardType = KeyboardType.Decimal,
                error = curveError(wizard, result, CurveField.RETENTION),
            ) { onCurve(curve.copy(retentionHours = it.filter { c -> c.isDigit() || c == '.' }.take(6))) }
            NumberInput(
                stringResource(R.string.panel_wiz_curve_max_points), curve.maxPoints, Modifier.weight(1f).testTag("wiz-max-points"),
                sub = stringResource(R.string.panel_wiz_curve_points_unit), error = curveError(wizard, result, CurveField.MAX_POINTS),
            ) { onCurve(curve.copy(maxPoints = it.filter(Char::isDigit).take(6))) }
        }
        Text(stringResource(R.string.panel_wiz_curve_memory_note), color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp)
    }
}

private fun windowChipLabel(sec: Int): Int = when (sec) {
    60 -> R.string.panel_wiz_curve_w_1m
    300 -> R.string.panel_wiz_curve_w_5m
    600 -> R.string.panel_wiz_curve_w_10m
    else -> R.string.panel_wiz_curve_w_1h
}

private fun snapshotStepText(curve: CurveForm): String {
    val ms = maxOf(1000L, curve.series.maxOfOrNull { it.point.pollMs.trim().toLongOrNull() ?: 1000L } ?: 1000L)
    return if (ms % 1000 == 0L) "${ms / 1000} 秒" else "${ms} 毫秒"
}

@Composable
private fun AxesSection(wizard: WizardState, result: CurveResult, onCurve: (CurveForm) -> Unit) {
    val colors = LocalModbusColors.current
    val curve = wizard.curve ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AxisBlock(wizard, result, YAxis.LEFT, onCurve)
        if (curve.usesAxis(YAxis.RIGHT)) AxisBlock(wizard, result, YAxis.RIGHT, onCurve)
        SettingRow(
            stringResource(R.string.panel_wiz_curve_grid), Modifier.testTag("wiz-grid"), trailing = SettingTrailing.SWITCH, checked = curve.grid,
            onChecked = { onCurve(curve.copy(grid = it)) },
        )
    }
}

@Composable
private fun AxisBlock(wizard: WizardState, result: CurveResult, side: YAxis, onCurve: (CurveForm) -> Unit) {
    val colors = LocalModbusColors.current
    val curve = wizard.curve ?: return
    val left = side == YAxis.LEFT
    val auto = if (left) curve.leftAuto else curve.rightAuto
    val unit = curve.axisUnit(side)
    val sideName = stringResource(if (left) R.string.panel_wiz_curve_axis_name_left else R.string.panel_wiz_curve_axis_name_right)
    val prefix = if (left) "left" else "right"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (unit.isEmpty()) stringResource(R.string.panel_wiz_curve_axis_title, sideName) else stringResource(R.string.panel_wiz_curve_axis_title_unit, sideName, unit),
                color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f).testTag("wiz-$prefix-title"),
            )
            val autoLabel = stringResource(R.string.panel_wiz_curve_auto)
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { role = Role.Switch; selected = auto; contentDescription = autoLabel }
                    .clickable {
                        onCurve(if (left) curve.copy(leftAuto = !curve.leftAuto) else curve.copy(rightAuto = !curve.rightAuto))
                    }
                    .padding(horizontal = 6.dp)
                    .testTag("wiz-$prefix-auto"),
                contentAlignment = Alignment.CenterEnd,
            ) { Text(if (auto) "$autoLabel ✓" else autoLabel, color = if (auto) colors.accentText else colors.muted, fontSize = 13.sp) }
        }
        if (!auto) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(
                    stringResource(R.string.panel_wiz_range_min), if (left) curve.leftMin else curve.rightMin, Modifier.weight(1f).testTag("wiz-$prefix-min"),
                    keyboardType = KeyboardType.Decimal, error = curveError(wizard, result, if (left) CurveField.LEFT_MIN else CurveField.RIGHT_MIN),
                ) { onCurve(if (left) curve.copy(leftMin = it) else curve.copy(rightMin = it)) }
                NumberInput(
                    stringResource(R.string.panel_wiz_range_max), if (left) curve.leftMax else curve.rightMax, Modifier.weight(1f).testTag("wiz-$prefix-max"),
                    keyboardType = KeyboardType.Decimal, error = curveError(wizard, result, if (left) CurveField.LEFT_MAX else CurveField.RIGHT_MAX),
                ) { onCurve(if (left) curve.copy(leftMax = it) else curve.copy(rightMax = it)) }
            }
        }
    }
}

@Composable
private fun DisplaySection(curve: CurveForm, onCurve: (CurveForm) -> Unit) {
    val colors = LocalModbusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.panel_wiz_curve_legend), color = colors.muted, fontSize = 12.sp)
        SegmentedTextControl(
            LegendPosition.entries, curve.legend, { onCurve(curve.copy(legend = it)) }, Modifier.testTag("wiz-legend"), small = true,
        ) {
            stringResource(
                when (it) {
                    LegendPosition.TOP -> R.string.panel_wiz_curve_legend_top
                    LegendPosition.BOTTOM -> R.string.panel_wiz_curve_legend_bottom
                    LegendPosition.HIDDEN -> R.string.panel_wiz_curve_legend_hidden
                },
            )
        }
        Text(stringResource(R.string.panel_wiz_curve_gaps), color = colors.muted, fontSize = 12.sp)
        SegmentedTextControl(
            GapStyle.entries, curve.gaps, { onCurve(curve.copy(gaps = it)) }, Modifier.testTag("wiz-gaps"), small = true,
        ) { stringResource(if (it == GapStyle.HATCH) R.string.panel_wiz_curve_gaps_hatch else R.string.panel_wiz_curve_gaps_break) }
        SettingRow(
            stringResource(R.string.panel_wiz_curve_cursor), Modifier.testTag("wiz-cursor"), trailing = SettingTrailing.SWITCH, checked = curve.cursor,
            onChecked = { onCurve(curve.copy(cursor = it)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
internal fun curveError(wizard: WizardState, result: CurveResult, field: CurveField): String? {
    val err = result.errors[field] ?: return null
    if (err == FormError.REQUIRED && !wizard.showErrors) return null
    return stringResource(
        when (err) {
            FormError.REQUIRED -> R.string.panel_wiz_err_required
            FormError.NOT_A_NUMBER -> R.string.panel_wiz_err_number
            FormError.NOT_INTEGER -> R.string.panel_wiz_err_integer
            FormError.OUT_OF_RANGE -> when (field) {
                CurveField.WINDOW -> R.string.panel_wiz_curve_err_window
                CurveField.RETENTION -> R.string.panel_wiz_curve_err_retention
                CurveField.MAX_POINTS -> R.string.panel_wiz_curve_err_points
                else -> R.string.panel_wiz_err_number
            }
            FormError.RANGE_ORDER -> R.string.panel_wiz_err_range_order
            FormError.EXCEEDS_RETENTION -> R.string.panel_wiz_curve_err_exceeds
            FormError.TOO_MANY -> R.string.panel_wiz_curve_err_too_many
            else -> R.string.panel_wiz_err_number
        },
    )
}

// ---- 系列编辑底部弹窗（b2-6）----

/**
 * 系列编辑：页内浮层（遮罩 + 贴底面板），不是系统对话框窗口，所以里面的输入框和普通页面一样好用。
 * 点遮罩 / 返回键 / “完成”都只是收起；改动实时写进向导表单。
 */
@Composable
internal fun SeriesSheet(
    index: Int,
    series: SeriesForm,
    onChange: ((SeriesForm) -> SeriesForm) -> Unit,
    onOpenPoint: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Box(Modifier.fillMaxSize().testTag("wiz-sheet")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .testTag("wiz-sheet-scrim"),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(shape)
                .background(colors.surface1)
                .border(1.dp, colors.line, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(colors.line))
            // 内容可滚动，“完成”钉在底部（小屏 / 大字体也点得到）
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.panel_wiz_series_title, series.name.ifBlank { stringResource(R.string.panel_wiz_series_unnamed) }),
                    color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f).testTag("wiz-sheet-title"),
                )
                val delete = stringResource(R.string.panel_wiz_series_delete)
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = delete; role = Role.Button }
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 6.dp)
                        .testTag("wiz-series-delete"),
                    contentAlignment = Alignment.Center,
                ) { Text(delete, color = colors.error, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(
                    stringResource(R.string.panel_wiz_series_name), series.name, Modifier.weight(1f).testTag("wiz-series-name"),
                    keyboardType = KeyboardType.Text,
                ) { text -> onChange { it.copy(point = it.point.copy(name = text.take(WidgetForm.MAX_NAME))) } }
                ChoiceTile(
                    stringResource(R.string.panel_wiz_series_point), seriesPointText(series) + " ›", onOpenPoint,
                    Modifier.weight(1f).testTag("wiz-series-point"), mono = true,
                )
            }
            Text(stringResource(R.string.panel_wiz_series_color), color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            ColorPicker(series.colorIndex) { c -> onChange { it.copy(colorIndex = c) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.panel_wiz_series_style), color = colors.muted, fontSize = 12.sp)
                    SegmentedTextControl(LineStyle.entries, series.lineStyle, { s -> onChange { it.copy(lineStyle = s) } }, Modifier.testTag("wiz-series-style"), small = true) {
                        stringResource(
                            when (it) {
                                LineStyle.SOLID -> R.string.panel_wiz_series_style_solid
                                LineStyle.DASH -> R.string.panel_wiz_series_style_dash
                                LineStyle.DOT -> R.string.panel_wiz_series_style_dot
                            },
                        )
                    }
                }
                Column(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.panel_wiz_series_width), color = colors.muted, fontSize = 12.sp)
                    SegmentedTextControl(listOf(1, 2, 3), series.width, { w -> onChange { it.copy(width = w) } }, Modifier.testTag("wiz-series-width"), small = true) { it.toString() }
                }
            }
            Text(stringResource(R.string.panel_wiz_series_axis), color = colors.muted, fontSize = 12.sp)
            SegmentedTextControl(YAxis.entries, series.axis, { a -> onChange { it.copy(axis = a) } }, Modifier.testTag("wiz-series-axis"), small = true) {
                stringResource(if (it == YAxis.LEFT) R.string.panel_wiz_series_axis_left else R.string.panel_wiz_series_axis_right)
            }
            SettingsCard {
                SettingRow(
                    stringResource(R.string.panel_wiz_series_points), Modifier.testTag("wiz-series-points"), trailing = SettingTrailing.SWITCH,
                    checked = series.showPoints, onChecked = { v -> onChange { it.copy(showPoints = v) } },
                )
                RowDivider()
                SettingRow(
                    stringResource(R.string.panel_wiz_series_fill), Modifier.testTag("wiz-series-fill"), trailing = SettingTrailing.SWITCH,
                    checked = series.fill, onChecked = { v -> onChange { it.copy(fill = v) } },
                )
                RowDivider()
                SettingRow(
                    stringResource(R.string.panel_wiz_series_step), Modifier.testTag("wiz-series-step"), trailing = SettingTrailing.SWITCH,
                    checked = series.step, onChecked = { v -> onChange { it.copy(step = v) } },
                )
            }
            }
            PrimaryButton(stringResource(R.string.panel_edit_done), onClose, Modifier.fillMaxWidth().testTag("wiz-series-done"))
        }
    }
}

private fun seriesPointText(s: SeriesForm): String = seriesSubline(s).substringBefore(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    val colors = LocalModbusColors.current
    FlowRow(Modifier.fillMaxWidth().testTag("wiz-series-colors")) {
        for (i in 0 until CurveSeries.COLOR_COUNT) {
            val on = i == selected
            val cd = stringResource(R.string.panel_wiz_series_color_cd, i + 1)
            Box(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = cd; role = Role.RadioButton; this.selected = on }
                    .clickable { onPick(i) }
                    .testTag("wiz-series-color-$i"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .then(if (on) Modifier.border(2.dp, colors.text, CircleShape).padding(3.dp) else Modifier)
                        .clip(CircleShape)
                        .background(seriesColor(i, colors.isDark)),
                )
            }
        }
    }
}

// ---- 系列的“数据点位”页（弹窗里点“数据点位 ›”进入）----

/** 把系列的点位表单包成“单点位向导状态”，复用数据源 / 换算两个区块。 */
internal fun seriesPointWizard(wizard: WizardState, series: SeriesForm): WizardState =
    WizardState(2, series.point, "series", series.pointId, showErrors = wizard.showErrors)

@Composable
internal fun SeriesPointPage(
    wizard: WizardState,
    series: SeriesForm,
    snapshot: PointSnapshot?,
    live: Boolean,
    onChange: ((SeriesForm) -> SeriesForm) -> Unit,
) {
    val inner = seriesPointWizard(wizard, series)
    val result = inner.result()
    val onForm: (WidgetForm) -> Unit = { f -> onChange { it.copy(point = f) } }
    PreviewCard(inner, result, snapshot, live)
    SectionTitle(stringResource(R.string.panel_wiz_sec_source))
    ModbusCard(Modifier.fillMaxWidth()) { SourceSection(inner, result, onForm) }
    SectionTitle(stringResource(R.string.panel_wiz_sec_convert))
    ModbusCard(Modifier.fillMaxWidth()) { ConvertSection(inner, result, snapshot, onForm, compact = true) }
    SectionTitle(stringResource(R.string.panel_wiz_sec_poll))
    ModbusCard(Modifier.fillMaxWidth()) {
        NumberInput(
            stringResource(R.string.panel_wiz_poll), series.point.pollMs, Modifier.fillMaxWidth().testTag("wiz-poll"),
            sub = stringResource(R.string.panel_wiz_poll_unit),
            error = pollError(inner, result),
        ) { onForm(series.point.copy(pollMs = it.filter(Char::isDigit).take(6))) }
    }
}

@Composable
private fun pollError(wizard: WizardState, result: io.github.xingyingyuzhui.modbusmobile.application.panel.FormResult): String? =
    fieldError(wizard, result, io.github.xingyingyuzhui.modbusmobile.application.panel.FormField.POLL)

// ---- 3/3 外观：曲线只有尺寸（2×1 / 2×2）----

private val CurveSizes = listOf(1, 2)

@Composable
internal fun CurveLookStep(
    wizard: WizardState,
    result: CurveResult,
    history: SampleHistory?,
    live: Boolean,
    onCurve: (CurveForm) -> Unit,
) {
    val colors = LocalModbusColors.current
    val curve = wizard.curve ?: return
    val widget = result.widget
    val sizeLabel = "2 × ${curve.spanRows}"
    val config = widget?.curve
    if (widget != null && config != null) {
        // 稿面 3-3：预览放在一格“假网格”里（虚线边 + 6dp 内边距），按选中尺寸显示
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.fillMaxWidth().clip(shape).background(colors.bg)
                .drawBehind {
                    drawRoundRect(
                        colors.line, cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .padding(6.dp)
                .testTag("wiz-look-preview"),
        ) {
            PanelGrid(
                columns = 2, spans = listOf(2), spanRows = listOf(curve.spanRows),
                modifier = Modifier.fillMaxWidth(), gap = 6.dp,
            ) {
                val now = rememberCurveNow()
                val frame = remember(config, now, history, live) {
                    CurveMath.frame(config, now) { id, from -> if (live) history?.read(id, from) ?: SampleHistory.Slice.EMPTY else SampleHistory.Slice.EMPTY }
                }
                CurveCardBody(
                    widget.displayTitle, "look", config, frame, Modifier, borderColor = colors.accent,
                    previewBadge = stringResource(R.string.panel_wiz_preview_badge),
                )
            }
        }
        Text(
            stringResource(R.string.panel_wiz_actual_size, sizeLabel), color = colors.muted, fontSize = 12.sp,
            modifier = Modifier.testTag("wiz-actual-size"),
        )
    }
    ModbusCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.panel_wiz_size), color = colors.muted, fontSize = 12.sp)
            SegmentedTextControl(CurveSizes, curve.spanRows, { onCurve(curve.copy(spanRows = it)) }, Modifier.testTag("wiz-size"), small = true) { "2 × $it" }
        }
    }
    SectionTitle(stringResource(R.string.panel_wiz_curve_sec_axes))
    ModbusCard(Modifier.fillMaxWidth()) { AxesSection(wizard, result, onCurve) }
    SectionTitle(stringResource(R.string.panel_wiz_curve_sec_display))
    ModbusCard(Modifier.fillMaxWidth()) { DisplaySection(curve, onCurve) }
    Text(
        stringResource(R.string.panel_wiz_curve_size_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.testTag("wiz-size-note"),
    )
}
