package io.github.xingyingyuzhui.modbusmobile.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.AddressStyle
import io.github.xingyingyuzhui.modbusmobile.application.DensityMode
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.TimeStyle
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.history.HistoryOverview
import io.github.xingyingyuzhui.modbusmobile.application.history.HistorySession
import io.github.xingyingyuzhui.modbusmobile.application.history.RecordingState
import io.github.xingyingyuzhui.modbusmobile.ui.LocalHistoryCatalog
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSettingsRepository
import io.github.xingyingyuzhui.modbusmobile.ui.components.InPageSheet
import io.github.xingyingyuzhui.modbusmobile.ui.components.AppearanceIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.ColorSwatchRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.CommIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.DisplayFormatIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.InfoIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.LockIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.RowDivider
import io.github.xingyingyuzhui.modbusmobile.ui.components.SectionLabel
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusMenu
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusMenuItem
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedTextControl
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingRow
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingTrailing
import io.github.xingyingyuzhui.modbusmobile.ui.components.SettingsCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.SubPageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

/** 「我的」里的页面。HOME 是入口；SETTINGS 是把所有分组按顺序排在一页的完整设置页（稿面 b3-2）。 */
enum class MinePage { HOME, APPEARANCE, DISPLAY, SECURITY, COMM, ABOUT, HISTORY, STORAGE }

private val GroupPages = listOf(MinePage.APPEARANCE, MinePage.DISPLAY, MinePage.SECURITY, MinePage.COMM, MinePage.ABOUT)

@Composable
fun MineScreen() {
    val repository = LocalSettingsRepository.current
    val history = LocalHistoryCatalog.current
    val model = viewModel(key = "mine-${System.identityHashCode(repository)}-${System.identityHashCode(history)}") {
        MineViewModel(repository, history)
    }
    val settings by model.settings.collectAsStateWithLifecycle()
    val overview by model.overview.collectAsStateWithLifecycle()
    val recording by model.recording.collectAsStateWithLifecycle()
    val deviceExport = rememberDeviceExport()
    val export = LocalMineExport.current ?: deviceExport
    val importer = rememberDeviceImport()
    val importScope = rememberCoroutineScope()
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    var pageName by rememberSaveable { mutableStateOf(MinePage.HOME.name) }
    val page = MinePage.entries.firstOrNull { it.name == pageName } ?: MinePage.HOME
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pointCount by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    val go = { next: MinePage -> pageName = next.name }
    val onDelete: (HistorySession) -> Unit = { session ->
        if (!session.retained && session.endedAtMs != null) pendingDelete = session.id
    }
    LaunchedEffect(page) { model.refresh() }
    LaunchedEffect(showExport) {
        if (showExport) pointCount = export.pointCount()
    }
    Box(Modifier.fillMaxSize()) {
        if (landscape) {
            val detail = page.takeIf { it in GroupPages || it == MinePage.HISTORY || it == MinePage.STORAGE } ?: MinePage.APPEARANCE
            MineLandscape(model, settings, overview, recording, detail, go, { showExport = true }, importer::pick, onDelete)
        } else {
            BackHandler(enabled = page != MinePage.HOME && !showExport && importer.read == null && pendingDelete == null) { pageName = MinePage.HOME.name }
            MinePortrait(model, settings, overview, recording, page, go, { showExport = true }, importer::pick, onDelete)
        }
        if (showExport) {
            // 保存 / 分享一开始就收起：系统界面接手后回来，不该还压着一层浮层（redroid 上实测要手动关）。
            ExportSheet(
                pointCount,
                onSave = { parts -> showExport = false; export.save(parts) },
                onShare = { parts -> showExport = false; export.share(parts) },
            ) { showExport = false }
        }
        importer.read?.let { read ->
            ImportSheet(
                read,
                importer.busy,
                onImport = { preview, parts -> importScope.launch { importer.apply(preview, parts) } },
                onDismiss = importer::dismiss,
            )
        }
        val deleting = pendingDelete
        if (deleting != null) {
            DeleteSessionSheet(
                onConfirm = {
                    pendingDelete = null
                    model.deleteClosed(deleting)
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

// ---------------------------------------------------------------- 竖屏

@Composable
private fun MinePortrait(
    model: MineViewModel,
    settings: UiSettings,
    overview: HistoryOverview,
    recording: RecordingState,
    page: MinePage,
    go: (MinePage) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: (HistorySession) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(Modifier.screenFrame()) {
        if (page == MinePage.HOME) {
            // 页头不再放 ⚙：下面的分组列表就是设置入口，两个入口进的是同一批设置（E-20261028-ux1 C2）。
            PageHeader(stringResource(R.string.title_mine))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = dockClearance()),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                MineDataSection(settings, overview, recording, null, onExport, { go(MinePage.HISTORY) }, { go(MinePage.STORAGE) }, onImport)
                SectionLabel(stringResource(R.string.mine_section_settings))
                GroupList(settings, selected = null, onSelect = go)
                AuthorCredit()
            }
        } else {
            SubPageHeader(pageTitle(page), onBack = { go(MinePage.HOME) })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = dockClearance()),
                verticalArrangement = Arrangement.spacedBy(spacing.group),
            ) {
                GroupContent(page, model, settings, overview, recording, onDelete)
            }
        }
    }
}

// ---------------------------------------------------------------- 横屏：左分组，右直接显示设置

@Composable
private fun MineLandscape(
    model: MineViewModel,
    settings: UiSettings,
    overview: HistoryOverview,
    recording: RecordingState,
    page: MinePage,
    go: (MinePage) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: (HistorySession) -> Unit,
) {
    val spacing = LocalSpacing.current
    val colors = LocalModbusColors.current
    Column(Modifier.screenFrame()) {
        PageHeader(stringResource(R.string.title_mine))
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.group)) {
            Column(
                Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = dockClearance()),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                MineDataSection(settings, overview, recording, page, onExport, { go(MinePage.HISTORY) }, { go(MinePage.STORAGE) }, onImport)
                GroupList(settings, selected = page, onSelect = go)
                AuthorCredit()
            }
            Column(
                Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = dockClearance()),
                verticalArrangement = Arrangement.spacedBy(spacing.group),
            ) {
                Text(pageTitle(page), color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("mine-detail-title"))
                GroupContent(page, model, settings, overview, recording, onDelete)
            }
        }
    }
}

