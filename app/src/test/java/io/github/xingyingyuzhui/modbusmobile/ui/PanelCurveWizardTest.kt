package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.GapStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.LegendPosition
import io.github.xingyingyuzhui.modbusmobile.application.panel.LineStyle
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.SamplingMode
import io.github.xingyingyuzhui.modbusmobile.application.panel.SeriesForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.application.panel.YAxis
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.curve.FixedCurveClock
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelWizard
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardState
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

/** 曲线向导（P7）：配置页各节、系列弹窗、点位页、外观页；字段联动、触控 ≥48dp、字体 1.3 倍、渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelCurveWizardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private var state by mutableStateOf<WizardState?>(null)
    private val calls = mutableListOf<String>()
    private val now = PanelFixtures.WALL_MS

    private fun point(name: String, address: String, unit: String = "", scale: String = "1", decimals: String = "") =
        WidgetForm.forKind(WidgetKinds.NUMBER).copy(name = name, address = address, type = DataType.S16, scale = scale, unitLabel = unit, decimals = decimals)

    private val temp = SeriesForm("p-1", point("供水温度", "100", "℃", "0.1", "1"), colorIndex = 0)
    private val press = SeriesForm("p-2", point("供水压力", "101", "MPa", "0.01", "2"), colorIndex = 1, lineStyle = LineStyle.DASH, axis = YAxis.RIGHT)

    private fun curveState(step: Int, curve: CurveForm = CurveForm(), sheet: Int? = null, pointPage: Boolean = false, showErrors: Boolean = false, editing: Boolean = false) =
        WizardState(
            step, WidgetForm(), "w-new", "p-new", editingWidgetId = if (editing) "w-new" else null,
            original = null, showErrors = showErrors, curve = curve, originalCurve = if (editing) curve else null, sheet = sheet, pointPage = pointPage,
        )

    private fun previewHistory(): SampleHistory {
        val h = SampleHistory()
        h.track(mapOf("p-1" to SampleHistory.Tracked(temp.toSeries()!!.point, 600_000, 5000), "p-2" to SampleHistory.Tracked(press.toSeries()!!.point, 600_000, 5000)))
        for (s in -60..0) {
            h.record("p-1", now + s * 1000L, 23 + 3 * sin(s / 8.0))
            h.record("p-2", now + s * 1000L, 0.75 + 0.15 * sin(s / 11.0 + 1))
        }
        return h
    }

    private fun show(
        initial: WizardState,
        theme: ThemeMode = ThemeMode.DARK,
        fontScale: Float = 1f,
        history: SampleHistory? = previewHistory(),
        landscape: Boolean = false,
    ) {
        state = initial
        val settings = UiSettings(theme = theme)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                FixedCurveClock(now) {
                    ModbusTheme(settings) {
                        Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                            val s = state
                            if (s != null) {
                                PanelWizard(
                                    s, null, true,
                                    WizardActions(
                                        onForm = { f -> state = s.copy(form = f) },
                                        onNext = { calls += "next"; state = if (s.step < 3) s.copy(step = s.step + 1) else s },
                                        onBack = {
                                            calls += "back"
                                            state = when {
                                                s.pointPage -> s.copy(pointPage = false)
                                                s.sheet != null -> s.copy(sheet = null)
                                                else -> s.copy(step = s.step - 1)
                                            }
                                        },
                                        onExit = { calls += "exit" },
                                        onCurve = { c -> state = s.copy(curve = c) },
                                        onAddSeries = {
                                            calls += "addSeries"
                                            val c = s.curve!!.addSeries("p-n${s.curve.series.size}")
                                            state = s.copy(curve = c, sheet = c.series.lastIndex)
                                        },
                                        onOpenSeries = { i -> calls += "open:$i"; state = s.copy(sheet = i) },
                                        onOpenSeriesPoint = { calls += "point"; state = s.copy(pointPage = true) },
                                        onDeleteSeries = { i -> calls += "delete:$i"; state = s.copy(curve = s.curve!!.removeSeries(i), sheet = null) },
                                    ),
                                    previewHistory = history,
                                    seriesSnapshots = emptyMap(),
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    /** 带 tag 的包装 Box 本身不可点时，量它里面的可点节点（chip 的 48dp 热区不占布局，包装 Box 只有视觉高）。 */
    private fun touch(tag: String): androidx.compose.ui.geometry.Rect {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val clickable = { n: androidx.compose.ui.semantics.SemanticsNode -> androidx.compose.ui.semantics.SemanticsActions.OnClick in n.config }
        return (if (clickable(node)) node else node.children.firstOrNull(clickable) ?: node).touchBoundsInRoot
    }
    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private val dp get() = compose.density.density
    private fun curve() = state!!.curve!!
    private fun tap(tag: String) {
        val n = compose.onNodeWithTag(tag, useUnmergedTree = true)
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }
    private fun type(tag: String, text: String) {
        val n = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        runCatching { n.performScrollTo() }
        n.performTextReplacement(text)
        compose.waitForIdle()
    }
    private fun assertTouch(tag: String) {
        runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }
        val t = touch(tag)
        assertTrue("$tag 宽 ${t.width / dp}", t.width / dp >= 47.5f)
        assertTrue("$tag 高 ${t.height / dp}", t.height / dp >= 47.5f)
    }

    // ---- 2/3 配置页 ----

    @Test fun configStepHasEverySectionOfTheDesign() {
        show(curveState(2, CurveForm(listOf(temp, press))))
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertContentDescriptionContains("第 2 步", substring = true)
        // v3 3-4：第 2 步 = 预览 + 系列 + 时间窗口 + “采样与内存”（折叠）
        for (tag in listOf(
            "wiz-preview", "wiz-series-title", "wiz-series-add", "wiz-series-row-0", "wiz-series-row-1", "wiz-window", "wiz-sampling-block",
        )) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }
            assertEquals(tag, 1, count(tag))
        }
        // 规范 65：曲线预览卡也带“预览”徽标（面板里的真卡片没有）
        compose.onNodeWithTag("wiz-preview-badge", useUnmergedTree = true).assertTextContains("预览")
        tap("wiz-sampling-block-toggle")
        for (tag in listOf("wiz-sampling", "wiz-retention", "wiz-max-points")) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }
            assertEquals(tag, 1, count(tag))
        }
        // v3：坐标轴 / 图例 / 缺测 / 游标挪到第 3 步「显示」
        tap("wiz-next")
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertContentDescriptionContains("第 3 步", substring = true)
        for (tag in listOf("wiz-left-title", "wiz-right-title", "wiz-grid", "wiz-legend", "wiz-gaps", "wiz-cursor")) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }
            assertEquals(tag, 1, count(tag))
        }
    }

    @Test fun emptySeriesListSaysSoAndOffersAdd() {
        show(curveState(2, CurveForm()))
        compose.onNodeWithTag("wiz-series-empty", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("没有系列就没有预览曲线", 0, count("curve-chart-w-new"))
        assertEquals(1, count("wiz-preview-none") + count("wiz-preview-placeholder") + count("curve-waiting").coerceAtMost(1))
        compose.onNodeWithTag("wiz-preview-badge", useUnmergedTree = true).assertTextContains("预览")
        tap("wiz-series-add")
        assertEquals(listOf("addSeries"), calls)
        compose.onNodeWithTag("wiz-sheet", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, curve().series.size)
    }

    @Test fun rightAxisBlockOnlyAppearsWhenSomeSeriesUsesIt() {
        // v3：坐标轴在第 3 步「显示」
        show(curveState(3, CurveForm(listOf(temp))))
        assertEquals(1, count("wiz-left-title"))
        assertEquals(0, count("wiz-right-title"))
        state = curveState(3, CurveForm(listOf(temp, press)))
        compose.waitForIdle()
        assertEquals(1, count("wiz-right-title"))
    }

    @Test fun seriesRowShowsNameAndSourceAndTapOpensTheSheet() {
        show(curveState(2, CurveForm(listOf(temp, press))))
        compose.onNodeWithTag("wiz-series-sub-1", useUnmergedTree = true).assertTextContains("HR101", substring = true)
        // v3 3-4：轴是独立小标签，不再拼在来源行里
        compose.onNodeWithTag("wiz-series-axis-1", useUnmergedTree = true).assertTextContains("右轴", substring = true)
        assertEquals("行内不画线宽 / 线型样例", 0, compose.onAllNodesWithText("px", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        tap("wiz-series-row-1")
        assertEquals(listOf("open:1"), calls)
        compose.onNodeWithTag("wiz-sheet-title", useUnmergedTree = true).assertTextContains("供水压力", substring = true)
    }

    @Test fun windowPresetsSelectAndCustomRevealsMinutesInput() {
        show(curveState(2, CurveForm(listOf(temp))))
        // 时间窗口是下拉格：点格子弹菜单，预设和「自定义」都在菜单里
        tap("wiz-window")
        for (sec in listOf(60, 300, 600, 3600)) assertEquals("菜单里有 $sec", 1, count("wiz-window-$sec"))
        tap("wiz-window-600")
        assertEquals(600, curve().windowSec)
        assertEquals("选完菜单关闭", 0, count("wiz-window-60"))
        assertEquals(0, count("wiz-window-minutes"))
        tap("wiz-window")
        tap("wiz-window-custom")
        assertEquals(null, curve().windowSec)
        assertEquals(1, count("wiz-window-minutes"))
        type("wiz-window-minutes", "90")
        assertEquals("90", curve().customMinutes)
        assertEquals("默认保留 1 小时，90 分钟的窗口先报错", io.github.xingyingyuzhui.modbusmobile.application.panel.FormError.EXCEEDS_RETENTION, curve().build("w").errors[io.github.xingyingyuzhui.modbusmobile.application.panel.CurveField.RETENTION])
        state = state!!.copy(curve = curve().copy(retentionHours = "2"))
        compose.waitForIdle()
        assertEquals(5400, curve().build("w").widget!!.curve!!.windowSec)
    }

    @Test fun windowLongerThanRetentionIsExplained() {
        show(curveState(2, CurveForm(listOf(temp), windowSec = 3600 * 2, retentionHours = "1"), showErrors = true))
        runCatching { compose.onNodeWithTag("wiz-retention", useUnmergedTree = true).performScrollTo() }
        assertTrue(compose.onAllNodesWithText("内存保留不能短于时间窗口", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun samplingSwitchShowsTheSnapshotNote() {
        show(curveState(2, CurveForm(listOf(temp))))
        assertEquals(0, count("wiz-snapshot-note"))
        // v3：“采样与内存”收进折叠块
        tap("wiz-sampling-block-toggle")
        compose.onNodeWithTag("wiz-sampling-note", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("定时快照", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SamplingMode.SNAPSHOT, curve().sampling)
        compose.onNodeWithTag("wiz-snapshot-note", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun snapshotSamplingShowsItsIntervalNote() {
        show(curveState(2, CurveForm(listOf(temp), sampling = SamplingMode.SNAPSHOT)))
        tap("wiz-sampling-block-toggle")
        compose.onNodeWithTag("wiz-snapshot-note", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun manualAxisRangeShowsMinMaxFieldsAndValidates() {
        // v3：坐标轴在第 3 步「显示」
        show(curveState(3, CurveForm(listOf(temp), leftAuto = false), showErrors = true))
        assertEquals(1, count("wiz-left-min"))
        assertEquals(1, count("wiz-left-max"))
        type("wiz-left-min", "30")
        type("wiz-left-max", "10")
        assertEquals("30", curve().leftMin)
        assertTrue(compose.onAllNodesWithText("最小值要小于最大值", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || curve().build("w").errors.isNotEmpty())
    }

    @Test fun legendGapsAndSwitchesWriteBackToTheForm() {
        // v3：图例 / 缺测 / 游标 / 网格都在第 3 步「显示」
        show(curveState(3, CurveForm(listOf(temp))))
        tap("wiz-grid")
        assertFalse(curve().grid)
        tap("wiz-cursor")
        assertFalse(curve().cursor)
    }

    @Test fun configTouchTargetsAreAtLeast48dp() {
        show(curveState(2, CurveForm(listOf(temp, press))))
        for (tag in listOf("wiz-series-add", "wiz-series-row-0", "wiz-window")) assertTouch(tag)
        // 第 3 步的坐标轴 / 图例 / 游标同样 ≥48dp
        tap("wiz-next")
        for (tag in listOf("wiz-grid", "wiz-cursor")) assertTouch(tag)
    }

    // ---- 系列弹窗 ----

    @Test fun sheetShowsEveryDesignFieldAndEightColours() {
        show(curveState(2, CurveForm(listOf(temp, press)), sheet = 1))
        for (tag in listOf(
            "wiz-sheet-title", "wiz-series-delete", "wiz-series-name", "wiz-series-point", "wiz-series-colors", "wiz-series-style", "wiz-series-width",
            "wiz-series-axis", "wiz-series-points", "wiz-series-fill", "wiz-series-step", "wiz-series-done",
        )) assertEquals(tag, 1, count(tag))
        assertEquals(8, (0 until 8).count { count("wiz-series-color-$it") == 1 })
    }

    @Test fun sheetControlsEditTheSeries() {
        show(curveState(2, CurveForm(listOf(temp, press)), sheet = 0))
        type("wiz-series-name", "进水温度")
        assertEquals("进水温度", curve().series[0].name)
        tap("wiz-series-color-3")
        assertEquals(3, curve().series[0].colorIndex)
        tap("wiz-series-points")
        assertTrue(curve().series[0].showPoints)
        tap("wiz-series-fill")
        assertTrue(curve().series[0].fill)
        tap("wiz-series-step")
        assertTrue(curve().series[0].step)
    }

    @Test fun sheetTouchTargetsAreAtLeast48dp() {
        show(curveState(2, CurveForm(listOf(temp, press)), sheet = 0))
        for (tag in listOf("wiz-series-delete", "wiz-series-color-0", "wiz-series-color-7", "wiz-series-points", "wiz-series-done", "wiz-series-point")) assertTouch(tag)
    }

    @Test fun doneAndScrimBothCloseTheSheet() {
        show(curveState(2, CurveForm(listOf(temp)), sheet = 0))
        tap("wiz-series-done")
        assertEquals(0, count("wiz-sheet"))
        state = curveState(2, state!!.curve!!, sheet = 0)
        compose.waitForIdle()
        compose.onNodeWithTag("wiz-sheet-scrim", useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(10f, 10f)) }
        compose.waitForIdle()
        assertEquals(0, count("wiz-sheet"))
    }

    @Test fun doneStaysVisibleAndReachableAtLargeFontAndLandscape() {
        show(curveState(2, CurveForm(listOf(temp, press)), sheet = 0), fontScale = 1.3f)
        val done = bounds("wiz-series-done")
        assertTrue("完成按钮在屏内 ${done.bottom.value}", done.bottom.value <= 780f && done.top.value >= 0f)
        compose.onNodeWithTag("wiz-series-done", useUnmergedTree = true).assertIsDisplayed()
        assertTouch("wiz-series-done")
    }

    @Test fun deleteRemovesTheSeriesAndClosesTheSheet() {
        show(curveState(2, CurveForm(listOf(temp, press)), sheet = 0))
        tap("wiz-series-delete")
        assertEquals(listOf("delete:0"), calls)
        assertEquals(listOf("供水压力"), curve().series.map { it.name })
        assertEquals(0, count("wiz-sheet"))
    }

    @Test fun sheetOpensThePointPageWhichIsAFullDataSourceEditor() {
        show(curveState(2, CurveForm(listOf(temp)), sheet = 0))
        tap("wiz-series-point")
        assertEquals(listOf("point"), calls)
        assertEquals(1, count("wiz-point-done"))
        assertEquals("数据点位页没有向导底栏", 0, count("wiz-next"))
        type("wiz-address", "205")
        assertEquals("205", curve().series[0].point.address)
        assertEquals("数据点位页是单独一页，不再叠着弹窗", 0, count("wiz-sheet"))
        tap("wiz-point-done")
        compose.onNodeWithTag("wiz-sheet", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 3/3 外观页 ----

    @Test fun lookStepShowsPreviewAndOnlyTheTwoWideSizes() {
        show(curveState(3, CurveForm(listOf(temp, press))))
        compose.onNodeWithTag("wiz-step", useUnmergedTree = true).assertContentDescriptionContains("第 3 步", substring = true)
        compose.onNodeWithTag("wiz-look-preview", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-size", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("wiz-size-note", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        tap("wiz-size")
        assertTrue(curve().spanRows in 1..2)
        assertEquals("曲线永远 2 列宽", 2, curve().spanColumns)
    }

    // ---- 渲染图 ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    private val two get() = CurveForm(listOf(temp, press))

    @Test fun render_wizard_curve_picker() { show(WizardState(1, WidgetForm(), "w-new", "p-new")); shot("wizard_curve_picker_dark.png") }

    @Test fun render_wizard_curve_config_dark() { show(curveState(2, two)); shot("wizard_curve_config_dark.png") }

    @Test fun render_wizard_curve_config_light() { show(curveState(2, two), theme = ThemeMode.LIGHT); shot("wizard_curve_config_light.png") }

    @Test fun render_wizard_curve_config_scrolled_dark() {
        show(curveState(2, two.copy(sampling = SamplingMode.SNAPSHOT, leftAuto = false, leftMin = "18", leftMax = "28")))
        runCatching { compose.onNodeWithTag("wiz-cursor", useUnmergedTree = true).performScrollTo() }
        shot("wizard_curve_config_bottom_dark.png")
    }

    @Test fun render_wizard_curve_config_empty_dark() { show(curveState(2, CurveForm()), history = null); shot("wizard_curve_config_empty_dark.png") }

    @Test fun render_wizard_curve_errors_dark() {
        show(curveState(2, two.copy(windowSec = null, customMinutes = "", maxPoints = "5"), showErrors = true))
        runCatching { compose.onNodeWithTag("wiz-window", useUnmergedTree = true).performScrollTo() }
        shot("wizard_curve_errors_dark.png")
    }

    @Test fun render_wizard_curve_sheet_dark() { show(curveState(2, two, sheet = 1)); shot("wizard_curve_sheet_dark.png") }

    @Test fun render_wizard_curve_sheet_light() { show(curveState(2, two, sheet = 1), theme = ThemeMode.LIGHT); shot("wizard_curve_sheet_light.png") }

    @Test fun render_wizard_curve_sheet_font13_dark() { show(curveState(2, two, sheet = 1), fontScale = 1.3f); shot("wizard_curve_sheet_font13_dark.png") }

    @Test fun render_wizard_curve_point_page_dark() { show(curveState(2, two, sheet = 0, pointPage = true)); shot("wizard_curve_point_dark.png") }

    @Test fun render_wizard_curve_look_dark() { show(curveState(3, two)); shot("wizard_curve_look_dark.png") }

    @Test fun render_wizard_curve_config_font13_dark() { show(curveState(2, two), fontScale = 1.3f); shot("wizard_curve_config_font13_dark.png") }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_wizard_curve_config_landscape() { show(curveState(2, two), landscape = true); shot("wizard_curve_config_landscape.png") }
}
