package io.github.xingyingyuzhui.modbusmobile.application.panel

/** 组件类型。文件里出现不认识的类型时原样保留（见 [Widget.raw]），不丢、不崩。 */
object WidgetKinds {
    const val NUMBER = "number"
    const val LAMP = "lamp"
    const val STATUS_TEXT = "status_text"
    const val PROGRESS = "progress"
    const val CURVE = "curve"
    const val GAUGE = "gauge"

    /** 可写组件（P8b）：开关 = 切换一个线圈 / 寄存器位；按钮 = 一键写入预设值。写设备永远经确认弹窗。 */
    const val SWITCH = "switch"
    const val BUTTON = "button"

    /** 寄存器表（P8b2）：一段连续地址按行显示；勾选“允许面板修改”后每行可以写（逐行确认）。 */
    const val REGISTER_TABLE = "register_table"
    val known = setOf(NUMBER, LAMP, STATUS_TEXT, PROGRESS, CURVE, GAUGE, SWITCH, BUTTON, REGISTER_TABLE)

    /** 一定会写设备的类型：编辑模式 / 向导预览里不可操作，从站模式下禁用（寄存器表只有勾了“允许修改”才会写）。 */
    val writable = setOf(SWITCH, BUTTON)
}

enum class StateTone { GREEN, AMBER, RED, BLUE, GRAY }

/** 状态文本组件的取值映射：原始整数值 → 文字 + 颜色。 */
data class StateMapping(val value: Long, val label: String, val tone: StateTone = StateTone.BLUE)

/**
 * 面板上的一个组件。[raw] 非空表示“这个组件现在读不懂”（类型是更新版本才有的、或字段不合法）：
 * 界面不渲染它，但保存时把 [raw] 原样写回，等换回新版本还能用。
 */
data class Widget(
    val id: String,
    val kind: String,
    val title: String = "",
    val point: PointDefinition? = null,
    /** 占几列 / 几行（1..2）。 */
    val spanColumns: Int = 1,
    val spanRows: Int = 1,
    /** 指示灯：为“开”/“关”时显示的文字，空 = 用默认的“开”/“关”。 */
    val onLabel: String = "",
    val offLabel: String = "",
    /** 状态文本：取值映射；没有匹配的值显示“未定义（值）”。 */
    val states: List<StateMapping> = emptyList(),
    /** 按钮：卡片上的按钮文字（空 = “写入”）。 */
    val buttonLabel: String = "",
    /** 按钮：要写入的预设值（线圈 1 / 0；寄存器为数值，按点位类型编码，不做缩放换算）。 */
    val writeValue: Double? = null,
    /** 寄存器表：行数（1..16，其它类型为 0）。第 i 行的地址 = 起始地址 + i × 每行占用个数。 */
    val tableRows: Int = 0,
    /** 寄存器表：允许在面板上改值（每行一个 ✎，仍然逐次确认）。默认关。 */
    val tableWritable: Boolean = false,
    /**
     * 写入功能码（开关 / 按钮 / 可写寄存器表）：false = 单个值用 FC05 / FC06（多字自动 FC16）；
     * true = 一律用 FC15 / FC16（有些设备不支持 FC05 / FC06）。和工作台的“强制 15/16”一样。
     */
    val forceMultiple: Boolean = false,
    /** 曲线组件的配置（[kind] == [WidgetKinds.CURVE] 时非空；此时 [point] 为空，点位在各系列里）。 */
    val curve: CurveConfig? = null,
    val raw: String? = null,
) {
    val isUsable: Boolean
        get() = raw == null && kind in WidgetKinds.known && when (kind) {
            WidgetKinds.CURVE -> curve != null && curve.series.isNotEmpty()
            else -> point != null && WidgetWrites.problem(this) == null
        }

    /** 这个组件要轮询的全部点位。按钮不读（它的点位只用来写），所以不进轮询。 */
    val points: List<PointDefinition>
        get() = when (kind) {
            WidgetKinds.CURVE -> curve?.series?.map { it.point }.orEmpty()
            WidgetKinds.BUTTON -> emptyList()
            WidgetKinds.REGISTER_TABLE -> tablePoints
            else -> listOfNotNull(point)
        }

    /** 寄存器表的每一行都是一个独立点位（id = “起始点位 id#行号”），所以复用同一套轮询 / 质量 / 换算。 */
    val tablePoints: List<PointDefinition>
        get() = if (kind != WidgetKinds.REGISTER_TABLE || point == null) emptyList() else List(tableRows) { rowPoint(it) }

    fun rowPoint(index: Int): PointDefinition {
        val base = requireNotNull(point)
        return base.copy(
            id = "${base.id}#$index",
            name = "${base.name} ${index + 1}",
            address = base.address + index * base.quantity,
            rangeMin = null, rangeMax = null, thresholds = emptyList(),
        )
    }
    /** 界面上显示的组件名：自定义标题 > 曲线的“系列名 / 系列名” > 点位名。 */
    val displayTitle: String
        get() = title.ifBlank { if (kind == WidgetKinds.CURVE) curve?.series?.joinToString(" / ") { it.point.name }.orEmpty() else point?.name.orEmpty() }

    /** 最少占几列（曲线至少 2 列才画得下图例和坐标）。 */
    val minColumns: Int get() = if (kind == WidgetKinds.CURVE || kind == WidgetKinds.REGISTER_TABLE) 2 else 1
}

data class Panel(
    val id: String,
    val name: String,
    /** 竖屏列数（2..4）。 */
    val columns: Int = 2,
    val widgets: List<Widget> = emptyList(),
)

data class Project(
    val id: String,
    val name: String,
    val panels: List<Panel> = emptyList(),
) {
    /** 所有点位（含重复引用），供轮询计划使用（P3）。 */
    val usablePoints: List<PointDefinition>
        get() = panels.flatMap { p -> p.widgets.filter { it.isUsable }.flatMap { it.points } }

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_ID = "default"
        fun empty(name: String = "我的工程") = Project(DEFAULT_ID, name)
    }
}
