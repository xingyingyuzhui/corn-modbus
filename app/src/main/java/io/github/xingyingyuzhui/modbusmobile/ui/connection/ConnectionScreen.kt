package io.github.xingyingyuzhui.modbusmobile.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChevronIcon
import io.github.xingyingyuzhui.modbusmobile.ui.components.HeaderIconButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.PageHeader
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.em
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.BusTopology
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.InputError
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.LocalSession
import io.github.xingyingyuzhui.modbusmobile.ui.components.FieldTileMenu
import io.github.xingyingyuzhui.modbusmobile.ui.components.ModbusCard
import io.github.xingyingyuzhui.modbusmobile.ui.components.NumberInput
import io.github.xingyingyuzhui.modbusmobile.ui.components.PrimaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SecondaryButton
import io.github.xingyingyuzhui.modbusmobile.ui.components.SectionLabel
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegmentedControl
import io.github.xingyingyuzhui.modbusmobile.ui.shell.dockClearance
import io.github.xingyingyuzhui.modbusmobile.ui.shell.screenFrame
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalUiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalSpacing
import io.github.xingyingyuzhui.modbusmobile.ui.theme.MinTouch
import io.github.xingyingyuzhui.modbusmobile.ui.theme.Mono

/** 稿面文字行高 = 1.4 × 字号。 */
val DesignLineHeight = 1.4.em

/** 稿面 `.seg.big`：整体 60 = 项 54 + 上下内边距 3。 */
private val RoleSegItemHeight = 54.dp

private val baudChoices = listOf(1_200, 2_400, 4_800, 9_600, 19_200, 38_400, 57_600, 115_200)

@Composable
fun ConnectionScreen() {
    val session = LocalSession.current
    val uiSettings = LocalUiSettings.current
    val model = viewModel { ConnectionViewModel(session, uiSettings) }
    LaunchedEffect(
        uiSettings.defaultResponseTimeoutMs, uiSettings.defaultInterRequestMs,
        uiSettings.defaultBroadcastWaitMs, uiSettings.defaultEcho,
    ) { model.applyDefaults(uiSettings) }
    val state by model.state.collectAsStateWithLifecycle()
    val devices by model.devices.collectAsStateWithLifecycle()
    val echoDetected by model.echoDetected.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val landscape = LocalShellMode.current == ShellMode.LANDSCAPE
    // 稿面文字行高 ≈ 字号 × 1.4（12px → 17，14px → 20）；Material 默认 bodyLarge 行高 24sp，
    // 会把每个小字行撑到 24：标题行→内容的间距、设备行、“高级”折叠行都因此偏大。
    ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = DesignLineHeight)) {
    if (landscape) {
        ConnectionLandscape(model, state, devices, echoDetected)
    } else {
        // AL-12：主按钮底到 Dock 顶 = space.dockGap 16（dockClearance 已含），不再另加；没有 Dock 时到底边也是 16。
        val bottomInset = dockClearance().takeIf { it > 0.dp } ?: spacing.dockGap
        Column(Modifier.screenFrame().padding(bottom = bottomInset)) {
            PageHeader(stringResource(R.string.title_connection)) { HelpButton(model) }
            ErrorCaption(state)
            FadedScroll(Modifier.weight(1f)) {
                DeviceCard(model, devices)
                RoleCard(model)
                SerialCard(model)
                AdvancedCard(model, state, echoDetected)
            }
            ActionButton(model, state, compact = false, modifier = Modifier.padding(top = spacing.s))
        }
    }
    }
    if (model.helpOpen) HelpDialog { model.helpOpen = false }
}

/** 横屏：稿面没有连接页横屏，按“左缩进 88、上下 12、无页头”的通用横屏规则排两列，主按钮压成 44 高。 */
@Composable
private fun ConnectionLandscape(
    model: ConnectionViewModel,
    state: SessionState,
    devices: List<UsbDeviceSummary>,
    echoDetected: Boolean?,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.screenFrame().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FadedScroll(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ErrorCaption(state) }
                    HelpButton(model)
                }
                DeviceCard(model, devices)
                RoleCard(model)
            }
            FadedScroll(Modifier.weight(1f).fillMaxHeight()) {
                SerialCard(model)
                AdvancedCard(model, state, echoDetected)
            }
        }
        ActionButton(model, state, compact = true)
    }
}

