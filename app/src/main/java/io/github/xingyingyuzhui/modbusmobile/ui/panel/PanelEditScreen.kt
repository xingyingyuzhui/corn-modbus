package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectEdits
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.ui.components.CloseIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.GearIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.InPageSheet
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PencilIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SmallButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SubPageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.pageStart
import io.github.xingyingyuzhui.modbusmobile.ui.components.TextLink
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ButtonRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.FieldRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSize
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import kotlin.math.roundToInt

/** 编辑模式用到的操作（界面不直接碰 ViewModel，方便测试）。 */
data class EditActions(
    val onRename: (String) -> Unit = {},
    val onColumns: (Int) -> Unit = {},
    val onMove: (widgetId: String, targetId: String) -> Unit = { _, _ -> },
    val onSpan: (widgetId: String, columns: Int, rows: Int) -> Unit = { _, _, _ -> },
    val onDelete: (String) -> Unit = {},
    val onSelectPanel: (String) -> Unit = {},
    val onAddPanel: () -> Unit = {},
    val onDeletePanel: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onCancel: () -> Unit = {},
    /** 打开添加向导 / 用同一个向导配置这个组件。 */
    val onAddWidget: () -> Unit = {},
    val onOpenWidget: (String) -> Unit = {},
    /** 清掉这个面板里看不到的组件（读不懂 / 配置不完整）。放在最后：测试里有按位置传参的。 */
    val onDeleteHidden: () -> Unit = {},
)

/**
 * 面板编辑模式（v3 · docs/design/panel-v3.html ②）：✕ + “编辑 · 面板名” + ⚙（面板设置浮层）+ 琥珀“完成”；
 * 一行提示（编辑时不会写设备）；组件卡是安静的编辑态（surface2 底、灰值、左 ⠿、右上尺寸），网格最后一格是「＋ 添加组件」。
 * 点一张卡 = 选中（琥珀描边 + 勾），底部工具条给出：尺寸、前移 / 后移、配置、删除。长按仍可拖动排序。
 * 面板名称 / 列数 / 新建 / 删除面板在 ⚙ 浮层里。所有修改在草稿里，“完成”才写盘，✕ 放弃（有修改先确认）。
 */
