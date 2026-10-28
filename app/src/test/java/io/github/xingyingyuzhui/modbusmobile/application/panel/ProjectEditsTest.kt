package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditsTest {
    private fun w(id: String, raw: String? = null) = Widget(
        id, WidgetKinds.NUMBER, id,
        PointDefinition("p-$id", id, 1, RegisterArea.HOLDING_REGISTER, 0, DataType.U16),
        raw = raw,
    )

    private fun project() = Project(
        "default", "x",
        listOf(
            Panel("a", "机房 A", 2, listOf(w("1"), w("2"), w("hidden", raw = "{\"id\":\"hidden\"}"), w("3"), w("4"))),
            Panel("b", "冷站", 3, listOf(w("5"))),
        ),
    )

    private fun ids(p: Project, panel: String) = p.panels.first { it.id == panel }.widgets.map { it.id }

    @Test fun deleteWidgetCannotReachHiddenWidgetsButDeleteHiddenClearsThem() {
        val incomplete = Widget("noPoint", WidgetKinds.NUMBER, "x")
        val start = ProjectEdits.addWidget(project(), "a", incomplete)
        assertEquals(ids(start, "a"), ids(ProjectEdits.deleteWidget(start, "a", "hidden"), "a"))

        val p = ProjectEdits.deleteHidden(start, "a")
        assertEquals(listOf("1", "2", "3", "4"), ids(p, "a"))
        // 只动这一个面板
        assertEquals(listOf("5"), ids(p, "b"))
    }

    @Test fun renameTrimsToMaxLengthAndLeavesOthers() {
        val p = ProjectEdits.renamePanel(project(), "a", "x".repeat(40))
        assertEquals(ProjectEdits.MAX_NAME, p.panels[0].name.length)
        assertEquals("冷站", p.panels[1].name)
        assertTrue(!ProjectEdits.isValidName("  "))
    }

    @Test fun columnsAreClamped() {
        assertEquals(4, ProjectEdits.setColumns(project(), "a", 9).panels[0].columns)
        assertEquals(2, ProjectEdits.setColumns(project(), "a", 0).panels[0].columns)
        assertEquals(3, ProjectEdits.setColumns(project(), "a", 3).panels[0].columns)
    }

    @Test fun moveTakesTheTargetsSlotForwardAndBackward() {
        assertEquals(listOf("2", "3", "1", "hidden", "4").sorted(), ids(ProjectEdits.moveWidget(project(), "a", "1", "3"), "a").sorted())
        assertEquals(listOf("2", "hidden", "3", "1", "4"), ids(ProjectEdits.moveWidget(project(), "a", "1", "3"), "a"))
        assertEquals(listOf("3", "1", "2", "hidden", "4"), ids(ProjectEdits.moveWidget(project(), "a", "3", "1"), "a"))
    }

    @Test fun moveKeepsUnreadableWidgetsInTheList() {
        val moved = ProjectEdits.moveWidget(project(), "a", "4", "1")
        val hidden = moved.panels[0].widgets.first { it.id == "hidden" }
        assertEquals("{\"id\":\"hidden\"}", hidden.raw)
        assertEquals(5, moved.panels[0].widgets.size)
    }

    @Test fun invalidMovesAreNoOps() {
        val p = project()
        assertSame(p.panels[0], ProjectEdits.moveWidget(p, "a", "1", "1").panels[0])
        assertEquals(p, ProjectEdits.moveWidget(p, "a", "nope", "1"))
        assertEquals(p, ProjectEdits.moveWidget(p, "zzz", "1", "2"))
    }

    @Test fun setSpanClampsAndNeverTouchesUnreadableWidgets() {
        val p = ProjectEdits.setSpan(project(), "a", "1", 5, 0)
        assertEquals(2 to 1, p.panels[0].widgets[0].spanColumns to p.panels[0].widgets[0].spanRows)
        assertEquals(project(), ProjectEdits.setSpan(project(), "a", "hidden", 2, 2))
    }

    @Test fun deleteRemovesOnlyThatWidgetAndNotUnreadableOnes() {
        assertEquals(listOf("1", "hidden", "3", "4"), ids(ProjectEdits.deleteWidget(project(), "a", "2"), "a"))
        assertEquals(ids(project(), "a"), ids(ProjectEdits.deleteWidget(project(), "a", "hidden"), "a"))
        assertEquals("别的面板里的同名 id 不受影响", ids(project(), "b"), ids(ProjectEdits.deleteWidget(project(), "a", "5"), "b"))
    }

    @Test fun addPanelPicksFirstFreeNameAndUniqueId() {
        val base = project()
        val (p1, id1) = ProjectEdits.addPanel(base) { "same" }
        assertEquals("面板 1", p1.panels.last().name)
        assertEquals(id1, p1.panels.last().id)
        val (p2, id2) = ProjectEdits.addPanel(p1, sequenceOf("same", "same", "other").iterator()::next)
        assertEquals("面板 2", p2.panels.last().name)
        assertEquals("other", id2)
        assertEquals(setOf("a", "b", "same", "other"), p2.panels.map { it.id }.toSet())
    }

    @Test fun deletePanelKeepsAtLeastOne() {
        val one = ProjectEdits.deletePanel(project(), "b")
        assertEquals(listOf("a"), one.panels.map { it.id })
        assertSame(one, ProjectEdits.deletePanel(one, "a"))
        assertEquals(project(), ProjectEdits.deletePanel(project(), "nope"))
    }

    @Test fun nextSizeCyclesAndRespectsColumns() {
        assertEquals(2 to 1, ProjectEdits.nextSize(1, 1, 2))
        assertEquals(2 to 2, ProjectEdits.nextSize(2, 1, 2))
        assertEquals(1 to 2, ProjectEdits.nextSize(2, 2, 2))
        assertEquals(1 to 1, ProjectEdits.nextSize(1, 2, 2))
    }

    @Test fun resizeFromDragRoundsToNearestCell() {
        // 列宽 160、行高 90、间距 8；当前 1×1（160×90）
        fun r(dx: Float, dy: Float, cols: Int = 2) = ProjectEdits.resizedSpan(160f, 90f, dx, dy, 160f, 90f, 8f, cols)
        assertEquals(1 to 1, r(0f, 0f))
        assertEquals(1 to 1, r(60f, 30f))
        assertEquals(2 to 1, r(100f, 0f))
        assertEquals(2 to 2, r(120f, 80f))
        assertEquals(1 to 2, r(10f, 60f))
        assertEquals("往回拖缩小", 1 to 1, ProjectEdits.resizedSpan(328f, 188f, -170f, -100f, 160f, 90f, 8f, 2))
        assertEquals("夹在 2 以内", 2 to 2, r(900f, 900f, 4))
    }

    // ---- P6：添加 / 替换组件、新 id ----

    @Test fun addWidgetAppendsToThatPanelOnly() {
        val added = ProjectEdits.addWidget(project(), "a", w("new"))
        assertEquals(ids(project(), "a") + "new", ids(added, "a"))
        assertEquals(ids(project(), "b"), ids(added, "b"))
        assertEquals("面板不存在时原样返回", project(), ProjectEdits.addWidget(project(), "zzz", w("new")))
    }

    @Test fun replaceWidgetKeepsPositionAndSkipsUnreadable() {
        val base = project()
        val target = base.panels.first { it.id == "a" }.widgets[1]
        val replaced = ProjectEdits.replaceWidget(base, "a", target.copy(title = "改过"))
        assertEquals(ids(base, "a"), ids(replaced, "a"))
        assertEquals("改过", replaced.panels.first { it.id == "a" }.widgets[1].title)
        val raw = base.panels.first { it.id == "a" }.widgets.first { it.raw != null }
        assertEquals("读不懂的组件不被替换", base, ProjectEdits.replaceWidget(base, "a", raw.copy(title = "不该生效", raw = null)))
    }

    @Test fun newIdsAvoidEverythingInTheProject() {
        val used = project().panels.flatMap { p -> p.widgets.map { it.id } }
        val seq = ArrayDeque(listOf("aa", "bb"))
        val (widgetId, pointId) = ProjectEdits.newIds(project()) { seq.removeFirst() }
        assertEquals("w-aa", widgetId)
        assertEquals("p-aa", pointId)
        assertTrue(widgetId !in used)
        // 撞上已有 id 就换下一个
        val clash = project().copy(panels = listOf(Panel("a", "A", widgets = listOf(w("w-aa")))))
        val seq2 = ArrayDeque(listOf("aa", "bb"))
        assertEquals("w-bb", ProjectEdits.newIds(clash) { seq2.removeFirst() }.first)
    }

    private fun curveWidget(id: String = "c1", vararg pointIds: String) = Widget(
        id, WidgetKinds.CURVE, curve = CurveConfig(
            pointIds.map { CurveSeries(PointDefinition(it, it, 1, RegisterArea.HOLDING_REGISTER, 0, DataType.U16)) },
        ),
        spanColumns = 2,
    )

    @Test fun curveWidgetsNeverShrinkBelowTwoColumns() {
        val p = Project("default", "x", listOf(Panel("a", "A", 3, listOf(curveWidget("c1", "s1")))))
        val one = ProjectEdits.setSpan(p, "a", "c1", 1, 2)
        assertEquals(2, one.panels[0].widgets[0].spanColumns)
        assertEquals(2, one.panels[0].widgets[0].spanRows)
        assertEquals(2 to 2, ProjectEdits.nextSize(2, 1, 3, minColumns = 2))
        assertEquals(2 to 1, ProjectEdits.nextSize(2, 2, 3, minColumns = 2))
        assertEquals(2, ProjectEdits.resizedSpan(100f, 100f, -80f, 0f, 100f, 100f, 8f, 3, minColumns = 2).first)
    }

    @Test fun idsStayUniqueAgainstCurveSeriesPointIds() {
        val p = Project("default", "x", listOf(Panel("a", "A", 2, listOf(curveWidget("w-aa", "p-aa", "p-bb")))))
        val seq = sequenceOf("aa", "bb", "cc").iterator()
        val (wid, pid) = ProjectEdits.newIds(p) { seq.next() }
        assertEquals("w-cc" to "p-cc", wid to pid)
        val seq2 = sequenceOf("aa", "dd").iterator()
        assertEquals("p-dd", ProjectEdits.newPointId(p, setOf("p-xx")) { seq2.next() })
        val seq3 = sequenceOf("xx", "ee").iterator()
        assertEquals("extraUsed 也要避开", "p-ee", ProjectEdits.newPointId(p, setOf("p-xx")) { seq3.next() })
    }

    @Test fun usablePointsIncludeEverySeriesAndSkipUnusableCurves() {
        val broken = Widget("bad", WidgetKinds.CURVE, curve = CurveConfig(emptyList()))
        val p = Project("default", "x", listOf(Panel("a", "A", 2, listOf(curveWidget("c1", "s1", "s2"), broken, w("1")))))
        assertEquals(listOf("s1", "s2", "p-1"), p.usablePoints.map { it.id })
        assertTrue(!broken.isUsable)
    }
}
