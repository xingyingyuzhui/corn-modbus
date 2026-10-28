package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import io.github.xingyingyuzhui.modbusmobile.ui.components.ArrowGlyph
import io.github.xingyingyuzhui.modbusmobile.ui.components.ConfirmValueRow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalAppViewModelStoreOwner
import io.github.xingyingyuzhui.modbusmobile.ui.LocalMasterPoller
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChoiceTile
import io.github.xingyingyuzhui.modbusmobile.ui.components.FieldTileMenu
import io.github.xingyingyuzhui.modbusmobile.ui.components.ConfirmSheet
import io.github.xingyingyuzhui.modbusmobile.ui.components.GearIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.HexFrame
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusMenu
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusMenuItem
import io.github.xingyingyuzhui.modbusmobile.ui.components.MoreIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.RefreshIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterTable
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import androidx.compose.ui.platform.testTag
import io.github.xingyingyuzhui.modbusmobile.ui.components.ConfirmSheetBody
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.TextLink
import io.github.xingyingyuzhui.modbusmobile.ui.components.overhangHeight
import io.github.xingyingyuzhui.modbusmobile.ui.resolve
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import io.github.xingyingyuzhui.modbusmobile.ui.LocalOpenConnection
import io.github.xingyingyuzhui.modbusmobile.ui.components.ErrorBanner
import io.github.xingyingyuzhui.modbusmobile.ui.components.ResultBanner
import io.github.xingyingyuzhui.modbusmobile.ui.components.ResultNotice
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.height
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReadAreas = listOf(
    RegisterArea.COIL,
    RegisterArea.DISCRETE_INPUT,
    RegisterArea.HOLDING_REGISTER,
    RegisterArea.INPUT_REGISTER,
)

private val WriteAreas = listOf(RegisterArea.COIL, RegisterArea.HOLDING_REGISTER)

@Composable
fun WorkbenchScreen() {
    val session = LocalSession.current
    // 用 Activity 级的 store：切页、系统返回键都不会把工作台的表单和结果清空。
    val owner = LocalAppViewModelStoreOwner.current ?: checkNotNull(LocalViewModelStoreOwner.current)
    val poller = LocalMasterPoller.current
    // 轮询在应用级 MasterPoller 里跑，切页 / 退到后台都不停（T-20261018-qa-02），这里只订阅状态。
    val model = viewModel(viewModelStoreOwner = owner) { WorkbenchViewModel(session, poller = poller) }
    val sessionState by model.sessionState.collectAsStateWithLifecycle()
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val running = sessionState as? SessionState.Running
    when (running?.role) {
        Role.MASTER -> MasterWorkbench(model, landscape)
        Role.SLAVE -> SlaveWorkbench(running.target, landscape)
        null -> NoSessionWorkbench()
    }
}

// ---------------------------------------------------------------------------------------------
// 主站工作台
// ---------------------------------------------------------------------------------------------

@Composable
private fun MasterWorkbench(model: WorkbenchViewModel, landscape: Boolean) {
    val settings = LocalUiSettings.current
    val format = model.formatOverride ?: settings.numberFormat
    // 系统返回键：写入页先回读取页（上一层），不直接退到连接页。确认弹层自己处理返回。
    BackHandler(enabled = model.mode == WorkbenchMode.WRITE && model.pending == null) {
        model.selectMode(WorkbenchMode.READ)
    }
    if (landscape) {
        MasterLandscape(model, settings, format)
    } else {
        MasterPortrait(model, settings, format)
    }
    model.pending?.let { WriteConfirm(it, model, settings) }
}

@Composable
private fun MasterPortrait(model: WorkbenchViewModel, settings: UiSettings, format: NumberFormat) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()
    Column(Modifier.screenFrame()) {
        PageHeader(stringResource(R.string.title_workbench)) { WorkbenchMenu(model) }
        SessionStrip(Modifier.padding(bottom = 8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(scroll).testTag(WorkbenchScrollTag),
            verticalArrangement = Arrangement.spacedBy(spacing.group),
        ) {
            RequestCard(model, settings, landscape = false)
            FormError(model)
            if (model.mode == WorkbenchMode.READ) {
                ReadSection(model, settings, format, landscape = false)
            } else {
                RecentSection(model, settings)
            }
            // qa and-02 bug 6：滚到底时最后一行要完全露在浮动 Dock 上方。留白 = LocalDockInsets.bottom + dockGap（dockClearance），
            // 放在滚动内容的最后，滚到底才出现；不在滚动容器外面加 padding（那样会把可视区截短，不是把内容垫高）。
            Spacer(Modifier.height(dockClearance()).testTag(WorkbenchDockSpacerTag))
        }
    }
}

