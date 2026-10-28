package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.ByteWordOrder
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.FormError
import io.github.xingyingyuzhui.modbusmobile.application.panel.FormField
import io.github.xingyingyuzhui.modbusmobile.application.panel.FormResult
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateRow
import io.github.xingyingyuzhui.modbusmobile.application.panel.StateTone
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdLevel
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdOp
import io.github.xingyingyuzhui.modbusmobile.application.panel.ThresholdRow
import io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusSwitchVisual
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusMenuItem
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusMenu
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetWrites
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingTrailing
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.SubPageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.JetBrainsMono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.components.LocalErrorReveal
import io.github.xingyingyuzhui.modbusmobile.ui.components.rememberErrorReveal

data class WizardActions(
    val onKind: (String) -> Unit = {},
    val onForm: (WidgetForm) -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onExit: () -> Unit = {},
    val onCurve: (CurveForm) -> Unit = {},
    val onAddSeries: () -> Unit = {},
    val onOpenSeries: (Int) -> Unit = {},
    val onOpenSeriesPoint: () -> Unit = {},
    val onDeleteSeries: (Int) -> Unit = {},
)

private val Areas = listOf(RegisterArea.HOLDING_REGISTER, RegisterArea.INPUT_REGISTER, RegisterArea.COIL, RegisterArea.DISCRETE_INPUT)

/** 开关 / 按钮要写设备，只能选线圈或保持寄存器（输入类区域只读）。 */
private val WritableAreas = listOf(RegisterArea.COIL, RegisterArea.HOLDING_REGISTER)
private val UnitPresets = listOf("℃", "%", "%RH", "MPa", "bar", "A", "V", "kW", "Hz", "rpm")
private val Sizes = listOf(1 to 1, 2 to 1, 2 to 2)
private val Tones = listOf(StateTone.GREEN, StateTone.AMBER, StateTone.RED, StateTone.BLUE, StateTone.GRAY)

/**
 * 添加 / 编辑组件向导（稿面 b2-3 / b2-4 / 外观页）：1/3 选类型 → 2/3 数据源 + 换算（按类型取舍）+ 实时预览 → 3/3 外观（尺寸 + 实际效果）。
 * 在编辑草稿上操作：“添加到面板”只把组件放进草稿，回到编辑页点“完成”才写盘。
 */
@Composable
fun PanelWizard(
    wizard: WizardState,
    previewSnapshot: PointSnapshot?,
    live: Boolean,
    actions: WizardActions,
    /** 曲线向导的预览样本历史 / 每个系列点位的预览读数（key = 点位 id）。 */
    previewHistory: SampleHistory? = null,
    seriesSnapshots: Map<String, PointSnapshot> = emptyMap(),
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    // 第 2 步只显示数据类字段的错误（名称、阈值等在第 3 步，见 WizardState.stepResult）
    val result = remember(wizard.form, wizard.widgetId, wizard.pointId, wizard.step) { wizard.stepResult() }
    var confirmDiscard by remember { mutableStateOf(false) }
    val firstStep = if (wizard.editingWidgetId != null) 2 else 1
    val needsConfirm = wizard.changed
    val curveResult = remember(wizard.curve, wizard.widgetId) { if (wizard.curve != null) wizard.curveResult() else null }
    val goBack: () -> Unit = {
        when {
            wizard.pointPage || wizard.sheet != null || wizard.step > firstStep -> actions.onBack()
            needsConfirm -> confirmDiscard = true
            else -> actions.onExit()
        }
    }
    BackHandler(onBack = goBack)

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SubPageHeader(
            title = if (wizard.pointPage) stringResource(R.string.panel_wiz_series_point_title) else stepTitle(wizard),
            onBack = goBack,
            modifier = Modifier.padding(start = pageStart(), end = spacing.page),
        )
        if (!wizard.pointPage) {
            StepBar(wizard.step, Modifier.padding(start = pageStart(), end = spacing.page, bottom = 8.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = pageStart(), end = spacing.page, top = 4.dp, bottom = spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            CompositionLocalProvider(LocalErrorReveal provides rememberErrorReveal(wizard.errorAttempt)) {
                val curve = wizard.curve
                val sheetSeries = wizard.sheet?.let { curve?.series?.getOrNull(it) }
                when {
                    wizard.pointPage && sheetSeries != null && curve != null -> SeriesPointPage(
                        wizard, sheetSeries, seriesSnapshots[sheetSeries.pointId], live,
                    ) { transform -> actions.onCurve(curve.updateSeries(wizard.sheet!!, transform)) }
                    wizard.step == 1 -> GroupedKindPicker(
                        (if (wizard.curve != null) WidgetKinds.CURVE else wizard.form.kind).takeIf { wizard.editingWidgetId != null }, actions.onKind,
                    )
                    curve != null && curveResult != null -> if (wizard.step == 2) {
                        CurveConfigStep(wizard, curveResult, previewHistory, live, actions.onCurve, actions.onAddSeries, actions.onOpenSeries)
                    } else {
                        CurveLookStep(wizard, curveResult, previewHistory, live, actions.onCurve)
                    }
                    wizard.step == 2 -> DataStep(wizard, result, previewSnapshot, live, actions.onForm)
                    else -> DisplayStep(wizard, result, previewSnapshot, live, actions.onForm, seriesSnapshots)
                }
            }
        }
        if (wizard.pointPage) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .navigationBarsPadding()
                    .padding(start = pageStart(), end = spacing.page, top = 10.dp, bottom = 14.dp),
            ) {
                PrimaryButton(stringResource(R.string.panel_edit_done), goBack, Modifier.fillMaxWidth().testTag("wiz-point-done"))
            }
        } else if (wizard.step >= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .navigationBarsPadding()
                    .padding(start = pageStart(), end = spacing.page, top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    stringResource(if (wizard.step == firstStep) R.string.action_cancel else R.string.panel_wiz_prev),
                    goBack, Modifier.weight(1f).testTag("wiz-prev"),
                )
                PrimaryButton(
                    stringResource(
                        when {
                            wizard.step == 2 -> R.string.panel_wiz_next_look
                            wizard.editingWidgetId != null -> R.string.panel_wiz_save_changes
                            else -> R.string.panel_wiz_add_to_panel
                        },
                    ),
                    actions.onNext, Modifier.weight(1.6f).testTag("wiz-next"),
                )
            }
        }
    }
    val sheetCurve = wizard.curve
    val sheetIndex = wizard.sheet
    val sheetSeries = sheetIndex?.let { sheetCurve?.series?.getOrNull(it) }
    if (sheetCurve != null && sheetIndex != null && sheetSeries != null && !wizard.pointPage) {
        SeriesSheet(
            sheetIndex, sheetSeries,
            onChange = { transform -> actions.onCurve(sheetCurve.updateSeries(sheetIndex, transform)) },
            onOpenPoint = actions.onOpenSeriesPoint,
            onDelete = { actions.onDeleteSeries(sheetIndex) },
            onClose = goBack,
        )
    }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = colors.surface1,
            titleContentColor = colors.text,
            textContentColor = colors.muted,
            title = { Text(stringResource(R.string.panel_wiz_discard_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(stringResource(R.string.panel_wiz_discard_body), fontSize = 14.sp) },
            confirmButton = {
                TextButton({ confirmDiscard = false; actions.onExit() }, Modifier.testTag("wiz-discard-confirm")) {
                    Text(stringResource(R.string.panel_edit_discard), color = colors.error)
                }
            },
            dismissButton = {
                TextButton({ confirmDiscard = false }, Modifier.testTag("wiz-discard-dismiss")) {
                    Text(stringResource(R.string.panel_edit_keep), color = colors.accentText)
                }
            },
        )
    }
}

