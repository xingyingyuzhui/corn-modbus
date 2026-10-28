@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.DefaultSessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionCounters
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.rtu.Crc16Modbus
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSessionTest {
    @Test
    fun masterDemoReadsPresetWritesAndLogsCrc() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        val read = session.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 10))
        val values = (read as ModbusResult.Success).response as ModbusResponse.ReadRegisters
        assertEquals(listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90), values.values)

        val write = session.execute(ModbusRequest.WriteSingleRegister(1, 0, 123))
        assertTrue(write is ModbusResult.Success)
        val again = session.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 1))
        val echoed = (again as ModbusResult.Success).response as ModbusResponse.ReadRegisters
        assertEquals(listOf(123), echoed.values)

        runCurrent()
        val traffic = session.traffic.replayCache
        assertTrue(traffic.any { it.direction == TrafficDirection.TX })
        assertTrue(traffic.any { it.direction == TrafficDirection.RX })
        traffic.forEach { event ->
            assertTrue(Crc16Modbus.matches(event.bytes))
        }
        session.disconnect()
        assertEquals(SessionState.Idle, session.state.value)
    }

    @Test
    fun slaveDemoChangesHoldingRegisterEveryFiveSeconds() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        val bank = session.slaveBank.value ?: error("从站寄存器库为空")
        assertEquals(0, bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 0 }.value)
        // 第 5 拍先读再写。写请求还要等 SerialSettings 默认的 20ms 请求间隔。
        advanceTimeBy(5_100)
        runCurrent()
        val value = bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 0 }.value
        assertTrue("HR0=$value", value >= 1)
        val traffic = session.traffic.replayCache
        assertTrue(traffic.any { it.direction == TrafficDirection.RX })
        assertTrue(traffic.any { it.direction == TrafficDirection.TX })
        session.disconnect()
    }

    @Test
    fun masterCountersFollowTheSessionAndResetOnDisconnect() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        assertEquals("连接时从 0 开始", SessionCounters.Empty, session.counters.value)
        assertTrue(session.execute(ModbusRequest.ReadHoldingRegisters(1, 0, 10)) is ModbusResult.Success)
        // 地址 100 没有映射，演示从站回异常 02：算一次错误。
        assertTrue(session.execute(ModbusRequest.ReadHoldingRegisters(1, 100, 1)) is ModbusResult.Exception)
        runCurrent()
        val counters = session.counters.value
        assertEquals(2, counters.tx)
        assertEquals(2, counters.rx)
        assertEquals(1, counters.errors)
        session.disconnect()
        assertEquals("断开后归零", SessionCounters.Empty, session.counters.value)
    }

    @Test
    fun slaveCountersCountLocalRequestsAndResponses() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        advanceTimeBy(5_100)
        runCurrent()
        val counters = session.counters.value
        assertTrue("演示主站每秒读一次：${counters.localRequests}", counters.localRequests >= 5)
        assertEquals("每个本机请求都回了", counters.localRequests, counters.responses)
        assertEquals(0, counters.exceptions)
        assertEquals(0, counters.foreignFrames)
        session.disconnect()
    }

    @Test
    fun slaveSessionRunsSimulationRulesAndStopsThemOnDisconnect() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        val simulation = session.simulation.value ?: error("从站会话应该有模拟器")
        simulation.setRules(
            listOf(
                io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule(
                    id = "hr5",
                    target = io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget(1, RegisterArea.HOLDING_REGISTER, 5),
                    waveform = io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform.Ramp(start = 1.0, step = 1.0, min = 0.0, max = 100.0),
                    refreshMs = 1_000,
                ),
            ),
        )
        advanceTimeBy(3_100)
        runCurrent()
        val bank = session.slaveBank.value ?: error("从站寄存器库为空")
        val hr5 = bank.words(1, RegisterArea.HOLDING_REGISTER).first { it.address == 5 }.value
        assertTrue("模拟规则在写 HR5：$hr5", hr5 >= 3)
        assertEquals(
            io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource.SIMULATION,
            bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 5)?.source,
        )
        session.disconnect()
        assertEquals(null, session.simulation.value)
    }

    @Test
    fun masterSessionHasNoSimulation() = runTest {
        val session = session()
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        assertEquals(null, session.simulation.value)
        session.disconnect()
    }

    @Test
    fun previewFrameIsTheBytesTheMasterSends() = runTest {
        val session = session()
        val request = ModbusRequest.WriteSingleRegister(1, 5, 123)
        val preview = session.previewFrame(request)
        assertEquals("01 06 00 05 00 7B D9 E8", preview.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) })
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.MASTER)
        assertTrue(session.execute(request) is ModbusResult.Success)
        runCurrent()
        val sent = session.traffic.replayCache.last { it.direction == TrafficDirection.TX }
        assertTrue("预览和实际发出的字节一致", preview.contentEquals(sent.bytes))
        session.disconnect()
    }

    @Test
    fun slaveSessionLoadsSavedUnitsAndRegisters() = runTest {
        val store = io.github.xingyingyuzhui.modbusmobile.application.slave.MemorySlaveSetupStore(
            io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup(
                listOf(
                    io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveUnitSetup(
                        1,
                        listOf(io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveRegion(RegisterArea.HOLDING_REGISTER, 0, List(10) { 7 })),
                    ),
                    io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveUnitSetup(
                        3,
                        listOf(io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveRegion(RegisterArea.INPUT_REGISTER, 50, listOf(1, 2))),
                    ),
                ),
            ),
        )
        val session = session(store)
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        assertEquals(listOf(1, 3), session.slaveUnits.value)
        val bank = session.slaveBank.value ?: error("从站寄存器库为空")
        assertEquals(listOf(1, 2), bank.words(3, RegisterArea.INPUT_REGISTER).map { it.value })
        assertEquals(7, bank.words(1, RegisterArea.HOLDING_REGISTER).first().value)
        session.disconnect()
        assertEquals(emptyList<Int>(), session.slaveUnits.value)
    }

    @Test
    fun changingUnitsRebuildsTheEngineKeepsAnsweringAndSaves() = runTest {
        val store = io.github.xingyingyuzhui.modbusmobile.application.slave.MemorySlaveSetupStore()
        val session = session(store)
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        advanceTimeBy(2_100)
        runCurrent()
        val before = session.counters.value.responses
        assertTrue(before >= 2)
        assertTrue(session.setSlaveUnits(listOf(1, 2)))
        assertEquals(listOf(1, 2), session.slaveUnits.value)
        assertEquals("改 Unit 后保存了配置", listOf(1, 2), store.current.unitIds)
        // 换引擎之后演示主站对 Unit 1 的读取仍然有回应
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue("重建引擎后继续回应：$before → ${session.counters.value.responses}", session.counters.value.responses >= before + 2)
        assertTrue("不合法的 Unit 拒绝", !session.setSlaveUnits(listOf(0)))
        assertTrue("重复的 Unit 拒绝", !session.setSlaveUnits(listOf(1, 1)))
        session.disconnect()
        assertTrue("断开后不能改", !session.setSlaveUnits(listOf(1)))
    }

    @Test
    fun removingEveryUnitStopsAnsweringSavesEmptyAndCanAddBack() = runTest {
        val store = io.github.xingyingyuzhui.modbusmobile.application.slave.MemorySlaveSetupStore()
        val session = session(store)
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        advanceTimeBy(2_100)
        runCurrent()
        assertTrue(session.counters.value.responses >= 2)
        assertTrue("可以删光 Unit", session.setSlaveUnits(emptyList()))
        assertEquals(emptyList<Int>(), session.slaveUnits.value)
        assertEquals("保存的是 0 个 Unit", emptyList<Int>(), store.current.unitIds)
        assertTrue(session.saveSlaveSetup())
        val silent = session.counters.value.responses
        // 演示主站继续每秒读 Unit 1，空 Unit 的从站一律不应答
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("0 个 Unit 不应答", silent, session.counters.value.responses)
        assertTrue("仍能加回", session.setSlaveUnits(listOf(1)))
        advanceTimeBy(2_100)
        runCurrent()
        assertTrue("加回后恢复应答", session.counters.value.responses >= silent + 1)
        session.disconnect()
        // 下次连接按保存的空配置启动，也不应答
        store.save(io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup(emptyList()))
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        assertEquals(emptyList<Int>(), session.slaveUnits.value)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(0L, session.counters.value.responses)
        session.disconnect()
    }

    @Test
    fun saveSlaveSetupSnapshotsValuesAndRules() = runTest {
        val store = io.github.xingyingyuzhui.modbusmobile.application.slave.MemorySlaveSetupStore()
        val session = session(store)
        session.connect(ConnectionTarget.Demo, SerialSettings(responseTimeoutMs = 300, turnaroundMs = 10), Role.SLAVE)
        val bank = session.slaveBank.value ?: error("从站寄存器库为空")
        bank.putWord(1, RegisterArea.HOLDING_REGISTER, 3, 333)
        val rule = io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule(
            "c",
            io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget(1, RegisterArea.HOLDING_REGISTER, 9),
            io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform.Constant(9.0),
        )
        session.simulation.value!!.setRules(listOf(rule))
        assertTrue(session.saveSlaveSetup())
        val saved = store.current
        assertEquals(333, saved.units.single().regions.first { it.area == RegisterArea.HOLDING_REGISTER }.values[3])
        assertEquals(listOf(rule), saved.rules)
        session.disconnect()
        assertTrue("没有从站会话时不保存", !session.saveSlaveSetup())
    }

    @Test
    fun changingUnitsReportsPersistenceFailureAndCanRetrySaving() = runTest {
        var writable = false
        var saved: io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup? = null
        val store = object : io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetupStore {
            override fun load() = io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup.default()
            override fun save(setup: io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetup): Boolean {
                if (!writable) return false
                saved = setup
                return true
            }
        }
        val session = session(store)
        session.connect(ConnectionTarget.Demo, SerialSettings(), Role.SLAVE)
        assertTrue("保存失败必须传给界面", !session.setSlaveUnits(listOf(1, 2)))
        assertEquals("运行中的 Unit 已生效", listOf(1, 2), session.slaveUnits.value)
        assertEquals(null, saved)
        writable = true
        assertTrue(session.saveSlaveSetup())
        assertEquals(listOf(1, 2), saved!!.unitIds)
        session.disconnect()
    }

    private fun TestScope.session(
        store: io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveSetupStore =
            io.github.xingyingyuzhui.modbusmobile.application.slave.MemorySlaveSetupStore(),
    ): DefaultSessionManager = DefaultSessionManager(
        scope = backgroundScope,
        clock = object : MonotonicClock {
            override fun nowNs(): Long = testScheduler.currentTime * 1_000_000L
        },
        slaveSetupStore = store,
    )
}