// ---------------------------------------------------------------- 分组列表

@Composable
private fun GroupList(settings: UiSettings, selected: MinePage?, onSelect: (MinePage) -> Unit) {
    val colors = LocalModbusColors.current
    SettingsCard {
        GroupPages.forEachIndexed { index, group ->
            if (index > 0) RowDivider()
            SettingRow(
                title = pageTitle(group),
                modifier = Modifier.testTag("mine-row-" + group.name.lowercase()),
                value = if (selected == null) groupValue(group, settings) else null,
                icon = if (selected == null) {
                    { GroupIcon(group, colors.accentText) }
                } else {
                    null
                },
                selected = selected == group,
                onClick = { onSelect(group) },
            )
        }
    }
}

@Composable
private fun GroupIcon(group: MinePage, tint: androidx.compose.ui.graphics.Color) {
    when (group) {
        MinePage.APPEARANCE -> AppearanceIcon(tint)
        MinePage.DISPLAY -> DisplayFormatIcon(tint)
        MinePage.SECURITY -> LockIcon(tint)
        MinePage.COMM -> CommIcon(tint)
        else -> InfoIcon(tint)
    }
}

@Composable
private fun pageTitle(page: MinePage): String = stringResource(
    when (page) {
        MinePage.HOME -> R.string.title_mine
        MinePage.APPEARANCE -> R.string.mine_group_appearance
        MinePage.DISPLAY -> R.string.mine_group_display
        MinePage.SECURITY -> R.string.mine_group_security
        MinePage.COMM -> R.string.mine_group_comm
        MinePage.ABOUT -> R.string.mine_group_about
        MinePage.HISTORY -> R.string.mine_history
        MinePage.STORAGE -> R.string.mine_storage_title
    },
)