/** v3 页头：第 1 步“添加组件”，之后是“数值 · 数据”/“数值 · 显示”。 */
@Composable
private fun stepTitle(wizard: WizardState): String {
    if (wizard.step == 1) return stringResource(R.string.panel_wiz_title_pick)
    val kind = stringResource(
        when {
            wizard.curve != null -> R.string.panel_wiz_kind_curve
            else -> when (wizard.form.kind) {
                WidgetKinds.LAMP -> R.string.panel_wiz_kind_lamp
                WidgetKinds.STATUS_TEXT -> R.string.panel_wiz_kind_status
                WidgetKinds.PROGRESS -> R.string.panel_wiz_kind_progress
                WidgetKinds.GAUGE -> R.string.panel_wiz_kind_gauge
                WidgetKinds.SWITCH -> R.string.panel_wiz_kind_switch
                WidgetKinds.BUTTON -> R.string.panel_wiz_kind_button
                WidgetKinds.REGISTER_TABLE -> R.string.panel_wiz_kind_table
                else -> R.string.panel_wiz_kind_number
            }
        },
    )
    val step = stringResource(if (wizard.step == 2) R.string.panel_wiz_step_data else R.string.panel_wiz_step_display)
    return stringResource(R.string.panel_wiz_title_step, kind, step)
}


// ---- 第 2 步「数据」（v3 3-2）：只管读什么 / 写什么 ----

@Composable
private fun DataStep(
    wizard: WizardState,
    result: FormResult,
    snapshot: PointSnapshot?,
    live: Boolean,
    onForm: (WidgetForm) -> Unit,
) {
    val colors = LocalModbusColors.current
    val form = wizard.form
    if (form.kind != WidgetKinds.BUTTON) {
        PreviewBar(
            name = form.name, unitId = form.unitId, address = form.address, snapshot = snapshot, live = live,
            addressLabel = form.address.trim().toIntOrNull()?.let { addressPrefix(form.area) + it },
            unitLabel = form.unitLabel,
        )
        if (!live) {
            Text(
                stringResource(R.string.panel_wiz_preview_none), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.testTag("wiz-preview-none"),
            )
        }
    }
    ModbusCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.panel_wiz_sec_point), color = colors.muted, fontSize = 12.sp)
            SourceSection(wizard, result, onForm)
            if (form.kind == WidgetKinds.BUTTON) WriteValueField(wizard, result, onForm)
            if (form.isTable) TableFields(wizard, result, onForm)
        }
    }
    if (form.usesConversion && form.type != DataType.ASCII) {
        // 有要显示的错误就自动展开：必填等“下一步”后才报，输错的内容立刻报（规范 40）
        val shown = listOf(FormField.SCALE, FormField.OFFSET).mapNotNull { result.errors[it] }
            .any { wizard.showErrors || it != FormError.REQUIRED }
        val scale = form.scale.ifBlank { "1" }
        val offset = form.offset.ifBlank { "0" }
        val decimals = form.decimals.trim().toIntOrNull()
        // 稿面 3-2：摘要写“× 0.1 + 0 · 1 位小数”，收起时也能看出怎么换算、显示几位
        val summary = if (decimals != null) {
            stringResource(R.string.panel_wiz_conv_summary_decimals, scale, offset, decimals)
        } else {
            stringResource(R.string.panel_wiz_conv_summary, scale, offset)
        }
        CollapsibleBlock(
            stringResource(R.string.panel_wiz_conv_title), summary, tag = "wiz-convert", forceOpen = shown,
        ) { ScaleFields(wizard, result, snapshot, onForm) }
    }
    if (form.isWriter) WriteNote(form)
    if (form.isTable && form.tableWritable) TableWriteNote()
    if (form.usesPoll) {
        val default = form.pollMs.trim() == io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition.DEFAULT_POLL_MS.toString()
        CollapsibleBlock(
            stringResource(R.string.panel_wiz_poll),
            stringResource(if (default) R.string.panel_wiz_poll_default else R.string.panel_wiz_poll_summary, form.pollMs.ifBlank { "—" }),
            tag = "wiz-poll-block", forceOpen = result.errors[FormField.POLL]?.let { wizard.showErrors || it != FormError.REQUIRED } == true,
        ) {
            NumberInput(
                stringResource(R.string.panel_wiz_poll), form.pollMs, Modifier.fillMaxWidth().testTag("wiz-poll"),
                sub = stringResource(R.string.panel_wiz_poll_unit),
                error = fieldError(wizard, result, FormField.POLL),
            ) { onForm(form.copy(pollMs = it.filter(Char::isDigit).take(6))) }
        }
    }
}

