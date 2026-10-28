package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.export.ArchivePart
import io.github.xingyingyuzhui.modbusmobile.application.history.HistoryCatalog
import io.github.xingyingyuzhui.modbusmobile.application.history.HistoryOverview
import io.github.xingyingyuzhui.modbusmobile.application.history.HistorySample
import io.github.xingyingyuzhui.modbusmobile.application.history.HistorySession
import io.github.xingyingyuzhui.modbusmobile.application.history.RecordingState
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
import io.github.xingyingyuzhui.modbusmobile.ui.components.SwitchVisualTag
import io.github.xingyingyuzhui.modbusmobile.ui.mine.LocalMineExport
import io.github.xingyingyuzhui.modbusmobile.ui.mine.MineExport
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 「我的」：入口列表、二级页（← 返回）、设置项真的写进存储、通信默认值校验、横屏左分组右详情。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class MinePageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Idle : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Idle)
        override val traffic = MutableSharedFlow<TrafficEvent>(replay = 8)
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(null)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(null)
        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
    }

    private val repository = MemorySettingsRepository(UiSettings())

    private fun show(
        theme: ThemeMode = ThemeMode.DARK,
        history: HistoryCatalog = EmptyMineHistory(),
        export: MineExport? = null,
    ) {
        val settings = UiSettings(theme = theme)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides Idle(),
                LocalSettingsRepository provides repository,
                LocalHistoryCatalog provides history,
                LocalMineExport provides export,
            ) { ModbusTheme(settings) { ModbusApp(startRoute = Destination.Mine.route) } }
        }
        compose.waitForIdle()
    }

    private fun open(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test
    fun homeListsTheGroupsWithValuesAndHasNoPlaceholderCards() {
        show()
        listOf("appearance", "display", "security", "comm", "about").forEach {
            compose.onNodeWithTag("mine-row-$it").assertIsDisplayed()
        }
        compose.onNodeWithText("外观").assertIsDisplayed()
        compose.onNodeWithText("显示与格式").assertIsDisplayed()
        compose.onNodeWithText("通信默认值").assertIsDisplayed()
        // 页头不再有 ⚙ 设置入口：分组列表就是唯一入口
        compose.onNodeWithTag("mine-settings").assertDoesNotExist()
        compose.onNodeWithText("导出与分享").assertIsDisplayed()
        compose.onNodeWithText("历史会话").assertIsDisplayed()
        compose.onNodeWithText("本地存储").assertIsDisplayed()
        compose.onNodeWithText("0 个").assertIsDisplayed()
        compose.onNodeWithTag("mine-recording").assertTextContains("未在记录", substring = true)
        compose.onNodeWithTag("mine-import").assertIsDisplayed()
        compose.onNodeWithText("导入配置").assertIsDisplayed()
    }

    @Test
    fun rowsAndSwitchesKeepDesignSizesWithAtLeast48dpTouch() {
        show()
        open("mine-row-appearance")
        val row = compose.onNodeWithTag("sw-keep").getBoundsInRoot()
        assertTrue("整行触控高度 ≥ 48dp：${row.height}", row.height.value >= 47.9f)
        val visual = compose.onAllNodesWithTag(SwitchVisualTag, useUnmergedTree = true)[0].getBoundsInRoot()
        assertEquals("开关轨道 44dp", 44f, visual.width.value, 0.6f)
        assertEquals("开关轨道 24dp", 24f, visual.height.value, 0.6f)
    }

    @Test
    fun openingAGroupShowsASubPageAndBackReturnsHome() {
        show()
        open("mine-row-display")
        compose.onNodeWithTag("sub-title").assertTextContains("显示与格式")
        compose.onNodeWithTag("sub-back").assertHasClickAction()
        open("sub-back")
        compose.onNodeWithTag("mine-row-display").assertIsDisplayed()
    }

    @Test
    fun switchesWriteThroughToTheStore() {
        show()
        open("mine-row-appearance")
        assertEquals(false, repository.settings.value.keepScreenOn)
        open("sw-keep")
        assertEquals(true, repository.settings.value.keepScreenOn)
        open("sw-vibration")
        assertEquals(true, repository.settings.value.vibration)
        open("sub-back")
        open("mine-row-display")
        assertEquals(true, repository.settings.value.logCrcDim)
        compose.onNodeWithTag("sw-crc-dim").performScrollTo()
        open("sw-crc-dim")
        assertEquals(false, repository.settings.value.logCrcDim)
    }

    @Test
    fun securityConfirmIsLockedAndCoilWarningIsAnOptionalSwitch() {
        show()
        open("mine-row-security")
        compose.onNodeWithTag("row-secure-confirm").assertIsDisplayed()
        compose.onNodeWithText("始终").assertIsDisplayed()
        assertEquals(true, repository.settings.value.coilExtraWarning)
        open("sw-coil-warning")
        assertEquals(false, repository.settings.value.coilExtraWarning)
        // 写入确认本身没有开关：这一页不能出现“写入前确认”的可切换项
        compose.onNodeWithTag("row-secure-confirm").assertIsNotEnabledOrNoToggle()
    }

    @Test
    fun commDefaultsAreValidatedAndStored() {
        show()
        open("mine-row-comm")
        compose.onNodeWithTag("comm-timeout").assertTextContains("1000 ms")
        compose.onNodeWithTag("comm-gap").assertTextContains("20 ms")
        compose.onNodeWithTag("comm-broadcast").assertTextContains("150 ms")
        compose.onNodeWithTag("comm-echo").assertTextContains("关")

        open("comm-timeout")
        compose.onNodeWithTag("comm-editor").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("请输入 1 到 120000 的整数").assertIsDisplayed()
        compose.onNodeWithTag("comm-save").assertIsNotEnabled()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("2500")
        compose.onNodeWithTag("comm-save").assertIsEnabled()
        open("comm-save")
        assertEquals(2_500L, repository.settings.value.defaultResponseTimeoutMs)
        compose.onNodeWithTag("comm-timeout").assertTextContains("2500 ms")

        // 取消不改
        open("comm-gap")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("77")
        open("comm-cancel")
        assertEquals(20L, repository.settings.value.defaultInterRequestMs)

        // 广播等待允许 0，请求间隔不允许
        open("comm-broadcast")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithTag("comm-save").assertIsEnabled()
        open("comm-save")
        assertEquals(0L, repository.settings.value.defaultBroadcastWaitMs)
        open("comm-gap")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithTag("comm-save").assertIsNotEnabled()
        open("comm-cancel")

        // 回显三选一：点行直接弹菜单，选中即存，不进编辑器
        open("comm-echo")
        compose.onNodeWithTag("comm-editor").assertDoesNotExist()
        compose.onNodeWithTag("comm-echo-menu").assertExists()
        compose.onNodeWithText("自动").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("comm-echo-menu").assertDoesNotExist()
        assertEquals(io.github.xingyingyuzhui.modbusmobile.application.EchoMode.AUTO, repository.settings.value.defaultEcho)
        compose.onNodeWithTag("comm-echo").assertTextContains("自动")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeShowsGroupsOnTheLeftAndTheSelectedOneOnTheRight() {
        show()
        compose.onNodeWithTag("mine-detail-title").assertTextContains("外观")
        compose.onNodeWithTag("sw-keep").assertExists()
        compose.onNodeWithTag("mine-row-display").performScrollTo()
        open("mine-row-display")
        compose.onNodeWithTag("mine-detail-title").assertTextContains("显示与格式")
        compose.onNodeWithTag("sw-hex").assertExists()
        compose.onNodeWithTag("sw-keep").assertDoesNotExist()
    }

    @Test
    fun emptyHistorySaysSoAndFullStorageSaysRecordingStopped() {
        show(history = EmptyMineHistory(RecordingState.STOPPED_STORAGE, 38L * 1024 * 1024))
        compose.onNodeWithTag("mine-recording").assertTextContains("已停止记录", substring = true)
        compose.onNodeWithText("空间不足，已停止写入。通信仍在进行。").assertIsDisplayed()
        compose.onNodeWithTag("mine-storage-usage").assertTextContains("38 MB / 250 MB")
        open("mine-history")
        compose.onNodeWithTag("history-empty").assertIsDisplayed()
        compose.onNodeWithText("还没有历史会话").assertIsDisplayed()
    }

    @Test
    fun closedUnretainedSessionCanBeDeletedAndAKeptOneCannot() {
        val history = EmptyMineHistory(
            sessions = listOf(
                HistorySession(1, "MASTER", "DEMO", 1, 1_700_000_000_000, null, false),
                HistorySession(2, "SLAVE", "USB", 2, 1_700_000_100_000, 1_700_000_200_000, false),
                HistorySession(3, "MASTER", "LAB", 3, 1_700_000_300_000, 1_700_000_400_000, true),
            ),
        )
        show(history = history)
        open("mine-history")
        compose.onNodeWithText("主站 · 演示").assertIsDisplayed()
        compose.onNodeWithText("进行中", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("history-delete-1").assertDoesNotExist()
        compose.onNodeWithTag("history-delete-3").assertDoesNotExist()
        compose.onNodeWithTag("history-delete-2").performScrollTo().performClick()
        compose.onNodeWithTag("history-delete-confirm").performClick()
        compose.waitForIdle()
        compose.waitUntil(2_000) { history.deleted.contains(2L) }
        compose.onNodeWithTag("history-delete-2").assertDoesNotExist()
        compose.onNodeWithText("从站 · USB").assertDoesNotExist()
    }

    @Test
    fun retentionAndCapacitySaveFromTheStoragePage() {
        show()
        compose.onNodeWithTag("mine-storage-manage").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("history-retention").performScrollTo().assertTextContains("30 天")
        open("history-retention")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("7")
        open("storage-save")
        assertEquals(7, repository.settings.value.historyRetentionDays)
        compose.onNodeWithTag("history-capacity").performScrollTo()
        open("history-capacity")
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithTag("storage-save").assertIsNotEnabled()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("64")
        open("storage-save")
        assertEquals(64, repository.settings.value.historyCapacityMb)
        compose.onNodeWithText("1 秒").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(
            io.github.xingyingyuzhui.modbusmobile.application.history.HistoryInterval.SEC_1,
            repository.settings.value.historyInterval,
        )
    }

    @Test
    fun exportSheetSavesTheCheckedParts() {
        val export = RecordingExport()
        show(export = export)
        open("mine-export")
        compose.waitUntil(2_000) { compose.onAllNodesWithText("CSV · 14 个点位").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("导入").assertDoesNotExist()
        compose.onNodeWithTag("export-part-TRAFFIC").performScrollTo().performClick()
        open("export-save")
        val saved = export.saved
        assertEquals(
            setOf(ArchivePart.PROJECT, ArchivePart.SLAVE, ArchivePart.POINTS, ArchivePart.HISTORY, ArchivePart.TRAFFIC),
            saved,
        )
        // 交给系统保存界面后浮层收起，回来不用再手动关。
        compose.onNodeWithTag("export-sheet").assertDoesNotExist()
    }

    @Test
    fun exportSheetClosesWhenSharing() {
        val export = RecordingExport()
        show(export = export)
        open("mine-export")
        compose.waitUntil(2_000) { compose.onAllNodesWithText("CSV · 14 个点位").fetchSemanticsNodes().isNotEmpty() }
        open("export-share")
        compose.onNodeWithTag("export-sheet").assertDoesNotExist()
    }

    @Test
    fun authorCreditSitsAfterTheSettingsAndOpensTheRepo() {
        val opened = mutableListOf<String>()
        val handler = object : androidx.compose.ui.platform.UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides Idle(),
                LocalSettingsRepository provides repository,
                LocalHistoryCatalog provides EmptyMineHistory(),
                androidx.compose.ui.platform.LocalUriHandler provides handler,
            ) { ModbusTheme(UiSettings()) { ModbusApp(startRoute = Destination.Mine.route) } }
        }
        compose.waitForIdle()
        val credit = compose.onNodeWithTag("author-credit").performScrollTo()
        credit.assertTextContains("by xingyingyuzhui", substring = true)
        // 在设置分组（最后一行“关于”）之后
        val about = compose.onNodeWithTag("mine-row-about").getBoundsInRoot()
        assertTrue(credit.getBoundsInRoot().top >= about.bottom)
        assertTrue("热区至少 48dp", credit.getBoundsInRoot().height >= androidx.compose.ui.unit.Dp(48f))
        credit.performClick()
        assertEquals(listOf("https://github.com/xingyingyuzhui/corn-modbus"), opened)
        // 关于页有作者行
        open("mine-row-about")
        compose.onNodeWithTag("about-author").assertTextContains("xingyingyuzhui", substring = true)
    }

    // ---- 截图（Roborazzi 渲染，不是实机）----
    @Test fun screenshotHomeDark() { show(ThemeMode.DARK); compose.onRoot().captureRoboImage("mine_home_dark_portrait.png") }
    @Test fun screenshotHomeLight() { show(ThemeMode.LIGHT); compose.onRoot().captureRoboImage("mine_home_light_portrait.png") }

    @Test
    fun screenshotAppearanceDark() {
        show(ThemeMode.DARK); open("mine-row-appearance")
        compose.onRoot().captureRoboImage("mine_appearance_dark_portrait.png")
    }

    @Test
    fun screenshotCommLight() {
        show(ThemeMode.LIGHT); open("mine-row-comm")
        compose.onRoot().captureRoboImage("mine_comm_light_portrait.png")
    }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun screenshotLandscapeLight() {
        show(ThemeMode.LIGHT)
        compose.onRoot().captureRoboImage("mine_light_landscape.png")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotEnabledOrNoToggle() {
    val config = fetchSemanticsNode().config
    check(!config.contains(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState)) { "写入前确认不应是可切换项" }
}

private class EmptyMineHistory(
    state: RecordingState = RecordingState.IDLE,
    private val usage: Long = 0,
    sessions: List<HistorySession> = emptyList(),
) : HistoryCatalog {
    private val rows = sessions.toMutableList()
    override val recording: StateFlow<RecordingState> = MutableStateFlow(state)
    val deleted = mutableListOf<Long>()

    override suspend fun overview() = HistoryOverview(usage, rows.toList(), 0)

    override suspend fun setRetained(id: Long, retained: Boolean) {
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(retained = retained)
    }

    override suspend fun deleteClosed(id: Long): Boolean {
        val session = rows.firstOrNull { it.id == id } ?: return false
        if (session.retained || session.endedAtMs == null) return false
        rows.removeAll { it.id == id }
        deleted += id
        return true
    }

    override suspend fun samples(sessionId: Long): List<HistorySample> = emptyList()
}

private class RecordingExport : MineExport {
    var saved: Set<ArchivePart>? = null
    override fun save(parts: Set<ArchivePart>) { saved = parts }
    override fun share(parts: Set<ArchivePart>) = Unit
    override suspend fun pointCount() = 14
}