@Composable
private fun MasterLandscape(model: WorkbenchViewModel, settings: UiSettings, format: NumberFormat) {
    val colors = LocalModbusColors.current
    val leftScroll = rememberScrollState()
    val rightScroll = rememberScrollState()
    Row(
        Modifier.screenFrame().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(280.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(leftScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionStrip()
                RequestCard(model, settings, landscape = true)
                FormError(model)
            }
            // 读取页：主按钮固定在左栏底部，不随表单滚出屏幕。写入页的主按钮在右栏（帧预览下面）。
            if (model.mode == WorkbenchMode.READ) ReadButtons(model)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rightScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model.mode == WorkbenchMode.READ) {
                ReadSection(model, settings, format, landscape = true)
            } else {
                // 写入页右栏：帧预览 → 预览并写入 → 最近写入。不再显示读取结果表。
                WritePreview(model)
                WriteButtons(model)
                RecentSection(model, settings)
            }
        }
    }
}

@Composable
private fun WorkbenchMenu(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(stringResource(R.string.menu_more), { open = true }) { MoreIcon(colors.muted) }
        ModbusMenu(expanded = open, onDismiss = { open = false }) {
            PollIntervalsMs.forEach { ms ->
                ModbusMenuItem(
                    text = stringResource(R.string.menu_poll_interval, secondsLabel(ms)),
                    selected = ms == model.pollIntervalMs,
                    onClick = {
                        model.setPollInterval(ms)
                        open = false
                    },
                )
            }
            HorizontalDivider(color = colors.line)
            ModbusMenuItem(
                text = stringResource(R.string.menu_clear_result),
                onClick = {
                    model.clearResult()
                    open = false
                },
            )
            ModbusMenuItem(
                text = stringResource(R.string.menu_clear_recent),
                onClick = {
                    model.clearRecentWrites()
                    open = false
                },
            )
        }
    }
}

@Composable
private fun areaShortLabel(area: RegisterArea): String = stringResource(
    when (area) {
        RegisterArea.COIL -> R.string.area_short_coil
        RegisterArea.DISCRETE_INPUT -> R.string.area_short_discrete
        RegisterArea.HOLDING_REGISTER -> R.string.area_short_holding
        RegisterArea.INPUT_REGISTER -> R.string.area_short_input
    },
)

private fun readFunctionCode(area: RegisterArea): Int = when (area) {
    RegisterArea.COIL -> 0x01
    RegisterArea.DISCRETE_INPUT -> 0x02
    RegisterArea.HOLDING_REGISTER -> 0x03
    RegisterArea.INPUT_REGISTER -> 0x04
}

/** 起始地址输入格右侧的参考号小字：“0 基”模式不显示，“参考号”“两者”显示。 */
private fun referenceLabel(settings: UiSettings, area: RegisterArea, addressText: String): String? {
    if (settings.addressStyle == AddressStyle.ZERO_BASED) return null
    val address = addressText.trim().toIntOrNull() ?: return null
    if (address !in 0..0xFFFF) return null
    return modiconLabel(area, address)
}

