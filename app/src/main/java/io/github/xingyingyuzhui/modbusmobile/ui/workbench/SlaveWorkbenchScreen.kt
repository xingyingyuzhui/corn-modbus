package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.xingyingyuzhui.modbusmobile.ui.LocalAppViewModelStoreOwner
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SessionCounters
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveAreas
import io.github.xingyingyuzhui.modbusmobile.application.slave.isBitArea
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.InPageSheet
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.RowBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.RegisterTable
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.WarnIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.groupedCount
import io.github.xingyingyuzhui.modbusmobile.ui.resolve
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusSpacing


@Composable
fun SlaveWorkbench(target: ConnectionTarget, landscape: Boolean) {
    val session = LocalSession.current
    val owner = LocalAppViewModelStoreOwner.current ?: checkNotNull(LocalViewModelStoreOwner.current)
    val model = viewModel(viewModelStoreOwner = owner) { SlaveViewModel(session) }
    val settings = LocalUiSettings.current
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        if (model.ruleDraft != null) {
            SimulationRuleEditor(model)
        } else if (landscape) {
            SlaveLandscape(model, settings, target)
        } else {
            SlavePortrait(model, settings, target)
        }
        if (model.ruleDraft == null) SlaveSheets(model)
    }
}

@Composable
private fun SlavePortrait(model: SlaveViewModel, settings: UiSettings, target: ConnectionTarget) {
    Column(Modifier.screenFrame()) {
        PageHeader(stringResource(R.string.title_slave_workbench))
        SessionStrip(Modifier.padding(bottom = 8.dp), showEcho = true)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = dockClearance()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (target == ConnectionTarget.Demo) DemoNote()
            SlaveStats(columns = 4)
            UnitRow(model)
            SaveFailedNote(model)
            AreaSegment(model)
            AreaTable(model, settings, landscape = false)
            RangeButtons(model)
            SimulationRules(model)
        }
    }
}

@Composable
private fun SlaveLandscape(model: SlaveViewModel, settings: UiSettings, target: ConnectionTarget) {
    Row(
        Modifier.screenFrame().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionStrip(showEcho = true)
            if (target == ConnectionTarget.Demo) DemoNote()
            SlaveStats(columns = 2)
            UnitRow(model)
            SaveFailedNote(model)
            AreaSegment(model)
            RangeButtons(model)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AreaTable(model, settings, landscape = true)
            SimulationRules(model)
        }
    }
}

const val SlaveStatsTag = "slave_stats"

/**
 * 稿面 b1-5 的四个统计格：本机请求 / 已响应 / 异常 / 外站帧，下面一行“方向不明 N”。
 * 数字来自 [io.github.xingyingyuzhui.modbusmobile.application.SessionManager.counters]，本次连接从 0 开始。
 * 稿面还有“CRC 错”：扫描器丢弃的字节不会发布成报文，application 层数不到，所以不画，不编数字。
 */
@Composable
private fun SlaveStats(columns: Int) {
    val counters by LocalSession.current.counters.collectAsStateWithLifecycle()
    SlaveStatsContent(counters, columns)
}