private fun addressPrefix(area: RegisterArea): String = when (area) {
    RegisterArea.COIL -> "C"
    RegisterArea.DISCRETE_INPUT -> "DI"
    RegisterArea.INPUT_REGISTER -> "IR"
    RegisterArea.HOLDING_REGISTER -> "HR"
}

/** 按钮的预设值（第 2 步：写什么）：线圈 OFF / ON 分段；寄存器填数字，按所选类型编码，不做缩放。 */
@Composable
private fun WriteValueField(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit) {
    val form = wizard.form
    val colors = LocalModbusColors.current
    if (form.area == RegisterArea.COIL) {
        Text(stringResource(R.string.panel_wiz_write_value), color = colors.muted, fontSize = 12.sp)
        SegmentedTextControl(listOf("0", "1"), if (form.writeValue.trim() == "0") "0" else "1", { onForm(form.copy(writeValue = it)) }, Modifier.testTag("wiz-write-coil"), small = true) {
            if (it == "1") "ON" else "OFF"
        }
    } else {
        NumberInput(
            stringResource(R.string.panel_wiz_write_value), form.writeValue, Modifier.fillMaxWidth().testTag("wiz-write-value"),
            error = fieldError(wizard, result, FormField.WRITE_VALUE),
        ) { onForm(form.copy(writeValue = it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(12))) }
    }
}

/** 按钮的显示：按钮文字（可空，默认“写入”）。预设值在第 2 步（[WriteValueField]）。 */
@Composable
private fun ButtonLabelField(form: WidgetForm, onForm: (WidgetForm) -> Unit) {
    NumberInput(
        stringResource(R.string.panel_wiz_button_label), form.buttonLabel, Modifier.fillMaxWidth().testTag("wiz-button-label"),
        sub = null, keyboardType = KeyboardType.Text,
    ) { onForm(form.copy(buttonLabel = it.take(WidgetForm.MAX_BUTTON_LABEL))) }
}

/** 寄存器表的配置：行数（地址范围实时显示）+ “允许在面板上修改”（默认关；输入类区域只读，不能开）。 */
@Composable
private fun TableFields(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit) {
    val colors = LocalModbusColors.current
    val form = wizard.form
    NumberInput(
        stringResource(R.string.panel_wiz_table_rows), form.tableRows, Modifier.fillMaxWidth().testTag("wiz-table-rows"),
        error = fieldError(wizard, result, FormField.ROWS),
    ) { onForm(form.copy(tableRows = it.filter(Char::isDigit).take(2))) }
    val rows = form.tableRows.toIntOrNull()
    val start = form.address.toIntOrNull()
    val per = if (form.type == DataType.BOOL) 1 else form.type.registers ?: 1
    if (rows != null && rows in 1..WidgetWrites.MAX_TABLE_ROWS && start != null) {
        val prefix = when (form.area) {
            RegisterArea.COIL -> "C"
            RegisterArea.DISCRETE_INPUT -> "DI"
            RegisterArea.INPUT_REGISTER -> "IR"
            RegisterArea.HOLDING_REGISTER -> "HR"
        }
        Text(
            stringResource(
                R.string.panel_wiz_table_rows_hint, "$prefix$start", "$prefix${start + rows * per - 1}", rows * per,
                stringResource(if (form.type == DataType.BOOL) R.string.panel_wiz_occupies_coil else R.string.panel_wiz_occupies_reg),
            ),
            color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.testTag("wiz-table-hint"),
        )
    }
    SettingRow(
        stringResource(R.string.panel_wiz_table_writable), Modifier.testTag("wiz-table-writable"),
        subtitle = stringResource(if (form.canAllowWrite) R.string.panel_wiz_table_writable_sub else R.string.panel_wiz_table_readonly_sub),
        trailing = if (form.canAllowWrite) SettingTrailing.SWITCH else SettingTrailing.LOCKED,
        checked = form.tableWritable && form.canAllowWrite,
        onChecked = { onForm(form.copy(tableWritable = it)) },
    )
}

