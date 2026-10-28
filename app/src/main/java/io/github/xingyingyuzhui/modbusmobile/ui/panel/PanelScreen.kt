package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.xingyingyuzhui.modbusmobile.ui.LocalOpenConnection
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalPanelRuntime
import io.github.xingyingyuzhui.modbusmobile.ui.LocalProjectRepository
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.PencilIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.LocalOpenCurve
import io.github.xingyingyuzhui.modbusmobile.ui.LocalOpenWizard
import io.github.xingyingyuzhui.modbusmobile.ui.LocalReportCurveTarget
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing

@Composable
fun PanelScreen() {
    val repository = LocalProjectRepository.current
    val runtime = LocalPanelRuntime.current
    val session = LocalSession.current
    val model = viewModel { PanelViewModel(repository, runtime, session = session) }
    val state by model.state.collectAsStateWithLifecycle()
    val snapshots by model.snapshots.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val paused by model.paused.collectAsStateWithLifecycle()
    val selected by model.selectedPanelId.collectAsStateWithLifecycle()
    // 页面在前台（STARTED 以上）才轮询；离开页面 / 退到后台立即停。
    LifecycleStartEffect(model) {
        model.setActive(true)
        onStopOrDispose { model.setActive(false) }
    }
    val edit by model.edit.collectAsStateWithLifecycle()
    val openWizard = LocalOpenWizard.current
    val openCurve = LocalOpenCurve.current
    val editing = edit
    // 编辑模式隐藏 dock（v3 2-1）。离开面板页去向导时不复原：向导本来就不显示 dock，
    // 回来时也不会先闪一下 dock；退出编辑（完成 / 放弃）时这里报 false。
    val reportEditing = io.github.xingyingyuzhui.modbusmobile.ui.LocalReportPanelEditing.current
    androidx.compose.runtime.LaunchedEffect(editing != null) {
        reportEditing(editing != null)
    }
    if (editing != null) {
        PanelEditor(
            edit = editing,
            snapshots = snapshots,
            actions = EditActions(
                onRename = model::rename,
                onColumns = model::setColumns,
                onMove = model::moveWidget,
                onSpan = model::setSpan,
                onDelete = model::deleteWidget,
                onDeleteHidden = model::deleteHiddenWidgets,
                onSelectPanel = model::selectEditPanel,
                onAddPanel = model::addPanel,
                onDeletePanel = model::deletePanel,
                onDone = model::save,
                onCancel = model::cancelEdit,
                onAddWidget = { model.startWizard(null); openWizard() },
                onOpenWidget = { id -> model.startWizard(id); if (model.wizard.value != null) openWizard() },
            ),
        )
    } else {
        PanelContent(
            state = state,
            snapshots = snapshots,
            history = model.history,
            stats = stats,
            paused = paused,
            selectedPanelId = selected,
            onSelectPanel = model::selectPanel,
            onTogglePause = model::togglePause,
            onEdit = if (model.canEdit) ({ model.startEdit(selected) }) else null,
            onAdd = if (model.canEdit) ({ model.startEdit(selected); model.startWizard(null); openWizard() }) else null,
            onOpenCurve = openCurve,
            writer = model.writer,
        )
    }
}

/**
 * 面板页（v3，docs/design/panel-v3.html ①）。竖屏：页头（⤢ ✎）+ 一行「面板页签 · 轮询胶囊」+ 2 列网格；
 * 横屏：一行页头（面板 + 页签 + 胶囊 + ⤢ ✎）+ 4 列网格。没有组件时是空状态引导。没有功能的入口不画。
 */
