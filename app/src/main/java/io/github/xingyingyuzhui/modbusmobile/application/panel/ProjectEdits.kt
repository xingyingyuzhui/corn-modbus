package io.github.xingyingyuzhui.modbusmobile.application.panel

import java.util.UUID
import kotlin.math.roundToInt

/**
 * 面板编辑的纯函数：都返回新的 [Project]，不动原来的；找不到目标就原样返回。
 * 读不懂的组件（[Widget.raw]）也在列表里，排序 / 保存都原样保留，只是界面不显示它们。
 */
object ProjectEdits {
    const val MAX_NAME = 24
    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 4

    fun cleanName(input: String): String = input.take(MAX_NAME)

    fun isValidName(name: String): Boolean = name.isNotBlank()

    private inline fun Project.mapPanel(panelId: String, transform: (Panel) -> Panel): Project {
        if (panels.none { it.id == panelId }) return this
        return copy(panels = panels.map { if (it.id == panelId) transform(it) else it })
    }

    fun renamePanel(project: Project, panelId: String, name: String): Project =
        project.mapPanel(panelId) { it.copy(name = cleanName(name)) }

    fun setColumns(project: Project, panelId: String, columns: Int): Project =
        project.mapPanel(panelId) { it.copy(columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)) }

    /** 把 [widgetId] 挪到 [targetWidgetId] 当前的位置（拖到谁身上就占谁的位置）。 */
    fun moveWidget(project: Project, panelId: String, widgetId: String, targetWidgetId: String): Project =
        project.mapPanel(panelId) { panel ->
            val list = panel.widgets.toMutableList()
            val from = list.indexOfFirst { it.id == widgetId }
            val to = list.indexOfFirst { it.id == targetWidgetId }
            if (from < 0 || to < 0 || from == to) return@mapPanel panel
            list.add(to, list.removeAt(from))
            panel.copy(widgets = list)
        }

    fun setSpan(project: Project, panelId: String, widgetId: String, columns: Int, rows: Int): Project =
        project.mapPanel(panelId) { panel ->
            panel.copy(
                widgets = panel.widgets.map {
                    if (it.id == widgetId && it.raw == null) it.copy(spanColumns = columns.coerceIn(it.minColumns, 2), spanRows = rows.coerceIn(1, 2)) else it
                },
            )
        }

    fun deleteWidget(project: Project, panelId: String, widgetId: String): Project =
        project.mapPanel(panelId) { panel -> panel.copy(widgets = panel.widgets.filterNot { it.id == widgetId && it.raw == null }) }

    /**
     * 删掉面板里界面上看不到的组件：读不懂的（[Widget.raw]，更新版本才有的类型 / 字段不合法）和配置不完整的
     * （没有点位、曲线没有系列、开关 / 按钮配置自相矛盾）。编辑页不显示它们，[deleteWidget] 也删不到，只能从这里清。
     */
    fun deleteHidden(project: Project, panelId: String): Project =
        project.mapPanel(panelId) { panel -> panel.copy(widgets = panel.widgets.filter { it.isUsable }) }

    /** 新面板叫“面板 N”（N 取没被占用的最小正整数），加在最后。返回新工程和新面板 id。 */
    fun addPanel(project: Project, newId: () -> String = { UUID.randomUUID().toString().take(8) }): Pair<Project, String> {
        var n = 1
        while (project.panels.any { it.name == "面板 $n" }) n++
        var id = newId()
        while (project.panels.any { it.id == id }) id = newId()
        return project.copy(panels = project.panels + Panel(id, "面板 $n")) to id
    }

    /** 添加到面板末尾；面板不存在时原样返回。 */
    fun addWidget(project: Project, panelId: String, widget: Widget): Project =
        project.mapPanel(panelId) { it.copy(widgets = it.widgets + widget) }

    /** 按 id 替换组件（编辑已有组件）；读不懂的组件（raw）不替换。 */
    fun replaceWidget(project: Project, panelId: String, widget: Widget): Project =
        project.mapPanel(panelId) { panel ->
            panel.copy(widgets = panel.widgets.map { if (it.id == widget.id && it.raw == null) widget else it })
        }

    /** 全工程里没被占用的组件 id / 点位 id（成对生成：w-xxxxxxxx / p-xxxxxxxx）。 */
    fun newIds(project: Project, random: () -> String = { UUID.randomUUID().toString().take(8) }): Pair<String, String> {
        val usedWidgets = project.panels.flatMap { p -> p.widgets.map { it.id } }.toSet()
        val usedPoints = project.panels.flatMap { p -> p.widgets.flatMap { w -> w.points.map { it.id } } }.toSet()
        while (true) {
            val suffix = random()
            val widgetId = "w-$suffix"
            val pointId = "p-$suffix"
            if (widgetId !in usedWidgets && pointId !in usedPoints) return widgetId to pointId
        }
    }

    /** 全工程（含 [extraUsed]）里没被占用的点位 id（曲线加系列时用）。 */
    fun newPointId(project: Project, extraUsed: Set<String> = emptySet(), random: () -> String = { UUID.randomUUID().toString().take(8) }): String {
        val used = project.panels.flatMap { p -> p.widgets.flatMap { w -> w.points.map { it.id } } }.toSet() + extraUsed
        while (true) {
            val id = "p-${random()}"
            if (id !in used) return id
        }
    }

    /** 至少留一个面板：只剩一个时拒绝（原样返回）。 */
    fun deletePanel(project: Project, panelId: String): Project =
        if (project.panels.size <= 1 || project.panels.none { it.id == panelId }) project else project.copy(panels = project.panels.filterNot { it.id == panelId })

    /** 点一下右下角把手：1×1 → 2×1 → 2×2 → 1×2 → 1×1；只有 1 列（不可能）或列数不够 2 时跳过跨列的项。 */
    /** 编辑工具条里可选的尺寸（列 × 行）：受面板列数和组件最小列数限制（曲线至少 2 列）。 */
    fun sizes(panelColumns: Int, minColumns: Int = 1): List<Pair<Int, Int>> =
        listOf(1 to 1, 2 to 1, 2 to 2, 1 to 2).filter { it.first <= panelColumns && it.first >= minColumns }

    fun nextSize(columns: Int, rows: Int, panelColumns: Int, minColumns: Int = 1): Pair<Int, Int> {
        val cycle = listOf(1 to 1, 2 to 1, 2 to 2, 1 to 2).filter { it.first <= panelColumns && it.first >= minColumns }
        val index = cycle.indexOf(columns to rows)
        return cycle[(if (index < 0) 0 else index + 1) % cycle.size]
    }

    /**
     * 拖把手改尺寸：按手指拖到的位置换算成跨几列 / 几行（四舍五入到最近的格数，夹在 1..2 和面板列数内）。
     * [cellWidth] 一列的宽，[rowHeight] 一行（单行卡）的高，[gap] 间距；[width]/[height] 是卡片当前尺寸，[dx]/[dy] 是拖动量（像素）。
     */
    fun resizedSpan(
        width: Float,
        height: Float,
        dx: Float,
        dy: Float,
        cellWidth: Float,
        rowHeight: Float,
        gap: Float,
        panelColumns: Int,
        minColumns: Int = 1,
    ): Pair<Int, Int> {
        val cols = (((width + dx + gap) / (cellWidth + gap)).roundToInt()).coerceIn(minColumns, minOf(2, panelColumns))
        val rows = (((height + dy + gap) / (rowHeight + gap)).roundToInt()).coerceIn(1, 2)
        return cols to rows
    }
}