@Composable
private fun groupValue(group: MinePage, settings: UiSettings): String? = when (group) {
    MinePage.APPEARANCE -> themeName(settings.theme)
    MinePage.DISPLAY -> addressName(settings.addressStyle)
    MinePage.SECURITY -> stringResource(R.string.mine_summary_security)
    MinePage.COMM -> stringResource(R.string.mine_summary_comm, settings.defaultResponseTimeoutMs)
    MinePage.ABOUT -> appVersion()
    else -> null
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    val unknown = stringResource(R.string.about_unknown_version)
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: unknown
    }
}

// ---------------------------------------------------------------- 各分组的内容

@Composable
private fun GroupContent(
    group: MinePage,
    model: MineViewModel,
    settings: UiSettings,
    overview: HistoryOverview,
    recording: RecordingState,
    onDelete: (HistorySession) -> Unit,
) {
    when (group) {
        MinePage.APPEARANCE -> AppearanceGroup(model, settings)
        MinePage.DISPLAY -> DisplayGroup(model, settings)
        MinePage.SECURITY -> SecurityGroup(model, settings)
        MinePage.COMM -> CommGroup(model, settings)
        MinePage.ABOUT -> AboutGroup()
        MinePage.HISTORY -> HistoryPage(model, overview, recording, onDelete)
        MinePage.STORAGE -> StorageSettings(model, settings)
        else -> Unit
    }
}

@Composable
private fun DeleteSessionSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    InPageSheet(stringResource(R.string.mine_history_delete_title), onDismiss, tag = "history-delete-sheet") {
        Text(stringResource(R.string.mine_history_delete_body), color = LocalModbusColors.current.muted, fontSize = 14.sp, lineHeight = 20.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.action_cancel), onDismiss, Modifier.weight(1f).testTag("history-delete-cancel"), compact = true)
            PrimaryButton(stringResource(R.string.mine_history_delete), onConfirm, Modifier.weight(1f).testTag("history-delete-confirm"), danger = true, compact = true)
        }
    }
}

@Composable
private fun AppearanceGroup(model: MineViewModel, settings: UiSettings) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        SectionLabel(stringResource(R.string.setting_theme))
        SegmentedTextControl(ThemeMode.entries, settings.theme, model::setTheme) { themeName(it) }
        SectionLabel(stringResource(R.string.setting_accent))
        ColorSwatchRow(settings.accent, onSelect = model::setAccent)
        SectionLabel(stringResource(R.string.setting_font))
        SegmentedTextControl(FontScale.entries, settings.fontScale, model::setFont) { fontName(it) }
        SectionLabel(stringResource(R.string.setting_density))
        SegmentedTextControl(DensityMode.entries, settings.density, model::setDensity) { densityName(it) }
        SettingsCard {
            SettingRow(
                stringResource(R.string.setting_keep_screen),
                Modifier.testTag("sw-keep"),
                subtitle = stringResource(R.string.keep_screen_hint),
                trailing = SettingTrailing.SWITCH,
                checked = settings.keepScreenOn,
                onChecked = model::setKeepScreen,
            )
            RowDivider()
            SettingRow(
                stringResource(R.string.setting_landscape_curve),
                Modifier.testTag("sw-curve"),
                subtitle = stringResource(R.string.landscape_curve_hint),
                trailing = SettingTrailing.SWITCH,
                checked = settings.landscapeCurveFullscreen,
                onChecked = model::setLandscapeCurve,
            )
            RowDivider()
            SettingRow(
                stringResource(R.string.setting_vibration),
                Modifier.testTag("sw-vibration"),
                subtitle = stringResource(R.string.vibration_hint),
                trailing = SettingTrailing.SWITCH,
                checked = settings.vibration,
                onChecked = model::setVibration,
            )
        }
    }
}