@Composable
fun PanelEditor(
    edit: EditState,
    snapshots: Map<String, PointSnapshot>,
    actions: EditActions,
) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val panel: Panel = edit.draft.panels.firstOrNull { it.id == edit.panelId } ?: edit.draft.panels.first()
    val columns = if (landscape) 4 else panel.columns
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteHidden by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedId by remember(panel.id) { mutableStateOf<String?>(null) }
    val widgets = panel.widgets.filter { it.isUsable }
    val hidden = panel.widgets.size - widgets.size
    // 选中的组件被删掉 / 换了面板：工具条收起
    val selected = widgets.firstOrNull { it.id == selectedId }
    val requestCancel = { if (edit.dirty) confirmDiscard = true else actions.onCancel() }
    BackHandler(onBack = { if (selected != null) selectedId = null else requestCancel() })

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // AL-03：关闭钮和其它二级页的返回钮同一位置（SubPageHeader 统一处理），不再自己减 8。
            SubPageHeader(
                title = stringResource(R.string.panel_edit_title_named, panel.name.ifBlank { "…" }),
                onBack = requestCancel,
                modifier = Modifier.padding(start = pageStart(), end = spacing.page),
                leadingDescription = stringResource(R.string.panel_edit_close),
                leadingTag = "edit-close",
                leadingIcon = { CloseIcon(it, size = ModbusSize.iconM) },
            ) {
                HeaderIconButton(stringResource(R.string.panel_edit_settings), { settingsOpen = true }, Modifier.testTag("edit-settings")) {
                    GearIcon(colors.text, size = 18.dp)
                }
                SmallButton(
                    stringResource(R.string.panel_edit_done), actions.onDone, Modifier.testTag("edit-done"),
                    primary = true, enabled = !edit.saving,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = pageStart(), end = spacing.page, bottom = 6.dp).testTag("edit-hint"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.rxLocal))
                Text(stringResource(R.string.panel_edit_hint), color = colors.muted, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = pageStart(), end = spacing.page, top = 2.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                val error = when (edit.error) {
                    EditError.BLANK_NAME -> R.string.panel_edit_name_blank
                    EditError.SAVE_FAILED -> R.string.panel_edit_error_save
                    EditError.READ_ONLY -> R.string.panel_edit_error_readonly
                    null -> null
                }
                if (error != null) {
                    Text(stringResource(error), color = colors.error, fontSize = 12.sp, modifier = Modifier.testTag("edit-error"))
                }
                if (widgets.isEmpty()) {
                    Text(
                        stringResource(R.string.panel_edit_empty), color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(vertical = 4.dp).testTag("edit-empty"),
                    )
                }
                if (hidden > 0) HiddenWidgetsNote(hidden) { confirmDeleteHidden = true }
                EditGrid(
                    widgets, columns, snapshots, actions, spacing.s, selectedId,
                    onSelect = { id -> selectedId = if (selectedId == id) null else id },
                )
            }
            if (selected != null) {
                EditToolbar(
                    widget = selected,
                    index = widgets.indexOf(selected),
                    count = widgets.size,
                    panelColumns = panel.columns,
                    onSpan = { c, r -> actions.onSpan(selected.id, c, r) },
                    onEarlier = { widgets.getOrNull(widgets.indexOf(selected) - 1)?.let { actions.onMove(selected.id, it.id) } },
                    onLater = { widgets.getOrNull(widgets.indexOf(selected) + 1)?.let { actions.onMove(selected.id, it.id) } },
                    onConfigure = { actions.onOpenWidget(selected.id) },
                    onDelete = { selectedId = null; actions.onDelete(selected.id) },
                    modifier = Modifier.navigationBarsPadding().padding(start = pageStart(), end = spacing.page, bottom = 12.dp),
                )
            }
        }
        if (settingsOpen) {
            PanelSettingsSheet(
                edit = edit,
                panel = panel,
                actions = actions,
                onDeletePanel = { confirmDelete = true },
                onDismiss = { settingsOpen = false },
            )
        }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.panel_edit_discard_title),
            body = stringResource(R.string.panel_edit_discard_body),
            confirm = stringResource(R.string.panel_edit_discard),
            dismiss = stringResource(R.string.panel_edit_keep),
            onConfirm = { confirmDiscard = false; actions.onCancel() },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.panel_edit_delete_panel_title, panel.name),
            body = stringResource(R.string.panel_edit_delete_panel_body, panel.widgets.size),
            confirm = stringResource(R.string.panel_edit_delete_confirm),
            dismiss = stringResource(R.string.action_cancel),
            onConfirm = { confirmDelete = false; settingsOpen = false; actions.onDeletePanel() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmDeleteHidden) {
        ConfirmDialog(
            title = stringResource(R.string.panel_edit_hidden_title, hidden),
            body = stringResource(R.string.panel_edit_hidden_body),
            confirm = stringResource(R.string.panel_edit_delete_confirm),
            dismiss = stringResource(R.string.action_cancel),
            onConfirm = { confirmDeleteHidden = false; actions.onDeleteHidden() },
            onDismiss = { confirmDeleteHidden = false },
        )
    }
}

/** 编辑页看不到的组件：说明有几个、为什么看不到，给一个“清除”（仍要确认，点「完成」才写盘）。 */
@Composable
private fun HiddenWidgetsNote(count: Int, onClear: () -> Unit) {
    val colors = LocalModbusColors.current
    Row(
        Modifier.fillMaxWidth().testTag("edit-hidden"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.panel_edit_hidden_note, count), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
        TextLink(stringResource(R.string.panel_edit_hidden_clear), onClear, Modifier.testTag("edit-hidden-clear"), color = colors.error)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalModbusColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface1,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("edit-dialog-confirm")) { Text(confirm, color = colors.error) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("edit-dialog-dismiss")) { Text(dismiss, color = colors.accentText) } },
    )
}

/**
 * 面板设置浮层（v3 2-3）：名称、竖屏列数（2 / 3 / 4 分段，横屏固定 4 列）、全部面板（点一个切过去编辑）、
 * ＋ 新建面板、删除当前面板（只剩一个面板时不出现；二次确认）。仍是草稿，“完成”才写盘。
 */