@Composable
private fun RequestCard(model: WorkbenchViewModel, settings: UiSettings, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val tileMin = if (landscape) MinTouch else 50.dp
    val writing = model.mode == WorkbenchMode.WRITE
    val activeArea = if (writing) model.writeArea else model.area
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(if (landscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 10.dp),
    ) {
        SegmentedTextControl(
            WorkbenchMode.entries,
            model.mode,
            model::selectMode,
            modifier = if (landscape) Modifier else Modifier.width(150.dp),
            small = true,
        ) { stringResource(if (it == WorkbenchMode.READ) R.string.action_read else R.string.action_write) }

        if (writing) {
            SegmentedTextControl(WriteAreas, model.writeArea, model::selectWriteArea, small = landscape) {
                stringResource(if (it == RegisterArea.COIL) R.string.area_coil else R.string.area_holding)
            }
        } else {
            SegmentedTextControl(ReadAreas, model.area, { model.area = it }, small = landscape) { areaShortLabel(it) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberInput(
                stringResource(R.string.field_unit),
                model.unit,
                Modifier.weight(if (landscape) 0.7f else 0.8f),
                error = model.fieldErrors[WorkbenchField.UNIT]?.deep(),
                minHeight = tileMin,
                readOnly = model.busy,
            ) { model.unit = it }
            NumberInput(
                stringResource(if (landscape) R.string.field_address else R.string.field_start_address),
                model.address,
                Modifier.weight(1.3f).testTag(WorkbenchAddressTag),
                error = model.fieldErrors[WorkbenchField.ADDRESS]?.deep(),
                sub = referenceLabel(settings, activeArea, model.address),
                minHeight = tileMin,
                readOnly = model.busy,
            ) { model.address = it }
            if (!writing) {
                NumberInput(
                    stringResource(R.string.field_quantity),
                    model.quantity,
                    Modifier.weight(1f),
                    error = model.fieldErrors[WorkbenchField.QUANTITY]?.deep(),
                    minHeight = tileMin,
                    readOnly = model.busy,
                ) { model.quantity = it }
            } else if (model.writeArea == RegisterArea.HOLDING_REGISTER) {
                FieldTileMenu(
                    stringResource(R.string.field_format),
                    model.writeFormat,
                    WriteFormat.entries,
                    { stringResource(if (it == WriteFormat.DEC) R.string.format_dec else R.string.format_hex) },
                    model::selectWriteFormat,
                    Modifier.weight(1f).testTag("write-format"),
                    mono = false,
                    minHeight = tileMin,
                )
            }
        }

        if (writing) WriteValues(model, landscape) else if (!landscape) ReadButtons(model)
        if (writing && !landscape) {
            WritePreview(model)
            WriteButtons(model)
        }
    }
}

@Composable
private fun ReadButtons(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 读取在途：按钮 loading（“正在读取…”）；写入 / 回读在途：读取不可点（§7.4 同类按钮互斥）。
        PrimaryButton(
            stringResource(R.string.action_read_fc, fcLabel(readFunctionCode(model.area))),
            model::read,
            Modifier.weight(1.4f).testTag(WorkbenchReadButtonTag),
            enabled = !model.busy,
            compact = true,
            loading = model.reading,
            loadingText = stringResource(R.string.wb_reading),
        )
        val seconds = secondsLabel(model.pollIntervalMs)
        SecondaryButton(
            stringResource(if (model.polling) R.string.poll_running else R.string.poll_idle, seconds),
            { model.changePolling(!model.polling) },
            Modifier.weight(1f),
            enabled = !model.busy,
            compact = true,
            active = model.polling,
            icon = { RefreshIcon(if (model.polling) colors.accentText else colors.muted) },
        )
    }
}