@Composable
private fun DisplayGroup(model: MineViewModel, settings: UiSettings) {
    val spacing = LocalSpacing.current
    val colors = LocalModbusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        SectionLabel(stringResource(R.string.setting_address))
        SegmentedTextControl(AddressStyle.entries, settings.addressStyle, model::setAddress) { addressName(it) }
        SectionLabel(stringResource(R.string.setting_number))
        SegmentedTextControl(NumberFormat.entries, settings.numberFormat, model::setNumber) { it.name }
        SectionLabel(stringResource(R.string.setting_time))
        SegmentedTextControl(TimeStyle.entries, settings.timeStyle, model::setTime) { timeName(it) }
        Text(stringResource(R.string.time_hint), color = colors.dim, fontSize = 12.sp, lineHeight = 17.sp)
        SettingsCard {
            SettingRow(
                stringResource(R.string.setting_hex),
                Modifier.testTag("sw-hex"),
                trailing = SettingTrailing.SWITCH,
                checked = settings.hexPrefix,
                onChecked = model::setHexPrefix,
            )
            RowDivider()
            SettingRow(
                stringResource(R.string.setting_crc_dim),
                Modifier.testTag("sw-crc-dim"),
                trailing = SettingTrailing.SWITCH,
                checked = settings.logCrcDim,
                onChecked = model::setLogCrcDim,
            )
        }
    }
}

@Composable
private fun SecurityGroup(model: MineViewModel, settings: UiSettings) {
    SettingsCard {
        SettingRow(
            stringResource(R.string.mine_label_security),
            Modifier.testTag("row-secure-confirm"),
            subtitle = stringResource(R.string.secure_confirm_hint),
            trailing = SettingTrailing.LOCKED,
        )
        RowDivider()
        SettingRow(
            stringResource(R.string.setting_coil_warning),
            Modifier.testTag("sw-coil-warning"),
            subtitle = stringResource(R.string.coil_warning_hint),
            trailing = SettingTrailing.SWITCH,
            checked = settings.coilExtraWarning,
            onChecked = model::setCoilExtraWarning,
        )
    }
}

private enum class CommField(val label: Int) {
    TIMEOUT(R.string.comm_timeout),
    GAP(R.string.comm_gap),
    BROADCAST(R.string.comm_broadcast),
}