@Composable
private fun HelpButton(model: ConnectionViewModel) {
    val colors = LocalModbusColors.current
    HeaderIconButton(stringResource(R.string.action_help), { model.helpOpen = true }) {
        Text("?", color = colors.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 页头下面不再放“主站 · 运行中”之类的状态副标题（老板反馈，已删）。
 * 只保留一个例外：连接失败的原因（红字）——不显示就没人知道为什么连不上。
 */
@Composable
private fun ErrorCaption(state: SessionState) {
    val colors = LocalModbusColors.current
    if (state is SessionState.Error) {
        Text(state.reason, color = colors.error, fontSize = 13.sp, modifier = Modifier.testTag("error-caption"))
    }
}

/**
 * 卡片列表在主按钮上方滚动。内容没滚到底时，底边用 bg 色渐隐，
 * 避免卡片被生硬地切在主按钮（连接态是“断开”）上沿，看上去像按钮压在卡片上。
 */
@Composable
private fun FadedScroll(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalModbusColors.current
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(spacing.group),
            content = content,
        )
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.bg))),
            )
        }
    }
}

@Composable
private fun ActionButton(
    model: ConnectionViewModel,
    state: SessionState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val busy = state is SessionState.RequestingPermission ||
        state is SessionState.Opening ||
        state is SessionState.Stopping
    val roleName = stringResource(if (model.role == Role.MASTER) R.string.role_master else R.string.role_slave)
    if (state is SessionState.Running) {
        SecondaryButton(stringResource(R.string.action_disconnect), model::disconnect, modifier, enabled = !busy, compact = compact)
    } else {
        PrimaryButton(
            stringResource(R.string.connect_action, roleName, model.wireLabel),
            model::connect,
            modifier,
            enabled = !busy && model.validation.ok,
            compact = compact,
        )
    }
}

@Composable
private fun DeviceCard(model: ConnectionViewModel, devices: List<UsbDeviceSummary>) {
    val colors = LocalModbusColors.current
    ModbusCard(Modifier.testTag("card-device")) {
        SectionLabel(stringResource(R.string.section_device)) {
            // 稿面：一行 12px 文字，不撑高卡头。点击热区靠 Compose 最小触控目标补足（真机未验证）。
            Text(
                stringResource(R.string.action_refresh),
                color = colors.accentText,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = model::refresh),
            )
        }
        DeviceRow(
            icon = R.drawable.ic_device_demo,
            title = stringResource(R.string.device_demo),
            subtitle = stringResource(R.string.device_demo_sub),
            selected = model.selected == ConnectionTarget.Demo,
            onClick = { model.selected = ConnectionTarget.Demo },
        )
        devices.forEach { device ->
            val target = ConnectionTarget.Usb(device.deviceId, device.portIndex)
            DeviceRow(
                icon = R.drawable.ic_device_usb,
                title = device.productName,
                subtitle = device.subtitle(),
                selected = model.selected == target,
                onClick = { model.selected = target },
            )
        }
    }
}