@Composable
private fun WriteValues(model: WorkbenchViewModel, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val coil = model.writeArea == RegisterArea.COIL
    val count = if (coil) model.writeCoils.size else model.writeWords.size
    val multi = count > 1 || model.forceMultiple
    val fc = when {
        coil && multi -> 0x0F
        coil -> 0x05
        multi -> 0x10
        else -> 0x06
    }
    val label = when {
        count == 1 && !model.forceMultiple -> stringResource(R.string.write_values_single, fcLabel(fc))
        count == 1 -> stringResource(R.string.write_values_forced, count, fcLabel(fc))
        else -> stringResource(R.string.write_values_auto, count, fcLabel(fc))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (count > 1) TextLink(stringResource(R.string.write_remove), model::removeValue)
        TextLink(stringResource(R.string.write_add), model::addValue, color = colors.txText)
    }
    val start = model.address.trim().toIntOrNull()
    val tileMin = if (landscape) MinTouch else 50.dp
    val indices = (0 until count).toList()
    indices.chunked(3).forEach { chunk ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chunk.forEach { index ->
                val cellLabel = start?.let { (it + index).toString() } ?: "—"
                if (coil) {
                    val on = model.writeCoils[index]
                    ChoiceTile(
                        cellLabel,
                        if (on) "ON" else "OFF",
                        { model.toggleCoil(index) },
                        Modifier.weight(1f),
                        minHeight = tileMin,
                        mono = true,
                        valueColor = if (on) colors.txText else colors.muted,
                    )
                } else {
                    NumberInput(
                        cellLabel,
                        model.writeWords[index],
                        Modifier.weight(1f),
                        minHeight = tileMin,
                        keyboardType = if (model.writeFormat == WriteFormat.HEX) KeyboardType.Ascii else KeyboardType.Number,
                    ) { model.setWordText(index, it) }
                }
            }
            repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 将要发送的整帧（变化字节琥珀色、CRC 灰）。输入不合法时不显示。 */
@Composable
private fun WritePreview(model: WorkbenchViewModel) {
    val planned = model.currentPlan()
    if (planned is PlanResult.Ok) HexFrame(planned.plan.frame, highlight = planned.plan.payload, compact = true)
}

/** “预览并写入 · FCxx” + “强制 16/15”。 */
@Composable
private fun WriteButtons(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val coil = model.writeArea == RegisterArea.COIL
    val count = if (coil) model.writeCoils.size else model.writeWords.size
    val multi = count > 1 || model.forceMultiple
    val fc = when {
        coil && multi -> 0x0F
        coil -> 0x05
        multi -> 0x10
        else -> 0x06
    }
    val spacing = LocalSpacing.current
    // §1-P1：结果出现在操作位置——写入按钮正上方（S2-01）。
    WriteResult(model)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
        PrimaryButton(
            stringResource(R.string.write_preview, fcLabel(fc)),
            model::requestWrite,
            Modifier.weight(1f).testTag(WorkbenchWriteButtonTag),
            enabled = !model.busy,
            compact = true,
            loading = model.writing,
            loadingText = stringResource(R.string.wb_writing),
        )
        SecondaryButton(
            stringResource(R.string.write_force, if (coil) "15" else "16"),
            model::toggleForceMultiple,
            Modifier.width(92.dp),
            enabled = !model.busy,
            compact = true,
            fontSize = 12.sp,
            active = model.forceMultiple,
            icon = { GearIcon(if (model.forceMultiple) colors.accentText else colors.muted) },
        )
    }
}

@Composable
private fun WriteResult(model: WorkbenchViewModel) {
    val notice = model.writeNotice
    ResultBanner(
        notice = notice?.let { ResultNotice(it.kind, it.text.deep(), it.id) },
        onReadBack = if (notice?.canReadBack == true) model::readBack else null,
        readBackLoading = model.readingBack,
        onRetry = if (notice?.canRetry == true) model::retryWrite else null,
        onDismiss = model::dismissWriteNotice,
    )
}

/** 字段级错误已经写在对应输入格下面时，不再在卡下重复一遍；只有值格这类没有专属输入格的错误显示在这里。 */
@Composable
private fun FormError(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val error = model.formError ?: return
    if (model.fieldErrors.isNotEmpty()) return
    Text(error.deep(), color = colors.error, style = ModbusType.label, modifier = Modifier)
}

/**
 * [UiText.resolve] 只解析一层参数；工作台的提示会把异常说明、字段名、值列表作为参数嵌进句子里
 * （“设备拒绝写入（异常 02 非法地址），…”“地址应在 0–65535 之间”），这里逐层解析。List 参数用“, ”连起来。
 */
@Composable
internal fun UiText.deep(): String = when (this) {
    is UiText.Of -> stringResource(id, *args.map { arg -> deepArg(arg) }.toTypedArray())
    else -> resolve()
}

@Composable
private fun deepArg(arg: Any): Any = when (arg) {
    is UiText -> arg.deep()
    is List<*> -> arg.map { item -> if (item is UiText) item.deep() else item.toString() }.joinToString(", ")
    else -> arg
}

// ---- 读取结果 ----

@Composable
private fun ReadSection(model: WorkbenchViewModel, settings: UiSettings, format: NumberFormat, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    when (val state = model.readState) {
        ReadState.None -> Text(
            stringResource(R.string.wb_no_result),
            color = colors.muted,
            style = ModbusType.bodyS,
            modifier = Modifier,
        )
        is ReadState.Failed -> {
            // S2-04：错误放表上方（ErrorBanner：原因 + 怎么办 + 详情折叠），上一次成功的表保留并标“上次成功 hh:mm:ss”。
            ErrorBanner(
                reason = state.message.deep(),
                actionLabel = stringResource(R.string.feedback_retry),
                onAction = if (model.busy) null else model::read,
                details = state.details?.deep() ?: state.badge.deep(),
            )
            val last = model.lastGood
            if (last != null) {
                LastGoodHead(model)
                ResultTable(model, last, settings, format, landscape)
            }
        }
        is ReadState.Ok -> {
            val result = state.result
            if (!landscape) {
                ResultHead(model, result, format)
            }
            ResultTable(model, result, settings, format, landscape)
        }
    }
}

@Composable
private fun LastGoodHead(model: WorkbenchViewModel) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val at = model.lastGoodAtMillis ?: return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.wb_last_ok, ClockFormat.get()!!.format(Date(at))),
            color = colors.muted,
            style = ModbusType.caption,
            modifier = Modifier.weight(1f).testTag(WorkbenchLastGoodTag),
        )
        ReadProgress(model)
    }
}

