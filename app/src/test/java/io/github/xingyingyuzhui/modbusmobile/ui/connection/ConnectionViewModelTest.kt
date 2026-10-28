package io.github.xingyingyuzhui.modbusmobile.ui.connection

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.EchoMode
import io.github.xingyingyuzhui.modbusmobile.application.Parity
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {
    @Test
    fun invalidTimeoutDisablesConnectAndSerialEditsReachTheSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = RecordingSession()
            val model = ConnectionViewModel(session)
            assertTrue(model.validation.ok)
            assertEquals("9600 8N1", model.wireLabel)

            model.timeoutText = "0"
            assertFalse(model.validation.ok)
            model.connect()
            advanceUntilIdle()
            assertNull(session.connected)

            model.timeoutText = "1000"
            model.gapText = "0"
            assertFalse(model.validation.ok)

            model.gapText = "20"
            model.baud = 115200
            model.parity = Parity.EVEN
            assertEquals("115200 8E1", model.wireLabel)
            model.connect()
            advanceUntilIdle()
            val serial = session.connected
            assertEquals(115200, serial?.baudRate)
            assertEquals(Parity.EVEN, serial?.parity)
            assertEquals(20L, serial?.interRequestDelayMs)
            assertEquals(EchoMode.OFF, serial?.echoMode)
            assertEquals(Role.MASTER, session.role)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshAsksTheSessionToRescan() {
        val session = RecordingSession()
        val model = ConnectionViewModel(session)
        assertEquals(0, session.refreshes)
        model.refresh()
        assertEquals(1, session.refreshes)
    }

    @Test
    fun commDefaultsSeedTheAdvancedFieldsAndBroadcastWaitReachesTheSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = RecordingSession()
            val defaults = UiSettings(
                defaultResponseTimeoutMs = 2_500, defaultInterRequestMs = 35,
                defaultBroadcastWaitMs = 400, defaultEcho = EchoMode.AUTO,
            )
            val model = ConnectionViewModel(session, defaults)
            assertEquals("2500", model.timeoutText)
            assertEquals("35", model.gapText)
            assertEquals(EchoMode.AUTO, model.echoMode)
            model.connect()
            advanceUntilIdle()
            val serial = session.connected!!
            assertEquals(2_500L, serial.responseTimeoutMs)
            assertEquals(35L, serial.interRequestDelayMs)
            assertEquals(400L, serial.turnaroundMs)
            assertEquals(EchoMode.AUTO, serial.echoMode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changingDefaultsLaterOnlyMovesFieldsTheUserHasNotTouched() {
        val model = ConnectionViewModel(RecordingSession())
        model.timeoutText = "3000" // 用户手动改过
        model.applyDefaults(UiSettings(defaultResponseTimeoutMs = 2_000, defaultInterRequestMs = 50, defaultEcho = EchoMode.ON))
        assertEquals("用户改过的不被覆盖", "3000", model.timeoutText)
        assertEquals("没动过的跟着默认值走", "50", model.gapText)
        assertEquals(EchoMode.ON, model.echoMode)
    }
}

private class RecordingSession : SessionManager {
    var refreshes = 0
    var connected: SerialSettings? = null
    var role: Role? = null
    override val state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val traffic = MutableSharedFlow<TrafficEvent>()
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) {
        connected = serial
        this.role = role
    }

    override suspend fun disconnect() = Unit

    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected

    override fun refreshDevices() {
        refreshes += 1
    }

}