/** 打开“允许修改”后的说明：每行单独确认、预览 / 编辑不写、从站不能写。 */
@Composable
private fun TableWriteNote() {
    val colors = LocalModbusColors.current
    ModbusCard(Modifier.fillMaxWidth().testTag("wiz-write-note")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Text("⚠", color = colors.txText, fontSize = 12.sp)
            Text(stringResource(R.string.panel_wiz_table_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/** 写入类组件在向导里的说明：点了不会直接写；预览和编辑模式都不会写；位写入是先读后写。 */
@Composable
private fun WriteNote(form: WidgetForm) {
    val colors = LocalModbusColors.current
    ModbusCard(Modifier.fillMaxWidth().testTag("wiz-write-note")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text("⚠", color = colors.txText, fontSize = 12.sp)
                Text(
                    stringResource(if (form.kind == WidgetKinds.SWITCH) R.string.panel_wiz_switch_note else R.string.panel_wiz_button_note),
                    color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            if (form.kind == WidgetKinds.SWITCH && form.area == RegisterArea.HOLDING_REGISTER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("⚠", color = colors.txText, fontSize = 12.sp)
                    Text(
                        stringResource(R.string.panel_wiz_switch_bit_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.testTag("wiz-bit-note"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    val colors = LocalModbusColors.current
    Text(
        text, color = colors.muted, fontSize = 12.5.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun fieldError(wizard: WizardState, result: FormResult, field: FormField): String? {
    val err = result.errors[field] ?: return null
    if (err == FormError.REQUIRED && !wizard.showErrors) return null
    return stringResource(
        when (err) {
            FormError.REQUIRED -> R.string.panel_wiz_err_required
            FormError.NOT_A_NUMBER -> R.string.panel_wiz_err_number
            FormError.NOT_INTEGER -> R.string.panel_wiz_err_integer
            FormError.OUT_OF_RANGE -> when (field) {
                FormField.UNIT -> if (wizard.form.allowsBroadcast) R.string.panel_wiz_err_unit_broadcast else R.string.panel_wiz_err_unit
                FormField.WRITE_VALUE -> R.string.panel_wiz_err_write_value
                FormField.ADDRESS -> R.string.panel_wiz_err_address
                FormField.BIT -> R.string.panel_wiz_err_bit
                FormField.ASCII -> R.string.panel_wiz_err_ascii
                FormField.DECIMALS -> R.string.panel_wiz_err_decimals
                FormField.POLL -> R.string.panel_wiz_err_poll
                FormField.ROWS -> R.string.panel_wiz_err_rows
                else -> R.string.panel_wiz_err_number
            }
            FormError.ZERO_SCALE -> R.string.panel_wiz_err_zero_scale
            FormError.RANGE_ORDER -> R.string.panel_wiz_err_range_order
            FormError.DUPLICATE -> R.string.panel_wiz_err_duplicate
            FormError.SPAN_OVERFLOW -> R.string.panel_wiz_err_overflow
            FormError.TOO_MANY -> R.string.panel_wiz_err_too_many
            FormError.EXCEEDS_RETENTION -> R.string.panel_wiz_curve_err_exceeds
        },
    )
}

/** 实时预览卡：用和面板一样的卡片画（强调色描边）；表单还不完整时画只有名称和“—”的占位。 */
@Composable
internal fun PreviewCard(
    wizard: WizardState, result: FormResult, snapshot: PointSnapshot?, live: Boolean, modifier: Modifier = Modifier,
    snapshots: Map<String, PointSnapshot> = emptyMap(),
) {
    val colors = LocalModbusColors.current
    val widget = result.widget?.let { previewWidget(wizard, it) }
    Column(modifier.fillMaxWidth().testTag("wiz-preview"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
            if (widget != null) {
                WidgetCard(widget, if (live) snapshot else null, Modifier.fillMaxWidth(), borderColor = colors.accent, snapshots = if (live) snapshots else emptyMap())
            } else {
                CardFrame(Modifier.fillMaxWidth(), "wiz-preview-placeholder", borderColor = colors.accent) {
                    // 稿面：名称行右侧是“实时预览”徽标。没填名称时写“未命名组件”而不是“名称”，
                    // 否则这张卡看起来像一个输入框（redroid 上实测会被当成名称输入框去点）。
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            wizard.form.name.ifBlank { stringResource(R.string.panel_wiz_preview_unnamed) },
                            color = if (wizard.form.name.isBlank()) colors.dim else colors.muted,
                            fontSize = 12.5.sp, lineHeight = 17.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).testTag("wiz-preview-name"),
                        )
                        io.github.xingyingyuzhui.modbusmobile.ui.components.ToneBadge(
                            stringResource(R.string.panel_wiz_preview_badge), colors.muted, Modifier.testTag("wiz-preview-badge"),
                        )
                    }
                    Text("—", color = colors.dim, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMono, modifier = Modifier.padding(top = 4.dp))
                    Text(stringResource(R.string.panel_wiz_preview_invalid), color = colors.dim, fontSize = 10.5.sp, lineHeight = 14.sp)
                }
            }
        }
        if (!live && wizard.form.kind != WidgetKinds.BUTTON) {
            Text(
                stringResource(R.string.panel_wiz_preview_none), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.testTag("wiz-preview-none"),
            )
        }
    }
}

/** 寄存器表的预览读的是“preview#行号”这些点位（见 `PanelViewModel.schedulePreview`），所以预览卡用同样的点位 id 重建。 */
private fun previewWidget(wizard: WizardState, widget: Widget): Widget =
    if (widget.kind == WidgetKinds.REGISTER_TABLE) wizard.form.build(widget.id, PREVIEW_POINT_ID).widget ?: widget else widget

// ---- 数据源 ----

@Composable
internal fun SourceSection(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit) {
    val colors = LocalModbusColors.current
    val form = wizard.form
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedTextControl(if (form.isWriter) WritableAreas else Areas, form.area, { onForm(form.withArea(it)) }, Modifier.testTag("wiz-area"), small = true) { area ->
            stringResource(
                when (area) {
                    RegisterArea.HOLDING_REGISTER -> R.string.area_short_holding
                    RegisterArea.INPUT_REGISTER -> R.string.area_short_input
                    RegisterArea.COIL -> R.string.area_short_coil
                    RegisterArea.DISCRETE_INPUT -> R.string.area_short_discrete
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput(
                stringResource(R.string.panel_wiz_field_unit), form.unitId, Modifier.weight(0.7f).testTag("wiz-unit"),
                error = fieldError(wizard, result, FormField.UNIT),
            ) { onForm(form.copy(unitId = it.filter(Char::isDigit).take(3))) }
            NumberInput(
                stringResource(R.string.panel_wiz_field_address), form.address, Modifier.weight(1.3f).testTag("wiz-address"),
                // 地址后面写参考号“= 40101”，对照设备手册用
                sub = modiconHint(form.area, form.address),
                error = fieldError(wizard, result, FormField.ADDRESS),
            ) { onForm(form.copy(address = it.filter(Char::isDigit).take(5))) }
        }
        if (form.allowsBroadcast && form.unitId.trim() == "0") {
            Text(
                stringResource(R.string.panel_wiz_unit_broadcast), color = colors.error, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.testTag("wiz-broadcast-note"),
            )
        }
        val occupies = if (form.type == DataType.BOOL) 1 else form.type.registers ?: form.asciiRegisters.toIntOrNull() ?: 1
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.panel_wiz_type), color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    R.string.panel_wiz_occupies, occupies,
                    stringResource(if (form.type == DataType.BOOL) R.string.panel_wiz_occupies_coil else R.string.panel_wiz_occupies_reg),
                ),
                color = colors.muted, fontSize = 12.sp, modifier = Modifier.testTag("wiz-occupies"),
            )
        }
        // v3：只列当前区域能用的类型（不画灰的）；常用的放外面，其余收进“更多 ▾”菜单（不再展开成一排 chip）。
        // 选了菜单里的类型后，这颗 chip 显示成选中的类型名，再点还是弹同一个菜单。
        val allowed = form.allowedTypes()
        val common = CommonTypes.filter { it in allowed }
        val rest = allowed.filter { it !in CommonTypes }
        var more by remember { mutableStateOf(false) }
        FlowRowChips {
            common.forEach { type ->
                Box(Modifier.testTag("wiz-type-${type.name}")) {
                    SelectChip(typeLabel(type), type == form.type, xs = true) { onForm(form.withType(type)) }
                }
            }
            if (rest.isNotEmpty()) {
                val picked = form.type in rest
                Box(Modifier.testTag("wiz-type-more")) {
                    SelectChip(
                        if (picked) typeLabel(form.type) + " ▾" else stringResource(R.string.panel_wiz_type_more),
                        picked,
                        xs = true,
                    ) { more = true }
                    ModbusMenu(expanded = more, onDismiss = { more = false }) {
                        rest.forEach { type ->
                            ModbusMenuItem(
                                typeLabel(type),
                                onClick = { more = false; onForm(form.withType(type)) },
                                selected = type == form.type,
                                modifier = Modifier.testTag("wiz-type-${type.name}"),
                            )
                        }
                    }
                }
            }
        }
        if (form.type == DataType.BIT) {
            NumberInput(
                stringResource(R.string.panel_wiz_field_bit), form.bit, Modifier.fillMaxWidth().testTag("wiz-bit"),
                error = fieldError(wizard, result, FormField.BIT),
            ) { onForm(form.copy(bit = it.filter(Char::isDigit).take(2))) }
        }
        if (form.type == DataType.ASCII) {
            NumberInput(
                stringResource(R.string.panel_wiz_field_ascii), form.asciiRegisters, Modifier.fillMaxWidth().testTag("wiz-ascii"),
                error = fieldError(wizard, result, FormField.ASCII),
            ) { onForm(form.copy(asciiRegisters = it.filter(Char::isDigit).take(3))) }
        }
        // 字节 / 字序只在 32 / 64 位时出现（v3：不再画一排灰的）
        if ((form.type.registers ?: 1) >= 2) {
            Text(stringResource(R.string.panel_wiz_order), color = colors.muted, fontSize = 12.sp)
            SegmentedTextControl(ByteWordOrder.entries, form.order, { onForm(form.copy(order = it)) }, Modifier.testTag("wiz-order"), small = true) { it.name }
        } else {
            // 稿面 3-2：字序不能选时不画灰控件，只留一句说明
            Text(
                stringResource(R.string.panel_wiz_order_hint), color = colors.dim, fontSize = 11.sp,
                modifier = Modifier.testTag("wiz-order-hint"),
            )
        }
    }
}

/** 第 2 步外面的常用类型；其余在“更多”里。 */
private val CommonTypes = listOf(DataType.BOOL, DataType.U16, DataType.S16, DataType.U32, DataType.F32, DataType.BIT)

private fun typeLabel(type: DataType): String = when (type) {
    DataType.BIT -> "位"
    else -> type.name
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

// ---- 换算（第 2 步）----

/** 缩放 / 偏移 + 算式（有实时原始值时接“→ 234 × 0.1 + 0 = 23.4”）。小数位、单位在第 3 步「显示」。 */
@Composable
internal fun ScaleFields(wizard: WizardState, result: FormResult, snapshot: PointSnapshot?, onForm: (WidgetForm) -> Unit) {
    val form = wizard.form
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberInput(
            stringResource(R.string.panel_wiz_scale), form.scale, Modifier.weight(1f).testTag("wiz-scale"),
            error = fieldError(wizard, result, FormField.SCALE), keyboardType = KeyboardType.Decimal,
        ) { onForm(form.copy(scale = it)) }
        NumberInput(
            stringResource(R.string.panel_wiz_offset), form.offset, Modifier.weight(1f).testTag("wiz-offset"),
            error = fieldError(wizard, result, FormField.OFFSET), keyboardType = KeyboardType.Decimal,
        ) { onForm(form.copy(offset = it)) }
    }
    Formula(form, snapshot)
}

/** 系列点位页（曲线）还用整块：缩放 / 偏移 / 小数位 / 单位 / 名称一起。 */
@Composable
internal fun ConvertSection(wizard: WizardState, result: FormResult, snapshot: PointSnapshot?, onForm: (WidgetForm) -> Unit, compact: Boolean = false) {
    val form = wizard.form
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (form.type != DataType.ASCII) {
            ScaleFields(wizard, result, snapshot, onForm)
            DecimalsField(wizard, result, onForm, Modifier.fillMaxWidth())
            UnitChips(form, onForm)
        }
        if (!compact) NameField(wizard, result, onForm)
    }
}

@Composable
private fun DecimalsField(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit, modifier: Modifier) {
    val form = wizard.form
    NumberInput(
        stringResource(R.string.panel_wiz_decimals), form.decimals, modifier.testTag("wiz-decimals"),
        error = fieldError(wizard, result, FormField.DECIMALS),
    ) { onForm(form.copy(decimals = it.filter(Char::isDigit).take(2))) }
}

@Composable
private fun NameField(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit) {
    val form = wizard.form
    NumberInput(
        stringResource(R.string.panel_wiz_name), form.name, Modifier.fillMaxWidth().testTag("wiz-name"),
        keyboardType = KeyboardType.Text, error = fieldError(wizard, result, FormField.NAME),
    ) { onForm(form.copy(name = it.take(WidgetForm.MAX_NAME))) }
}

/** 稿面 `.formula`：虚线框，mono；有实时原始值时接“→ 234 × 0.1 + 0 = 23.4”。 */
@Composable
private fun Formula(form: WidgetForm, snapshot: PointSnapshot?) {
    val colors = LocalModbusColors.current
    val scale = form.scale.ifBlank { "1" }
    val offset = form.offset.ifBlank { "0" }
    val raw = (snapshot?.raw as? RawValue.Integer)?.let { if (it.unsigned) it.toBigInteger().toString() else it.bits.toString() }
        ?: (snapshot?.raw as? RawValue.Real)?.let { ValueCodec.format(it.value, null) }
        ?: (snapshot?.raw as? RawValue.Flag)?.let { if (it.value) "1" else "0" }
    val line = stringResource(R.string.panel_wiz_formula, scale, offset) +
        if (raw != null && snapshot?.text != null) stringResource(R.string.panel_wiz_formula_live, raw, scale, offset, snapshot.text) else ""
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bg)
            .drawBehind {
                drawRoundRect(
                    colors.line, cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(line, color = colors.muted, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = JetBrainsMono, modifier = Modifier.testTag("wiz-formula"))
    }
}

@Composable
private fun UnitChips(form: WidgetForm, onForm: (WidgetForm) -> Unit) {
    val colors = LocalModbusColors.current
    var custom by remember { mutableStateOf(form.unitLabel.isNotBlank() && form.unitLabel !in UnitPresets) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.panel_wiz_unit_label), color = colors.muted, fontSize = 12.sp)
        FlowRowChips {
            UnitPresets.forEach { unit ->
                Box(Modifier.testTag("wiz-unit-chip-$unit")) {
                    SelectChip(unit, !custom && form.unitLabel == unit, xs = true) {
                        custom = false
                        onForm(form.copy(unitLabel = if (form.unitLabel == unit) "" else unit))
                    }
                }
            }
            Box(Modifier.testTag("wiz-unit-chip-custom")) {
                SelectChip(stringResource(R.string.panel_wiz_unit_custom), custom, xs = true) {
                    custom = true
                    if (form.unitLabel in UnitPresets) onForm(form.copy(unitLabel = ""))
                }
            }
        }
        if (custom) {
            NumberInput(
                stringResource(R.string.panel_wiz_unit_custom_field), form.unitLabel, Modifier.fillMaxWidth().testTag("wiz-unit-custom"),
                keyboardType = KeyboardType.Text,
            ) { onForm(form.copy(unitLabel = it.take(WidgetForm.MAX_UNIT_LABEL))) }
        }
    }
}

@Composable
private fun ThresholdList(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit) {
    val colors = LocalModbusColors.current
    val form = wizard.form
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.panel_wiz_thresholds), color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            val add = stringResource(R.string.panel_wiz_threshold_add)
            Box(
                Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = add; role = Role.Button }
                    .clickable { onForm(form.copy(thresholds = form.thresholds + ThresholdRow())) }
                    .testTag("wiz-threshold-add"),
                contentAlignment = Alignment.Center,
            ) { Text("＋", color = colors.accentText, fontSize = 18.sp) }
        }
        if (form.thresholds.isNotEmpty()) {
            val shape = RoundedCornerShape(12.dp)
            Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, colors.line, shape)) {
                form.thresholds.forEachIndexed { index, row ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                    ThresholdRowEditor(index, row, wizard.showErrors && index in result.badThresholds || (row.value.isNotBlank() && index in result.badThresholds)) { next ->
                        onForm(form.copy(thresholds = form.thresholds.toMutableList().also { list -> if (next == null) list.removeAt(index) else list[index] = next }))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdRowEditor(index: Int, row: ThresholdRow, error: Boolean, onChange: (ThresholdRow?) -> Unit) {
    val colors = LocalModbusColors.current
    val tone = if (row.level == ThresholdLevel.ALERT) colors.error else colors.txText
    val opText = if (row.op == ThresholdOp.GE) "≥" else "<"
    val levelText = stringResource(if (row.level == ThresholdLevel.ALERT) R.string.panel_wiz_level_alert else R.string.panel_wiz_level_warn)
    val opCd = stringResource(R.string.panel_wiz_threshold_op, opText)
    val levelCd = stringResource(R.string.panel_wiz_threshold_level, levelText)
    val deleteCd = stringResource(R.string.panel_wiz_threshold_delete)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("wiz-threshold-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.padding(start = 8.dp, end = 4.dp).size(12.dp).clip(CircleShape).background(tone))
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = opCd; role = Role.Button }
                .clickable { onChange(row.copy(op = if (row.op == ThresholdOp.GE) ThresholdOp.LT else ThresholdOp.GE)) }
                .testTag("wiz-threshold-op-$index"),
            contentAlignment = Alignment.Center,
        ) { Text(opText, color = colors.text, fontSize = 15.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold) }
        NumberInput(
            stringResource(R.string.panel_wiz_threshold_value), row.value, Modifier.weight(1f).padding(vertical = 4.dp).testTag("wiz-threshold-value-$index"),
            minHeight = 40.dp, error = if (error) stringResource(R.string.panel_wiz_err_number) else null,
            keyboardType = KeyboardType.Decimal,
        ) { onChange(row.copy(value = it)) }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = levelCd; role = Role.Button }
                .clickable { onChange(row.copy(level = if (row.level == ThresholdLevel.ALERT) ThresholdLevel.WARN else ThresholdLevel.ALERT)) }
                .testTag("wiz-threshold-level-$index"),
            contentAlignment = Alignment.Center,
        ) { Text(levelText, color = tone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = deleteCd; role = Role.Button }
                .clickable { onChange(null) }
                .testTag("wiz-threshold-delete-$index"),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = colors.muted, fontSize = 14.sp) }
    }
}

