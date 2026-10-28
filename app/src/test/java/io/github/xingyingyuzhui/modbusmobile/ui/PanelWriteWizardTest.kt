package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
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
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.panel.DataType
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.ui.panel.PanelWizard
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardActions
import io.github.xingyingyuzhui.modbusmobile.ui.panel.WizardState
import io.github.xingyingyuzhui.modbusmobile.ui.shell.DockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalDockInsets
import io.github.xingyingyuzhui.modbusmobile.ui.shell.LocalShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.shell.ShellMode
import io.github.xingyingyuzhui.modbusmobile.ui.theme.LocalModbusColors
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.PendingWrite
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.PlanResult
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteConfirmSheet
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.WriteFormat
import io.github.xingyingyuzhui.modbusmobile.ui.workbench.planWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/** 向导里的开关 / 按钮（P8b1）：选类型、数据源受限、广播提示、预设值、预览不可写；以及确认弹窗内容的渲染图。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class PanelWriteWizardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))

    private var state by mutableStateOf<WizardState?>(null)
    private val calls = mutableListOf<String>()

    private fun wizardState(step: Int, form: WidgetForm = WidgetForm(), showErrors: Boolean = false) =
        WizardState(step, form, "w-new", "p-new", showErrors = showErrors)

    private fun show(initial: WizardState, theme: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f) {
        state = initial
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                LocalSession provides PanelFakeSession(Role.MASTER),
                LocalOpenConnection provides {},
                LocalShellMode provides ShellMode.PORTRAIT,
                LocalDockInsets provides DockInsets(),
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density, fontScale),
            ) {
                ModbusTheme(UiSettings(theme = theme)) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.bg)) {
                        val s = state
                        if (s != null) {
                            PanelWizard(
                                s, null, true,
                                WizardActions(
                                    onKind = { kind -> calls += "kind:$kind"; state = s.copy(step = 2, form = WidgetForm.forKind(kind)) },
                                    onForm = { f -> state = s.copy(form = f) },
                                    onNext = { calls += "next" },
                                    onBack = { calls += "back" },
                                    onExit = { calls += "exit" },
                                ),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun countText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun form() = state!!.form
    private fun touch(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().touchBoundsInRoot
    private val dp get() = compose.density.density

    private fun type(tag: String, text: String) =
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performScrollTo().performTextReplacement(text)

    // ---- 1/3 选类型 ----

    @Test fun pickerHasSwitchButtonAndTableCards() {
        show(wizardState(1))
        for (kind in listOf("switch", "button", "register_table")) {
            val t = touch("wiz-kind-$kind")
            assertTrue("$kind 卡高 ${t.height / dp}", t.height >= 61.5f * dp)
        }
        assertEquals("没有“后续版本”的占位说明", 0, count("wiz-more-later"))
        // v3：开关 / 按钮在“控制设备 · 会写设备”分组里
        compose.onNodeWithText("线圈 / 寄存器位", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("一键写预设值", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wiz-group-control-tag", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("wiz-kind-switch", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf("kind:switch"), calls)
    }

    // ---- 开关 ----

    @Test fun switchConfigOnlyOffersWritableAreasAndBitForRegisters() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "压缩机")))
        compose.onNodeWithText("开关 · 数据", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("线圈 / 保持各一个", listOf(1, 1), listOf(countText("线圈"), countText("保持")))
        assertEquals("输入类区域只读，不给选", 0, countText("输入") + countText("离散"))
        compose.onNodeWithTag("wiz-write-note", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals("线圈开关没有“先读后写”提示", 0, count("wiz-bit-note"))
        compose.onNodeWithText("保持", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(RegisterArea.HOLDING_REGISTER, form().area)
        assertEquals("寄存器里只能是某一位", DataType.BIT, form().type)
        compose.onNodeWithTag("wiz-bit-note", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals("轮询周期仍可设（开关要读状态）", 1, count("wiz-poll-block"))
        assertEquals("亮 / 灭文字在第 3 步", 0, count("wiz-on-label"))
    }

    @Test fun switchRejectsUnitZero() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "x", unitId = "0"), showErrors = true))
        compose.onNodeWithText("范围 1–247", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, count("wiz-broadcast-note"))
    }

    // ---- 按钮 ----

    @Test fun buttonDataStepHasValueAndNoPollSectionAndCaptionIsOnTheDisplayStep() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "启动压缩机")))
        compose.onNodeWithText("按钮 · 数据", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("按钮文字在第 3 步", 0, count("wiz-button-label"))
        assertEquals("线圈按钮的预设值是 OFF / ON 分段", 1, count("wiz-write-coil"))
        assertEquals("按钮不读设备，没有轮询周期", 0, count("wiz-poll-block"))
        assertEquals("按钮不读设备，没有实时预览", 0, count("wiz-preview-bar"))
        assertEquals(0, count("wiz-preview-none"))
        compose.onNodeWithText("OFF", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("0", form().writeValue)
        compose.onNodeWithText("ON", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals("1", form().writeValue)
        compose.activityRule.scenario.recreate()
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "启动压缩机")))
        type("wiz-button-label", "启动")
        assertEquals("启动", form().buttonLabel)
    }

    @Test fun registerButtonTakesANumberAndValidatesItAgainstTheType() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.BUTTON).withArea(RegisterArea.HOLDING_REGISTER).copy(name = "设定", writeValue = "500")))
        assertEquals(1, count("wiz-write-value"))
        assertEquals(0, count("wiz-write-coil"))
        // 按钮只能写 5 种类型：外面 4 种常用，S32 在“更多”里；64 位 / 位 / ASCII 根本不画
        compose.onNodeWithTag("wiz-type-more", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(5, listOf("U16", "S16", "U32", "S32", "F32").count { count("wiz-type-$it") == 1 })
        assertEquals(0, listOf("U64", "S64", "F64", "BIT", "ASCII").sumOf { count("wiz-type-$it") })
        type("wiz-write-value", "70000")
        assertEquals("70000", form().writeValue)
        compose.onNodeWithText("超出该类型的范围", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun unitZeroIsBroadcastForButtonsWithARedWarning() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "全部停机", unitId = "0", writeValue = "0")))
        compose.onNodeWithTag("wiz-broadcast-note", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertTrue(form().build("w", "p").ok)
        type("wiz-unit", "248")
        compose.onNodeWithText("范围 0–247（0 = 广播）", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    // ---- 写入功能码 ----

    @Test fun writeFunctionCodeIsAPickableDropdownForSingleValueWrites() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "压缩机")))
        compose.onNodeWithText("FC05 写单个线圈", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wiz-write-fc", useUnmergedTree = true).performScrollTo()
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("wiz-write-fc")), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("wiz-write-fc-multi", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue(form().forceMultiple)
        compose.onNodeWithText("FC15 写多个线圈", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertTrue(form().build("w", "p").widget!!.forceMultiple)
    }

    @Test fun registerButtonOffersFc06OrFc16AndMultiWordTypesHideTheChoice() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.BUTTON).withArea(RegisterArea.HOLDING_REGISTER).copy(name = "设定", writeValue = "1", forceMultiple = true)))
        compose.onNodeWithText("FC16 写多个寄存器", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        state = state!!.copy(form = form().withType(DataType.F32))
        compose.waitForIdle()
        assertEquals("F32 本来就是 FC16，不显示这一格", 0, count("wiz-write-fc"))
    }

    @Test fun readOnlyTableHasNoWriteFunctionCode() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).copy(name = "表")))
        assertEquals(0, count("wiz-write-fc"))
        state = state!!.copy(form = form().copy(tableWritable = true))
        compose.waitForIdle()
        assertEquals(1, count("wiz-write-fc"))
    }

    @Test fun render_wizard_switch_fc15_dark() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "压缩机", address = "0", forceMultiple = true)))
        compose.onNodeWithTag("wiz-write-fc", useUnmergedTree = true).performScrollTo()
        shot("wizard_switch_fc15_dark.png")
    }

    @Test fun previewButtonIsNotInteractive() {
        show(wizardState(3, WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "启动压缩机", buttonLabel = "启动")))
        // 预览卡里只画了按钮的样子，没有可点击的按钮
        assertEquals(1, count("panel-button-visual-w-new"))
        compose.onNodeWithTag("panel-button-visual-w-new", useUnmergedTree = true).performClick()
        assertEquals(emptyList<String>(), calls)
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage(name)

    @Test fun render_wizard_picker_dark() { show(wizardState(1)); shot("wizard_picker_p8b1_dark.png") }

    @Test fun render_wizard_switch_coil_dark() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "压缩机", address = "0", onLabel = "运行", offLabel = "停止")))
        shot("wizard_switch_coil_dark.png")
    }

    @Test fun render_wizard_switch_bit_light() {
        show(
            wizardState(2, WidgetForm.forKind(WidgetKinds.SWITCH).withArea(RegisterArea.HOLDING_REGISTER).copy(name = "运行位", address = "140", bit = "3", onLabel = "运行", offLabel = "停止")),
            theme = ThemeMode.LIGHT,
        )
        shot("wizard_switch_bit_light.png")
    }

    @Test fun render_wizard_button_broadcast_dark() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "全部停机", unitId = "0", address = "9", buttonLabel = "全停", writeValue = "0")))
        shot("wizard_button_broadcast_dark.png")
    }

    @Test fun render_wizard_button_register_dark() {
        show(wizardState(2, WidgetForm.forKind(WidgetKinds.BUTTON).withArea(RegisterArea.HOLDING_REGISTER).copy(name = "设定值", address = "20", buttonLabel = "设为 500", writeValue = "500")))
        shot("wizard_button_register_dark.png")
    }

    // ---- 确认弹窗内容（和工作台同一个 WriteConfirmSheet；真实界面是弹层，这里 inline 画内容）----

    private fun sheet(
        theme: ThemeMode,
        pending: PendingWrite,
        context: String,
        warning: String? = null,
        wordText: (Int) -> String = Int::toString,
        name: String,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalSession provides PanelFakeSession(Role.MASTER)) {
                ModbusTheme(UiSettings(theme = theme)) {
                    Box(Modifier.fillMaxSize().background(LocalModbusColors.current.surface1)) {
                        WriteConfirmSheet(pending, UiSettings(theme = theme), {}, {}, {}, context, warning, wordText, inline = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        shot(name)
    }

    private fun plan(area: RegisterArea, unit: Int, address: Int, words: List<String> = emptyList(), coils: List<Boolean> = emptyList()) =
        (planWrite(area, unit.toString(), address.toString(), words, coils, WriteFormat.DEC, false) as PlanResult.Ok).plan

    @Test fun render_confirm_switch_coil_dark() = sheet(
        ThemeMode.DARK, PendingWrite(plan(RegisterArea.COIL, 1, 0, coils = listOf(false)), listOf(1), 3_000, true), "开关 · 压缩机", name = "confirm_panel_switch_coil_dark.png",
    )

    @Test fun render_confirm_switch_bit_dark() = sheet(
        ThemeMode.DARK, PendingWrite(plan(RegisterArea.HOLDING_REGISTER, 1, 140, listOf("33")), listOf(41), 0, true),
        "开关 · 运行位 · 位 3 → 0（整字 0x0029 → 0x0021）",
        warning = "位 3 的写入是「先读整字 → 只改这一位 → FC06 写回」，不是原子操作：确认时会再读一次，整字变了就不发，用新的值重新确认。",
        wordText = { "0x%04X".format(it) }, name = "confirm_panel_switch_bit_dark.png",
    )

    @Test fun render_confirm_button_broadcast_dark() = sheet(
        ThemeMode.DARK, PendingWrite(plan(RegisterArea.COIL, 0, 9, coils = listOf(false)), listOf(null)), "按钮“全停” · 全部停机", name = "confirm_panel_button_broadcast_dark.png",
    )

    @Test fun render_confirm_button_register_light() = sheet(
        ThemeMode.LIGHT, PendingWrite(plan(RegisterArea.HOLDING_REGISTER, 1, 20, listOf("500")), listOf(null)), "按钮“设为 500” · 设定值", name = "confirm_panel_button_register_light.png",
    )

    @Test fun confirmShowsOldReadsInDaysNotHundredsOfThousandsOfSeconds() {
        // 截图里出现过“来自 591152 秒前的读取”：旧值要换成更大的单位，才读得出有多旧。
        sheet(
            ThemeMode.LIGHT, PendingWrite(plan(RegisterArea.HOLDING_REGISTER, 1, 100, listOf("234")), listOf(234), 591_152_000, true),
            "寄存器表 · 水泵参数 第 1 行", name = "confirm_old_read_light.png",
        )
        compose.onNodeWithText("来自 6 天前的读取", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("591152", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun confirmKeepsSecondsForAFreshRead() {
        sheet(
            ThemeMode.LIGHT, PendingWrite(plan(RegisterArea.HOLDING_REGISTER, 1, 100, listOf("234")), listOf(234), 45_000, true),
            "寄存器表 · 水泵参数 第 1 行", name = "confirm_fresh_read_light.png",
        )
        compose.onNodeWithText("来自 45 秒前的读取", useUnmergedTree = true).assertExists()
    }
}