/** S2-03：读取在途时表头右侧 16dp 进度圈，旧表不清空。 */
@Composable
private fun ReadProgress(model: WorkbenchViewModel) {
    if (!model.reading) return
    CircularProgressIndicator(
        modifier = Modifier.size(ModbusSize.progress),
        color = LocalModbusColors.current.muted,
        strokeWidth = ModbusSize.progressStroke,
    )
}

@Composable
private fun ResultHead(model: WorkbenchViewModel, result: ReadResult, format: NumberFormat) {
    val colors = LocalModbusColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SemanticBadge(SemanticKind.RX_LOCAL, stringResource(R.string.result_ok))
            Text(
                stringResource(R.string.result_stats, result.elapsedMs, result.values.size),
                style = Mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.muted,
                maxLines = 1,
            )
            ReadProgress(model)
        }
        if (!result.isBits) {
            ChipRow(NumberFormat.entries, { it == format }, { it.name }, { model.formatOverride = it }, xs = true)
        }
    }
}

@Composable
private fun ResultTable(
    model: WorkbenchViewModel,
    result: ReadResult,
    settings: UiSettings,
    format: NumberFormat,
    landscape: Boolean,
) {
    val on = stringResource(R.string.coil_on)
    val off = stringResource(R.string.coil_off)
    val formatted = if (result.isBits) emptyList() else formatRegisterValues(result.values, format, settings.hexPrefix)
    val editable = result.area == RegisterArea.COIL || result.area == RegisterArea.HOLDING_REGISTER
    val rows = result.values.mapIndexed { index, value ->
        val address = result.start + index
        RegisterRow(
            address = address,
            addressText = if (settings.addressStyle == AddressStyle.MODICON) modiconLabel(result.area, address) else null,
            value = if (result.isBits) (if (value != 0) on else off) else formatted[index],
            sub = if (settings.addressStyle == AddressStyle.BOTH) modiconLabel(result.area, address) else null,
            hex = if (result.isBits) null else hexWord(value, settings.hexPrefix),
            flashAt = result.changedAt[address],
            editable = editable,
        )
    }
    val header = if (landscape && !result.isBits) {
        stringResource(R.string.table_value_format, format.name)
    } else {
        stringResource(R.string.table_value)
    }
    val cycle = NumberFormat.entries[(format.ordinal + 1) % NumberFormat.entries.size]
    RegisterTable(
        rows,
        valueHeader = header,
        showEdit = !landscape,
        rowHeight = if (landscape) 36.dp else 40.dp,
        addressWidth = if (landscape) 62.dp else 70.dp,
        hexWidth = if (landscape) 70.dp else 84.dp,
        onHeaderValueClick = if (landscape && !result.isBits) ({ model.formatOverride = cycle }) else null,
        onRowClick = { row -> model.editRow(result, row.address) },
    )
}

// ---- 最近写入 ----

private val ClockFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }

@Composable
private fun RecentSection(model: WorkbenchViewModel, settings: UiSettings) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    // 标题行和其它小标题一样只占一行字高（「清空」的 48dp 热区上下溢出，不撑布局），标题 → 卡片 8dp，
    // 上一张卡 → 标题只剩分组间距 16（原来两边各多出约 15dp，E-20261028-ux1 C3）。
    Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recent_title), color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextLink(stringResource(R.string.recent_clear), model::clearRecentWrites, Modifier.overhangHeight(18.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface1, shape)
                .border(1.dp, colors.line, shape),
        ) {
            if (model.recentWrites.isEmpty()) {
                Text(
                    stringResource(R.string.recent_empty),
                    color = colors.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                )
            }
            model.recentWrites.forEachIndexed { index, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clickable { model.reuse(entry) }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val kind = when (entry.outcome) {
                        WriteOutcome.OK -> SemanticKind.RX_LOCAL
                        WriteOutcome.SENT -> SemanticKind.DROPPED
                        WriteOutcome.UNKNOWN -> SemanticKind.AMBIGUOUS
                        else -> SemanticKind.ERROR
                    }
                    SemanticBadge(kind, outcomeLabel(entry))
                    Text(
                        writeSummary(entry, stringResource(R.string.recent_broadcast), settings.addressStyle),
                        style = Mono,
                        color = colors.text,
                        maxLines = 2,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    )
                    Text(
                        ClockFormat.get()!!.format(Date(entry.atMillis)),
                        style = Mono,
                        color = colors.muted,
                        maxLines = 1,
                    )
                }
                if (index != model.recentWrites.lastIndex) HorizontalDivider(color = colors.rowLine)
            }
        }
    }
}