// ---- 状态文本：映射表 ----

@Composable
private fun StatesSection(wizard: WizardState, result: FormResult, onForm: (WidgetForm) -> Unit) {
    val colors = LocalModbusColors.current
    val form = wizard.form
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.panel_wiz_sec_states, form.states.size), color = colors.muted, fontSize = 12.5.sp,
            modifier = Modifier.weight(1f).testTag("wiz-states-title"),
        )
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button) {
                    val next = (form.states.mapNotNull { it.value.trim().toLongOrNull() }.maxOrNull()?.plus(1)) ?: 0L
                    onForm(form.copy(states = form.states + StateRow(next.toString(), "", Tones[form.states.size % Tones.size])))
                }
                .testTag("wiz-state-add"),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.panel_wiz_state_add), color = colors.accentText, fontSize = 13.sp) }
    }
    val shape = RoundedCornerShape(12.dp)
    if (form.states.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface1).border(1.dp, colors.line, shape)) {
            form.states.forEachIndexed { index, row ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                StateRowEditor(index, row, index in result.badStates && (wizard.showErrors || (row.value.isNotBlank() && row.label.isNotBlank()))) { next ->
                    onForm(form.copy(states = form.states.toMutableList().also { list -> if (next == null) list.removeAt(index) else list[index] = next }))
                }
            }
        }
    }
    val error = fieldError(wizard, result, FormField.STATE)
    Text(
        if (error != null && (wizard.showErrors || form.states.isNotEmpty())) stringResource(R.string.panel_wiz_err_states) else stringResource(R.string.panel_wiz_state_hint),
        color = if (error != null && wizard.showErrors) colors.error else colors.muted,
        fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.testTag("wiz-states-note"),
    )
}

