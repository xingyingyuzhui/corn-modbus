package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.Panel
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointStore
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollScheduler
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectLoad
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionContains
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointPreview
import androidx.compose.ui.test.onFirst
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** 添加组件向导（P6）端到端：真 ViewModel + 真工程文件 + 真预览/轮询，向导只读不写。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelWizardFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    /** 保持寄存器 100 读 234、120 读 2，其余读 0。 */
    private class Session(role: Role? = Role.MASTER) : PanelFakeSession(role) {
        val requests = CopyOnWriteArrayList<ModbusRequest>()
        override suspend fun execute(request: ModbusRequest): ModbusResult {
            requests += request
            val r = request as? ModbusRequest.ReadHoldingRegisters
                ?: return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, listOf(0)))
            val values = (0 until r.quantity).map { i ->
                when (r.address + i) { 100 -> 234; 120 -> 2; else -> 0 }
            }
            return ModbusResult.Success(ModbusResponse.ReadRegisters(1, FunctionCode.READ_HOLDING_REGISTERS, values))
        }
    }

    private var session = Session()
    private val dir get() = File(tmp.root, "projects")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() { scope.cancel() }

    private fun repoWith(project: Project): ProjectRepository = ProjectRepository(dir).also { it.save(project) }

    private fun emptyProject() = Project("default", "x", listOf(Panel("p1", "机房 A")))

    private fun runtime(): PanelRuntime {
        val store = PointStore()
        val clock = { System.nanoTime() / 1_000_000 }
        return PanelRuntime(store, PollScheduler(session, scope, store, nowMs = clock)) { s -> PointPreview(session, s, clock) }
    }

    private fun show(repository: ProjectRepository, runtime: PanelRuntime? = runtime()) {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides MemorySettingsRepository(settings),
                LocalProjectRepository provides repository,
                LocalPanelRuntime provides runtime,
            ) { ModbusTheme(settings) { ModbusApp(startRoute = Destination.Panel.route) } }
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun waitTag(tag: String) = compose.waitUntil(5_000) { count(tag) > 0 }
    private fun waitGone(tag: String) = compose.waitUntil(5_000) { count(tag) == 0 }
    private fun reloaded(): Project = (ProjectRepository(dir).load() as ProjectLoad.Loaded).project
    private fun tap(tag: String) {
        val n = compose.onNodeWithTag(tag, useUnmergedTree = true)
        runCatching { n.performScrollTo() }
        n.performClick()
    }
    private fun type(tag: String, text: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
        .also { n -> runCatching { n.performScrollTo() } }.performTextReplacement(text)
    private fun waitText(text: String, ms: Long = 8_000) =
        compose.waitUntil(ms) { compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun tagStarting(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun step() = compose.onNodeWithTag("wiz-step", useUnmergedTree = true)

    private fun openWizardFromEmpty() {
        waitTag("panel-empty-add"); tap("panel-empty-add")
        waitTag("wiz-kind-number")
    }

    /** 第 2 步「数据」：地址 + 换算（v3 把这些折叠成一行，点开再填）。 */
    private fun fillData(address: String = "100", scale: String = "0.1") {
        waitTag("wiz-address")
        type("wiz-address", address)
        // 状态文本 / 灯这类不换算的组件没有“换算”块
        if (count("wiz-convert-toggle") > 0) {
            tap("wiz-convert-toggle")
            type("wiz-scale", scale)
        }
    }

    /** 数值向导：第 2 步读 100 / ×0.1，第 3 步起名、设 1 位小数。 */
    private fun fillTemperature() {
        tap("wiz-kind-number")
        fillData()
        tap("wiz-next")
        waitTag("wiz-look-preview")
        type("wiz-name", "供水温度")
        type("wiz-decimals", "1")
    }

    // 小屏：第 3 步的名称框在首屏之外，才能看出“没通过时滚过去”
    @Test @Config(sdk = [33], qualifiers = "w360dp-h340dp")
    fun nextWithAMissingNameScrollsTheNameFieldIntoView() {
        // 名称在第 3 步、首屏之外；按“添加到面板”没通过时要滚过去，不能看起来“没反应”。
        show(repoWith(emptyProject()))
        openWizardFromEmpty()
        tap("wiz-kind-number")
        fillData()
        tap("wiz-next")
        waitTag("wiz-look-preview")
        step().assertContentDescriptionContains("第 3 步", substring = true)
        // 先滚到页面底部：名称框在首屏之上，看不到
        compose.onNodeWithTag("wiz-size-note", useUnmergedTree = true).performScrollTo()
        compose.onNodeWithTag("wiz-name", useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithTag("wiz-next", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        step().assertContentDescriptionContains("第 3 步", substring = true)
        compose.onNodeWithTag("wiz-name", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun emptyPanelAddNumberWidgetThenSaveShowsPolledValueAndWritesFile() {
        val repo = repoWith(emptyProject())
        show(repo)
        openWizardFromEmpty()
        step().assertContentDescriptionContains("第 1 步", substring = true)
        assertEquals("向导期间草稿还没写盘", 0, reloaded().panels[0].widgets.size)
        tap("wiz-kind-number")
        fillData()
        // 第 2 步顶部的一行预览：轮询读到 234 → 23.4
        waitText("23.4")
        tap("wiz-next")
        waitTag("wiz-look-preview")
        step().assertContentDescriptionContains("第 3 步", substring = true)
        type("wiz-name", "供水温度")
        type("wiz-decimals", "1")
        tap("wiz-next")
        waitTag("edit-grid")
        compose.onAllNodes(tagStarting("edit-card-"), useUnmergedTree = true).assertCountEquals(1)
        assertEquals("加入草稿但完成前不写盘", 0, reloaded().panels[0].widgets.size)
        tap("edit-done")
        waitTag("panel-grid")
        val saved = reloaded().panels[0].widgets
        assertEquals(1, saved.size)
        val w = saved.single()
        assertEquals("number", w.kind)
        assertEquals("供水温度", w.point!!.name)
        assertEquals(100, w.point!!.address)
        assertEquals(0.1, w.point!!.conversion.scale, 1e-9)
        assertEquals(1, w.point!!.conversion.decimals)
        // 页面里这个新组件被轮询并显示数值
        waitText("23.4")
        assertTrue("轮询读过 100", session.requests.any { (it as? ModbusRequest.ReadHoldingRegisters)?.address == 100 })
        assertTrue("整个向导过程只读不写", session.requests.none { it.isWrite })
    }

    @Test fun statusTextWidgetMapsPolledValueToItsLabel() {
        val repo = repoWith(emptyProject())
        show(repo)
        openWizardFromEmpty()
        tap("wiz-kind-status_text")
        fillData(address = "120", scale = "1")
        tap("wiz-next")
        waitTag("wiz-look-preview")
        type("wiz-name", "运行模式")
        tap("wiz-state-add")
        type("wiz-state-value-0", "2")
        type("wiz-state-label-0", "制冷")
        tap("wiz-next")
        waitTag("edit-grid")
        tap("edit-done")
        waitTag("panel-grid")
        waitText("制冷")
        val w = reloaded().panels[0].widgets.single()
        assertEquals("status_text", w.kind)
        assertEquals(listOf("制冷"), w.states.map { it.label })
        assertTrue(session.requests.none { it.isWrite })
    }

    @Test fun nextWithMissingFieldsStaysAndExplainsInsteadOfAdvancing() {
        val repo = repoWith(emptyProject())
        show(repo)
        openWizardFromEmpty()
        tap("wiz-kind-number")
        waitTag("wiz-address")
        type("wiz-address", "")
        tap("wiz-next")
        step().assertContentDescriptionContains("第 2 步", substring = true)
        compose.onAllNodesWithText("必填", useUnmergedTree = true).onFirst().assertExists()
    }

    @Test fun cancellingTheWizardAddsNothingAndFileStaysUntouched() {
        val repo = repoWith(emptyProject())
        val before = File(dir, "default.json").readText()
        show(repo)
        openWizardFromEmpty()
        fillTemperature()
        tap("wiz-prev")   // 3 → 2
        waitTag("wiz-address")
        tap("wiz-prev")   // 2 → 1
        waitTag("wiz-kind-number")
        compose.waitForIdle()
        step().assertContentDescriptionContains("第 1 步", substring = true)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }   // 第 1 步按返回：退出向导，回到编辑页
        waitTag("edit-empty")
        compose.onAllNodes(tagStarting("edit-card-"), useUnmergedTree = true).assertCountEquals(0)
        tap("edit-done")
        waitTag("panel-empty-add")
        assertEquals(before, File(dir, "default.json").readText())
    }

    @Test fun tappingACardEditsThatWidgetInPlaceKeepingItsIdentity() {
        val repo = repoWith(PanelFixtures.project())
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        tap("edit-card-w-temp"); tap("edit-configure")
        waitTag("wiz-address")
        step().assertContentDescriptionContains("第 2 步", substring = true)
        tap("wiz-next")
        waitTag("wiz-look-preview")
        type("wiz-name", "回水温度")
        tap("wiz-next")           // 保存修改
        waitTag("edit-grid")
        tap("edit-done")
        waitTag("panel-grid")
        val saved = reloaded().panels[0].widgets
        assertEquals(PanelFixtures.widgets.map { it.id }, saved.map { it.id })
        assertEquals("回水温度", saved.first { it.id == "w-temp" }.point!!.name)
        assertEquals("其它组件原样", PanelFixtures.widgets[1], saved[1])
    }

    @Test fun backOnAChangedEditAsksBeforeDiscarding() {
        val repo = repoWith(PanelFixtures.project())
        val before = File(dir, "default.json").readText()
        show(repo)
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        tap("edit-card-w-temp"); tap("edit-configure")
        waitTag("wiz-address")
        type("wiz-address", "101")
        tap("wiz-prev")
        waitTag("wiz-discard-confirm")
        tap("wiz-discard-confirm")
        waitTag("edit-grid")
        tap("edit-done")
        waitTag("panel-grid")
        assertEquals(before, File(dir, "default.json").readText())
    }

    @Test fun editModeHidesTheDockAndKeepsTheDraftUntilDone() {
        val repo = repoWith(PanelFixtures.project())
        show(repo)
        assertTrue("面板页有 dock", compose.onAllNodesWithText("工作台", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        waitTag("panel-edit"); tap("panel-edit"); waitTag("edit-grid")
        // v3：编辑模式全屏专注，隐藏 dock（不会在编辑中切走，草稿也就不会被意外留在后台）
        compose.waitUntil(5_000) { compose.onAllNodesWithText("工作台", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        tap("edit-card-w-temp"); tap("edit-delete")
        waitGone("edit-card-w-temp")
        assertTrue("没点完成，文件仍有该组件", reloaded().panels[0].widgets.any { it.id == "w-temp" })
        tap("edit-done")
        waitTag("panel-grid")
        assertTrue(reloaded().panels[0].widgets.none { it.id == "w-temp" })
        compose.waitUntil(5_000) { compose.onAllNodesWithText("工作台", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun wizardHidesTheDockAndNeverTouchesDeviceUntilConnected() {
        session = Session(role = null)
        val repo = repoWith(emptyProject())
        show(repo)
        openWizardFromEmpty()
        assertEquals("向导是全屏子页，不显示底部导航", 0, count("dock"))
        tap("wiz-kind-number")
        fillData()
        // 第 2 步顶部的一行预览写原因，不画假数值
        waitTag("wiz-preview-none")
        assertTrue("未连接时预览不发任何请求", session.requests.isEmpty())
    }
}
