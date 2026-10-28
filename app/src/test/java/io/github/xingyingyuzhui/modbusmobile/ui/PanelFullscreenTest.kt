package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries
import io.github.xingyingyuzhui.modbusmobile.application.panel.LineStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.YAxis
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.curve.CurveFullscreenContent
import io.github.xingyingyuzhui.modbusmobile.ui.curve.CurveStatus
import io.github.xingyingyuzhui.modbusmobile.ui.curve.FixedCurveClock
import io.github.xingyingyuzhui.modbusmobile.ui.curve.windowChoices
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelContent
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelUiState
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.math.sin

/** 曲线全屏（P8a，稿面 b4-1）：页头 / 时间窗 / 暂停 / 读数 / 快照入口；曲线卡的 ⤢；渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w780dp-h360dp-land")
class PanelFullscreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private val now = PanelFixtures.WALL_MS
    private var closed = 0

    private fun widget(retention: Int = 60, window: Int = 60) = Widget(
        "w-curve", WidgetKinds.CURVE, spanColumns = 2, spanRows = 2,
        curve = CurveConfig(
            listOf(
                CurveSeries(PanelFixtures.temp, colorIndex = 0, axis = YAxis.LEFT),
                CurveSeries(PanelFixtures.pressure, colorIndex = 1, lineStyle = LineStyle.DASH, axis = YAxis.RIGHT),
            ),
            windowSec = window, retentionMinutes = retention,
        ),
    )

    private fun history(): SampleHistory {
        val h = SampleHistory()
        h.track(
            mapOf(
                "temp" to SampleHistory.Tracked(PanelFixtures.temp, 3_600_000, 20_000),
                "press" to SampleHistory.Tracked(PanelFixtures.pressure, 3_600_000, 20_000),
            ),
        )
        for (s in -75..0) {
            val t = now + s * 1000L
            if (s !in -40..-28) h.record("temp", t, 23 + 3 * sin(s / 8.0))
            h.record("press", t, 0.75 + 0.15 * sin(s / 11.0 + 1))
        }
        return h
    }

    private fun showFs(
        w: Widget? = widget(),
        history: SampleHistory? = history(),
        status: CurveStatus = CurveStatus.POLLING,
        theme: ThemeMode = ThemeMode.DARK,
        landscape: Boolean = true,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT) {
                FixedCurveClock(now) {
                    ModbusTheme(UiSettings(theme = theme)) {
                        Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                            CurveFullscreenContent(w, history, status) { closed++ }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()

    private fun assertTouch(tag: String) {
        // 带 tag 的包装 Box 不可点时量里面的可点节点（chip 的 48dp 热区不占布局，包装 Box 只有视觉高）。
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val clickable = { n: androidx.compose.ui.semantics.SemanticsNode -> androidx.compose.ui.semantics.SemanticsActions.OnClick in n.config }
        val b = (if (clickable(node)) node else node.children.firstOrNull(clickable) ?: node).touchBoundsInRoot
        val min = compose.density.density * 48f
        assertTrue("$tag 触控 ${b.width}x${b.height}px", b.width >= min - 0.5f && b.height >= min - 0.5f)
    }

    // ---- 页头 ----

    @Test fun headerShowsTitlePollingBadgeWindowChipsPauseAndClose() {
        showFs()
        compose.onNodeWithTag("curve-fs-title", useUnmergedTree = true).assertTextContains("供水温度 / 供水压力")
        assertEquals("轮询中", text("curve-fs-status"))
        listOf(60, 600, 3600).forEach { compose.onNodeWithTag("curve-fs-win-$it", useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithTag("curve-fs-pause", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("curve-fs-close", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("curve-fs-chart", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun statusBadgeFollowsPollingState() {
        showFs(status = CurveStatus.PAUSED)
        assertEquals("已暂停", text("curve-fs-status"))
    }

    @Test fun idleSessionSaysNotPollingInsteadOfFakingActivity() {
        showFs(status = CurveStatus.IDLE)
        assertEquals("未轮询", text("curve-fs-status"))
    }

    @Test fun closeButtonCallsBack() {
        showFs()
        compose.onNodeWithTag("curve-fs-close", useUnmergedTree = true).performClick()
        assertEquals(1, closed)
    }

    @Test fun headerTouchTargetsAreAtLeast48dp() {
        showFs()
        listOf("curve-fs-close", "curve-fs-pause", "curve-fs-win-60", "curve-fs-win-600", "curve-fs-win-3600", "curve-fs-snapshot").forEach(::assertTouch)
    }

    @Test fun pauseFreezesTheViewAndResumeLetsItRunAgain() {
        showFs()
        compose.onNodeWithTag("curve-fs-pause", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("曲线已暂停", text("curve-fs-status"))
        compose.onNodeWithTag("curve-fs-pause", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("轮询中", text("curve-fs-status"))
    }

    // ---- 时间窗 ----

    @Test fun windowChoicesOnlyOfferWhatTheRetentionCanCover() {
        val c = widget(retention = 10).curve!!
        assertEquals(listOf(60, 600), windowChoices(c))
        assertEquals(listOf(60, 600, 3600), windowChoices(widget(retention = 60).curve!!))
        assertEquals("配置的窗口不是预设时也保留", listOf(30, 60, 600), windowChoices(widget(retention = 10, window = 30).curve!!))
        assertEquals(listOf(60, 300, 600, 3600), windowChoices(widget(window = 300).curve!!))
    }

    @Test fun shortRetentionHidesTheHourChip() {
        showFs(widget(retention = 10))
        assertEquals(0, count("curve-fs-win-3600"))
        compose.onNodeWithTag("curve-fs-win-600", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun pickingAWindowChangesTheChartSummary() {
        showFs()
        fun summary() = compose.onNodeWithTag("curve-fs-chart", useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue(summary(), summary().contains("1 分钟"))
        compose.onNodeWithTag("curve-fs-win-600", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue(summary(), summary().contains("10 分钟"))
    }

    // ---- 读数 ----

    @Test fun legendShowsNameLatestValueAndMinMaxPerSeries() {
        showFs()
        assertTrue(text("curve-fs-value-0").matches(Regex("\\d+\\.\\d")))
        assertTrue(text("curve-fs-value-1").matches(Regex("\\d+\\.\\d\\d")))
        assertTrue(text("curve-fs-range-0"), text("curve-fs-range-0").startsWith("最小 ") && text("curve-fs-range-0").contains("· 最大 "))
        compose.onNodeWithText("供水压力", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun landscapeLegendColumnIs150dpWideAndSitsRightOfTheChart() {
        showFs()
        val chart = bounds("curve-fs-chart")
        val legend = bounds("curve-fs-legend-0")
        assertEquals(150f, legend.width.value, 1f)
        assertTrue("读数在图右边", legend.left >= chart.right - 0.5.dp)
        val snap = bounds("curve-fs-snapshot")
        assertTrue("快照按钮在读数列里（图右）", snap.left >= chart.right - 0.5.dp)
        assertEquals(150f, snap.width.value, 1f)
    }

    @Test fun landscapeSnapshotButtonSitsRightBelowTheLastReadoutLikeTheDraft() {
        showFs()
        val last = bounds("curve-fs-legend-1")
        val snap = bounds("curve-fs-snapshot")
        assertTrue("快照紧跟读数卡（稿面）：读数底 ${last.bottom} 快照顶 ${snap.top}", snap.top - last.bottom <= 20.dp)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun portraitFallbackStacksChartLegendAndSnapshotInsideTheScreen() {
        showFs(landscape = false)
        val chart = bounds("curve-fs-chart")
        val legend = bounds("curve-fs-legend-0")
        val snap = bounds("curve-fs-snapshot")
        assertTrue("读数在图下面", legend.top >= chart.bottom - 0.5.dp)
        assertTrue("快照在读数下面", snap.top >= legend.bottom - 0.5.dp)
        assertTrue(snap.right <= 360.dp && snap.bottom <= 780.dp)
    }

    // ---- 找不到 ----

    @Test fun missingWidgetExplainsInsteadOfDrawingAnEmptyChart() {
        showFs(w = null)
        compose.onNodeWithTag("curve-fs-missing", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, count("curve-fs-chart"))
        assertEquals(0, count("curve-fs-snapshot"))
        assertEquals(0, count("curve-fs-pause"))
        compose.onNodeWithTag("curve-fs-close", useUnmergedTree = true).performClick()
        assertEquals(1, closed)
    }

    @Test fun emptyHistoryShowsWaitingAndDashesNotZeros() {
        showFs(history = SampleHistory())
        compose.onNodeWithTag("curve-waiting", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("—", text("curve-fs-value-0"))
        assertEquals("最小 — · 最大 —", text("curve-fs-range-0"))
    }

    // ---- 游标 ----

    @Test fun cursorWorksInFullscreenToo() {
        showFs()
        compose.onNodeWithTag("curve-fs-chart", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag("curve-cursor", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 曲线卡上的 ⤢ ----

    private fun showPanel(landscape: Boolean = true, widgets: List<Widget> = listOf(widget()) + PanelFixtures.widgets.take(2), open: ((String) -> Unit)?) {
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", widgets = widgets)))
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT,
                LocalDockInsets provides if (landscape) DockInsets(start = 88.dp) else DockInsets(bottom = 76.dp),
            ) {
                FixedCurveClock(now) {
                    ModbusTheme(UiSettings(theme = ThemeMode.DARK)) {
                        Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                            PanelContent(
                                PanelUiState.Ready(project, emptyList(), false), PanelFixtures.snapshots, PollStats(1000, 1012),
                                history = history(), onEdit = {}, onOpenCurve = open,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun expandIconOnCurveCardOpensThatWidgetWith48dpTarget() {
        val opened = mutableListOf<String>()
        showPanel { opened += it }
        assertTouch("curve-expand-w-curve")
        compose.onNodeWithTag("curve-expand-w-curve", useUnmergedTree = true).performClick()
        assertEquals(listOf("w-curve"), opened)
        val card = bounds("panel-card-w-curve")
        val icon = bounds("curve-expand-w-curve")
        assertTrue("⤢ 在卡片内（右上角）", icon.right <= card.right + 0.5.dp && icon.top >= card.top - 0.5.dp)
        val title = bounds("curve-title-w-curve")
        assertTrue("标题不压住 ⤢", title.right <= icon.left + 12.dp)
    }

    @Test fun noExpandIconWithoutAHandler() {
        showPanel(open = null)
        assertEquals(0, count("curve-expand-w-curve"))
    }

    @Test fun otherCardsNeverGetAnExpandIcon() {
        showPanel { }
        assertEquals("数值卡没有 ⤢", 0, count("curve-expand-w-temp"))
        assertEquals(1, count("curve-expand-w-curve"))
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun portraitHeaderHasAnExpandButtonNextToEditThatOpensTheFirstCurve() {
        val opened = mutableListOf<String>()
        showPanel(landscape = false) { opened += it }
        assertTouch("panel-expand")
        val edit = bounds("panel-edit")
        val expand = bounds("panel-expand")
        assertTrue("⤢ 在 ✎ 左边（v3 1-1）", expand.right <= edit.left + 0.5.dp)
        compose.onNodeWithTag("panel-expand", useUnmergedTree = true).performClick()
        assertEquals(listOf("w-curve"), opened)
    }

    @Test fun landscapeHeaderExpandButtonOpensTheFirstCurveToo() {
        val opened = mutableListOf<String>()
        showPanel { opened += it }
        compose.onNodeWithTag("panel-expand", useUnmergedTree = true).performClick()
        assertEquals(listOf("w-curve"), opened)
    }

    @Test fun headerExpandIsHiddenWhenThePanelHasNoCurveOrNoHandler() {
        showPanel(widgets = PanelFixtures.widgets.take(2)) { }
        assertEquals("没有曲线不画 ⤢", 0, count("panel-expand"))
    }

    @Test fun headerExpandIsHiddenWithoutAHandler() {
        showPanel(open = null)
        assertEquals(0, count("panel-expand"))
    }

    // ---- 渲染图 ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_curve_fullscreen_dark_landscape() { showFs(); shot("curve_fs_dark_landscape.png") }

    @Test fun render_curve_fullscreen_light_landscape() { showFs(theme = ThemeMode.LIGHT); shot("curve_fs_light_landscape.png") }

    @Test fun render_curve_fullscreen_cursor_dark_landscape() {
        showFs()
        compose.onNodeWithTag("curve-fs-chart", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        shot("curve_fs_cursor_dark_landscape.png")
    }

    @Test fun render_curve_fullscreen_missing_dark_landscape() { showFs(w = null); shot("curve_fs_missing_dark_landscape.png") }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun render_curve_fullscreen_dark_portrait() { showFs(landscape = false); shot("curve_fs_dark_portrait.png") }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun render_curve_panel_with_expand_dark_portrait() { showPanel(landscape = false) { }; shot("curve_panel_expand_dark_portrait.png") }

    @Test fun render_curve_panel_with_expand_dark_landscape() { showPanel { }; shot("curve_panel_expand_dark_landscape.png") }
}