@Composable
private fun PanelSettingsSheet(
    edit: EditState,
    panel: Panel,
    actions: EditActions,
    onDeletePanel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalModbusColors.current
    InPageSheet(stringResource(R.string.panel_edit_settings), onDismiss, tag = "edit-settings-sheet") {
      // 面板多时内容可滚动，浮层最高到屏幕顶部附近
      Column(
          Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("edit-settings-scroll"),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        NumberInput(
            label = stringResource(R.string.panel_edit_name),
            value = panel.name,
            modifier = Modifier.fillMaxWidth().testTag("edit-name"),
            minHeight = 44.dp,
            keyboardType = KeyboardType.Text,
            error = if (!ProjectEdits.isValidName(panel.name)) stringResource(R.string.panel_edit_name_blank) else null,
            onValue = actions.onRename,
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp, end = 2.dp)) {
            Text(stringResource(R.string.panel_edit_columns_portrait), color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.panel_edit_columns_land_note), color = colors.dim, fontSize = 12.sp)
        }
        SegmentedTextControl(
            (ProjectEdits.MIN_COLUMNS..ProjectEdits.MAX_COLUMNS).toList(), panel.columns, actions.onColumns,
            Modifier.testTag("edit-columns"),
        ) { stringResource(R.string.panel_edit_columns_value, it) }
        Text(
            stringResource(R.string.panel_edit_all_panels, edit.draft.panels.size), color = colors.muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
        val shape = RoundedCornerShape(16.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface1).border(1.dp, colors.line, shape)) {
            edit.draft.panels.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
                val current = p.id == panel.id
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        .selectable(selected = current, role = Role.RadioButton) { actions.onSelectPanel(p.id) }
                        .padding(horizontal = 12.dp)
                        .testTag("edit-panel-${p.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (current) colors.rxLocal else colors.dim))
                    Text(p.name.ifBlank { "…" }, color = colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    val n = p.widgets.count { it.isUsable }
                    Text(
                        stringResource(if (current) R.string.panel_edit_panel_current else R.string.panel_edit_panel_count, n),
                        color = colors.muted, fontSize = 13.sp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
            val newDesc = stringResource(R.string.panel_edit_new_panel)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 50.dp)
                    .semantics { contentDescription = newDesc; role = Role.Button }
                    .clickable { actions.onAddPanel() }
                    .padding(horizontal = 12.dp)
                    .testTag("edit-new-panel"),
                contentAlignment = Alignment.CenterStart,
            ) { Text("＋ $newDesc", color = colors.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (edit.draft.panels.size > 1) {
            val shapeBtn = RoundedCornerShape(ButtonRadius)
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).minimumInteractiveComponentSize().height(44.dp)
                    .clip(shapeBtn).border(1.5.dp, colors.error.copy(alpha = 0.45f), shapeBtn)
                    .semantics { role = Role.Button }
                    .clickable(onClick = onDeletePanel)
                    .testTag("edit-delete-panel"),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.panel_edit_delete_named, panel.name), color = colors.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
      }
    }
}

/**
 * 选中组件后的底部工具条（v3 2-2）：标题（名称 · 类型 / 第 i / n 个）、尺寸分段（曲线只有 2 列的尺寸）、
 * 前移 / 后移 / 配置 / 删除（每个 52 高，删除红色在最右）。原来散在卡片上的 × 和角标都收到这里。
 */
@Composable
private fun EditToolbar(
    widget: Widget,
    index: Int,
    count: Int,
    panelColumns: Int,
    onSpan: (Int, Int) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(colors.island)
            .border(1.dp, colors.line, shape)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)
            .testTag("edit-toolbar"),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(widget.displayTitle, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(" · " + kindName(widget.kind), color = colors.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.panel_edit_position, index + 1, count), style = Mono.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.muted)
        }
        val sizes = ProjectEdits.sizes(panelColumns, widget.minColumns)
        if (sizes.size > 1) {
            SegmentedTextControl(
                sizes, widget.spanColumns to widget.spanRows, { (c, r) -> onSpan(c, r) },
                Modifier.padding(top = 4.dp).testTag("edit-size"), small = true,
            ) { (c, r) -> stringResource(R.string.panel_edit_size, c, r) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolbarAction(stringResource(R.string.panel_edit_move_earlier), "edit-move-earlier", enabled = index > 0, onClick = onEarlier) { ChevronLeftGlyph(it) }
            ToolbarAction(stringResource(R.string.panel_edit_move_later), "edit-move-later", enabled = index < count - 1, onClick = onLater) { ChevronRightGlyph(it) }
            ToolbarAction(stringResource(R.string.panel_edit_configure), "edit-configure", onClick = onConfigure) { PencilIcon(it, size = 16.dp) }
            ToolbarAction(stringResource(R.string.panel_edit_delete), "edit-delete", danger = true, onClick = onDelete) { TrashGlyph(it) }
        }
    }
}

