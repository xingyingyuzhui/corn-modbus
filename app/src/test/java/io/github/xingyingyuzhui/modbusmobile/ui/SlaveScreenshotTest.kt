package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.width
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.SlaveStatsTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionCounters
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 虚拟从站工作台（b1-5）的 Roborazzi 截图和交互测试。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class SlaveScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun slave_workbench_dark_portrait() = shot(ThemeMode.DARK, ConnectionTarget.Demo, externalWrite = true)

    @Test fun slave_workbench_light_portrait() = shot(ThemeMode.LIGHT, ConnectionTarget.Demo, externalWrite = true)

    @Test
    fun slave_workbench_coils_dark_portrait() {
        val bank = bank()
        show(ThemeMode.DARK, ConnectionTarget.Demo, bank)
        compose.onNodeWithText("线圈", substring = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun slave_workbench_dark_landscape() = shot(ThemeMode.DARK, ConnectionTarget.Demo, externalWrite = true)

    @Test
    fun statsTilesShowTheSessionCounters() {
        show(ThemeMode.DARK, ConnectionTarget.Demo, bank())
        val stats = compose.onNodeWithTag(SlaveStatsTag, useUnmergedTree = true)
        stats.assertIsDisplayed()
        compose.onNodeWithText("本机请求").assertIsDisplayed()
        compose.onNodeWithText("1,284").assertIsDisplayed()
        compose.onNodeWithText("1,280").assertIsDisplayed()
        compose.onNodeWithText("3,902").assertIsDisplayed()
        compose.onNodeWithText("方向不明 1").assertIsDisplayed()
        // 竖屏一行四格：整块宽度 = 内容区宽（360 − 左右各 14）
        assertEquals(328f, stats.getBoundsInRoot().width.value, 1.5f)
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeStatsUseTwoColumnsSoNumbersFit() {
        show(ThemeMode.DARK, ConnectionTarget.Demo, bank())
        compose.onNodeWithText("1,284").assertIsDisplayed()
        compose.onNodeWithText("3,902").assertIsDisplayed()
    }

    @Test
    fun usbSlaveHasNoDemoNote() {
        show(ThemeMode.DARK, ConnectionTarget.Usb(1, 0), bank())
        compose.onNodeWithText("虚拟从站").assertIsDisplayed()
        compose.onNodeWithText("演示设备：", substring = true).assertDoesNotExist()
    }

    @Test
    fun tappingABitRowFlipsIt() {
        val bank = bank()
        show(ThemeMode.DARK, ConnectionTarget.Demo, bank)
        compose.onNodeWithText("线圈", substring = true).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("ON")[0].performClick()
        compose.waitForIdle()
        // 线圈 0 原来是 ON，点“ON”那一行后翻成 OFF；其余不变
        assertEquals(listOf(false, false, true, false), bank.bits(1, RegisterArea.COIL).map { it.value })
    }

    /** 「－ 删除地址段」浮层：默认填当前区第一段连续地址（HR0 起 5 个）。 */
    @Test
    fun slave_remove_range_dark_portrait() {
        show(ThemeMode.DARK, ConnectionTarget.Demo, bank())
        compose.onNodeWithTag("slave-remove-range-open").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun removeRangeSheetDeletesTheDefaultRunAndLeavesOtherAreas() {
        val bank = bank()
        show(ThemeMode.DARK, ConnectionTarget.Demo, bank)
        compose.onNodeWithTag("slave-remove-range-open").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("slave-remove-range-confirm").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), bank.words(1, RegisterArea.HOLDING_REGISTER).map { it.address })
        assertEquals(2, bank.words(1, RegisterArea.INPUT_REGISTER).size)
        assertEquals(4, bank.bits(1, RegisterArea.COIL).size)
        // 这个区删空了：删除按钮不可点
        compose.onNodeWithTag("slave-remove-range-open").assertIsNotEnabled()
    }

    private fun bank(): InMemoryRegisterBank = InMemoryRegisterBank().also { bank ->
        listOf(236, 82, 500, 1, 0).forEachIndexed { i, v -> bank.putWord(1, RegisterArea.HOLDING_REGISTER, i, v) }
        listOf(10, 20).forEachIndexed { i, v -> bank.putWord(1, RegisterArea.INPUT_REGISTER, i, v) }
        listOf(true, false, true, false).forEachIndexed { i, v -> bank.putBit(1, RegisterArea.COIL, i, v) }
        listOf(false, true).forEachIndexed { i, v -> bank.putBit(1, RegisterArea.DISCRETE_INPUT, i, v) }
    }

    private fun shot(theme: ThemeMode, target: ConnectionTarget, externalWrite: Boolean) {
        val bank = bank()
        show(theme, target, bank)
        if (externalWrite) {
            // 外部主站写了 HR2：这一行带高亮。时钟停住，截图时高亮还在。
            compose.mainClock.autoAdvance = false
            bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 2, listOf(500))
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(50)
        }
        compose.onRoot().captureRoboImage()
    }

    private fun show(theme: ThemeMode, target: ConnectionTarget, bank: VirtualRegisterBank) {
        val session = SlaveShotSession(target, bank)
        val settings = UiSettings(theme = theme)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
            ) {
                ModbusTheme(settings) { ModbusApp(startRoute = Destination.Workbench.route) }
            }
        }
        compose.waitForIdle()
    }
}

private class SlaveShotSession(target: ConnectionTarget, bank: VirtualRegisterBank) : SessionManager {
    override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.SLAVE, target, 1))
    override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(bank)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(SerialSettings())
    // 稿面 b1-5 的统计数字
    override val counters = MutableStateFlow(
        SessionCounters(localRequests = 1_284, responses = 1_280, exceptions = 4, foreignFrames = 3_902, ambiguous = 1),
    )

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
}