@Composable
private fun RoleCard(model: ConnectionViewModel) {
    val colors = LocalModbusColors.current
    ModbusCard(Modifier.testTag("card-role")) {
        SectionLabel(stringResource(R.string.section_role))
        SegmentedControl(
            options = listOf(Role.MASTER, Role.SLAVE),
            selected = model.role,
            onSelect = { model.role = it },
            itemHeight = RoleSegItemHeight,
        ) { role ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (role == Role.MASTER) R.string.role_master else R.string.role_slave),
                    color = if (model.role == role) colors.text else colors.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    stringResource(if (role == Role.MASTER) R.string.role_master_hint else R.string.role_slave_hint),
                    color = colors.dim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SerialCard(model: ConnectionViewModel) {
    // 只有标题 + 四个下拉格：点格子弹菜单改单项，没有预设 chip、也不在角标重复线制（连接按钮上已有）。
    ModbusCard(Modifier.testTag("card-serial")) {
        SectionLabel(stringResource(R.string.section_serial))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldTileMenu(
                stringResource(R.string.field_baud), model.baud, baudChoices, { it.toString() }, { model.baud = it },
                Modifier.weight(1.7f).testTag("serial-baud"),
            )
            FieldTileMenu(
                stringResource(R.string.field_data_bits), model.dataBits, listOf(7, 8), { it.toString() }, { model.dataBits = it },
                Modifier.weight(1f).testTag("serial-data-bits"),
            )
            FieldTileMenu(
                stringResource(R.string.field_parity), model.parity, Parity.entries, { parityName(it) }, { model.parity = it },
                Modifier.weight(1f).testTag("serial-parity"),
                mono = false,
            )
            FieldTileMenu(
                stringResource(R.string.field_stop_bits), model.stopBits, listOf(1, 2), { it.toString() }, { model.stopBits = it },
                Modifier.weight(1f).testTag("serial-stop-bits"),
            )
        }
    }
}

/** 稿面“高级 · 超时 1000 · 间隔 20 · 回显 关 ›”折叠行；回显不再是独立卡片，展开后才是回显选项。 */
@Composable
private fun AdvancedCard(model: ConnectionViewModel, state: SessionState, echoDetected: Boolean?) {
    val colors = LocalModbusColors.current
    ModbusCard(Modifier.testTag("card-advanced")) {
        Row(
            Modifier.testTag("advanced-row").fillMaxWidth().clickable { model.advancedOpen = !model.advancedOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    R.string.advanced_summary,
                    stringResource(
                        R.string.advanced_detail,
                        model.timeoutText.trim().toLongOrNull() ?: 0,
                        model.gapText.trim().toLongOrNull() ?: 0,
                        echoName(model.echoMode),
                    ),
                ),
                color = colors.muted,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            ChevronIcon(colors.dim, expanded = model.advancedOpen)
        }
        if (model.advancedOpen) {
            // 回显 / 拓扑和串口四格一样用下拉格（点格子弹菜单），不再用 chip 行。
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldTileMenu(
                    stringResource(R.string.section_echo), model.echoMode, EchoMode.entries, { echoName(it) }, { model.echoMode = it },
                    Modifier.weight(1f).testTag("advanced-echo"),
                    mono = false,
                    optionTag = { "echo-${it.name}" },
                )
                if (model.role == Role.SLAVE) {
                    FieldTileMenu(
                        stringResource(R.string.section_topology), model.topology, BusTopology.entries, { topologyName(it) }, { model.topology = it },
                        Modifier.weight(1f).testTag("advanced-topology"),
                        mono = false,
                        optionTag = { "topology-${it.name}" },
                    )
                }
            }
            if (state is SessionState.Running) {
                Text(echoDetected.caption(), color = colors.muted, fontSize = 12.sp)
            }
            NumberInput(
                stringResource(R.string.field_timeout),
                model.timeoutText,
                error = inputErrorText(model.validation.timeoutError),
            ) { model.timeoutText = it }
            NumberInput(
                stringResource(R.string.field_gap),
                model.gapText,
                error = inputErrorText(model.validation.gapError),
            ) { model.gapText = it }
        } else if (!model.validation.ok) {
            val error = inputErrorText(model.validation.timeoutError ?: model.validation.gapError)
            if (error != null) Text(error, color = colors.error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HelpDialog(onClose: () -> Unit) {
    val colors = LocalModbusColors.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surface1,
        titleContentColor = colors.text,
        textContentColor = colors.muted,
        title = { Text(stringResource(R.string.help_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.help_role), fontSize = 14.sp)
                Text(stringResource(R.string.help_serial), fontSize = 14.sp)
                Text(stringResource(R.string.help_echo), fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close), color = colors.accentText) }
        },
    )
}

@Composable
private fun DeviceRow(
    icon: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalModbusColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .testTag("device-row")
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(if (selected) colors.wash(colors.accent) else colors.surface2)
            .border(1.dp, if (selected) colors.accent else colors.line, shape)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            // 稿面 `.dev`：padding 10 + border 1，图标从外缘 11 起；border 在 Compose 里不占位，所以内边距取 11。
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (selected) colors.accent else colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, style = Mono.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = colors.muted)
        }
        Box(
            Modifier
                .size(20.dp)
                .border(2.dp, if (selected) colors.accent else colors.dim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
            }
        }
    }
}

@Composable
private fun UsbDeviceSummary.subtitle(): String =
    stringResource(R.string.device_usb_sub, vendorId, productId, portIndex)

@Composable
private fun inputErrorText(error: InputError?): String? = when (error) {
    null -> null
    InputError.TIMEOUT_RANGE -> stringResource(R.string.error_timeout_range)
    InputError.GAP_POSITIVE -> stringResource(R.string.error_gap_positive)
}

@Composable
private fun parityName(parity: Parity): String = stringResource(
    when (parity) {
        Parity.NONE -> R.string.parity_none
        Parity.ODD -> R.string.parity_odd
        Parity.EVEN -> R.string.parity_even
    },
)

@Composable
private fun echoName(mode: EchoMode): String = stringResource(
    when (mode) {
        EchoMode.OFF -> R.string.echo_off
        EchoMode.ON -> R.string.echo_on
        EchoMode.AUTO -> R.string.echo_auto
    },
)

@Composable
private fun topologyName(topology: BusTopology): String = stringResource(
    when (topology) {
        BusTopology.POINT_TO_POINT -> R.string.topology_ptp
        BusTopology.MULTIDROP -> R.string.topology_multi
    },
)

@Composable
private fun Boolean?.caption(): String = stringResource(
    when (this) {
        true -> R.string.echo_detected_on
        false -> R.string.echo_detected_off
        null -> R.string.echo_detected_pending
    },
)
