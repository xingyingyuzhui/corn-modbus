package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.application.FieldKind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import io.github.xingyingyuzhui.modbusmobile.application.RecorderState
import io.github.xingyingyuzhui.modbusmobile.application.TimeStyle
import io.github.xingyingyuzhui.modbusmobile.application.TrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.ui.LocalTrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.HexFrameText
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.SelectChip
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticBadge
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.SessionStrip
import io.github.xingyingyuzhui.modbusmobile.ui.components.TextLink
import io.github.xingyingyuzhui.modbusmobile.ui.components.label
import io.github.xingyingyuzhui.modbusmobile.ui.components.tone
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.CardRadius
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

@Composable
fun TrafficScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val recorder = LocalTrafficRecorder.current ?: remember(session) { TrafficRecorder(session, scope) }
    val model = viewModel { TrafficViewModel(recorder) }
    val ui by model.ui.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val locked by model.locked.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val settings = LocalUiSettings.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    val saver = rememberLogSaver(model)
    var confirmClear by remember { mutableStateOf(false) }
    LogContent(
        ui = ui,
        filter = filter,
        locked = locked,
        selected = selected,
        crcDim = settings.logCrcDim,
        clockTime = settings.timeStyle == TimeStyle.CLOCK,
        landscape = landscape,
        maxFrames = recorder.maxFrames,
        onFilter = model::setFilter,
        onToggleLock = model::toggleLocked,
        onSelect = model::select,
        onStart = model::start,
        onPause = model::pauseRecording,
        onClear = { if (ui.recorded > 0 || ui.items.isNotEmpty()) confirmClear = true },
        onSave = saver::begin,
        onPrevious = model::previousPage,
        onNext = model::nextPage,
        onLatest = model::lastPage,
    )
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.log_clear_title)) },
            text = { Text(stringResource(R.string.log_clear_message, ui.recorded)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; model.clear() }) { Text(stringResource(R.string.log_btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    saver.Dialogs()
}

/** 无状态的页面内容（截图测试直接喂数据）。 */
@Composable
fun LogContent(
    ui: LogUiState,
    filter: LogFilter,
    locked: Boolean,
    selected: Long?,
    crcDim: Boolean,
    clockTime: Boolean,
    landscape: Boolean,
    maxFrames: Int,
    onFilter: (LogFilter) -> Unit,
    onToggleLock: () -> Unit,
    onSelect: (Long?) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLatest: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val entries = ui.items
    val frames = remember(entries) { entries.filterIsInstance<LogEntry>() }
    val effective = frames.firstOrNull { it.seq == selected } ?: frames.lastOrNull()
    val emptyText = stringResource(
        when {
            ui.recorded > 0 -> R.string.log_empty_filtered
            ui.recorder == RecorderState.IDLE -> R.string.log_empty_idle
            ui.recorder == RecorderState.RECORDING -> R.string.log_empty_recording
            else -> R.string.log_empty_paused
        },
    )
    val follow = ui.following && !locked
    if (landscape) {
        Row(
            Modifier.screenFrame().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.group),
        ) {
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.title_log),
                        color = LocalModbusColors.current.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    FilterChips(filter, onFilter, xs = true, modifier = Modifier.weight(1f))
                    LockButton(locked, onToggleLock)
                }
                LogCard(
                    entries, effective?.seq, follow, crcDim, clockTime, emptyText,
                    onSelect = { onSelect(it) },
                    modifier = Modifier.weight(1f, fill = false),
                )
                PageBar(ui, onPrevious, onNext, onLatest)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 横屏高度只有 ~360dp：把记录控制放到右侧，左侧让给列表
                RecorderBar(ui, onStart, onPause, onClear, onSave)
                LogStripInfoRow(ui, maxFrames)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    FrameDetailCard(effective, Modifier.fillMaxWidth())
                }
            }
        }
    } else {
        Column(Modifier.screenFrame()) {
            PageHeader(stringResource(R.string.title_log)) { LockButton(locked, onToggleLock) }
            SessionStrip(
                Modifier.padding(bottom = spacing.s),
                trailing = { LogStripInfo(ui, maxFrames) },
            )
            RecorderBar(ui, onStart, onPause, onClear, onSave, Modifier.padding(bottom = 4.dp))
            FilterChips(filter, onFilter, xs = false, modifier = Modifier.padding(bottom = 4.dp))
            LogCard(
                entries, null, follow, crcDim, clockTime, emptyText,
                onSelect = null,
                modifier = Modifier.weight(1f, fill = false),
            )
            PageBar(ui, onPrevious, onNext, onLatest, Modifier.padding(bottom = dockClearance().takeIf { it > 0.dp } ?: spacing.s))
        }
    }
}

@Composable
private fun LogStripInfoRow(ui: LogUiState, maxFrames: Int) {
    val colors = LocalModbusColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            recorderStateText(ui),
            style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = if (ui.recorder == RecorderState.FULL) colors.error else colors.muted,
            modifier = Modifier.testTag("log-state"),
        )
        Text(
            stringResource(R.string.log_limit, "%,d".format(maxFrames)),
            style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = colors.muted,
        )
    }
}