/** [columns]：竖屏 4（稿面一行四格），横屏左栏只有 280dp 宽，用 2 × 2，免得数字被截断。 */
@Composable
fun SlaveStatsContent(counters: SessionCounters, columns: Int = 4, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val tiles = listOf(
        Triple(R.string.slave_stat_local, counters.localRequests, colors.text),
        Triple(R.string.slave_stat_responses, counters.responses, colors.rxLocal),
        Triple(R.string.slave_stat_exceptions, counters.exceptions, colors.error),
        Triple(R.string.slave_stat_foreign, counters.foreignFrames, colors.foreign),
    )
    Column(modifier.testTag(SlaveStatsTag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(columns.coerceIn(1, 4)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, value, color) -> StatTile(stringResource(label), value, color, Modifier.weight(1f)) }
            }
        }
        Text(
            stringResource(R.string.slave_stat_ambiguous, groupedCount(counters.ambiguous)),
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** 稿面 .st：底 s1、1px line、圆角 12、内边距 8 四边相等（AL-04，原 8/8/8/7）；标签 10.5 muted，数值 mono 17 SemiBold。 */
/** 统计格这类卡内小格子的内边距：8 四边相等（space.s 的标准密度值，统计格不随密度缩；§13.2 / AL-04）。 */
private val StatTilePadding = ModbusSpacing.Comfortable.s

@Composable
private fun StatTile(label: String, value: Long, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(StatTilePadding),
    ) {
        Text(label, color = colors.muted, fontSize = 10.5.sp, lineHeight = 14.sp, maxLines = 1)
        Text(
            groupedCount(value),
            style = Mono.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            color = valueColor,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 演示设备的对端主站会每秒读 HR0–9、每 5 秒把 HR0 改写成递增值（DefaultSessionManager.runDemoMaster）。 */
@Composable
private fun DemoNote() {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface1, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WarnIcon(colors.accentText, Modifier.padding(top = 2.dp))
        Text(stringResource(R.string.slave_demo_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun AreaSegment(model: SlaveViewModel) {
    SegmentedTextControl(
        options = SlaveAreas,
        selected = model.area,
        onSelect = model::selectArea,
        small = true,
    ) { area ->
        stringResource(R.string.slave_area_count, areaShortName(area), model.rowsOf(area).size)
    }
}

@Composable
private fun areaShortName(area: RegisterArea): String = stringResource(
    when (area) {
        RegisterArea.COIL -> R.string.area_short_coil
        RegisterArea.DISCRETE_INPUT -> R.string.area_short_discrete
        RegisterArea.HOLDING_REGISTER -> R.string.area_short_holding
        RegisterArea.INPUT_REGISTER -> R.string.area_short_input
    },
)

@Composable
private fun AreaTable(model: SlaveViewModel, settings: UiSettings, landscape: Boolean) {
    val colors = LocalModbusColors.current
    val area = model.area
    val rows = model.rowsOf(area)
    if (rows.isEmpty()) {
        val shape = RoundedCornerShape(CardRadius)
        val unitEmpty = SlaveAreas.all { model.rowsOf(it).isEmpty() }
        Text(
            if (unitEmpty) stringResource(R.string.slave_unit_empty, model.unit) else stringResource(R.string.slave_empty),
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface1, shape)
                .border(1.dp, colors.line, shape)
                .padding(14.dp),
        )
        return
    }
    val on = stringResource(R.string.slave_bit_on)
    val off = stringResource(R.string.slave_bit_off)
    val bit = area.isBitArea()
    val table = rows.map { row ->
        RegisterRow(
            address = row.address,
            value = if (bit) (if (row.value != 0) on else off) else row.value.toString(),
            addressText = if (settings.addressStyle == AddressStyle.MODICON) modiconLabel(area, row.address) else null,
            sub = if (settings.addressStyle == AddressStyle.BOTH) modiconLabel(area, row.address) else null,
            hex = if (bit) null else hexWord(row.value, settings.hexPrefix),
            flashAt = row.changedAt,
            editable = true,
            badge = sourceBadge(row),
        )
    }
    RegisterTable(
        rows = table,
        badgeHeader = stringResource(R.string.slave_source_header),
        // 稿面 b1-5：地址 | 值 | 最近来源（110，右对齐），没有 HEX 和 ✎ 列；整行可点。
        showEdit = false,
        rowHeight = if (landscape) 36.dp else 40.dp,
        addressWidth = if (landscape) 62.dp else 70.dp,
        hexWidth = if (landscape) 120.dp else 110.dp,
        onRowClick = { row ->
            if (bit) model.toggleBit(area, row.address) else model.startEdit(area, row.address)
        },
    )
}

/** “＋ 添加地址段” | “－ 删除地址段”：当前区没有地址时删除不可点。 */
@Composable
private fun RangeButtons(model: SlaveViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(stringResource(R.string.slave_add_range), model::openAddRange, Modifier.weight(1f).testTag("slave-add-range-open"), compact = true)
        SecondaryButton(
            stringResource(R.string.slave_remove_range),
            model::openRemoveRange,
            Modifier.weight(1f).testTag("slave-remove-range-open"),
            enabled = model.rowsOf(model.area).isNotEmpty(),
            compact = true,
        )
    }
}

@Composable
private fun UnitRow(model: SlaveViewModel) {
    val units by model.units.collectAsStateWithLifecycle()
    if (units.isEmpty()) return
    val colors = LocalModbusColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            units.forEach { id ->
                SelectChip(stringResource(R.string.slave_unit_label, id), selected = id == model.unit, minWidth = 48.dp) { model.selectUnit(id) }
            }
            SelectChip("＋", selected = false, description = stringResource(R.string.cd_add_unit), minWidth = 48.dp, onClick = model::openAddUnit)
        }
        if (units.size > 1) {
            Text(
                stringResource(R.string.slave_remove_unit, model.unit),
                color = colors.error,
                fontSize = 12.sp,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = model::askRemoveUnit)
                    .padding(horizontal = 4.dp, vertical = 14.dp)
                    .testTag("slave-remove-unit"),
            )
        }
    }
}

@Composable
private fun SaveFailedNote(model: SlaveViewModel) {
    if (!model.saveFailed) return
    val colors = LocalModbusColors.current
    Text(stringResource(R.string.slave_save_failed), color = colors.error, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun sourceBadge(row: SlaveRow): RowBadge? {
    val age = row.ageSeconds?.let { ageText(it, row.changedWallMs) }.orEmpty()
    return when (row.source) {
        null -> null
        RowSource.EXTERNAL -> RowBadge(stringResource(R.string.slave_src_external, age), SemanticKind.TX)
        RowSource.BROADCAST -> RowBadge(stringResource(R.string.slave_src_broadcast, age), SemanticKind.TX)
        RowSource.LOCAL -> RowBadge(stringResource(R.string.slave_src_local), null)
        RowSource.SIMULATION -> RowBadge(
            row.wave?.let { stringResource(R.string.slave_src_sim, waveName(it)) } ?: stringResource(R.string.slave_src_sim_plain),
            SemanticKind.FOREIGN,
        )
    }
}

/** 1 分钟内写“N s”（每秒刷新），超过 1 分钟写改值时刻 HH:mm:ss（不再走字）。 */
@Composable
private fun ageText(seconds: Long, wallMs: Long?): String = when {
    seconds < 1 -> stringResource(R.string.slave_age_now)
    seconds < 60 || wallMs == null -> stringResource(R.string.slave_age_s, seconds.toInt())
    else -> java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date(wallMs))
}

@Composable
fun waveName(kind: WaveKind): String = stringResource(
    when (kind) {
        WaveKind.CONSTANT -> R.string.wave_constant
        WaveKind.RAMP -> R.string.wave_ramp
        WaveKind.SINE -> R.string.wave_sine
        WaveKind.SQUARE -> R.string.wave_square
        WaveKind.RANDOM -> R.string.wave_random
        WaveKind.SEQUENCE -> R.string.wave_sequence
    },
)

/** 改值 / 加地址段 / 加 Unit / 删 Unit：都是页内浮层（注意事项 #28），不是系统对话框。 */
@Composable
private fun SlaveSheets(model: SlaveViewModel) {
    val colors = LocalModbusColors.current
    model.edit?.let { edit ->
        InPageSheet(stringResource(R.string.slave_edit_title, areaShortName(edit.area), edit.address), model::cancelEdit, tag = "slave-edit") {
            NumberInput(
                stringResource(R.string.slave_edit_value),
                edit.draft,
                Modifier.fillMaxWidth().testTag("slave-edit-value"),
                error = edit.error?.resolve(),
                keyboardType = KeyboardType.Number,
                onValue = model::setDraft,
            )
            SheetButtons(stringResource(R.string.slave_apply), model::cancelEdit, model::commitEdit, "slave-edit")
        }
    }
    model.addRange?.let { request ->
        InPageSheet(stringResource(R.string.slave_add_title), model::cancelAddRange, tag = "slave-add-range") {
            ChipRow(
                SlaveAreas,
                { it == request.area },
                { areaShortName(it) },
                { next -> model.updateAddRange { copy(area = next) } },
                design = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(
                    stringResource(R.string.slave_add_start),
                    request.start,
                    Modifier.weight(1f).testTag("slave-add-start"),
                    keyboardType = KeyboardType.Number,
                    onValue = { model.updateAddRange { copy(start = it) } },
                )
                NumberInput(
                    stringResource(R.string.slave_add_count),
                    request.count,
                    Modifier.weight(1f).testTag("slave-add-count"),
                    keyboardType = KeyboardType.Number,
                    onValue = { model.updateAddRange { copy(count = it) } },
                )
            }
            request.error?.let { Text(it.resolve(), color = colors.error, fontSize = 12.sp) }
            SheetButtons(stringResource(R.string.slave_add_confirm), model::cancelAddRange, model::commitAddRange, "slave-add-range")
        }
    }
    model.removeRange?.let { request ->
        InPageSheet(stringResource(R.string.slave_remove_range_title), model::cancelRemoveRange, tag = "slave-remove-range") {
            ChipRow(
                SlaveAreas,
                { it == request.area },
                { areaShortName(it) },
                { next -> model.updateRemoveRange { copy(area = next) } },
                design = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(
                    stringResource(R.string.slave_add_start),
                    request.start,
                    Modifier.weight(1f).testTag("slave-remove-start"),
                    keyboardType = KeyboardType.Number,
                    onValue = { model.updateRemoveRange { copy(start = it) } },
                )
                NumberInput(
                    stringResource(R.string.slave_add_count),
                    request.count,
                    Modifier.weight(1f).testTag("slave-remove-count"),
                    keyboardType = KeyboardType.Number,
                    onValue = { model.updateRemoveRange { copy(count = it) } },
                )
            }
            Text(stringResource(R.string.slave_remove_range_note, model.unit), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
            request.error?.let { Text(it.resolve(), color = colors.error, fontSize = 12.sp) }
            SheetButtons(stringResource(R.string.slave_remove_confirm), model::cancelRemoveRange, model::commitRemoveRange, "slave-remove-range", danger = true)
        }
    }
    model.addUnit?.let { request ->
        InPageSheet(stringResource(R.string.slave_add_unit_title), model::cancelAddUnit, tag = "slave-add-unit") {
            NumberInput(
                stringResource(R.string.slave_add_unit_field),
                request.draft,
                Modifier.fillMaxWidth().testTag("slave-add-unit-value"),
                error = request.error?.resolve(),
                keyboardType = KeyboardType.Number,
                onValue = model::setUnitDraft,
            )
            Text(stringResource(R.string.slave_add_unit_note), color = colors.muted, fontSize = 12.sp, lineHeight = 17.sp)
            SheetButtons(stringResource(R.string.slave_add_unit_confirm), model::cancelAddUnit, model::commitAddUnit, "slave-add-unit")
        }
    }
    model.removeUnit?.let { id ->
        InPageSheet(stringResource(R.string.slave_remove_title, id), model::cancelRemoveUnit, tag = "slave-remove") {
            Text(stringResource(R.string.slave_remove_body, id), color = colors.muted, fontSize = 13.sp, lineHeight = 19.sp)
            SheetButtons(stringResource(R.string.slave_remove_confirm), model::cancelRemoveUnit, model::confirmRemoveUnit, "slave-remove", danger = true)
        }
    }
}

@Composable
private fun SheetButtons(confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit, tag: String, danger: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f).testTag("$tag-cancel"))
        PrimaryButton(confirm, onConfirm, Modifier.weight(1.6f).testTag("$tag-confirm"), danger = danger)
    }
}