@Composable
fun PanelContent(
    state: PanelUiState,
    snapshots: Map<String, PointSnapshot> = emptyMap(),
    stats: PollStats? = null,
    paused: Boolean = false,
    selectedPanelId: String? = null,
    onSelectPanel: (String) -> Unit = {},
    onTogglePause: () -> Unit = {},
    /** 为 null 时不画 ✎（没有仓库 / 工程文件只读 / 还没有面板）。 */
    onEdit: (() -> Unit)? = null,
    /** 空面板里的“添加组件”：为 null（没有仓库 / 只读）时不画。 */
    onAdd: (() -> Unit)? = null,
    /** 曲线卡的样本历史；为空时曲线卡显示“暂无数据”。 */
    history: io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory? = null,
    /** 曲线卡 ⤢：打开该曲线组件的全屏页；为 null 时曲线卡不画 ⤢。 */
    onOpenCurve: ((String) -> Unit)? = null,
    /** 开关 / 按钮的写入入口；为 null（截图 / 没有会话能力）时开关 / 按钮只显示、不可点。 */
    writer: PanelWriteController? = null,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val sessionState by LocalSession.current.state.collectAsStateWithLifecycle()
    val masterRunning = (sessionState as? SessionState.Running)?.role == Role.MASTER
    val slaveRunning = (sessionState as? SessionState.Running)?.role == Role.SLAVE

    val ready = state as? PanelUiState.Ready
    val panels = ready?.project?.panels.orEmpty()
    val current: Panel? = panels.firstOrNull { it.id == selectedPanelId } ?: panels.firstOrNull()
    val widgets = current?.widgets?.filter { it.isUsable }.orEmpty()
    // 当前面板里第一个曲线组件：报给外壳，横屏自动全屏用（没有曲线就是 null，不会自动进空页）
    val reportCurve = LocalReportCurveTarget.current
    val curveTarget = widgets.firstOrNull { it.curve != null }?.id
    androidx.compose.runtime.DisposableEffect(curveTarget) {
        reportCurve(curveTarget)
        onDispose { reportCurve(null) }
    }
    val anyWidgets = panels.any { p -> p.widgets.any { it.isUsable } }
    // 只有按钮的工程没有任何点位要读：不显示“轮询中 / 暂停”，也不提示“还没开始轮询”
    val anyPolled = panels.any { p -> p.widgets.any { it.isUsable && it.points.isNotEmpty() } }
    val columns = if (landscape) 4 else (current?.columns ?: 2)
    // 页头 ⤢（稿面 b2-1）：打开当前面板第一个曲线的全屏；没有曲线 / 没有处理函数就不画
    val expandEntry: (() -> Unit)? = curveTarget?.let { id -> onOpenCurve?.let { open -> { open(id) } } }
    val editEntry: (() -> Unit)? = if (ready != null && !ready.readOnly && panels.isNotEmpty()) onEdit else null

    writer?.let { w -> w.pending?.let { PanelWriteSheet(it, w) } }
    val openConnection = LocalOpenConnection.current
    val pill = pollPillState(masterRunning, slaveRunning, anyPolled, stats, paused)
    // 有过期卡时每秒更新“现在”，卡片上的“读取失败 · 12 秒前的值”跟着走
    val anyStale = snapshots.values.any { it.quality == io.github.xingyingyuzhui.modbusmobile.application.panel.Quality.STALE }
    val now = rememberPanelNow(anyStale, snapshots)
    Box(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalPanelNow provides now) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(start = pageStart(), end = spacing.page)) {
                if (landscape) {
                    LandscapeHeader(panels, current, pill, stats, onSelectPanel, onTogglePause, openConnection, editEntry, expandEntry)
                } else {
                    PageHeader(stringResource(R.string.title_panel)) {
                        if (expandEntry != null) ExpandEntryButton(expandEntry)
                        if (editEntry != null) EditEntryButton(editEntry)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (panels.isNotEmpty()) PanelTabs(panels, current, onSelectPanel)
                        }
                        PollPill(pill, stats, onTogglePause, openConnection)
                    }
                }
            }
            val emptyState = state is PanelUiState.Ready && !anyWidgets && state.warnings.isEmpty() && writer?.notice == null
            if (emptyState) {
                PanelEmptyState(
                    masterRunning = masterRunning,
                    onAdd = if (onAdd != null && !(state as PanelUiState.Ready).readOnly) onAdd else null,
                    onOpenConnection = openConnection,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = pageStart(), end = spacing.page, bottom = dockClearance()),
                )
            } else Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = pageStart(), end = spacing.page, top = 2.dp, bottom = dockClearance()),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                when (state) {
                    PanelUiState.Loading -> Unit
                    is PanelUiState.Corrupt -> NoticeCard(
                        title = stringResource(R.string.panel_corrupt_title),
                        body = stringResource(
                            if (state.copySaved) R.string.panel_corrupt_body_saved else R.string.panel_corrupt_body,
                            state.reason,
                        ),
                        tag = "panel-corrupt",
                    )
                    is PanelUiState.Ready -> {
                        state.warnings.forEach { NoticeCard(title = null, body = it, tag = "panel-warning") }
                        writer?.let { w -> w.notice?.let { WriteNotice(it, w::clearNotice) } }
                        if (!anyWidgets) {
                            PanelEmptyState(
                                masterRunning = masterRunning,
                                onAdd = if (onAdd != null && !state.readOnly) onAdd else null,
                                onOpenConnection = openConnection,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
                            )
                        } else {
                            PanelGrid(
                                columns = columns,
                                spans = widgets.map { it.spanColumns },
                                spanRows = widgets.map { it.spanRows },
                                modifier = Modifier.fillMaxWidth().testTag("panel-grid"),
                                gap = spacing.s,
                            ) { index ->
                                val widget: Widget = widgets[index]
                                if (widget.curve != null) {
                                    io.github.xingyingyuzhui.modbusmobile.ui.curve.CurveWidgetCard(
                                        widget, history,
                                        onFullscreen = onOpenCurve?.let { open -> { open(widget.id) } },
                                    )
                                } else {
                                    WidgetCard(
                                        widget, snapshots[widget.point!!.id],
                                        write = writer?.let { w -> cardWrite(w, widget, masterRunning, slaveRunning) },
                                        snapshots = snapshots,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
      }
        writer?.entry?.let { RowEntrySheet(it, writer) }
    }
}

/** 横屏页头（v3 1-6）：「面板」18/700 + 页签 + 轮询胶囊 + ⤢ ✎ 收进一行（热区 48），与竖屏同一套组件。 */
@Composable
private fun LandscapeHeader(
    panels: List<Panel>,
    current: Panel?,
    pill: PollPillState,
    stats: PollStats?,
    onSelect: (String) -> Unit,
    onTogglePause: () -> Unit,
    onOpenConnection: () -> Unit,
    onEdit: (() -> Unit)?,
    onExpand: (() -> Unit)? = null,
) {
    val colors = LocalModbusColors.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.title_panel),
            color = colors.text,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp),
        )
        Box(Modifier.weight(1f)) {
            if (panels.isNotEmpty()) PanelTabs(panels, current, onSelect)
        }
        PollPill(pill, stats, onTogglePause, onOpenConnection)
        if (onExpand != null) ExpandEntryButton(onExpand)
        if (onEdit != null) EditEntryButton(onEdit)
    }
}

