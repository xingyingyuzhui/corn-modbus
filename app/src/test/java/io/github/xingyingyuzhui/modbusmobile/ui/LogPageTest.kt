package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.RecorderState
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.TimeStyle
import io.github.xingyingyuzhui.modbusmobile.application.TrafficRecorder
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficDirection
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 日志页：默认不记录、点开始才记录、翻页、清除确认、保存前的隐私提示（真实的系统保存界面只能真机验证）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class LogPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Session : SessionManager {
        override val state = MutableStateFlow<SessionState>(
            SessionState.Running(Role.MASTER, ConnectionTarget.Demo, epoch = 1),
        )
        override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1_000)
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(SerialSettings())
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected

        fun frame(request: Boolean = true) {
            traffic.tryEmit(
                if (request) {
                    TrafficEvent(System.nanoTime(), TrafficDirection.TX, byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x02, 0xC4.toByte(), 0x0B), TrafficKind.TX_REQUEST)
                } else {
                    TrafficEvent(
                        System.nanoTime(), TrafficDirection.RX,
                        byteArrayOf(0x01, 0x03, 0x04, 0x00, 0x0A, 0x00, 0x14, 0xDA.toByte(), 0x3E), TrafficKind.RX_RESPONSE,
                    )
                },
            )
        }
    }

    private val session = Session()
    private val recorder = TrafficRecorder(session, CoroutineScope(Dispatchers.Unconfined))

    private fun show(theme: ThemeMode = ThemeMode.DARK, time: TimeStyle = TimeStyle.CLOCK) {
        val settings = UiSettings(theme = theme, timeStyle = time)
        val repository = MemorySettingsRepository(settings)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides repository,
                LocalTrafficRecorder provides recorder,
            ) {
                ModbusTheme(settings) { ModbusApp(startRoute = Destination.Log.route) }
            }
        }
        compose.waitForIdle()
    }

    private fun feed(n: Int) {
        repeat(n) { session.frame(it % 2 == 0) }
        compose.waitForIdle()
    }

    @Test
    fun defaultsToNotRecordingAndShowsHowToStart() {
        show()
        session.frame(); compose.waitForIdle()
        compose.onNodeWithTag("log-state", useUnmergedTree = true).assertTextContains("未开始")
        compose.onNodeWithTag("log-start").assertIsDisplayed()
        compose.onNodeWithTag("log-clear").assertIsNotEnabled()
        compose.onNodeWithTag("log-save").assertIsNotEnabled()
        compose.onNodeWithText("点「开始记录」", substring = true).assertIsDisplayed()
        assertEquals(RecorderState.IDLE, recorder.state.value)
        assertEquals("没点开始就不记录", 0, recorder.size)
    }

    @Test
    fun startRecordsFramesWithWallClockAndPauseStopsCapturing() {
        show()
        compose.onNodeWithTag("log-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("log-pause").assertIsDisplayed()
        feed(4)
        compose.onNodeWithTag("log-state", useUnmergedTree = true).assertTextContains("记录中 · 4 帧")
        compose.onAllNodesWithTagFirst("log-time").assertTextMatchesClock()
        compose.onNodeWithTag("log-clear").assertIsEnabled()
        compose.onNodeWithTag("log-save").assertIsEnabled()

        compose.onNodeWithTag("log-pause").performClick()
        compose.waitForIdle()
        feed(3)
        compose.onNodeWithTag("log-state", useUnmergedTree = true).assertTextContains("已暂停 · 4 帧")
        compose.onNodeWithTag("log-resume").assertIsDisplayed()
        assertEquals(4, recorder.frames)
    }

    /** 页头是文字「锁定滚动」，不是 ⏸：锁定只是列表不跟随，新帧照常记录，不能和「暂停记录」混。 */
    @Test
    fun headerLockIsWordedAsScrollLockAndDoesNotStopRecording() {
        show()
        compose.onNodeWithTag("log-start").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("log-lock").assertTextContains("锁定滚动")
        compose.onNodeWithTag("log-lock").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("log-lock").assertTextContains("恢复自动滚动")
        compose.onNodeWithTag("log-pause").assertIsDisplayed()
        compose.onNodeWithText("暂停记录").assertIsDisplayed()
    }

    @Test
    fun elapsedTimeStyleShowsOffsetsInsteadOfWallClock() {
        show(time = TimeStyle.ELAPSED)
        compose.onNodeWithTag("log-start").performClick()
        feed(2)
        val text = compose.onAllNodesWithTagFirst("log-time").fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        check(!Regex("""\d{2}:\d{2}:\d{2}\.\d{3}""").matches(text)) { "相对时间模式不应显示时钟：$text" }
    }

    @Test
    fun pageBarPagesThroughMoreThanOnePage() {
        show()
        compose.onNodeWithTag("log-start").performClick()
        feed(130) // 默认每页 100
        compose.onNodeWithTag("log-page").assertTextContains("第 2 / 2 页")
        compose.onNodeWithTag("log-prev").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("log-page").assertTextContains("第 1 / 2 页")
        compose.onNodeWithTag("log-next").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("log-page").assertTextContains("第 2 / 2 页")
    }

    @Test
    fun clearAsksFirstAndKeepsTheFramesWhenCancelled() {
        show()
        compose.onNodeWithTag("log-start").performClick()
        feed(3)
        compose.onNodeWithTag("log-clear").performClick()
        compose.onNodeWithText("清除全部记录？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertEquals(3, recorder.frames)
        compose.onNodeWithTag("log-clear").performClick()
        compose.onNodeWithText("已记录的 3 帧会被清空，不能恢复。").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasText("清除") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(0, recorder.frames)
    }

    @Test
    fun savingAlwaysShowsThePrivacyNoticeFirst() {
        show()
        compose.onNodeWithTag("log-start").performClick()
        feed(2)
        repeat(2) { // 每次保存前都要弹，不是只弹一次
            compose.onNodeWithTag("log-save").performClick()
            compose.onNodeWithText("保存前请注意").assertIsDisplayed()
            compose.onNodeWithText("可能涉及现场隐私", substring = true).assertIsDisplayed()
            compose.onNodeWithText("继续").performClick()
            compose.onNodeWithText("保存日志").assertIsDisplayed()
            compose.onNodeWithText("保存到文件…").assertIsDisplayed()
            compose.onNodeWithText("分享…").assertIsDisplayed()
            compose.onNodeWithText("取消").performClick()
            compose.waitForIdle()
        }
    }

    @Test
    fun fullStateStopsAndSaysSo() {
        val small = TrafficRecorder(session, CoroutineScope(Dispatchers.Unconfined), maxFrames = 3)
        val settings = UiSettings()
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides MemorySettingsRepository(settings),
                LocalTrafficRecorder provides small,
            ) { ModbusTheme(settings) { ModbusApp(startRoute = Destination.Log.route) } }
        }
        compose.onNodeWithTag("log-start").performClick()
        repeat(5) { session.frame() }
        compose.waitForIdle()
        compose.onNodeWithTag("log-state", useUnmergedTree = true).assertTextContains("已记满（3 帧），已自动停止")
        compose.onNodeWithTag("log-resume").assertIsNotEnabled()
        assertEquals(3, small.frames)
    }

    @Test
    fun screenshotRecordingDark() {
        show(ThemeMode.DARK)
        compose.onNodeWithTag("log-start").performClick()
        feed(6)
        compose.onRoot().captureRoboImage("log_recording_dark_portrait.png")
    }

    @Test
    fun screenshotRecordingLight() {
        show(ThemeMode.LIGHT)
        compose.onNodeWithTag("log-start").performClick()
        feed(6)
        compose.onRoot().captureRoboImage("log_recording_light_portrait.png")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun screenshotRecordingLandscape() {
        show(ThemeMode.DARK)
        compose.onNodeWithTag("log-start").performClick()
        feed(6)
        compose.onRoot().captureRoboImage("log_recording_dark_landscape.png")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTagFirst(tag: String) =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag))[0]

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertTextMatchesClock() {
    val text = fetchSemanticsNode().config
        .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    check(Regex("""\d{2}:\d{2}:\d{2}\.\d{3}""").matches(text)) { "时间应为 HH:mm:ss.SSS，实际 \"$text\"" }
}
