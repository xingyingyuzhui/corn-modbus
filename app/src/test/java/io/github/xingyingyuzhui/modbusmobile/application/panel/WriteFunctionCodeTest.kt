package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 面板写入功能码（FC05/06 ↔ FC15/16）：向导能选、能存进工程文件、再编辑时带回来；不写设备的组件不带。
 * org.json 在 JVM 单测里是桩，JSON 往返要 Robolectric。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WriteFunctionCodeTest {
    private fun build(form: WidgetForm) = requireNotNull(form.build("w-1", "p-1").widget)

    @Test fun switchButtonAndWritableTableKeepTheChoice() {
        val switch = build(WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "泵", forceMultiple = true))
        assertTrue(switch.forceMultiple)
        val button = build(
            WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "复位", area = RegisterArea.HOLDING_REGISTER, type = DataType.U16, writeValue = "1", forceMultiple = true),
        )
        assertTrue(button.forceMultiple)
        val table = build(WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).copy(name = "表", tableWritable = true, forceMultiple = true))
        assertTrue(table.forceMultiple)
        assertTrue(WidgetForm.fromWidget(table)!!.forceMultiple)
    }

    @Test fun readOnlyWidgetsAndMultiWordWritesDropTheFlag() {
        val number = WidgetForm(kind = WidgetKinds.NUMBER, name = "温度", forceMultiple = true)
        assertFalse(number.writesDevice)
        assertFalse(build(number).forceMultiple)
        // 表没勾“允许修改”：不会写，也就没有写入功能码
        assertFalse(build(WidgetForm.forKind(WidgetKinds.REGISTER_TABLE).copy(name = "表", forceMultiple = true)).forceMultiple)
        // F32 按钮本来就是 FC16，不用（也不显示）这个选项
        val f32 = WidgetForm.forKind(WidgetKinds.BUTTON).copy(name = "设定", area = RegisterArea.HOLDING_REGISTER, type = DataType.F32, writeValue = "1.5", forceMultiple = true)
        assertFalse(f32.singleValueWrite)
        assertFalse(build(f32).forceMultiple)
    }

    @Test fun jsonRoundTripsAndOldFilesDefaultToSingleWrites() {
        val switch = build(WidgetForm.forKind(WidgetKinds.SWITCH).copy(name = "泵", forceMultiple = true))
        val project = Project("default", "x", listOf(Panel("a", "A", widgets = listOf(switch, switch.copy(id = "w-2", forceMultiple = false)))))
        val text = ProjectJson.encode(project)
        val back = ProjectJson.decode(text).project.panels.single().widgets
        assertEquals(listOf(true, false), back.map { it.forceMultiple })
        // 默认不写字段：旧版本读到的文件和以前一模一样
        assertEquals(1, Regex("forceMultiple").findAll(text).count())
    }
}