@Composable
private fun CommGroup(model: MineViewModel, settings: UiSettings) {
    val spacing = LocalSpacing.current
    val colors = LocalModbusColors.current
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val field = CommField.entries.firstOrNull { it.name == editing }
    // 编辑在页内进行（不弹对话框）：输入框在对话框窗口里键盘 / 焦点不好控制，页内和连接页的输入一致。
    BackHandler(enabled = field != null) { editing = null }
    if (field != null) {
        CommEditor(field, settings, model, onDone = { editing = null })
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        SettingsCard {
            SettingRow(
                stringResource(R.string.comm_timeout),
                Modifier.testTag("comm-timeout"),
                value = stringResource(R.string.comm_value_ms, settings.defaultResponseTimeoutMs),
                onClick = { editing = CommField.TIMEOUT.name },
            )
            RowDivider()
            SettingRow(
                stringResource(R.string.comm_gap),
                Modifier.testTag("comm-gap"),
                value = stringResource(R.string.comm_value_ms, settings.defaultInterRequestMs),
                onClick = { editing = CommField.GAP.name },
            )
            RowDivider()
            SettingRow(
                stringResource(R.string.comm_broadcast),
                Modifier.testTag("comm-broadcast"),
                value = stringResource(R.string.comm_value_ms, settings.defaultBroadcastWaitMs),
                onClick = { editing = CommField.BROADCAST.name },
            )
            RowDivider()
            // 回显是三选一：点行直接在行尾弹菜单选，不进编辑器。
            var echoOpen by remember { mutableStateOf(false) }
            Box {
                SettingRow(
                    stringResource(R.string.comm_echo),
                    Modifier.testTag("comm-echo"),
                    value = echoName(settings.defaultEcho),
                    onClick = { echoOpen = true },
                )
                Box(Modifier.align(Alignment.BottomEnd)) {
                    ModbusMenu(expanded = echoOpen, onDismiss = { echoOpen = false }, modifier = Modifier.testTag("comm-echo-menu")) {
                        EchoMode.entries.forEach { mode ->
                            ModbusMenuItem(
                                echoName(mode),
                                onClick = { echoOpen = false; model.setDefaultEcho(mode) },
                                selected = mode == settings.defaultEcho,
                            )
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.comm_hint), color = colors.dim, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** 一项数值型通信默认值的编辑（不合法就不能保存，错误文案同连接页）；回显在列表行上直接弹菜单。 */
@Composable
private fun CommEditor(field: CommField, settings: UiSettings, model: MineViewModel, onDone: () -> Unit) {
    val spacing = LocalSpacing.current
    val label = stringResource(field.label)
    Column(Modifier.testTag("comm-editor"), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        SectionLabel(stringResource(R.string.comm_edit_title, label))
        val (initial, range, errorRes) = when (field) {
            CommField.TIMEOUT -> Triple(settings.defaultResponseTimeoutMs, MineViewModel.TimeoutRange, R.string.error_timeout_range)
            CommField.GAP -> Triple(settings.defaultInterRequestMs, MineViewModel.IntervalRange, R.string.error_timeout_range)
            else -> Triple(settings.defaultBroadcastWaitMs, MineViewModel.BroadcastRange, R.string.error_broadcast_range)
        }
        var text by rememberSaveable(field.name) { mutableStateOf(initial.toString()) }
        val parsed = MineViewModel.parseMs(text, range)
        NumberInput(
            label = label,
            value = text,
            error = if (parsed == null) stringResource(errorRes) else null,
            sub = stringResource(R.string.comm_unit_ms),
            modifier = Modifier.fillMaxWidth(),
            onValue = { text = it.filter(Char::isDigit).take(7) },
        )
        EditorButtons(
            enabled = parsed != null,
            onSave = {
                parsed?.let {
                    when (field) {
                        CommField.TIMEOUT -> model.setDefaultTimeout(it)
                        CommField.GAP -> model.setDefaultInterval(it)
                        else -> model.setDefaultBroadcastWait(it)
                    }
                    onDone()
                }
            },
            onCancel = onDone,
        )
    }
}

@Composable
private fun EditorButtons(enabled: Boolean, onSave: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f).testTag("comm-cancel"), compact = true)
        PrimaryButton(stringResource(R.string.action_ok), onSave, Modifier.weight(1f).testTag("comm-save"), enabled = enabled, compact = true)
    }
}

@Composable
private fun AboutGroup() {
    SettingsCard {
        SettingRow(
            stringResource(R.string.about_version),
            Modifier.testTag("about-version"),
            trailing = SettingTrailing.NONE,
            value = appVersion(),
        )
        RowDivider()
        SettingRow(
            stringResource(R.string.about_privacy),
            trailing = SettingTrailing.NONE,
            value = stringResource(R.string.about_privacy_value),
        )
        RowDivider()
        SettingRow(
            stringResource(R.string.about_author),
            Modifier.testTag("about-author"),
            subtitle = stringResource(R.string.about_license_value),
            value = io.github.xingyingyuzhui.modbusmobile.application.Authorship.AUTHOR,
            onClick = rememberOpenRepo(),
        )
    }
}

// ---------------------------------------------------------------- 文案

@Composable
private fun echoName(mode: EchoMode): String = stringResource(
    when (mode) {
        EchoMode.OFF -> R.string.echo_off
        EchoMode.ON -> R.string.echo_on
        EchoMode.AUTO -> R.string.echo_auto
    },
)

@Composable
private fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun fontName(scale: FontScale): String = stringResource(
    when (scale) {
        FontScale.STANDARD -> R.string.font_standard
        FontScale.LARGE -> R.string.font_large
        FontScale.EXTRA -> R.string.font_extra
    },
)

@Composable
private fun densityName(mode: DensityMode): String = stringResource(
    when (mode) {
        DensityMode.COMFORTABLE -> R.string.density_comfortable
        DensityMode.COMPACT -> R.string.density_compact
    },
)

@Composable
private fun addressName(style: AddressStyle): String = stringResource(
    when (style) {
        AddressStyle.ZERO_BASED -> R.string.address_zero
        AddressStyle.MODICON -> R.string.address_modicon
        AddressStyle.BOTH -> R.string.address_both
    },
)

@Composable
private fun timeName(style: TimeStyle): String = stringResource(
    when (style) {
        TimeStyle.ELAPSED -> R.string.time_elapsed
        TimeStyle.CLOCK -> R.string.time_clock
    },
)