@Composable
private fun outcomeLabel(entry: RecentWrite): String = when (entry.outcome) {
    WriteOutcome.OK -> stringResource(R.string.result_ok)
    WriteOutcome.SENT -> stringResource(R.string.badge_sent)
    WriteOutcome.EXCEPTION -> stringResource(R.string.badge_exception, entry.exceptionCode ?: 0)
    WriteOutcome.TIMEOUT -> stringResource(R.string.badge_timeout)
    WriteOutcome.UNKNOWN -> stringResource(R.string.badge_unknown)
    WriteOutcome.DISCONNECTED -> stringResource(R.string.badge_disconnected)
    WriteOutcome.INVALID -> stringResource(R.string.badge_invalid)
}

/**
 * “FC06 U1 0 · 40001 = 123”；多个值：“FC16 U1 100 · 40101 × 3”；广播（Unit 0）写“FC06 广播 5 · 40006 = 123”。
 * 地址跟随设置里的“地址显示”（0 基 / 参考号 / 两者），和结果表一致。
 */
internal fun writeSummary(entry: RecentWrite, broadcastLabel: String, style: AddressStyle): String {
    val target = if (entry.unit == 0) broadcastLabel else "U${entry.unit}"
    val modicon = modiconLabel(entry.area, entry.address)
    val where = when (style) {
        AddressStyle.ZERO_BASED -> entry.address.toString()
        AddressStyle.MODICON -> modicon
        AddressStyle.BOTH -> "${entry.address} · $modicon"
    }
    val head = "${fcLabel(entry.functionCode)} $target $where"
    val count = entry.words.size + entry.bits.size
    return when {
        count != 1 -> "$head × $count"
        entry.area == RegisterArea.COIL -> "$head = ${if (entry.bits[0]) "ON" else "OFF"}"
        else -> "$head = ${entry.words[0]}"
    }
}

// ---- 写入确认 ----

@Composable
private fun WriteConfirm(pending: PendingWrite, model: WorkbenchViewModel, settings: UiSettings) {
    WriteConfirmSheet(pending, settings, onCoil = model::setPendingCoil, onConfirm = model::confirmWrite, onDismiss = model::dismissPending)
}

/**
 * 写入确认弹窗（工作台和面板共用，不绕过）：先看到将要发的整帧，点“发送写入”才发，取消不发；
 * 广播（Unit 0）红色按钮 + 红色徽标。面板用 [context]（组件名 / 预设值说明）、[warningOverride]（位写入的“先读后写”提示）
 * 和 [wordText]（位写入按 16 进制显示整字）补充上下文，其余完全一致。
 */
@Composable
internal fun WriteConfirmSheet(
    pending: PendingWrite,
    settings: UiSettings,
    onCoil: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    context: String? = null,
    warningOverride: String? = null,
    wordText: (Int) -> String = Int::toString,
    /** 不开弹层，直接画弹层里的内容（截图 / 测试用；真实界面总是弹层）。 */
    inline: Boolean = false,
) {
    val plan = pending.plan
    val coil = plan.area == RegisterArea.COIL
    val multi = plan.words.size + plan.bits.size > 1
    val haptic = LocalHapticFeedback.current
    val title = stringResource(
        when {
            coil && multi -> R.string.confirm_title_coils
            coil -> R.string.confirm_title_coil
            multi -> R.string.confirm_title_registers
            else -> R.string.confirm_title_register
        },
    )
    val confirmLabel = stringResource(if (pending.broadcast) R.string.action_confirm_broadcast else R.string.action_confirm)
    val dismissLabel = stringResource(R.string.action_cancel)
    // Unit 0 = 广播：红色“广播 · 所有从站”，不再显示某个 Unit。
    val badge = if (pending.broadcast) {
        stringResource(R.string.confirm_badge_broadcast)
    } else {
        stringResource(R.string.confirm_badge, plan.unit, plan.address, modiconLabel(plan.area, plan.address))
    }
    val badgeKind = if (pending.broadcast) SemanticKind.ERROR else SemanticKind.DROPPED
    val caption = stringResource(R.string.confirm_frame_caption, plan.frame.size, fcLabel(plan.functionCode))
    val warning = warningOverride ?: stringResource(confirmWarningRes(pending.broadcast, coil, settings.coilExtraWarning))
    val confirm = {
        if (settings.vibration) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onConfirm()
    }
    val content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        if (context != null) {
            Text(
                context, color = LocalModbusColors.current.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("confirm-context"),
            )
        }
        ConfirmValues(pending, onCoil, wordText)
    }
    if (inline) {
        ConfirmSheetBody(
            title, confirmLabel, dismissLabel, confirm, onDismiss,
            badge = badge, badgeKind = badgeKind, confirmDanger = pending.broadcast,
            frame = plan.frame, frameHighlight = plan.payload, frameCaption = caption, warning = warning, content = content,
        )
    } else {
        ConfirmSheet(
            title, confirmLabel, dismissLabel, confirm, onDismiss,
            badge = badge, badgeKind = badgeKind, confirmDanger = pending.broadcast,
            frame = plan.frame, frameHighlight = plan.payload, frameCaption = caption, warning = warning, content = content,
        )
    }
}