@Composable
private fun StateRowEditor(index: Int, row: StateRow, error: Boolean, onChange: (StateRow?) -> Unit) {
    val colors = LocalModbusColors.current
    val toneName = stringResource(toneName(row.tone))
    val toneCd = stringResource(R.string.panel_wiz_state_tone, toneName)
    val deleteCd = stringResource(R.string.panel_wiz_state_delete)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("wiz-state-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NumberInput(
            stringResource(R.string.panel_wiz_state_value), row.value, Modifier.width(84.dp).padding(vertical = 4.dp).testTag("wiz-state-value-$index"),
            minHeight = 40.dp, error = if (error) "!" else null, keyboardType = KeyboardType.Number,
        ) { onChange(row.copy(value = it.filter { c -> c.isDigit() || c == '-' }.take(10))) }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = toneCd; role = Role.Button }
                .clickable { onChange(row.copy(tone = Tones[(Tones.indexOf(row.tone) + 1) % Tones.size])) }
                .testTag("wiz-state-tone-$index"),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(12.dp).clip(CircleShape).background(toneColor(row.tone))) }
        NumberInput(
            stringResource(R.string.panel_wiz_state_label), row.label, Modifier.weight(1f).padding(vertical = 4.dp).testTag("wiz-state-label-$index"),
            minHeight = 40.dp, keyboardType = KeyboardType.Text,
        ) { onChange(row.copy(label = it.take(12))) }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = deleteCd; role = Role.Button }
                .clickable { onChange(null) }
                .testTag("wiz-state-delete-$index"),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = colors.muted, fontSize = 14.sp) }
    }
}

