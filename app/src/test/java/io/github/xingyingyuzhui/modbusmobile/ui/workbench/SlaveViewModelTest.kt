@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlaveViewModelTest {
    private val hr = RegisterArea.HOLDING_REGISTER
    private val coil = RegisterArea.COIL

    @Test
    fun followsTheNewBankAfterReconnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SlaveSession()
            val model = SlaveViewModel(session)
            val first = InMemoryRegisterBank().also { it.putWord(1, hr, 0, 11) }
            val second = InMemoryRegisterBank().also { it.putWord(1, hr, 0, 22) }
            advanceUntilIdle()
            session.banks.value = first
            advanceUntilIdle()
            assertEquals(11, model.rowsOf(hr).single().value)

            session.banks.value = null
            advanceUntilIdle()
            assertTrue(model.rowsOf(hr).isEmpty())

            session.banks.value = second
            advanceUntilIdle()
            assertEquals(22, model.rowsOf(hr).single().value)

            model.startEdit(hr, 0)
            model.setDraft("33")
            model.commitEdit()
            advanceUntilIdle()
            assertEquals(33, second.words(1, hr).single().value)
            assertEquals(11, first.words(1, hr).single().value)
            assertEquals(33, model.rowsOf(hr).single().value)
            assertNull(model.edit)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidDraftStaysOpenWithAnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SlaveSession()
            val bank = InMemoryRegisterBank().also { it.putWord(1, hr, 0, 5) }
            val model = SlaveViewModel(session)
            session.banks.value = bank
            advanceUntilIdle()
            model.startEdit(hr, 0)
            model.setDraft("70000")
            model.commitEdit()
            assertNotNull(model.edit?.error)
            assertEquals(5, bank.words(1, hr).single().value)
            model.cancelEdit()
            assertNull(model.edit)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun bitRowsToggleAndExternalChangesFlash() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var now = 1_000L
            val session = SlaveSession()
            val bank = InMemoryRegisterBank().also {
                it.putBit(1, coil, 0, false)
                it.putWord(1, hr, 0, 1)
            }
            val model = SlaveViewModel(session) { now }
            session.banks.value = bank
            advanceUntilIdle()
            assertNull(model.rowsOf(hr).single().changedAt)

            model.toggleBit(coil, 0)
            advanceUntilIdle()
            assertEquals(1, model.rowsOf(coil).single().value)
            assertEquals(1_000L, model.rowsOf(coil).single().changedAt)

            // 外部主站（这里用直接写 bank 代替）改了寄存器：这一行带时间戳
            now = 2_500L
            bank.writeWords(1, hr, 0, listOf(9))
            advanceUntilIdle()
            assertEquals(9, model.rowsOf(hr).single().value)
            assertEquals(2_500L, model.rowsOf(hr).single().changedAt)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun addRangeCreatesOnlyMissingAddressesAndKeepsExistingValues() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SlaveSession()
            val bank = InMemoryRegisterBank().also { it.putWord(1, hr, 1, 77) }
            val model = SlaveViewModel(session)
            session.banks.value = bank
            advanceUntilIdle()

            model.openAddRange()
            model.updateAddRange { copy(area = RegisterArea.INPUT_REGISTER, start = "100", count = "3") }
            model.commitAddRange()
            advanceUntilIdle()
            assertEquals(listOf(100, 101, 102), model.rowsOf(RegisterArea.INPUT_REGISTER).map { it.address })
            assertEquals(RegisterArea.INPUT_REGISTER, model.area)
            assertNull(model.addRange)

            model.openAddRange()
            model.updateAddRange { copy(area = hr, start = "0", count = "3") }
            model.commitAddRange()
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 2), model.rowsOf(hr).map { it.address })
            assertEquals(77, model.rowsOf(hr).first { it.address == 1 }.value)

            model.openAddRange()
            model.updateAddRange { copy(area = coil, start = "0", count = "2") }
            model.commitAddRange()
            advanceUntilIdle()
            assertEquals(listOf(false, false), bank.bits(1, coil).map { it.value })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rejectsOutOfRangeRanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = SlaveSession()
            val bank = InMemoryRegisterBank()
            val model = SlaveViewModel(session)
            session.banks.value = bank
            advanceUntilIdle()
            for ((start, count) in listOf("65530" to "10", "0" to "0", "0" to "126", "x" to "1", "-1" to "1")) {
                model.openAddRange()
                model.updateAddRange { copy(start = start, count = count) }
                model.commitAddRange()
                assertNotNull("$start/$count 应该被拒绝", model.addRange?.error)
            }
            assertTrue(bank.words(1, hr).isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }
}

class SlaveSession : SessionManager {
    val banks = MutableStateFlow<VirtualRegisterBank?>(null)
    override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, 1))
    override val traffic = MutableSharedFlow<TrafficEvent>()
    override val slaveBank = banks
    // 真实会话在从站模式下总有 Unit 列表；空列表现在表示“Unit 删光了”。
    override val slaveUnits = MutableStateFlow(listOf(1))
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
}