@Composable
private fun ExpandEntryButton(onExpand: () -> Unit) {
    HeaderIconButton(stringResource(R.string.curve_open_fullscreen), onExpand, Modifier.testTag("panel-expand")) {
        io.github.xingyingyuzhui.modbusmobile.ui.components.ExpandIcon(LocalModbusColors.current.muted, size = 16.dp)
    }
}

@Composable
private fun EditEntryButton(onEdit: () -> Unit) {
    HeaderIconButton(stringResource(R.string.panel_edit_open), onEdit, Modifier.testTag("panel-edit")) {
        PencilIcon(LocalModbusColors.current.muted, size = 16.dp)
    }
}

/** 开关 / 按钮在这个会话状态下能不能点；点了只是“请求”，由 [PanelWriteController] 走确认弹窗。 */
@Composable
private fun cardWrite(writer: PanelWriteController, widget: Widget, master: Boolean, slave: Boolean): CardWrite = CardWrite(
    enabled = master,
    reason = stringResource(if (slave) R.string.panel_write_slave else R.string.panel_write_no_master),
    busy = writer.busyWidgetId == widget.id || writer.pending != null,
    onSwitch = { target -> writer.requestSwitch(widget, target) },
    onButton = { writer.requestButton(widget) },
    onRow = { index -> writer.requestRow(widget, index) },
)