private fun toneName(tone: StateTone): Int = when (tone) {
    StateTone.GREEN -> R.string.panel_wiz_tone_green
    StateTone.AMBER -> R.string.panel_wiz_tone_amber
    StateTone.RED -> R.string.panel_wiz_tone_red
    StateTone.BLUE -> R.string.panel_wiz_tone_blue
    StateTone.GRAY -> R.string.panel_wiz_tone_gray
}

@Composable
private fun toneColor(tone: StateTone): Color {
    val colors = LocalModbusColors.current
    return when (tone) {
        StateTone.GREEN -> colors.rxLocal
        StateTone.AMBER -> colors.txText
        StateTone.RED -> colors.error
        StateTone.BLUE -> colors.tx
        StateTone.GRAY -> colors.dropped
    }
}

// ---- 3/3 外观 ----

/**
 * 第 3 步「显示」（v3 3-3）：按选中尺寸放在一格“假网格”里的预览（旁边虚线格 = 面板里的其他组件），尺寸分段，
 * 然后是名称 / 小数位、单位、量程、颜色阈值、亮灭文字、按钮文字、状态映射（按类型取舍）。预览只读不写（规范 39）。
 */
@Composable
private fun DisplayStep(
    wizard: WizardState,
    result: FormResult,
    snapshot: PointSnapshot?,
    live: Boolean,
    onForm: (WidgetForm) -> Unit,
    snapshots: Map<String, PointSnapshot> = emptyMap(),
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val form = wizard.form
    // 名称等还没填时用占位建出来，照样能看到实际效果
    val widget = (result.widget ?: form.forPreview().build(wizard.widgetId, wizard.pointId).widget)?.let { previewWidget(wizard, it) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(colors.bg)
            .drawBehind {
                drawRoundRect(colors.line, cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
            }
            .padding(6.dp)
            .testTag("wiz-look-preview"),
    ) {
        if (widget != null) {
            val single = !form.isTable && form.spanColumns == 1
            PanelGrid(
                columns = 2,
                spans = if (single) listOf(1, 1) else listOf(2),
                spanRows = if (single) listOf(form.spanRows, form.spanRows) else listOf(if (form.isTable) 1 else form.spanRows),
                modifier = Modifier.fillMaxWidth(),
                gap = 6.dp,
            ) { index ->
                if (index == 0) {
                    WidgetCard(widget, if (live) snapshot else null, Modifier, borderColor = colors.accent, snapshots = if (live) snapshots else emptyMap())
                } else {
                    Box(
                        Modifier.fillMaxSize().drawBehind {
                            drawRoundRect(colors.line, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.panel_wiz_other_widgets), color = colors.dim, fontSize = 11.sp, modifier = Modifier.testTag("wiz-actual-size")) }
                }
            }
        }
    }
    if (form.isTable) {
        // 寄存器表固定占满一行宽度、高度随行数变化：没有尺寸可选，不画假选项
        Text(
            stringResource(R.string.panel_wiz_table_look_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.testTag("wiz-table-look-note"),
        )
    } else {
        SegmentedTextControl(
            Sizes, form.spanColumns to form.spanRows,
            { onForm(form.copy(spanColumns = it.first, spanRows = it.second)) },
            Modifier.testTag("wiz-size"), small = true,
        ) { "${it.first} × ${it.second}" }
    }
    ModbusCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val numeric = form.usesConversion && form.type != DataType.ASCII
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NameField(wizard, result, onForm) }
                if (numeric) DecimalsField(wizard, result, onForm, Modifier.width(96.dp))
            }
            if (form.usesUnitLabel && form.type != DataType.ASCII) UnitChips(form, onForm)
            if (form.usesRange && form.type != DataType.ASCII) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(
                        stringResource(R.string.panel_wiz_range_min), form.rangeMin, Modifier.weight(1f).testTag("wiz-range-min"),
                        error = fieldError(wizard, result, FormField.RANGE_MIN), keyboardType = KeyboardType.Decimal,
                    ) { onForm(form.copy(rangeMin = it)) }
                    NumberInput(
                        stringResource(R.string.panel_wiz_range_max), form.rangeMax, Modifier.weight(1f).testTag("wiz-range-max"),
                        error = fieldError(wizard, result, FormField.RANGE_MAX), keyboardType = KeyboardType.Decimal,
                    ) { onForm(form.copy(rangeMax = it)) }
                }
            }
            if (form.kind == WidgetKinds.LAMP || form.kind == WidgetKinds.SWITCH) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(
                        stringResource(R.string.panel_wiz_on_label), form.onLabel, Modifier.weight(1f).testTag("wiz-on-label"),
                        sub = null, keyboardType = KeyboardType.Text,
                    ) { onForm(form.copy(onLabel = it.take(8))) }
                    NumberInput(
                        stringResource(R.string.panel_wiz_off_label), form.offLabel, Modifier.weight(1f).testTag("wiz-off-label"),
                        keyboardType = KeyboardType.Text,
                    ) { onForm(form.copy(offLabel = it.take(8))) }
                }
                Text(
                    stringResource(R.string.panel_wiz_on_hint) + " · " + stringResource(R.string.panel_wiz_off_hint),
                    color = colors.muted, fontSize = 11.5.sp,
                )
            }
            if (form.kind == WidgetKinds.BUTTON) ButtonLabelField(form, onForm)
        }
    }
    if (form.usesThresholds && form.type != DataType.ASCII) {
        ModbusCard(Modifier.fillMaxWidth()) { ThresholdList(wizard, result, onForm) }
    }
    if (form.kind == WidgetKinds.STATUS_TEXT) StatesSection(wizard, result, onForm)
    Text(
        stringResource(R.string.panel_wiz_size_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
        modifier = Modifier.testTag("wiz-size-note"),
    )
}

