package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 工程文件的 JSON 读写（org.json，无新依赖）。格式见 docs/project-format.md。 */
object ProjectJson {

    class Parsed(val project: Project, val version: Int, val warnings: List<String>)

    class FormatException(message: String) : Exception(message)

    fun encode(project: Project): String {
        val root = JSONObject()
        root.put("version", Project.FORMAT_VERSION)
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("panels", JSONArray().also { arr -> project.panels.forEach { arr.put(panel(it)) } })
        return root.toString(2)
    }

    /** 解析。顶层坏了（不是 JSON / 缺 version）抛 [FormatException]；单个组件坏了只记警告并原样保留。 */
    fun decode(text: String): Parsed {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw FormatException("不是有效的 JSON")
        }
        if (!root.has("version") || root.optInt("version", -1) < 1) throw FormatException("缺少版本号")
        val version = root.getInt("version")
        val warnings = mutableListOf<String>()
        val panels = mutableListOf<Panel>()
        val panelArray = root.optJSONArray("panels") ?: JSONArray()
        for (i in 0 until panelArray.length()) {
            val obj = panelArray.optJSONObject(i)
            if (obj == null) {
                warnings += "第 ${i + 1} 个面板不是对象，已忽略"
                continue
            }
            panels += decodePanel(obj, i, warnings)
        }
        val project = Project(
            id = root.optString("id", Project.DEFAULT_ID).ifBlank { Project.DEFAULT_ID },
            name = root.optString("name", "").ifBlank { "我的工程" },
            panels = panels,
        )
        return Parsed(project, version, warnings)
    }

    private fun panel(p: Panel) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("columns", p.columns)
        put("widgets", JSONArray().also { arr -> p.widgets.forEach { arr.put(widget(it)) } })
    }

    private fun widget(w: Widget): JSONObject {
        w.raw?.let { return JSONObject(it) }
        return JSONObject().apply {
            put("id", w.id)
            put("kind", w.kind)
            put("title", w.title)
            put("spanColumns", w.spanColumns)
            put("spanRows", w.spanRows)
            if (w.onLabel.isNotEmpty()) put("onLabel", w.onLabel)
            if (w.offLabel.isNotEmpty()) put("offLabel", w.offLabel)
            if (w.buttonLabel.isNotEmpty()) put("buttonLabel", w.buttonLabel)
            w.writeValue?.let { put("writeValue", it) }
            if (w.kind == WidgetKinds.REGISTER_TABLE) {
                put("tableRows", w.tableRows)
                put("tableWritable", w.tableWritable)
            }
            if (w.forceMultiple) put("forceMultiple", true)
            if (w.states.isNotEmpty()) {
                put(
                    "states",
                    JSONArray().also { arr ->
                        w.states.forEach {
                            arr.put(JSONObject().put("value", it.value).put("label", it.label).put("tone", it.tone.name))
                        }
                    },
                )
            }
            w.point?.let { put("point", point(it)) }
            w.curve?.let { put("curve", curve(it)) }
        }
    }

    private fun axis(a: AxisRange) = JSONObject().apply {
        put("auto", a.auto)
        a.min?.let { put("min", it) }
        a.max?.let { put("max", it) }
    }

    private fun curve(c: CurveConfig) = JSONObject().apply {
        put("windowSec", c.windowSec)
        put("sampling", c.sampling.name)
        put("retentionMinutes", c.retentionMinutes)
        put("maxPoints", c.maxPoints)
        put("left", axis(c.left))
        put("right", axis(c.right))
        put("grid", c.grid)
        put("legend", c.legend.name)
        put("gaps", c.gaps.name)
        put("cursor", c.cursor)
        put(
            "series",
            JSONArray().also { arr ->
                c.series.forEach { s ->
                    arr.put(
                        JSONObject().apply {
                            put("point", point(s.point))
                            put("color", s.colorIndex)
                            put("lineStyle", s.lineStyle.name)
                            put("width", s.width)
                            put("axis", s.axis.name)
                            put("showPoints", s.showPoints)
                            put("fill", s.fill)
                            put("step", s.step)
                        },
                    )
                }
            },
        )
    }

    private fun point(p: PointDefinition) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("unitId", p.unitId)
        put("area", p.area.name)
        put("address", p.address)
        put("type", p.type.name)
        put("order", p.order.name)
        put("bit", p.bit)
        put("asciiRegisters", p.asciiRegisters)
        put("scale", p.conversion.scale)
        put("offset", p.conversion.offset)
        p.conversion.decimals?.let { put("decimals", it) }
        put("unitLabel", p.unitLabel)
        put("pollMs", p.pollMs)
        p.rangeMin?.let { put("rangeMin", it) }
        p.rangeMax?.let { put("rangeMax", it) }
        put(
            "thresholds",
            JSONArray().also { arr ->
                p.thresholds.forEach {
                    arr.put(
                        JSONObject().apply {
                            put("level", it.level.name)
                            it.min?.let { v -> put("min", v) }
                            it.max?.let { v -> put("max", v) }
                        },
                    )
                }
            },
        )
    }

    private fun decodePanel(obj: JSONObject, index: Int, warnings: MutableList<String>): Panel {
        val widgets = mutableListOf<Widget>()
        val array = obj.optJSONArray("widgets") ?: JSONArray()
        for (i in 0 until array.length()) {
            val w = array.optJSONObject(i)
            if (w == null) {
                warnings += "面板 ${index + 1} 的第 ${i + 1} 个组件不是对象，已忽略"
                continue
            }
            widgets += decodeWidget(w, warnings)
        }
        return Panel(
            id = obj.optString("id", "").ifBlank { "panel-${index + 1}" },
            name = obj.optString("name", "").ifBlank { "面板 ${index + 1}" },
            columns = obj.optInt("columns", 2).coerceIn(2, 4),
            widgets = widgets,
        )
    }

    private fun decodeWidget(obj: JSONObject, warnings: MutableList<String>): Widget {
        val id = obj.optString("id", "")
        val kind = obj.optString("kind", "")
        fun preserved(reason: String): Widget {
            warnings += "组件“${obj.optString("title", id)}”$reason，已保留但不显示"
            return Widget(id = id.ifBlank { "?" }, kind = kind, raw = obj.toString())
        }
        if (id.isBlank() || kind.isBlank()) return preserved("缺少 id 或类型")
        if (kind !in WidgetKinds.known) return preserved("的类型“$kind”当前版本不认识")
        if (kind == WidgetKinds.CURVE) {
            val curveObj = obj.optJSONObject("curve") ?: return preserved("缺少曲线配置")
            val curve = decodeCurve(curveObj) ?: return preserved("的曲线配置无法读取")
            val problems = curve.issues()
            if (problems.isNotEmpty()) return preserved("的曲线配置不合法（${problems.joinToString()}）")
            return Widget(
                id = id, kind = kind, title = obj.optString("title", ""), curve = curve,
                spanColumns = obj.optInt("spanColumns", 2).coerceIn(2, 2),
                spanRows = obj.optInt("spanRows", 1).coerceIn(1, 2),
            )
        }
        val pointObj = obj.optJSONObject("point") ?: return preserved("缺少点位")
        val point = decodePoint(pointObj) ?: return preserved("的点位配置无法读取")
        val issues = point.validate(allowBroadcast = kind == WidgetKinds.BUTTON)
        if (issues.isNotEmpty()) return preserved("的点位配置不合法（${issues.joinToString()}）")
        val widget = Widget(
            id = id,
            kind = kind,
            title = obj.optString("title", ""),
            point = point,
            spanColumns = obj.optInt("spanColumns", if (kind == WidgetKinds.REGISTER_TABLE) 2 else 1).coerceIn(if (kind == WidgetKinds.REGISTER_TABLE) 2 else 1, 2),
            spanRows = obj.optInt("spanRows", 1).coerceIn(1, 2),
            onLabel = obj.optString("onLabel", ""),
            offLabel = obj.optString("offLabel", ""),
            states = decodeStates(obj, warnings),
            buttonLabel = obj.optString("buttonLabel", ""),
            writeValue = if (obj.has("writeValue") && !obj.isNull("writeValue")) obj.optDouble("writeValue", Double.NaN).takeIf { !it.isNaN() } else null,
            tableRows = if (kind == WidgetKinds.REGISTER_TABLE) obj.optInt("tableRows", 0) else 0,
            tableWritable = kind == WidgetKinds.REGISTER_TABLE && obj.optBoolean("tableWritable", false),
            forceMultiple = obj.optBoolean("forceMultiple", false),
        )
        // 开关 / 按钮的额外规则（开关必须读得到状态、按钮必须有合法的预设值）；不满足就原样保留，不让它在面板上出现一个写不对的控件
        WidgetWrites.problem(widget)?.let { return preserved("的配置不合法（$it）") }
        return widget
    }

    private fun decodeAxis(o: JSONObject?): AxisRange? {
        if (o == null) return AxisRange()
        fun d(key: String): Double? = if (o.has(key) && !o.isNull(key)) o.optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null
        return AxisRange(o.optBoolean("auto", true), d("min"), d("max"))
    }

    private fun decodeCurve(o: JSONObject): CurveConfig? {
        val seriesArr = o.optJSONArray("series") ?: return null
        val series = mutableListOf<CurveSeries>()
        for (i in 0 until seriesArr.length()) {
            val so = seriesArr.optJSONObject(i) ?: return null
            val point = decodePoint(so.optJSONObject("point") ?: return null) ?: return null
            series += CurveSeries(
                point = point,
                colorIndex = so.optInt("color", 0),
                lineStyle = (if (so.has("lineStyle")) enumOrNull<LineStyle>(so.optString("lineStyle")) ?: return null else LineStyle.SOLID),
                width = so.optInt("width", 2),
                axis = (if (so.has("axis")) enumOrNull<YAxis>(so.optString("axis")) ?: return null else YAxis.LEFT),
                showPoints = so.optBoolean("showPoints", false),
                fill = so.optBoolean("fill", false),
                step = so.optBoolean("step", false),
            )
        }
        return CurveConfig(
            series = series,
            windowSec = o.optInt("windowSec", 60),
            sampling = (if (o.has("sampling")) enumOrNull<SamplingMode>(o.optString("sampling")) ?: return null else SamplingMode.EVERY_SAMPLE),
            retentionMinutes = o.optInt("retentionMinutes", 60),
            maxPoints = o.optInt("maxPoints", CurveConfig.DEFAULT_MAX_POINTS),
            left = decodeAxis(o.optJSONObject("left")) ?: return null,
            right = decodeAxis(o.optJSONObject("right")) ?: return null,
            grid = o.optBoolean("grid", true),
            legend = (if (o.has("legend")) enumOrNull<LegendPosition>(o.optString("legend")) ?: return null else LegendPosition.TOP),
            gaps = (if (o.has("gaps")) enumOrNull<GapStyle>(o.optString("gaps")) ?: return null else GapStyle.HATCH),
            cursor = o.optBoolean("cursor", true),
        )
    }

    private fun decodeStates(obj: JSONObject, warnings: MutableList<String>): List<StateMapping> {
        val arr = obj.optJSONArray("states") ?: return emptyList()
        val result = mutableListOf<StateMapping>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null || !o.has("value") || o.optString("label", "").isEmpty()) {
                warnings += "组件“${obj.optString("title", obj.optString("id"))}”的第 ${i + 1} 条状态映射不完整，已忽略"
                continue
            }
            result += StateMapping(
                o.optLong("value"),
                o.optString("label"),
                enumOrNull<StateTone>(o.optString("tone")) ?: StateTone.BLUE,
            )
        }
        return result
    }

    private fun decodePoint(o: JSONObject): PointDefinition? {
        val area = enumOrNull<RegisterArea>(o.optString("area")) ?: return null
        val type = enumOrNull<DataType>(o.optString("type")) ?: return null
        val order = if (o.has("order")) enumOrNull<ByteWordOrder>(o.optString("order")) ?: return null else ByteWordOrder.ABCD
        if (!o.has("unitId") || !o.has("address")) return null
        fun double(key: String): Double? = if (o.has(key) && !o.isNull(key)) o.optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null
        val thresholds = mutableListOf<ThresholdBand>()
        val arr = o.optJSONArray("thresholds") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: return null
            val level = enumOrNull<ThresholdLevel>(t.optString("level")) ?: return null
            thresholds += ThresholdBand(
                level,
                if (t.has("min")) t.optDouble("min", Double.NaN).takeIf { !it.isNaN() } ?: return null else null,
                if (t.has("max")) t.optDouble("max", Double.NaN).takeIf { !it.isNaN() } ?: return null else null,
            )
        }
        return PointDefinition(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            unitId = o.optInt("unitId", -1),
            area = area,
            address = o.optInt("address", -1),
            type = type,
            order = order,
            bit = o.optInt("bit", 0),
            asciiRegisters = o.optInt("asciiRegisters", 1),
            conversion = Conversion(
                scale = double("scale") ?: 1.0,
                offset = double("offset") ?: 0.0,
                decimals = if (o.has("decimals") && !o.isNull("decimals")) o.optInt("decimals", -1) else null,
            ),
            unitLabel = o.optString("unitLabel", ""),
            pollMs = o.optInt("pollMs", PointDefinition.DEFAULT_POLL_MS),
            rangeMin = double("rangeMin"),
            rangeMax = double("rangeMax"),
            thresholds = thresholds,
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
