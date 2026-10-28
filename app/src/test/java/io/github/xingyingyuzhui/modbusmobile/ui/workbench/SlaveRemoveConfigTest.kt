@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationEngine
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 虚拟从站“删除配置”要删干净：地址段、Unit、以及挂在它们上面的模拟规则，保存的配置里都不再出现。 */
class SlaveRemoveConfigTest {
    private val hr = RegisterArea.HOLDING_REGISTER
    private val coil = RegisterArea.COIL

    private class Session : SessionManager {
        val bank = InMemoryRegisterBank()
        val engine = SimulationEngine(bank, object : MonotonicClock { override fun nowNs() = 0L })
        var saved: SlaveSetup? = null
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, 1))
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(bank)
        override val simulation = MutableStateFlow<SimulationEngine?>(engine)
        override val slaveUnits = MutableStateFlow(listOf(1, 2))
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
        override suspend fun setSlaveUnits(units: List<Int>): Boolean {
            slaveUnits.value = units
            return saveSlaveSetup()
        }

        override fun saveSlaveSetup(): Boolean {
            saved = SlaveSetup.snapshot(bank, slaveUnits.value, engine.states.value.map { it.rule })
            return true
        }
    }

    private fun rule(id: String, unit: Int, area: RegisterArea, address: Int, count: Int = 1) =
        SimulationRule(id, SimulationTarget(unit, area, address, count), Waveform.Constant(1.0))

    private fun withModel(test: suspend TestScope.(Session, SlaveViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val session = Session()
            // 按“保存的配置装进来”的来源放初始值：没有“最近来源”，不会启动每秒刷新的计时器
            (0..9).forEach { session.bank.putWord(1, hr, it, it, ChangeSource.INITIAL) }
            (0..7).forEach { session.bank.putBit(1, coil, it, false, ChangeSource.INITIAL) }
            session.bank.putWord(2, hr, 0, 99, ChangeSource.INITIAL)
            val model = SlaveViewModel(session, clock = { 0L })
            advanceUntilIdle()
            test(session, model)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removeRangeDefaultsToTheFirstContiguousRunOfTheCurrentArea() = withModel { _, model ->
        model.openRemoveRange()
        val sheet = requireNotNull(model.removeRange)
        assertEquals(hr, sheet.area)
        assertEquals("0", sheet.start)
        assertEquals("10", sheet.count)
    }

    @Test
    fun removeRangeDeletesAddressesAndOverlappingRulesAndSaves() = withModel { session, model ->
        session.engine.setRules(
            listOf(
                rule("in", 1, hr, 4),
                rule("edge", 1, hr, 1, count = 2),
                rule("outside", 1, hr, 8),
                rule("otherUnit", 2, hr, 0),
                rule("coil", 1, coil, 3),
            ),
        )
        model.openRemoveRange()
        model.updateRemoveRange { copy(start = "2", count = "5") }
        model.commitRemoveRange()
        advanceUntilIdle()

        assertNull(model.removeRange)
        assertEquals(listOf(0, 1, 7, 8, 9), model.rowsOf(hr).map { it.address })
        assertEquals(listOf("outside", "otherUnit", "coil"), session.engine.states.value.map { it.rule.id })
        val saved = requireNotNull(session.saved)
        val unit1 = saved.units.single { it.unitId == 1 }
        assertEquals(listOf(0, 1, 7, 8, 9), unit1.regions.filter { it.area == hr }.flatMap { r -> r.values.indices.map { r.start + it } })
        assertEquals(listOf("outside", "otherUnit", "coil"), saved.rules.map { it.id })
        // Unit 2 同地址不受影响
        assertEquals(99, session.bank.words(2, hr).single().value)
    }

    @Test
    fun removeRangeInAnotherAreaSwitchesToIt() = withModel { _, model ->
        model.openRemoveRange()
        model.updateRemoveRange { copy(area = coil, start = "0", count = "8") }
        model.commitRemoveRange()
        assertEquals(coil, model.area)
        assertTrue(model.rowsOf(coil).isEmpty())
        assertEquals(10, model.rowsOf(hr).size)
    }

    @Test
    fun removeRangeWithNothingThereKeepsTheSheetWithAnError() = withModel { session, model ->
        model.openRemoveRange()
        model.updateRemoveRange { copy(start = "100", count = "5") }
        model.commitRemoveRange()
        assertNotNull(model.removeRange?.error)
        assertNull(session.saved)

        model.updateRemoveRange { copy(start = "x") }
        assertNull(model.removeRange?.error)
        model.commitRemoveRange()
        assertNotNull(model.removeRange?.error)
        assertEquals(10, model.rowsOf(hr).size)
    }

    @Test
    fun openRemoveRangeOnAnEmptyAreaDoesNothing() = withModel { _, model ->
        model.selectArea(RegisterArea.INPUT_REGISTER)
        model.openRemoveRange()
        assertNull(model.removeRange)
    }

    @Test
    fun removeUnitDropsItsRulesAndBankValues() = withModel { session, model ->
        session.engine.setRules(listOf(rule("u1", 1, hr, 0), rule("u2", 2, hr, 0)))
        model.selectUnit(2)
        model.openRule("u2")
        assertNotNull(model.ruleDraft)
        model.askRemoveUnit()
        assertEquals(2, model.removeUnit)
        model.confirmRemoveUnit()
        advanceUntilIdle()

        assertEquals(listOf(1), session.slaveUnits.value)
        assertEquals(listOf("u1"), session.engine.states.value.map { it.rule.id })
        assertTrue(session.bank.words(2, hr).isEmpty())
        assertNull(model.ruleDraft)
        val saved = requireNotNull(session.saved)
        assertEquals(listOf(1), saved.unitIds)
        assertEquals(listOf("u1"), saved.rules.map { it.id })
        assertFalse(model.saveFailed)
        assertEquals(1, model.unit)
    }
}