@Composable
private fun LogStripInfo(ui: LogUiState, maxFrames: Int) {
    val colors = LocalModbusColors.current
    Column(horizontalAlignment = Alignment.End) {
        Text(
            stringResource(R.string.log_limit, "%,d".format(maxFrames)),
            style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = colors.muted,
        )
        Text(
            recorderStateText(ui),
            style = Mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
            color = if (ui.recorder == RecorderState.FULL) colors.error else colors.muted,
            modifier = Modifier.testTag("log-state"),
        )
    }
}

@Composable
private fun recorderStateText(ui: LogUiState): String = when (ui.recorder) {
    RecorderState.IDLE -> stringResource(R.string.log_state_idle)
    RecorderState.RECORDING -> stringResource(R.string.log_state_recording, ui.recorded)
    RecorderState.PAUSED -> stringResource(R.string.log_state_paused, ui.recorded)
    RecorderState.FULL -> stringResource(R.string.log_state_full, ui.recorded)
}

/** 开始 / 暂停 / 继续 + 清除 + 保存。暂停记录（停止捕获）和“锁定滚动”（页头的按钮）是两回事。 */
@Composable
private fun RecorderBar(
    ui: LogUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasData = ui.recorded > 0
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        when (ui.recorder) {
            RecorderState.IDLE -> PrimaryButton(stringResource(R.string.log_btn_start), onStart, Modifier.weight(1.5f).testTag("log-start"), compact = true)
            RecorderState.RECORDING -> SecondaryButton(stringResource(R.string.log_btn_pause), onPause, Modifier.weight(1.5f).testTag("log-pause"), compact = true, active = true)
            RecorderState.PAUSED -> PrimaryButton(stringResource(R.string.log_btn_resume), onStart, Modifier.weight(1.5f).testTag("log-resume"), compact = true)
            // 记满：不能继续，要先清除或保存
            RecorderState.FULL -> SecondaryButton(stringResource(R.string.log_btn_resume), {}, Modifier.weight(1.5f).testTag("log-resume"), enabled = false, compact = true)
        }
        SecondaryButton(stringResource(R.string.log_btn_clear), onClear, Modifier.weight(1f).testTag("log-clear"), enabled = hasData, compact = true)
        SecondaryButton(stringResource(R.string.log_btn_save), onSave, Modifier.weight(1f).testTag("log-save"), enabled = hasData, compact = true)
    }
}

/** 分页栏：‹ 上一页 | 第 3 / 27 页 | 下一页 › | 最新。 */
@Composable
private fun PageBar(
    ui: LogUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextLink(
            stringResource(R.string.log_page_prev),
            onPrevious,
            Modifier.testTag("log-prev").alpha(if (ui.page > 0) 1f else 0.4f),
            color = if (ui.page > 0) colors.accentText else colors.dim,
        )
        Text(
            stringResource(R.string.log_page_of, ui.page + 1, ui.pageCount),
            color = colors.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).testTag("log-page"),
        )
        TextLink(
            stringResource(R.string.log_page_next),
            onNext,
            Modifier.testTag("log-next").alpha(if (ui.page < ui.pageCount - 1) 1f else 0.4f),
            color = if (ui.page < ui.pageCount - 1) colors.accentText else colors.dim,
        )
        TextLink(
            stringResource(R.string.log_page_latest),
            onLatest,
            Modifier.testTag("log-latest"),
            color = if (ui.page < ui.pageCount - 1) colors.accentText else colors.dim,
        )
    }
}

/**
 * 页头「锁定滚动 / 恢复自动滚动」：用文字而不是 ⏸ / ▶ 图标，免得和下面的「暂停记录」按钮混淆
 * （锁定滚动只是列表不跟随最新，新帧照常记录）。锁定时用强调色。
 */
@Composable
private fun LockButton(locked: Boolean, onClick: () -> Unit) {
    val colors = LocalModbusColors.current
    TextLink(
        stringResource(if (locked) R.string.cd_log_resume else R.string.cd_log_pause),
        onClick,
        Modifier.testTag("log-lock"),
        color = if (locked) colors.accentText else colors.muted,
    )
}

@Composable
private fun FilterChips(
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
    xs: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogFilter.entries.forEach { item ->
            SelectChip(
                text = stringResource(
                    when (item) {
                        LogFilter.ALL -> R.string.log_filter_all
                        LogFilter.LOCAL -> R.string.log_filter_local
                        LogFilter.FOREIGN -> R.string.log_filter_foreign
                        LogFilter.ECHO -> R.string.log_filter_echo
                        LogFilter.ERROR -> R.string.log_filter_error
                        LogFilter.UNKNOWN -> R.string.log_filter_unknown
                    },
                ),
                selected = item == filter,
                xs = xs,
                design = !xs,
                onClick = { onFilter(item) },
            )
        }
    }
}

