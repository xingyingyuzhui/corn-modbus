@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.xingyingyuzhui.modbusmobile.ui.panel

import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Quality
import io.github.xingyingyuzhui.modbusmobile.application.panel.RawValue
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 面板写入控制器（P8b1）：确认前不发、取消不发、广播红色、位写入先读后写（确认时再核对）、
 * 编辑模式 / 从站 / 没连接一律拒绝。
 */
class PanelWriteControllerTest {
    private class Session(role: Role? = Role.MASTER) : SessionManager {
        val requests = mutableListOf<ModbusRequest>()
        val reads get() = requests.filterIsInstance<ModbusRequest.ReadHoldingRegisters>()
        val writes get() = requests.filter { it !is ModbusRequest.ReadHoldingRegisters }

        /** 下一次 FC03 读到的字；null = 读失败（超时）。每次读取按顺序消费，用完保持最后一个。 */
        var words = ArrayDeque<Int?>(listOf(0x0029))
        var writeResult: ModbusResult? = null
        override val state = MutableStateFlow<SessionState>(
            if (role == null) SessionState.Idle else SessionState.Running(role, ConnectionTarget.Demo, epoch = 1),
        )
        override val traffic = MutableSharedFlow<TrafficEvent>()
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            return when (request) {
                is ModbusRequest.ReadHoldingRegisters -> {
                    val w = if (words.size > 1) words.removeFirst() else words.first()
                    if (w == null) ModbusResult.Timeout
                    else ModbusResult.Success(ModbusResponse.ReadRegisters(request.unitId, request.function, listOf(w)))
                }
                is ModbusRequest.WriteSingleRegister -> writeResult
                    ?: ModbusResult.Success(ModbusResponse.WriteSingleRegister(request.unitId, request.function, request.address, request.value))
                is ModbusRequest.WriteSingleCoil -> writeResult
                    ?: ModbusResult.Success(ModbusResponse.WriteSingleCoil(request.unitId, request.function, request.address, request.value))
                is ModbusRequest.WriteMultipleRegisters -> writeResult
                    ?: ModbusResult.Success(ModbusResponse.WriteMultipleRegisters(request.unitId, request.function, request.address, request.quantity))
                else -> ModbusResult.Disconnected
            }
        }
    }

    private fun coilSwitch() = Widget("s1", WidgetKinds.SWITCH, point = PointDefinition("c1", "压缩机", 1, RegisterArea.COIL, 10, DataType.BOOL))
    private fun bitSwitch(bit: Int = 3) = Widget(
        "s2", WidgetKinds.SWITCH, point = PointDefinition("b1", "运行位", 1, RegisterArea.HOLDING_REGISTER, 140, DataType.BIT, bit = bit),
    )
    private fun button(unit: Int = 1, area: RegisterArea = RegisterArea.COIL, type: DataType = DataType.BOOL, value: Double = 1.0) = Widget(
        "b1", WidgetKinds.BUTTON, point = PointDefinition("bp", "启动", unit, area, 5, type), buttonLabel = "启动", writeValue = value,
    )

    private fun TestScope.controller(
        session: Session,
        editing: () -> Boolean = { false },
        snapshots: Map<String, PointSnapshot> = emptyMap(),
    ) = PanelWriteController(session, this, editing, { snapshots[it] }, wallClock = { 100_000L })

    private fun TestScope.after(block: () -> Unit) {
        block()
        advanceUntilIdle()
    }

    // ---- 线圈开关 ----

    @Test fun coilSwitchNeverWritesBeforeConfirmAndCancelSendsNothing() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestSwitch(coilSwitch(), true) }
        val pending = c.pending
        assertNotNull(pending)
        assertTrue("点开关只是请求，不能直接发", session.requests.isEmpty())
        assertEquals(ModbusRequest.WriteSingleCoil(1, 10, true), pending!!.write.plan.request)
        after { c.dismiss() }
        assertNull(c.pending)
        assertTrue("取消后什么都不发", session.requests.isEmpty())
        assertNull(c.notice)

        after { c.requestSwitch(coilSwitch(), true) }
        after { c.confirm() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(1, 10, true)), session.requests)
        assertEquals(WriteNoticeKind.OK, c.notice!!.kind)
        assertNull(c.pending)
        assertNull(c.busyWidgetId)
    }

    @Test fun confirmSheetShowsKnownCurrentStateAndAge() = runTest {
        val snap = PointSnapshot("c1", Quality.OK, RawValue.Flag(false), 0.0, null, updatedAtMs = 1, updatedWallMs = 97_000L)
        val c = controller(Session(), snapshots = mapOf("c1" to snap))
        after { c.requestSwitch(coilSwitch(), true) }
        assertEquals(listOf<Int?>(0), c.pending!!.write.current)
        assertEquals(3_000L, c.pending!!.write.currentAgeMs)
        // 状态没读到过 / 过期：“当前”显示未读取，而不是猜一个
        val stale = snap.copy(quality = Quality.STALE)
        val c2 = controller(Session(), snapshots = mapOf("c1" to stale))
        after { c2.requestSwitch(coilSwitch(), true) }
        assertEquals(listOf<Int?>(null), c2.pending!!.write.current)
    }

    @Test fun coilSegmentInTheSheetCanFlipTheTargetBeforeSending() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestSwitch(coilSwitch(), true) }
        after { c.setPendingCoil(false) }
        after { c.confirm() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(1, 10, false)), session.requests)
    }

    // ---- 寄存器位开关：先读后写 ----

    @Test fun bitSwitchReadsWordFirstThenAsksThenWritesTheWholeWordBack() = runTest {
        val session = Session() // 0x0029 = 0b101001：bit3 = 1
        val c = controller(session)
        after { c.requestSwitch(bitSwitch(3), false) }
        assertEquals("先读整字，还没写", 1, session.reads.size)
        assertTrue(session.writes.isEmpty())
        val pending = c.pending!!
        assertEquals(ModbusRequest.ReadHoldingRegisters(1, 140, 1), session.reads.single())
        assertEquals(listOf(0x0029), pending.write.current.map { it })
        assertEquals(ModbusRequest.WriteSingleRegister(1, 140, 0x0021), pending.write.plan.request)
        assertEquals(3, pending.bit!!.bit)
        assertEquals(0x0029, pending.bit!!.baseWord)

        after { c.confirm() }
        assertEquals("确认时再读一次核对", 2, session.reads.size)
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 140, 0x0021)), session.writes)
        assertEquals(WriteNoticeKind.OK, c.notice!!.kind)
    }

    @Test fun bitSwitchSetsOnlyThatBit() = runTest {
        val session = Session().apply { words = ArrayDeque<Int?>(listOf<Int?>(0x0000)) }
        val c = controller(session)
        after { c.requestSwitch(bitSwitch(15), true) }
        assertEquals(0x8000, (c.pending!!.write.plan.request as ModbusRequest.WriteSingleRegister).value)
    }

    @Test fun bitSwitchWordChangedWhileSheetOpenIsNotWrittenOverAndReconfirmsWithNewWord() = runTest {
        val session = Session().apply { words = ArrayDeque<Int?>(listOf<Int?>(0x0029, 0x0129)) } // 弹窗开着时别人改了 bit8
        val c = controller(session)
        after { c.requestSwitch(bitSwitch(3), false) }
        after { c.confirm() }
        assertTrue("整字变了：绝不能拿旧字写回", session.writes.isEmpty())
        assertEquals(WriteNoticeKind.CHANGED, c.notice!!.kind)
        val again = c.pending!!
        assertEquals(ModbusRequest.WriteSingleRegister(1, 140, 0x0121), again.write.plan.request)
        assertEquals(0x0129, again.bit!!.baseWord)
        // 用户对新的值再点一次发送才写
        after { c.confirm() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleRegister(1, 140, 0x0121)), session.writes)
    }

    @Test fun bitSwitchReadFailureNeverWrites() = runTest {
        val session = Session().apply { words = ArrayDeque<Int?>(listOf<Int?>(null)) }
        val c = controller(session)
        after { c.requestSwitch(bitSwitch(), true) }
        assertNull(c.pending)
        assertEquals(WriteNoticeKind.READ_FAILED, c.notice!!.kind)
        assertTrue(session.writes.isEmpty())
        assertNull(c.busyWidgetId)
        // 确认时的那次核对读失败：同样不写
        val s2 = Session().apply { words = ArrayDeque<Int?>(listOf<Int?>(0x0029, null)) }
        val c2 = controller(s2)
        after { c2.requestSwitch(bitSwitch(), false) }
        after { c2.confirm() }
        assertTrue(s2.writes.isEmpty())
        assertEquals(WriteNoticeKind.READ_FAILED, c2.notice!!.kind)
    }

    // ---- 按钮 ----

    @Test fun buttonWritesPresetOnlyAfterConfirm() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestButton(button()) }
        assertTrue(session.requests.isEmpty())
        assertFalse(c.pending!!.write.broadcast)
        assertEquals(listOf<Int?>(null), c.pending!!.write.current)
        after { c.dismiss() }
        assertTrue(session.requests.isEmpty())
        after { c.requestButton(button()) }
        after { c.confirm() }
        assertEquals(listOf<ModbusRequest>(ModbusRequest.WriteSingleCoil(1, 5, true)), session.requests)
    }

    @Test fun registerButtonEncodesByTypeAndUsesFc16ForTwoWords() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestButton(button(area = RegisterArea.HOLDING_REGISTER, type = DataType.U32, value = 70000.0)) }
        assertEquals(ModbusRequest.WriteMultipleRegisters(1, 5, listOf(0x0001, 0x1170)), c.pending!!.write.plan.request)
        after { c.dismiss() }
        after { c.requestButton(button(area = RegisterArea.HOLDING_REGISTER, type = DataType.U16, value = 500.0)) }
        assertEquals(ModbusRequest.WriteSingleRegister(1, 5, 500), c.pending!!.write.plan.request)
    }

    @Test fun broadcastButtonIsFlaggedForRedConfirmAndReportsSentNotOk() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestButton(button(unit = 0)) }
        assertTrue("Unit 0 = 广播：确认弹窗必须是红色那一档", c.pending!!.write.broadcast)
        assertTrue(session.requests.isEmpty())
        after { c.confirm() }
        assertEquals(ModbusRequest.WriteSingleCoil(0, 5, true), session.requests.single())
        assertEquals("广播没有响应：只能说已发送", WriteNoticeKind.SENT, c.notice!!.kind)
    }

    @Test fun broadcastNeverDoesAReadFirst() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestButton(button(unit = 0, area = RegisterArea.HOLDING_REGISTER, type = DataType.U16, value = 1.0)) }
        after { c.confirm() }
        assertTrue(session.reads.isEmpty())
    }

    // ---- 守卫 ----

    @Test fun editModeRejectsEverythingAndSendsNothing() = runTest {
        val session = Session()
        var editing = true
        val c = controller(session, editing = { editing })
        after { c.requestSwitch(coilSwitch(), true) }
        after { c.requestSwitch(bitSwitch(), true) }
        after { c.requestButton(button()) }
        assertNull(c.pending)
        assertTrue("编辑模式 / 向导里不可能发出任何请求（连读也没有）", session.requests.isEmpty())
        // 弹窗开着时进入编辑模式：确认也不发
        editing = false
        after { c.requestButton(button()) }
        assertNotNull(c.pending)
        editing = true
        after { c.confirm() }
        assertTrue(session.requests.isEmpty())
        assertNull(c.pending)
    }

    @Test fun slaveAndIdleSessionsCannotWrite() = runTest {
        for (role in listOf(Role.SLAVE, null)) {
            val session = Session(role)
            val c = controller(session)
            after { c.requestSwitch(coilSwitch(), true) }
            after { c.requestButton(button(unit = 0)) }
            assertNull(c.pending)
            assertEquals(WriteNoticeKind.NOT_MASTER, c.notice!!.kind)
            assertTrue(session.requests.isEmpty())
        }
    }

    @Test fun sessionTurningIntoSlaveBeforeConfirmDoesNotSend() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestButton(button()) }
        session.state.value = SessionState.Running(Role.SLAVE, ConnectionTarget.Demo, epoch = 2)
        after { c.confirm() }
        assertTrue(session.requests.isEmpty())
    }

    @Test fun onlyOneWriteFlowAtATime() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestSwitch(coilSwitch(), true) }
        val first = c.pending
        after { c.requestButton(button()) }
        assertEquals("弹窗开着时新的请求被忽略", first, c.pending)
    }

    @Test fun malformedWidgetsProduceNoPlan() = runTest {
        val session = Session()
        val c = controller(session)
        // 开关配成了整个寄存器、按钮没有预设值：控制器不替它们编一个写入
        val badSwitch = Widget("x", WidgetKinds.SWITCH, point = PointDefinition("p", "x", 1, RegisterArea.HOLDING_REGISTER, 1, DataType.U16))
        val noValue = Widget("y", WidgetKinds.BUTTON, point = PointDefinition("p", "y", 1, RegisterArea.COIL, 1, DataType.BOOL))
        after { c.requestSwitch(badSwitch, true) }
        after { c.requestButton(noValue) }
        after { c.requestButton(coilSwitch()) } // 类型不对
        assertNull(c.pending)
        assertTrue(session.requests.isEmpty())
    }

    // ---- 结果 ----

    @Test fun outcomesMapToNotices() = runTest {
        suspend fun run(result: ModbusResult): PanelWriteNotice {
            val session = Session().apply { writeResult = result }
            val c = controller(session)
            c.requestButton(button())
            c.confirm()
            testScheduler.advanceUntilIdle()
            return c.notice!!
        }
        assertEquals(WriteNoticeKind.EXCEPTION, run(ModbusResult.Exception(ExceptionCode(2))).kind)
        assertEquals(2, run(ModbusResult.Exception(ExceptionCode(2))).code)
        assertEquals(WriteNoticeKind.TIMEOUT, run(ModbusResult.Timeout).kind)
        assertEquals(WriteNoticeKind.UNKNOWN, run(ModbusResult.UnknownOutcome).kind)
        assertEquals(WriteNoticeKind.DISCONNECTED, run(ModbusResult.Disconnected).kind)
        assertEquals("U1 C5 = ON", run(ModbusResult.Success(ModbusResponse.WriteSingleCoil(1, FunctionCode.WRITE_SINGLE_COIL, 5, true))).summary)
    }

    // ---- 写入功能码：组件勾了“强制 FC15 / FC16”就用写多个 ----

    @Test fun forcedWidgetsUseTheMultipleWriteFunctionCodes() = runTest {
        val session = Session()
        val c = controller(session)

        after { c.requestSwitch(coilSwitch().copy(forceMultiple = true), true) }
        assertEquals(ModbusRequest.WriteMultipleCoils(1, 10, listOf(true)), c.pending!!.write.plan.request)
        after { c.dismiss() }

        after { c.requestButton(button(area = RegisterArea.HOLDING_REGISTER, type = DataType.U16, value = 7.0).copy(forceMultiple = true)) }
        assertEquals(ModbusRequest.WriteMultipleRegisters(1, 5, listOf(7)), c.pending!!.write.plan.request)
        after { c.dismiss() }

        // 寄存器位：仍然先 FC03 读整字，写回用 FC16
        after { c.requestSwitch(bitSwitch().copy(forceMultiple = true), true) }
        assertEquals(ModbusRequest.WriteMultipleRegisters(1, 140, listOf(0x0029 or (1 shl 3))), c.pending!!.write.plan.request)
        after { c.dismiss() }
        assertTrue("确认前不发写入", session.writes.isEmpty())
    }

    @Test fun unforcedWidgetsKeepTheSingleWriteFunctionCodes() = runTest {
        val session = Session()
        val c = controller(session)
        after { c.requestButton(button(area = RegisterArea.HOLDING_REGISTER, type = DataType.U16, value = 7.0)) }
        assertEquals(ModbusRequest.WriteSingleRegister(1, 5, 7), c.pending!!.write.plan.request)
        after { c.dismiss() }
        after { c.requestButton(button()) }
        assertEquals(ModbusRequest.WriteSingleCoil(1, 5, true), c.pending!!.write.plan.request)
    }
}