@Composable
private fun WriteNotice(notice: PanelWriteNotice, onClear: () -> Unit) {
    val colors = LocalModbusColors.current
    val text = when (notice.kind) {
        WriteNoticeKind.OK -> stringResource(R.string.panel_write_ok, notice.widgetName, notice.summary)
        WriteNoticeKind.SENT -> stringResource(R.string.panel_write_sent, notice.widgetName, notice.summary)
        WriteNoticeKind.EXCEPTION -> stringResource(R.string.panel_write_exception, notice.widgetName, notice.code ?: 0)
        WriteNoticeKind.TIMEOUT -> stringResource(R.string.panel_write_timeout, notice.widgetName)
        WriteNoticeKind.UNKNOWN -> stringResource(R.string.panel_write_unknown, notice.widgetName)
        WriteNoticeKind.DISCONNECTED -> stringResource(R.string.panel_write_disconnected, notice.widgetName)
        WriteNoticeKind.INVALID -> stringResource(R.string.panel_write_invalid, notice.widgetName)
        WriteNoticeKind.READ_FAILED -> stringResource(R.string.panel_write_read_failed, notice.widgetName)
        WriteNoticeKind.CHANGED -> stringResource(R.string.panel_write_changed, notice.widgetName)
        WriteNoticeKind.NOT_MASTER -> stringResource(R.string.panel_write_not_master, notice.widgetName)
        WriteNoticeKind.BAD_STATE -> stringResource(R.string.panel_write_bad_state, notice.widgetName)
    }
    val color = when (notice.kind) {
        WriteNoticeKind.OK -> colors.rxLocal
        WriteNoticeKind.SENT, WriteNoticeKind.CHANGED, WriteNoticeKind.UNKNOWN, WriteNoticeKind.INVALID -> colors.txText
        else -> colors.error
    }
    ModbusCard(Modifier.fillMaxWidth().testTag("panel-write-notice")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = color, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            io.github.xingyingyuzhui.modbusmobile.ui.components.TextLink(
                stringResource(R.string.panel_write_notice_close), onClear, Modifier.testTag("panel-write-notice-close"),
            )
        }
    }
}

/** 面板写入的确认弹窗：和工作台是同一个 [WriteConfirmSheet]（整帧、取消不发、广播红色），多一行组件上下文和位写入提示。 */
@Composable
private fun PanelWriteSheet(pending: PanelPending, writer: PanelWriteController) {
    val settings = io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings.current
    val widget = pending.widget
    val name = widget.point?.let { widget.displayName(it) } ?: widget.displayTitle
    val bit = pending.bit
    val plan = pending.write.plan
    val row = pending.row
    val context = when {
        row != null -> {
            val rowPoint = widget.rowPoint(row.index)
            stringResource(R.string.panel_write_ctx_row, name, row.index + 1, addressLabel(rowPoint), row.shownValue + rowPoint.unitLabel.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty())
        }
        bit != null -> stringResource(
            R.string.panel_write_ctx_bit, name, bit.bit, if (bit.target) 1 else 0, bit.baseWord, plan.words.firstOrNull() ?: 0,
        )
        widget.kind == io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds.BUTTON -> stringResource(
            R.string.panel_write_ctx_button, name, widget.buttonLabel.ifBlank { stringResource(R.string.panel_button_default) },
        )
        else -> stringResource(R.string.panel_write_ctx_switch, name)
    }
    io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteConfirmSheet(
        pending = pending.write,
        settings = settings,
        onCoil = writer::setPendingCoil,
        onConfirm = writer::confirm,
        onDismiss = writer::dismiss,
        context = context,
        warningOverride = bit?.let { stringResource(R.string.panel_write_bit_warning, it.bit) },
        wordText = if (bit != null || row != null) ({ w -> "0x%04X".format(w) }) else Int::toString,
    )
}

