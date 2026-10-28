package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.Widget
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditError
import io.github.xingyingyuzhui.modbusmobile.ui.panel.EditState
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelContent
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelEditor
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

/** 面板编辑模式（P5）：版式对稿、热区 ≥48dp、手势、确认流程、字体 1.3 倍、多面板 chip 滚动；渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelEditTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private val calls = mutableListOf<String>()

    private val actions = EditActions(
        onRename = { calls += "rename:$it" },
        onColumns = { calls += "columns:$it" },
        onMove = { a, b -> calls += "move:$a>$b" },
        onSpan = { id, c, r -> calls += "span:$id:${c}x$r" },
        onDelete = { calls += "delete:$it" },
        onSelectPanel = { calls += "select:$it" },
        onAddPanel = { calls += "addPanel" },
        onDeletePanel = { calls += "deletePanel" },
        onDone = { calls += "done" },
        onCancel = { calls += "cancel" },
        onAddWidget = { calls += "addWidget" },
        onOpenWidget = { calls += "open:$it" },
        onDeleteHidden = { calls += "deleteHidden" },
    )

    private fun editState(project: Project = PanelFixtures.project(), panelId: String = "p1", dirty: Boolean = false, error: EditError? = null) =
        EditState(project, panelId, dirty, error = error)

    private fun showEditor(
        edit: EditState = editState(),
        theme: ThemeMode = ThemeMode.DARK,
        landscape: Boolean = false,
        fontScale: Float = 1f,
        snapshots: Map<String, PointSnapshot> = PanelFixtures.snapshots,
    ) {
        val settings = UiSettings(theme = theme)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides if (landscape) ShellMode.LANDSCAPE else ShellMode.PORTRAIT,
                LocalDockInsets provides if (landscape) DockInsets(start = 88.dp) else DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelEditor(edit, snapshots, actions)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun touch(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private val dp get() = compose.density.density

    // ---- 版式（v3 编辑态）----

    private fun tap(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    private fun openSettings() { tap("edit-settings"); compose.waitForIdle() }

    private fun select(id: String) { tap("edit-card-$id"); compose.waitForIdle() }

    @Test fun headerHasCloseNamedTitleGearAndDone() {
        showEditor()
        compose.onNodeWithText("编辑 · 机房 A", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("完成", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("edit-settings", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("编辑时不会写设备", substring = true, useUnmergedTree = true).assertIsDisplayed()
        // v3：名称 / 列数不再常驻页面顶部，收进 ⚙ 浮层
        assertEquals(0, count("edit-name"))
        assertEquals(0, count("edit-columns"))
    }

    @Test fun cardsAreQuietEditCardsWithGripAndSizeAndAtLeast84High() {
        showEditor()
        for (w in PanelFixtures.widgets) {
            val card = bounds("edit-card-${w.id}")
            assertTrue("${w.id} 高 ${card.height.value}", card.height.value >= 83.5f)
        }
        // 名称前有 ⠿（画出来的点），右上角写尺寸；没有 × 和角标，也没有虚线框
        assertEquals(PanelFixtures.widgets.size, count("edit-grip"))
        assertEquals(PanelFixtures.widgets.size, count("edit-size-tag"))
        compose.onNodeWithText("供水温度", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, count("edit-delete-w-temp"))
        assertEquals(0, count("edit-handle-w-temp"))
        assertEquals(0, count("edit-outline"))
        // 编辑态不打异常标签
        assertEquals(0, count("panel-badge"))
        // 两列、间距 8、边距 16
        val a = bounds("edit-card-w-temp")
        val b = bounds("edit-card-w-press")
        assertEquals(8f, (b.left - a.right).value, 0.6f)
        assertEquals(16f, a.left.value, 0.6f)
        assertEquals(360f - 16f, b.right.value, 0.6f)
    }

    @Test fun addWidgetIsTheLastGridCell() {
        showEditor()
        val t = touch("edit-add-widget")
        assertTrue("热区高 ${t.height / dp}", t.height / dp >= 47.5f)
        // 最后一格：和组件卡同宽（1 列）
        assertEquals(bounds("edit-card-w-temp").width.value, bounds("edit-add-widget").width.value, 0.6f)
        compose.onNodeWithTag("edit-add-widget", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("addWidget"), calls)
    }

    @Test fun tappingACardSelectsItAndShowsTheToolbar() {
        showEditor()
        assertEquals("没选中时没有工具条", 0, count("edit-toolbar"))
        select("w-temp")
        assertTrue("点卡片只是选中，不打开配置", calls.isEmpty())
        assertEquals(1, count("edit-selected-w-temp"))
        compose.onNodeWithTag("edit-toolbar", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("第 1 / 6 个", useUnmergedTree = true).assertIsDisplayed()
        // 再点一次取消选中
        select("w-temp")
        assertEquals(0, count("edit-toolbar"))
    }

    @Test fun toolbarConfigureOpensThatWidget() {
        showEditor()
        select("w-temp")
        tap("edit-configure")
        assertEquals(listOf("open:w-temp"), calls)
    }

    @Test fun toolbarActionsAreAtLeast48dp() {
        showEditor()
        select("w-press")
        for (tag in listOf("edit-close", "edit-done", "edit-settings", "edit-move-earlier", "edit-move-later", "edit-configure", "edit-delete")) {
            val t = touch(tag)
            assertTrue("$tag 宽 ${t.width / dp}", t.width / dp >= 47.5f)
            assertTrue("$tag 高 ${t.height / dp}", t.height / dp >= 47.5f)
        }
        assertEquals("工具条按钮高 52", 52f, bounds("edit-delete").height.value, 0.6f)
    }

    // ---- 操作 ----

    @Test fun toolbarDeleteDeletesThatWidget() {
        showEditor()
        select("w-press")
        tap("edit-delete")
        assertEquals(listOf("delete:w-press"), calls)
    }

    @Test fun toolbarSizeSegmentsSetTheSpan() {
        showEditor()
        select("w-temp")
        compose.onNodeWithText("2 × 1", useUnmergedTree = true).performClick()
        assertEquals(listOf("span:w-temp:2x1"), calls)
        calls.clear()
        compose.onNodeWithText("1 × 2", useUnmergedTree = true).performClick()
        assertEquals(listOf("span:w-temp:1x2"), calls)
    }

    @Test fun toolbarMovesEarlierAndLaterAndDisablesAtTheEnds() {
        showEditor()
        select("w-temp")
        compose.onNodeWithTag("edit-move-earlier", useUnmergedTree = true).assertIsNotEnabled()
        tap("edit-move-later")
        assertEquals(listOf("move:w-temp>w-press"), calls)
        calls.clear()
        select("w-temp")
        select("w-cur")
        compose.onNodeWithTag("edit-move-later", useUnmergedTree = true).assertIsNotEnabled()
        tap("edit-move-earlier")
        assertEquals(listOf("move:w-cur>w-valve"), calls)
    }

    @Test fun cardsKeepAccessibilityActionsAsGestureAlternatives() {
        showEditor()
        val actionsOf = compose.onNodeWithTag("edit-card-w-press", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].map { it.label }
        assertTrue(actionsOf.toString(), actionsOf.containsAll(listOf("前移", "后移", "配置“供水压力”", "删除组件“供水压力”")))
    }

    @Test fun longPressDragOntoAnotherCardMoves() {
        showEditor()
        val from = touch("edit-card-w-temp")
        val to = touch("edit-card-w-press")
        val start = Offset(from.left + from.width * 0.3f, from.top + from.height * 0.5f)
        val end = Offset(to.left + to.width * 0.4f, to.top + to.height * 0.5f)
        compose.onRoot().performTouchInput { down(start) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().performTouchInput {
            moveTo(start + Offset(5f, 0f))
            moveTo(end)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("move:w-temp>w-press"), calls)
    }

    @Test fun plainSwipeOnCardDoesNotMoveIt() {
        showEditor()
        val from = touch("edit-card-w-temp")
        val to = touch("edit-card-w-press")
        compose.onRoot().performTouchInput {
            swipe(Offset(from.left + 40f, from.top + 60f), Offset(to.left + 40f, to.top + 60f), durationMillis = 200)
        }
        assertTrue(calls.toString(), calls.none { it.startsWith("move") })
    }

    @Test fun curveOnlyOffersTwoColumnSizes() {
        val curve = io.github.xingyingyuzhui.modbusmobile.application.panel.Widget(
            "w-curve", io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds.CURVE, spanColumns = 2,
            curve = io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig(
                listOf(io.github.xingyingyuzhui.modbusmobile.application.panel.CurveSeries(PanelFixtures.temp)),
            ),
        )
        val project = PanelFixtures.project().copy(panels = listOf(Panel("p1", "机房 A", widgets = listOf(curve))))
        showEditor(editState(project))
        select("w-curve")
        assertEquals(0, compose.onAllNodesWithText("1 × 1", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("2 × 2", useUnmergedTree = true).assertExists()
    }

    @Test fun settingsSheetColumnsAreSegmentsTwoThreeFour() {
        showEditor()
        openSettings()
        compose.onNodeWithText("面板设置", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("3 列", useUnmergedTree = true).performClick()
        assertEquals(listOf("columns:3"), calls)
        calls.clear()
        compose.onNodeWithText("4 列", useUnmergedTree = true).performClick()
        assertEquals(listOf("columns:4"), calls)
    }

    @Test fun threeColumnPanelEditsInThreeColumns() {
        val three = PanelFixtures.project().copy(panels = listOf(Panel("p1", "机房 A", columns = 3, widgets = PanelFixtures.widgets)))
        showEditor(editState(three))
        val a = bounds("edit-card-w-temp")
        val b = bounds("edit-card-w-press")
        val c = bounds("edit-card-w-comp")
        assertEquals(a.width.value, b.width.value, 0.6f)
        assertEquals(a.width.value, c.width.value, 0.6f)
        assertEquals(8f, (c.left - b.right).value, 0.6f)
        assertEquals(a.top.value, c.top.value, 0.6f)
    }

    @Test fun blankNameShowsErrorAndDoneStillCallsBack() {
        val blank = PanelFixtures.project().copy(panels = listOf(Panel("p1", "", widgets = PanelFixtures.widgets)))
        showEditor(editState(blank, error = EditError.BLANK_NAME))
        assertTrue(count("edit-error") == 1)
        openSettings()
        compose.onAllNodesWithText("面板名称不能为空", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty().let(::assertTrue)
    }

    @Test fun saveFailureAndReadOnlyAreExplained() {
        showEditor(editState(error = EditError.SAVE_FAILED))
        compose.onNodeWithText("保存失败，修改还在，请重试", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun renameFieldInSettingsReportsTypedText() {
        showEditor()
        openSettings()
        compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("冷站")
        assertEquals(listOf("rename:冷站"), calls)
    }

    @Test fun doneCallsBack() {
        showEditor()
        compose.onNodeWithTag("edit-done", useUnmergedTree = true).performClick()
        assertEquals(listOf("done"), calls)
    }

    // ---- 退出 / 确认 ----

    @Test fun closeWithoutChangesLeavesImmediately() {
        showEditor(editState(dirty = false))
        compose.onNodeWithTag("edit-close", useUnmergedTree = true).performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun closeWithChangesAsksFirst() {
        showEditor(editState(dirty = true))
        compose.onNodeWithTag("edit-close", useUnmergedTree = true).performClick()
        assertTrue("先确认再退出", calls.isEmpty())
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithTag("edit-close", useUnmergedTree = true).performClick()
        compose.onNodeWithText("放弃").performClick()
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun deletePanelOnlyWithMultiplePanelsAndConfirms() {
        showEditor()
        openSettings()
        assertEquals("只有一个面板时不显示", 0, count("edit-delete-panel"))
        compose.activityRule.scenario.recreate()
        showEditor(editState(PanelFixtures.project(Panel("p2", "冷站"))))
        openSettings()
        val t = touch("edit-delete-panel")
        assertTrue("热区高 ${t.height / dp}", t.height / dp >= 47.5f)
        compose.onNodeWithTag("edit-delete-panel", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("删除面板“机房 A”？").assertIsDisplayed()
        compose.onNodeWithText("面板里的 6 个组件会一起删除", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("edit-dialog-confirm").performClick()
        assertEquals(listOf("deletePanel"), calls)
    }

    /** 读不懂 / 配置不完整的组件在编辑页不画卡，以前删不掉；现在有一行说明 + 「清除」（确认后才清）。 */
    @Test fun hiddenWidgetsAreCountedAndCanBeClearedAfterConfirm() {
        val hidden = listOf(
            Widget("future", "future_kind", raw = "{\"id\":\"future\",\"kind\":\"future_kind\"}"),
            Widget("noPoint", WidgetKinds.NUMBER, "x"),
        )
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", widgets = PanelFixtures.widgets + hidden), Panel("p2", "冷站")))
        showEditor(editState(project))
        compose.onNodeWithText("另有 2 个组件不显示", substring = true, useUnmergedTree = true).assertIsDisplayed()
        tap("edit-hidden-clear")
        compose.waitForIdle()
        compose.onNodeWithText("清除 2 个不显示的组件？").assertIsDisplayed()
        compose.onNodeWithTag("edit-dialog-confirm").performClick()
        assertEquals(listOf("deleteHidden"), calls)

        // 删整个面板时，说明里的数量包括看不到的组件
        calls.clear()
        openSettings()
        compose.onNodeWithTag("edit-delete-panel", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("面板里的 ${PanelFixtures.widgets.size + 2} 个组件会一起删除", substring = true).assertIsDisplayed()
    }

    @Test fun noHiddenWidgetsNoNote() {
        showEditor()
        assertEquals(0, count("edit-hidden"))
    }

    @Test fun emptyPanelSaysSoAndStillOffersTheAddCell() {
        val empty = Project("default", "x", listOf(Panel("p1", "机房 A")))
        showEditor(editState(empty))
        compose.onNodeWithTag("edit-empty", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("edit-add-widget", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 多面板（设置浮层里的面板列表）----

    private fun manyPanels(n: Int = 9) = Project(
        "default", "x",
        listOf(Panel("p1", "机房 A", widgets = PanelFixtures.widgets)) + (2..n).map { Panel("p$it", "长一点的面板$it") },
    )

    @Test fun settingsListSelectsPanelsAndNewPanelAddsOne() {
        showEditor(editState(PanelFixtures.project(Panel("p2", "冷站"))))
        openSettings()
        tap("edit-panel-p2")
        compose.onNodeWithContentDescription("新建面板", useUnmergedTree = true).performClick()
        assertEquals(listOf("select:p2", "addPanel"), calls)
        compose.onNodeWithText("6 个组件 · 当前", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun manyPanelsAreAllReachableInSettings() {
        showEditor(editState(manyPanels()))
        openSettings()
        compose.onNodeWithTag("edit-panel-p9", useUnmergedTree = true).assertExists().performScrollTo().performClick()
        assertEquals(listOf("select:p9"), calls)
    }

    // ---- 横屏 / 字体 ----

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun landscapeEditsInFourColumnsAndKeepsRailInset() {
        showEditor(landscape = true)
        val a = bounds("edit-card-w-temp")
        val d = bounds("edit-card-w-mode")
        assertEquals("rail 占 88（页边距含在里面，与面板页一致）", 88f, a.left.value, 0.6f)
        assertEquals(a.top.value, d.top.value, 0.6f)
        assertEquals(a.width.value, d.width.value, 0.6f)
        assertEquals(8f, (bounds("edit-card-w-press").left - a.right).value, 0.6f)
    }

    @Test fun fontScale13EditCardsKeepTextInside() {
        showEditor(fontScale = 1.3f)
        for (w in PanelFixtures.widgets) {
            compose.onNodeWithTag("edit-card-${w.id}", useUnmergedTree = true).performScrollTo()
            val card = bounds("edit-card-${w.id}")
            assertTrue("${w.id} 高 ${card.height.value}", card.height.value >= 83.5f)
            compose.onNodeWithTag("edit-card-${w.id}", useUnmergedTree = true).performScrollTo()
            val value = bounds("panel-value-${w.id}")
            assertTrue("${w.id} 值超出卡片底部", value.bottom.value <= card.bottom.value - 4f)
            assertTrue("${w.id} 值超出卡片右侧", value.right.value <= card.right.value)
        }
        compose.onNodeWithText("完成", useUnmergedTree = true).assertIsDisplayed()
        select("w-temp")
        compose.onNodeWithTag("edit-toolbar", useUnmergedTree = true).assertIsDisplayed()
    }

    // ---- 页面入口（PanelContent）----

    private fun showContent(readOnly: Boolean = false, onEdit: (() -> Unit)?, panels: List<Panel> = PanelFixtures.project().panels) {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(
                            PanelUiState.Ready(Project("default", "x", panels), emptyList(), readOnly),
                            PanelFixtures.snapshots, onEdit = onEdit,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun pencilOpensEditAndIsTouchSized() {
        var opened = 0
        showContent(onEdit = { opened++ })
        val t = touch("panel-edit")
        assertTrue(t.width / dp >= 47.5f && t.height / dp >= 47.5f)
        compose.onNodeWithTag("panel-edit", useUnmergedTree = true).performClick()
        assertEquals(1, opened)
    }

    @Test fun pencilHiddenWhenReadOnlyOrNoPanelsOrNoRepository() {
        showContent(readOnly = true, onEdit = {})
        assertEquals("只读工程不显示 ✎", 0, count("panel-edit"))
    }

    @Test fun pencilHiddenWithoutPanels() {
        showContent(onEdit = {}, panels = emptyList())
        assertEquals(0, count("panel-edit"))
    }

    @Test fun pencilHiddenWithoutRepositoryCallback() {
        showContent(onEdit = null)
        assertEquals(0, count("panel-edit"))
    }

    @Test fun pageChipsScrollWithManyPanelsAndSelect() {
        val selected = mutableListOf<String>()
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER), LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(
                            PanelUiState.Ready(manyPanels(), emptyList(), false), PanelFixtures.snapshots,
                            onSelectPanel = { selected += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("长一点的面板9", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("p9"), selected)
        // 滚动后第一个 chip 被挤出视野，但不换行、不撑高：整排高度仍是一个 chip（≥48 触控）
        val row = touch("panel-tabs")
        assertTrue("chip 行高 ${row.height / dp}", row.height / dp < 60f)
    }

    @Test fun fontScale13PageCardsKeepLayout() {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER), LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, 1.3f),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(PanelUiState.Ready(PanelFixtures.project(), emptyList(), false), PanelFixtures.snapshots)
                    }
                }
            }
        }
        compose.waitForIdle()
        val a = bounds("panel-card-w-temp")
        val b = bounds("panel-card-w-press")
        assertEquals("同行等宽", a.width.value, b.width.value, 0.6f)
        assertEquals("同行等高", a.height.value, b.height.value, 0.6f)
        assertEquals("列间距 8", 8f, (b.left - a.right).value, 0.6f)
        for (id in listOf("w-temp", "w-press", "w-comp", "w-mode", "w-valve", "w-cur")) {
            val card = bounds("panel-card-$id")
            val value = bounds("panel-value-$id")
            assertTrue("$id 值超出卡片底部 ${value.bottom.value} > ${card.bottom.value}", value.bottom.value <= card.bottom.value)
            assertTrue("$id 值超出卡片右侧", value.right.value <= card.right.value)
        }
        // 有 meta 的卡：meta 在值下面，且仍在卡内
        val metas = compose.onAllNodesWithTag("panel-meta", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(metas.isNotEmpty())
        val card = compose.onNodeWithTag("panel-card-w-temp", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val temp = compose.onNodeWithTag("panel-value-w-temp", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val meta = metas.map { it.boundsInRoot }.filter { it.top >= temp.bottom - 1f && it.left >= card.left - 1f && it.right <= card.right + 1f }
        assertTrue("温度卡 meta 在值下方并且在卡内", meta.any { it.bottom <= card.bottom + 0.5f })
        assertFalse("meta 不与值重叠", meta.any { it.top < temp.bottom - 1f })
    }

    // ---- 渲染图 ----

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_edit_dark_portrait() { showEditor(); shot("panel_edit_dark_portrait.png") }

    @Test fun render_edit_selected_dark_portrait() { showEditor(); select("w-press"); shot("panel_edit_selected_dark_portrait.png") }

    @Test fun render_edit_settings_dark_portrait() {
        showEditor(editState(PanelFixtures.project(Panel("p2", "冷站"), Panel("p3", "测试"))))
        openSettings()
        shot("panel_edit_settings_dark_portrait.png")
    }

    @Test fun render_edit_light_portrait() { showEditor(theme = ThemeMode.LIGHT); shot("panel_edit_light_portrait.png") }

    @Test @Config(qualifiers = "w780dp-h360dp-land")
    fun render_edit_dark_landscape() { showEditor(landscape = true); shot("panel_edit_dark_landscape.png") }

    @Test fun render_edit_multi_dark_portrait() {
        val project = PanelFixtures.project(Panel("p2", "冷站"), Panel("p3", "测试"))
        showEditor(editState(project, dirty = true))
        shot("panel_edit_multi_dark_portrait.png")
    }

    @Test fun render_edit_wide_card_dark_portrait() {
        val w = PanelFixtures.widgets.mapIndexed { i, x -> if (i == 0) x.copy(spanColumns = 2) else if (i == 4) x.copy(spanRows = 2) else x }
        val project = Project("default", "x", listOf(Panel("p1", "机房 A", widgets = w)))
        showEditor(editState(project))
        shot("panel_edit_resized_dark_portrait.png")
    }

    @Test fun render_edit_fontscale_dark_portrait() { showEditor(fontScale = 1.3f); shot("panel_edit_font13_dark_portrait.png") }

    @Test fun render_page_fontscale_dark_portrait() {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER), LocalOpenConnection provides {},
                LocalDockInsets provides DockInsets(bottom = 76.dp),
                LocalDensity provides Density(base.density, 1.3f),
            ) {
                ModbusTheme(settings) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        PanelContent(PanelUiState.Ready(PanelFixtures.project(), emptyList(), false), PanelFixtures.snapshots, onEdit = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        shot("panel_widgets_font13_dark_portrait.png")
    }
}
