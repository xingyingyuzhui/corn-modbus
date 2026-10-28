package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.FontScale
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.components.SegContainerTag
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 连接页间距（老板反馈：「设备 / 刷新」标题行到设备行间距过大；页头下不要状态副标题；连接后“断开”条盖住“高级”卡）。
 * 稿面数值取自 ui-v2.html b1-1 在 Chrome 里量出的 getBoundingClientRect（单位 px = dp）：
 *   卡内边距 12 + 边框 1 → 标题行顶 = 卡顶 + 13；标题行高 17（12px 字）；标题行底 → 内容顶 = 8；
 *   设备行高 58；角色分段高 60；“高级”折叠卡总高 46；卡间距 10。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ConnectionSpacingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun titleRowToContentGapsMatchTheDesign() {
        show(ThemeMode.LIGHT, idleWithUsb())
        val labels = (0..2).map { b(compose.onAllNodesWithTag("section-label")[it].getBoundsInRoot()) }
        val deviceCard = tag("card-device")
        val roleCard = tag("card-role")
        val serialCard = tag("card-serial")
        val advanced = tag("card-advanced")

        // 标题行顶 = 卡顶 + 12（v2 内边距不另计边框）
        assertClose("设备卡 标题行顶", labels[0].top - deviceCard.top, 12f)
        assertClose("角色卡 标题行顶", labels[1].top - roleCard.top, 12f)
        assertClose("串口卡 标题行顶", labels[2].top - serialCard.top, 12f)
        // v2 label 使用16sp行高（系统字体实际布局18dp）；串口卡不再有 Mono 角标，三张卡标题行同高。
        labels.zip(listOf(18f, 18f, 18f)).forEach { (label, height) ->
            assertClose("标题行高", label.height.value, height, 0.6f)
        }

        // 标题行底 → 首行内容顶 = 8
        val firstDevice = b(compose.onAllNodesWithTag("device-row")[0].getBoundsInRoot())
        println("MEASURE 设备卡 标题行底→设备行顶 = ${(firstDevice.top - labels[0].bottom).value}dp")
        assertClose("设备卡：标题行底→设备行顶", firstDevice.top - labels[0].bottom, 8f)
        val seg = b(compose.onAllNodesWithTag(SegContainerTag)[0].getBoundsInRoot())
        println("MEASURE 角色卡 标题行底→分段顶 = ${(seg.top - labels[1].bottom).value}dp")
        assertClose("角色卡：标题行底→分段顶", seg.top - labels[1].bottom, 8f)
        val tiles = tag("serial-baud")
        println("MEASURE 串口卡 标题行底→串口格顶 = ${(tiles.top - labels[2].bottom).value}dp")
        assertClose("串口卡：标题行底→串口格顶", tiles.top - labels[2].bottom, 8f)
        assertClose("串口卡：串口格底→卡底", serialCard.bottom - tiles.bottom, 12f)

        // 设备行58（字体取整59）、角色分段62（padding4）、高级卡44（12+20+12）
        compose.onAllNodesWithTag("device-row").fetchSemanticsNodes().indices.forEach {
            val row = b(compose.onAllNodesWithTag("device-row")[it].getBoundsInRoot())
            assertClose("设备行高", row.height.value, 58f, 1f)
        }
        assertClose("角色分段高", seg.height.value, 62f, 1f)
        println("MEASURE 高级折叠卡高 = ${advanced.height.value}dp；设备行高 = ${b(compose.onAllNodesWithTag("device-row")[0].getBoundsInRoot()).height.value}dp；角色分段高 = ${seg.height.value}dp")
        assertClose("“高级”折叠卡总高", advanced.height.value, 44f, 1f)

        // v2 卡间距16；设备卡底内边距12
        assertClose("设备卡→角色卡", roleCard.top - deviceCard.bottom, 16f) // space.group 16（CL-16，v1 是 10）
        assertClose("角色卡→串口卡", serialCard.top - roleCard.bottom, 16f) // space.group 16（CL-16，v1 是 10）
        assertClose("串口卡→高级卡", advanced.top - serialCard.bottom, 16f) // space.group 16（CL-16，v1 是 10）
        val lastDevice = b(compose.onAllNodesWithTag("device-row")[1].getBoundsInRoot())
        assertClose("设备卡底内边距", deviceCard.bottom - lastDevice.bottom, 12f) // CL-16：卡片内边距只有 12（去掉 border-box 的 1dp）
        compose.onRoot().captureRoboImage("src/test/screenshots/connection-spacing/idle_light.png")
    }

    @Test
    fun noStatusSubtitleUnderTheHeaderWhileRunning() {
        show(ThemeMode.LIGHT, ConnSpacingSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)))
        compose.onNodeWithText("主站 · 运行中").assertDoesNotExistCompat()
                // 标题下方紧接第一张卡：页头底 → 设备卡顶 = 页头与卡之间只有稿面间距（页头 52 + 0）
        val header = b(compose.onAllNodesWithText("连接")[0].getBoundsInRoot())
        val deviceCard = tag("card-device")
        println("MEASURE 页头标题底→设备卡顶 = ${(deviceCard.top - header.bottom).value}dp")
        assertTrue("标题下方没有多余一行", deviceCard.top - header.bottom < 30.dp)
    }

    @Test
    fun errorReasonStillShowsInRedWhenConnectingFails() {
        show(ThemeMode.LIGHT, ConnSpacingSession(SessionState.Error("端口打不开")))
        compose.onNodeWithTag("error-caption").assertExists()
    }

    /** 360×780 一屏放得下：连接态下四张卡全部在“断开”条上方，不需要滚动。 */
    @Test
    fun runningFitsOnOneScreenAtDesignSize() {
        show(ThemeMode.LIGHT, ConnSpacingSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)))
        val button = b(compose.onNodeWithText("断开").getBoundsInRoot())
        val advanced = tag("card-advanced")
        println("MEASURE 780 高：高级卡底 ${advanced.bottom.value}dp，断开条顶 ${button.top.value}dp，差 ${(button.top - advanced.bottom).value}dp")
        assertTrue("高级卡底(${advanced.bottom}) 应在断开条顶(${button.top})上方", advanced.bottom <= button.top)
        compose.onRoot().captureRoboImage("src/test/screenshots/connection-spacing/running_780_light.png")
    }

    /** 小屏（360×560）：内容超出时滚到底，最后一张卡完整可见，不被“断开”条遮挡。 */
    @Test @Config(qualifiers = "w360dp-h560dp")
    fun scrolledToTheEndTheLastCardIsFullyAboveTheDisconnectBar() {
        show(ThemeMode.LIGHT, ConnSpacingSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)))
        compose.onNodeWithTag("card-advanced").performScrollTo()
        compose.waitForIdle()
        val button = b(compose.onNodeWithText("断开").getBoundsInRoot())
        val advanced = tag("card-advanced")
        println("MEASURE 560 高滚到底：高级卡底 ${advanced.bottom.value}dp，断开条顶 ${button.top.value}dp")
        assertTrue("高级卡底(${advanced.bottom}) 应在断开条顶(${button.top})上方", advanced.bottom <= button.top)
        assertTrue("高级卡顶不应被推出屏幕", advanced.top >= 0.dp)
        compose.onRoot().captureRoboImage("src/test/screenshots/connection-spacing/running_560_scrolled_light.png")
    }

    /** 字体 1.3（内容一定超出一屏）：滚到底，最后一张卡完整在“断开”条上方；没滚到底时底部渐隐只是提示，不是遮挡。 */
    @Test
    fun withFont13ScrolledToTheEndTheLastCardIsFullyAboveTheDisconnectBar() {
        show(ThemeMode.LIGHT, ConnSpacingSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)), FontScale.EXTRA)
        compose.onNodeWithTag("card-advanced").performScrollTo()
        compose.waitForIdle()
        val button = b(compose.onNodeWithText("断开").getBoundsInRoot())
        val advanced = tag("card-advanced")
        println("MEASURE 字体 1.3 滚到底：高级卡底 ${advanced.bottom.value}dp，断开条顶 ${button.top.value}dp")
        assertTrue("高级卡底(${advanced.bottom}) 应在断开条顶(${button.top})上方", advanced.bottom <= button.top)
        compose.onRoot().captureRoboImage("src/test/screenshots/connection-spacing/running_font13_scrolled_light.png")
    }

    /** 横屏同理：右列滚到底，“高级”卡完整在“断开”条上方。 */
    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeScrolledToTheEndTheLastCardIsFullyAboveTheDisconnectBar() {
        show(ThemeMode.LIGHT, ConnSpacingSession(SessionState.Running(Role.MASTER, ConnectionTarget.Demo, 1)))
        compose.onNodeWithTag("card-advanced").performScrollTo()
        compose.waitForIdle()
        val button = b(compose.onNodeWithText("断开").getBoundsInRoot())
        val advanced = tag("card-advanced")
        println("MEASURE 横屏滚到底：高级卡底 ${advanced.bottom.value}dp，断开条顶 ${button.top.value}dp")
        assertTrue("高级卡底(${advanced.bottom}) 应在断开条顶(${button.top})上方", advanced.bottom <= button.top)
        compose.onRoot().captureRoboImage("src/test/screenshots/connection-spacing/running_land_scrolled_light.png")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() {
        this.assertDoesNotExist()
    }

    private fun b(r: androidx.compose.ui.unit.DpRect) = r

    private fun tag(t: String): DpRect = compose.onNodeWithTag(t).getBoundsInRoot().let { DpRect(it.left, it.top, it.right, it.bottom) }

    private fun assertClose(message: String, actual: Dp, expected: Float, tolerance: Float = 0.6f) =
        assertClose(message, actual.value, expected, tolerance)

    private fun assertClose(message: String, actual: Float, expected: Float, tolerance: Float = 0.6f) {
        assertEquals(message, expected, actual, tolerance)
    }

    private fun idleWithUsb() = ConnSpacingSession(SessionState.Idle).also {
        it.usbDevices.value = listOf(UsbDeviceSummary(1, 0, "FT232R USB UART", 0x0403, 0x6001))
    }

    private fun show(theme: ThemeMode, session: SessionManager, scale: FontScale = FontScale.STANDARD) {
        val settings = UiSettings(theme = theme, fontScale = scale)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
            ) {
                ModbusTheme(settings) { ModbusApp(startRoute = Destination.Connection.route) }
            }
        }
        compose.waitForIdle()
    }
}

private class ConnSpacingSession(initial: SessionState) : SessionManager {
    override val state = MutableStateFlow(initial)
    override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
    override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
    override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
    override val echoDetected = MutableStateFlow<Boolean?>(null)
    override val serial = MutableStateFlow<SerialSettings?>(null)

    override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
}