@Composable
private fun NoticeCard(title: String?, body: String, tag: String, action: (@Composable () -> Unit)? = null) {
    val colors = LocalModbusColors.current
    ModbusCard(Modifier.fillMaxWidth().testTag(tag)) {
        if (title != null) {
            Text(title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(body, color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
        action?.invoke()
    }
}

/**
 * 寄存器表某一行的“新值”输入：页内浮层（遮罩 + 贴底面板，不是系统对话框，所以输入框和普通页面一样好用）。
 * 这里只收集数字；点“下一步”才由控制器生成写入计划、弹出和工作台同一个确认弹窗，**确认前什么都不发**。
 * 取消 / 点遮罩 / 返回键都只是关掉输入框。
 */
@Composable
private fun RowEntrySheet(entry: RowEntry, writer: PanelWriteController) {
    val colors = LocalModbusColors.current
    val widget = entry.widget
    val point = widget.point ?: return
    val row = widget.rowPoint(entry.index)
    val snap = writer.snapshotFor(row.id)
    val currentText = snap?.takeIf { it.raw != null }?.text
    var text by androidx.compose.runtime.remember(entry) { androidx.compose.runtime.mutableStateOf(currentText.orEmpty()) }
    var error by androidx.compose.runtime.remember(entry) { androidx.compose.runtime.mutableStateOf<RowEntryError?>(null) }
    androidx.activity.compose.BackHandler(onBack = writer::cancelEntry)
    // 离开面板页（点了 dock 的别的 tab / 系统返回）就收起输入框，回来不会还挂着一个没人管的浮层
    androidx.compose.runtime.DisposableEffect(entry) { onDispose { writer.cancelEntry() } }
    val dock = LocalDockInsets.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Box(Modifier.fillMaxSize().testTag("row-entry")) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = writer::cancelEntry)
                .testTag("row-entry-scrim"),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                // 面板页底部的悬浮 dock 盖在页面内容上面：面板要坐在 dock 上方，否则“取消 / 下一步”会被 dock 挡住、点到别的 tab
                .padding(bottom = dock.bottom)
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface1)
                .border(1.dp, colors.line, shape)
                .clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { }
                .imePadding()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.line))
            Text(
                stringResource(R.string.panel_row_entry_title, widget.displayName(point), entry.index + 1),
                color = colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.testTag("row-entry-title"),
            )
            Text(
                stringResource(R.string.panel_row_entry_sub, row.unitId, addressLabel(row), row.type.name) + " · " +
                    (currentText?.let { stringResource(R.string.panel_row_entry_current, it + row.unitLabel.takeIf { u -> u.isNotBlank() }?.let { u -> " $u" }.orEmpty()) }
                        ?: stringResource(R.string.panel_row_entry_current_none)),
                color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.testTag("row-entry-sub"),
            )
            val errorText = error?.let {
                when (it) {
                    RowEntryError.EMPTY -> stringResource(R.string.panel_row_err_empty)
                    RowEntryError.NOT_A_NUMBER -> stringResource(R.string.panel_row_err_number)
                    RowEntryError.OUT_OF_RANGE -> stringResource(R.string.panel_row_err_range, row.type.name)
                    RowEntryError.BAD_CONVERSION -> stringResource(R.string.panel_row_err_conversion)
                }
            }
            io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput(
                stringResource(R.string.panel_row_entry_value), text, Modifier.fillMaxWidth().testTag("row-entry-value"),
                error = errorText, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ) { text = it.filter { c -> c.isDigit() || c == '-' || c == '.' || c == 'e' || c == 'E' || c == '+' }.take(16); error = null }
            val conv = row.conversion
            Text(
                stringResource(
                    R.string.panel_row_entry_hint,
                    io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec.format(conv.scale, null).orEmpty(),
                    io.github.xingyingyuzhui.modbusmobile.application.panel.ValueCodec.format(conv.offset, null).orEmpty(),
                    row.type.name,
                ),
                color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.testTag("row-entry-hint"),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton(
                    stringResource(R.string.action_cancel), writer::cancelEntry, Modifier.weight(1f).testTag("row-entry-cancel"),
                )
                PrimaryButton(
                    stringResource(R.string.panel_row_entry_next), { error = writer.submitRow(text) },
                    Modifier.weight(1.6f).testTag("row-entry-next"),
                )
            }
        }
    }
}
