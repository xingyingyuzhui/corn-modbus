package io.github.xingyingyuzhui.modbusmobile.ui.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.BusTopology
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.TimeoutGapValidation
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.validateTimeoutGap
import io.github.xingyingyuzhui.modbusmobile.application.wireLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import kotlinx.coroutines.launch

/**
 * [defaults] 是「我的 · 通信默认值」：只作为高级设置的初始值，连接页仍可逐次修改。
 * 默认值之后又改了：只有“用户没动过”的输入框会跟着变（输入框当前值 == 上一次应用的默认值）。
 */
class ConnectionViewModel(
    private val session: SessionManager,
    defaults: UiSettings = UiSettings(),
) : ViewModel() {
    private var applied = defaults
    private var broadcastWaitMs = defaults.defaultBroadcastWaitMs

    val state = session.state
    val devices = session.usbDevices
    val echoDetected = session.echoDetected

    var baud by mutableStateOf(9_600)
    var dataBits by mutableStateOf(8)
    var stopBits by mutableStateOf(1)
    var parity by mutableStateOf(Parity.NONE)
    var role by mutableStateOf(Role.MASTER)
    var topology by mutableStateOf(BusTopology.POINT_TO_POINT)
    var echoMode by mutableStateOf(defaults.defaultEcho)
    var selected by mutableStateOf<ConnectionTarget>(ConnectionTarget.Demo)
    var timeoutText by mutableStateOf(defaults.defaultResponseTimeoutMs.toString())
    var gapText by mutableStateOf(defaults.defaultInterRequestMs.toString())
    var advancedOpen by mutableStateOf(false)
    var helpOpen by mutableStateOf(false)

    val validation: TimeoutGapValidation
        get() = validateTimeoutGap(timeoutText, gapText)

    val wireLabel: String
        get() = wireLabel(baud, dataBits, parity, stopBits)

    fun applyDefaults(new: UiSettings) {
        val old = applied
        if (timeoutText == old.defaultResponseTimeoutMs.toString()) timeoutText = new.defaultResponseTimeoutMs.toString()
        if (gapText == old.defaultInterRequestMs.toString()) gapText = new.defaultInterRequestMs.toString()
        if (echoMode == old.defaultEcho) echoMode = new.defaultEcho
        broadcastWaitMs = new.defaultBroadcastWaitMs
        applied = new
    }

    fun refresh() {
        session.refreshDevices()
    }

    fun connect() {
        if (!validation.ok) return
        val timeout = timeoutText.trim().toLong()
        val gap = gapText.trim().toLong()
        viewModelScope.launch {
            session.connect(
                selected,
                SerialSettings(
                    baudRate = baud,
                    dataBits = dataBits,
                    parity = parity,
                    stopBits = stopBits,
                    responseTimeoutMs = timeout,
                    interRequestDelayMs = gap,
                    turnaroundMs = broadcastWaitMs,
                    topology = topology,
                    echoMode = echoMode,
                ),
                role,
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch { session.disconnect() }
    }
}