/** 向导页（独立路由，隐藏 dock）：状态在面板页的 [PanelViewModel] 里（和编辑草稿同一个实例）。 */
@Composable
fun PanelWizardScreen(parent: androidx.lifecycle.ViewModelStoreOwner, onClose: () -> Unit) {
    val repository = io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository.current
    val runtime = io.github.xingyingyuzhui.modbusmobile.ui.LocalPanelRuntime.current
    val session = io.github.xingyingyuzhui.modbusmobile.ui.LocalSession.current
    val model = androidx.lifecycle.viewmodel.compose.viewModel<PanelViewModel>(viewModelStoreOwner = parent) { PanelViewModel(repository, runtime, session = session) }
    val wizard by model.wizard.collectAsStateWithLifecycle()
    val w = wizard
    if (w == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }
        return
    }
    val previews by model.previewSnapshots.collectAsStateWithLifecycle()
    val sessionState by io.github.xingyingyuzhui.modbusmobile.ui.LocalSession.current.state.collectAsStateWithLifecycle()
    val master = (sessionState as? io.github.xingyingyuzhui.modbusmobile.application.SessionState.Running)?.role == io.github.xingyingyuzhui.modbusmobile.core.model.Role.MASTER
    androidx.lifecycle.compose.LifecycleStartEffect(model) {
        model.setPreviewActive(true)
        onStopOrDispose { model.setPreviewActive(false) }
    }
    PanelWizard(
        wizard = w,
        previewSnapshot = previews[PREVIEW_POINT_ID],
        live = master && model.hasPreview,
        previewHistory = model.previewHistory,
        seriesSnapshots = previews,
        actions = WizardActions(
            onCurve = model::wizardCurve,
            onAddSeries = model::wizardAddSeries,
            onOpenSeries = model::wizardOpenSeries,
            onOpenSeriesPoint = model::wizardOpenSeriesPoint,
            onDeleteSeries = model::wizardDeleteSeries,
            onKind = model::wizardSelectKind,
            onForm = model::wizardForm,
            onNext = model::wizardNext,
            onBack = { model.wizardBack() },
            onExit = model::cancelWizard,
        ),
    )
}