@Composable
private fun RowScope.ToolbarAction(
    label: String,
    tag: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val colors = LocalModbusColors.current
    val tint = when {
        !enabled -> colors.dim
        danger -> colors.error
        else -> colors.text
    }
    Column(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (danger && enabled) colors.wash(colors.error) else colors.surface2)
            .semantics { role = Role.Button; if (!enabled) disabled() }
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(tint)
        Text(label, color = tint, fontSize = 11.5.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun kindName(kind: String): String = stringResource(
    when (kind) {
        WidgetKinds.LAMP -> R.string.panel_wiz_kind_lamp
        WidgetKinds.STATUS_TEXT -> R.string.panel_wiz_kind_status
        WidgetKinds.PROGRESS -> R.string.panel_wiz_kind_progress
        WidgetKinds.CURVE -> R.string.panel_wiz_kind_curve
        WidgetKinds.GAUGE -> R.string.panel_wiz_kind_gauge
        WidgetKinds.SWITCH -> R.string.panel_wiz_kind_switch
        WidgetKinds.BUTTON -> R.string.panel_wiz_kind_button
        WidgetKinds.REGISTER_TABLE -> R.string.panel_wiz_kind_table
        else -> R.string.panel_wiz_kind_number
    },
)

@Composable
private fun ChevronLeftGlyph(color: Color) = Canvas(Modifier.size(16.dp)) {
    val w = size.width; val h = size.height; val s = 1.8.dp.toPx()
    drawLine(color, Offset(w * 0.62f, h * 0.2f), Offset(w * 0.32f, h * 0.5f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.62f, h * 0.8f), s, StrokeCap.Round)
}

@Composable
private fun ChevronRightGlyph(color: Color) = Canvas(Modifier.size(16.dp)) {
    val w = size.width; val h = size.height; val s = 1.8.dp.toPx()
    drawLine(color, Offset(w * 0.38f, h * 0.2f), Offset(w * 0.68f, h * 0.5f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.68f, h * 0.5f), Offset(w * 0.38f, h * 0.8f), s, StrokeCap.Round)
}

@Composable
private fun TrashGlyph(color: Color) = Canvas(Modifier.size(16.dp)) {
    val w = size.width; val h = size.height; val s = 1.6.dp.toPx()
    drawLine(color, Offset(w * 0.15f, h * 0.27f), Offset(w * 0.85f, h * 0.27f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.38f, h * 0.27f), Offset(w * 0.4f, h * 0.12f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.4f, h * 0.12f), Offset(w * 0.6f, h * 0.12f), s, StrokeCap.Round)
    drawLine(color, Offset(w * 0.6f, h * 0.12f), Offset(w * 0.62f, h * 0.27f), s, StrokeCap.Round)
    val body = Path().apply {
        moveTo(w * 0.24f, h * 0.27f); lineTo(w * 0.3f, h * 0.88f); lineTo(w * 0.7f, h * 0.88f); lineTo(w * 0.76f, h * 0.27f)
    }
    drawPath(body, color, style = Stroke(s, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

// ---- 可拖动的网格 ----

@Composable
private fun EditGrid(
    widgets: List<Widget>,
    columns: Int,
    snapshots: Map<String, PointSnapshot>,
    actions: EditActions,
    gap: Dp,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val colors = LocalModbusColors.current
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoverId by remember { mutableStateOf<String?>(null) }
    // 最后一格是「＋ 添加组件」，占 1 列
    val spans = widgets.map { it.spanColumns } + 1
    val rows = widgets.map { it.spanRows } + 1

    PanelGrid(
        columns = columns,
        spans = spans,
        spanRows = rows,
        modifier = Modifier.fillMaxWidth().testTag("edit-grid"),
        gap = gap,
        // 被拖的那一行画在最上面，不被后面的行盖住
        rowModifier = { indices -> if (indices.any { widgets.getOrNull(it)?.id == dragId }) Modifier.zIndex(1f) else Modifier },
    ) { index ->
        val widget = widgets.getOrNull(index)
        if (widget == null) {
            AddWidgetTile(actions.onAddWidget)
            return@PanelGrid
        }
        val dragging = dragId == widget.id
        val selected = selectedId == widget.id
        val title = widget.displayTitle
        val moveEarlier = stringResource(R.string.panel_edit_move_earlier)
        val moveLater = stringResource(R.string.panel_edit_move_later)
        val selectLabel = stringResource(if (selected) R.string.panel_edit_deselect_cd else R.string.panel_edit_select_cd, title)
        val openLabel = stringResource(R.string.panel_edit_open_widget, title)
        val deleteLabel = stringResource(R.string.panel_edit_delete_widget, title)
        Box(
            Modifier
                .fillMaxSize()
                .heightIn(min = EDIT_CARD_MIN)
                .onGloballyPositioned { bounds[widget.id] = it.boundsInRoot() }
                .graphicsLayer {
                    if (dragging) {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                        alpha = 0.92f
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }
                .semantics {
                    this.selected = selected
                    // 不用手势的替代（规范 36）：前移 / 后移 / 配置 / 删除都有无障碍动作
                    customActions = listOfNotNull(
                        widgets.getOrNull(index - 1)?.let { prev ->
                            CustomAccessibilityAction(moveEarlier) { actions.onMove(widget.id, prev.id); true }
                        },
                        widgets.getOrNull(index + 1)?.let { next ->
                            CustomAccessibilityAction(moveLater) { actions.onMove(widget.id, next.id); true }
                        },
                        CustomAccessibilityAction(openLabel) { actions.onOpenWidget(widget.id); true },
                        CustomAccessibilityAction(deleteLabel) { actions.onDelete(widget.id); true },
                    )
                }
                .clickable(onClickLabel = selectLabel) { onSelect(widget.id) }
                .pointerInput(widget.id, widgets.map { it.id }) {
                    var start = Offset.Zero
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            start = local
                            dragId = widget.id
                            dragOffset = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            val origin = bounds[widget.id]?.topLeft ?: Offset.Zero
                            // onGloballyPositioned 在 graphicsLayer 之前，记录的是没有位移的原位置
                            val finger = origin + start + dragOffset
                            hoverId = bounds.entries.firstOrNull { (id, rect) -> id != widget.id && rect.contains(finger) }?.key
                        },
                        onDragEnd = {
                            hoverId?.let { actions.onMove(widget.id, it) }
                            dragId = null; hoverId = null; dragOffset = Offset.Zero
                        },
                        onDragCancel = { dragId = null; hoverId = null; dragOffset = Offset.Zero },
                    )
                }
                .testTag("edit-card-${widget.id}"),
        ) {
            if (widget.curve != null) {
                io.github.xingyingyuzhui.modbusmobile.ui.curve.CurveEditCard(widget, Modifier.fillMaxSize())
            } else {
                WidgetCard(widget, snapshots[widget.point?.id], Modifier.fillMaxSize(), editing = true)
            }
            val ring = when {
                selected -> colors.accent
                hoverId == widget.id -> colors.accent.copy(alpha = 0.6f)
                else -> null
            }
            if (ring != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val r = CardRadius.toPx()
                            drawRoundRect(colors.wash(colors.accent), cornerRadius = CornerRadius(r + 4.dp.toPx()), topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()), size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()), style = Stroke(4.dp.toPx()))
                            drawRoundRect(ring, cornerRadius = CornerRadius(r), topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), style = Stroke(2.dp.toPx()))
                        }
                        .testTag(if (selected) "edit-selected-${widget.id}" else "edit-hover"),
                )
            }
            if (selected) {
                val accent = colors.accent
                val onAccent = colors.onAccent
                Canvas(Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-7).dp).size(22.dp)) {
                    drawCircle(accent)
                    val s = 2.dp.toPx()
                    drawLine(onAccent, Offset(size.width * 0.3f, size.height * 0.52f), Offset(size.width * 0.45f, size.height * 0.67f), s, StrokeCap.Round)
                    drawLine(onAccent, Offset(size.width * 0.45f, size.height * 0.67f), Offset(size.width * 0.72f, size.height * 0.36f), s, StrokeCap.Round)
                }
            }
        }
    }
}

/** 编辑卡最小高 84（v3：安静的编辑态比原来的 96 矮，因为卡上不再有 × 和角标的热区）。 */
internal val EDIT_CARD_MIN = 84.dp

/** 网格最后一格「＋ 添加组件」：1.5dp 虚线框、琥珀字，高 ≥84。 */
@Composable
private fun AddWidgetTile(onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    Box(
        Modifier
            .fillMaxSize()
            .heightIn(min = EDIT_CARD_MIN)
            .clip(RoundedCornerShape(CardRadius))
            .drawBehind {
                drawRoundRect(
                    colors.line, cornerRadius = CornerRadius(CardRadius.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .testTag("edit-add-widget"),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.panel_add_widget_plus), color = colors.accentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