/** 写入确认里的提示文案：线圈额外提示可在「我的 · 安全」里关掉；广播提示（红色按钮那一档）不受影响。 */
internal fun confirmWarningRes(broadcast: Boolean, coil: Boolean, coilExtra: Boolean): Int = when {
    broadcast && coil && coilExtra -> R.string.confirm_warning_broadcast_coil
    broadcast -> R.string.confirm_warning_broadcast
    coil && coilExtra -> R.string.confirm_warning_coil
    else -> R.string.confirm_warning
}

@Composable
private fun ConfirmValues(pending: PendingWrite, onCoil: (Boolean) -> Unit, wordText: (Int) -> String) {
    val colors = LocalModbusColors.current
    val plan = pending.plan
    val coil = plan.area == RegisterArea.COIL
    val unknown = stringResource(R.string.confirm_current_unknown)
    val ageText = pending.currentAgeMs?.let {
        stringResource(if (pending.currentFromRead) R.string.confirm_current_age_read else R.string.confirm_current_age_write, elapsedText(it))
    }
    fun show(value: Int?, isCoil: Boolean): String = when {
        value == null -> "—"
        isCoil -> if (value != 0) "ON" else "OFF"
        else -> wordText(value)
    }
    if (plan.words.size + plan.bits.size == 1) {
        val currentValue = pending.current.firstOrNull()
        val newValue = if (coil) (if (plan.bits[0]) 1 else 0) else plan.words[0]
        // “当前”来自上次写入而不是真实读取时，标签写“上次写入”，和“读取值”分开（技术总工要求）。
        val label = when {
            pending.broadcast -> stringResource(R.string.confirm_address)
            currentValue == null -> stringResource(R.string.confirm_current)
            pending.currentFromRead -> stringResource(R.string.confirm_label_read)
            else -> stringResource(R.string.confirm_label_last_write)
        }
        val same = !pending.broadcast && currentValue != null && currentValue == newValue
        ConfirmValueRow(
            leftLabel = label,
            // 广播没有“当前值”：只显示地址，不写“未读取”。
            leftValue = if (pending.broadcast) plan.address.toString() else show(currentValue, coil),
            leftNote = when {
                pending.broadcast -> null
                currentValue == null -> unknown
                else -> ageText
            },
            rightLabel = if (coil) null else stringResource(R.string.confirm_new),
            rightValue = if (coil) null else wordText(plan.words[0]),
            rightControl = if (coil) {
                {
                    SegmentedTextControl(listOf(false, true), plan.bits[0], onCoil, Modifier.fillMaxWidth()) {
                        if (it) "ON" else "OFF"
                    }
                }
            } else {
                null
            },
            hint = if (same) {
                stringResource(if (pending.currentFromRead) R.string.confirm_same_read else R.string.confirm_same_write)
            } else {
                null
            },
        )
    } else {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val total = plan.words.size + plan.bits.size
            Text(stringResource(R.string.confirm_multi_head, plan.address, total), color = colors.muted, fontSize = 12.sp)
            if (!pending.broadcast && ageText != null) Text(ageText, color = colors.dim, fontSize = 11.sp)
            // S2-08：超过 6 行时“还有 n 个，展开”；全部展开后 Sheet 内滚动，按钮固定在底（ConfirmSheet）。
            var expanded by remember(pending.plan) { mutableStateOf(false) }
            val shown = if (expanded) total else minOf(total, ConfirmPreviewRows)
            // 列宽按字号换算（5 位数 = 5 个等宽字符），箭头才会在每一行上下对齐，放大字体也一样。
            val addressWidth = with(LocalDensity.current) { 52.sp.toDp() }
            val valueWidth = with(LocalDensity.current) { 46.sp.toDp() }
            repeat(shown) { index ->
                val new = if (coil) (if (plan.bits[index]) 1 else 0) else plan.words[index]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((plan.address + index).toString(), style = Mono, color = colors.dim, modifier = Modifier.width(addressWidth))
                    Text(show(pending.current.getOrNull(index), coil), style = Mono, color = colors.muted, modifier = Modifier.width(valueWidth))
                    ArrowGlyph(colors.dim, Modifier.size(width = 14.dp, height = 10.dp))
                    // S2-09：新值用 text 色 SemiBold，变化靠“当前 → 新值”箭头表达，不着琥珀。
                    Text(show(new, coil), style = Mono.copy(fontWeight = FontWeight.SemiBold, lineHeight = Mono.lineHeight), color = colors.text)
                }
            }
            if (total > ConfirmPreviewRows) {
                TextLink(
                    if (expanded) {
                        stringResource(R.string.wb_confirm_collapse)
                    } else {
                        stringResource(R.string.wb_confirm_show_all, total - shown)
                    },
                    { expanded = !expanded },
                    modifier = Modifier.testTag(WorkbenchConfirmExpandTag),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 没有会话
// ---------------------------------------------------------------------------------------------

@Composable
private fun NoSessionWorkbench() {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val openConnection = LocalOpenConnection.current
    Column(Modifier.screenFrame(), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        PageHeader(stringResource(R.string.title_workbench))
        SessionStrip()
        // §7.1 空态：为什么空（标题）+ 能做什么、怎么做（一句）+ 一个动词按钮；左对齐、不加卡片，上下使用 v2 empty 留白。
        Column(
            Modifier.fillMaxWidth().padding(vertical = spacing.empty).testTag(WorkbenchEmptyTag),
            verticalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            Text(stringResource(R.string.wb_empty_title), color = colors.text, style = ModbusType.titleS)
            Text(stringResource(R.string.wb_empty_body), color = colors.muted, style = ModbusType.bodyS)
            Spacer(Modifier.height(spacing.xs))
            SecondaryButton(
                stringResource(R.string.wb_go_connect),
                openConnection,
                Modifier.testTag(WorkbenchGoConnectTag),
            )
        }
    }
}

/** 多值确认默认显示的行数（S2-08，稿面 6 行）。 */
private const val ConfirmPreviewRows = 6

internal const val WorkbenchScrollTag = "workbench-scroll"
internal const val WorkbenchDockSpacerTag = "workbench-dock-spacer"
internal const val WorkbenchAddressTag = "workbench-address"
internal const val WorkbenchReadButtonTag = "workbench-read"
internal const val WorkbenchWriteButtonTag = "workbench-write"
internal const val WorkbenchLastGoodTag = "workbench-last-good"
internal const val WorkbenchEmptyTag = "workbench-empty"
internal const val WorkbenchGoConnectTag = "workbench-go-connect"
internal const val WorkbenchConfirmExpandTag = "workbench-confirm-expand"

/** “74 秒” / “12 分钟” / “3 小时” / “6 天”（单位规则见 [ElapsedAge]）。 */
@Composable
internal fun elapsedText(elapsedMs: Long): String {
    val age = io.github.xingyingyuzhui.modbusmobile.application.ElapsedAge.of(elapsedMs)
    val amount = age.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return when (age.unit) {
        io.github.xingyingyuzhui.modbusmobile.application.AgeUnit.SECONDS -> stringResource(R.string.age_seconds, amount)
        io.github.xingyingyuzhui.modbusmobile.application.AgeUnit.MINUTES -> stringResource(R.string.age_minutes, amount)
        io.github.xingyingyuzhui.modbusmobile.application.AgeUnit.HOURS -> stringResource(R.string.age_hours, amount)
        io.github.xingyingyuzhui.modbusmobile.application.AgeUnit.DAYS -> stringResource(R.string.age_days, amount)
    }
}