/** 稿面日志容器：一张卡（圆角 16 + 边框），行间 1px rowLine，每行左侧 3px 语义色条。 */
@Composable
private fun LogCard(
    entries: List<LogItem>,
    selected: Long?,
    follow: Boolean,
    crcDim: Boolean,
    clockTime: Boolean,
    emptyText: String,
    onSelect: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val listState = rememberLazyListState()
    val lastSeq = entries.lastOrNull()?.seq
    // 翻页后回到页首；跟随最新（在最后一页、记录中、没锁定）时滚到底。
    val firstSeq = entries.firstOrNull()?.seq
    LaunchedEffect(firstSeq) { if (entries.isNotEmpty()) listState.scrollToItem(0) }
    LaunchedEffect(lastSeq, follow) {
        if (follow && entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.line, shape),
    ) {
        if (entries.isEmpty()) {
            Text(emptyText, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(0.dp)) {
                items(entries, key = { it.seq }) { entry ->
                    when (entry) {
                        is LogMarker -> MarkerRow(entry)
                        is LogEntry -> LogRow(entry, picked = entry.seq == selected, crcDim = crcDim, clockTime = clockTime, onClick = onSelect?.let { f -> { f(entry.seq) } })
                    }
                    if (entry !== entries.last()) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerRow(marker: LogMarker) {
    val colors = LocalModbusColors.current
    Text(
        "— ${stringResource(R.string.log_marker_new_session)} · ${formatWallClock(marker.wallMs)} —",
        color = colors.dim,
        fontSize = 11.5.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(colors.surface2).padding(vertical = 4.dp),
    )
}

@Composable
private fun LogRow(entry: LogEntry, picked: Boolean, crcDim: Boolean, clockTime: Boolean, onClick: (() -> Unit)?) {
    val colors = LocalModbusColors.current
    val semantic = entry.semantic()
    val tone = colors.tone(semantic)
    val foreign = entry.event.kind == TrafficKind.RX_FOREIGN_REQUEST ||
        entry.event.kind == TrafficKind.RX_FOREIGN_RESPONSE
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (picked) colors.wash(colors.accent) else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tone))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (foreign && semantic != SemanticKind.ERROR) 0.6f else 1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (clockTime) formatWallClock(entry.wallMs) else formatOffset(entry.offsetMs),
                    style = Mono.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = colors.dim,
                    maxLines = 1,
                    modifier = Modifier.testTag("log-time"),
                )
                SemanticBadge(semantic, entry.badgeText(entry.event.kind.label()))
                Text(
                    summaryText(entry.info, entry.event.kind),
                    color = colors.text,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.log_bytes, entry.info.size),
                    style = Mono.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = colors.dim,
                    maxLines = 1,
                )
            }
            HexFrameText(
                entry.event.bytes,
                style = Mono.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                dimCrc = crcDim,
                modifier = Modifier.alpha(0.9f),
            )
        }
    }
}

/** 稿面“帧详情”：标题 + CRC 结论 + 复制 HEX；字段列表；脚注。 */
@Composable
private fun FrameDetailCard(entry: LogEntry?, modifier: Modifier = Modifier) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(CardRadius)
    val clipboard = LocalClipboardManager.current
    var copied by remember(entry?.seq) { mutableStateOf(false) }
    Column(
        modifier
            .clip(shape)
            .background(colors.surface1)
            .border(1.dp, colors.line, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (entry == null) {
            Text(stringResource(R.string.log_detail_title), color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.log_detail_empty), color = colors.muted, fontSize = 13.sp)
            return@Column
        }
        val info = entry.info
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.log_detail_title), color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            when (info.crcOk) {
                true -> SemanticBadge(SemanticKind.RX_LOCAL, stringResource(R.string.badge_crc_ok))
                false -> SemanticBadge(SemanticKind.ERROR, stringResource(R.string.badge_crc_bad))
                null -> Unit
            }
            Box(Modifier.weight(1f))
            val copiedText = stringResource(R.string.log_copied)
            TextLink(
                text = if (copied) copiedText else stringResource(R.string.log_copy_hex),
                onClick = {
                    clipboard.setText(AnnotatedString(entry.event.hex))
                    copied = true
                },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface2),
        ) {
            info.fields.forEachIndexed { index, field ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        field.hex,
                        style = Mono.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                        color = if (field.kind == FieldKind.CRC) colors.dim else colors.accentText,
                        modifier = Modifier.width(60.dp),
                    )
                    Text(fieldName(field, info), color = colors.text, fontSize = 12.5.sp, modifier = Modifier.width(64.dp))
                    Text(
                        fieldValue(field, info),
                        style = Mono.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                        color = if (field.kind == FieldKind.CRC && info.crcOk == false) colors.error else colors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index != info.fields.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rowLine))
                }
            }
        }
        Text(
            stringResource(R.string.log_detail_footer, info.size, formatOffset(entry.offsetMs), formatWallClock(entry.wallMs)),
            color = colors.muted,
            fontSize = 11.5.sp,
        )
    }
}
