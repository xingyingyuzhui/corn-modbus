package io.github.xingyingyuzhui.modbusmobile.application.panel

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea

/*
 * 添加 / 编辑组件向导的表单（纯 Kotlin）。所有输入框都是字符串（用户可能正输到一半），
 * [WidgetForm.build] 一次性校验并产出 [Widget]；界面只负责显示 [FormError]。
 */

enum class FormField { NAME, UNIT, ADDRESS, BIT, ASCII, SCALE, OFFSET, DECIMALS, RANGE_MIN, RANGE_MAX, POLL, THRESHOLD, STATE, WRITE_VALUE, ROWS }

enum class FormError {
    REQUIRED, NOT_A_NUMBER, NOT_INTEGER, OUT_OF_RANGE, ZERO_SCALE, RANGE_ORDER, DUPLICATE, SPAN_OVERFLOW, TOO_MANY,
    /** 曲线：时间窗口比内存保留还长。 */
    EXCEEDS_RETENTION,
}

/** 阈值方向：≥ = 区间 [值, ∞)；< = 区间 (−∞, 值)。 */
enum class ThresholdOp { GE, LT }

data class ThresholdRow(val op: ThresholdOp = ThresholdOp.GE, val value: String = "", val level: ThresholdLevel = ThresholdLevel.WARN)

data class StateRow(val value: String = "", val label: String = "", val tone: StateTone = StateTone.BLUE)

class FormResult(
    val widget: Widget?,
    val errors: Map<FormField, FormError>,
    /** 出错的阈值行 / 映射行下标。 */
    val badThresholds: Set<Int> = emptySet(),
    val badStates: Set<Int> = emptySet(),
) {
    val ok: Boolean get() = widget != null && errors.isEmpty()
}

data class WidgetForm(
    val kind: String = WidgetKinds.NUMBER,
    val name: String = "",
    val unitId: String = "1",
    val area: RegisterArea = RegisterArea.HOLDING_REGISTER,
    val address: String = "0",
    val type: DataType = DataType.U16,
    val order: ByteWordOrder = ByteWordOrder.ABCD,
    val bit: String = "0",
    val asciiRegisters: String = "1",
    val scale: String = "1",
    val offset: String = "0",
    val decimals: String = "",
    val unitLabel: String = "",
    val rangeMin: String = "",
    val rangeMax: String = "",
    val thresholds: List<ThresholdRow> = emptyList(),
    val pollMs: String = PointDefinition.DEFAULT_POLL_MS.toString(),
    val onLabel: String = "",
    val offLabel: String = "",
    val states: List<StateRow> = emptyList(),
    val spanColumns: Int = 1,
    val spanRows: Int = 1,
    /** 按钮：卡片上的文字（可空 = “写入”）和预设值（线圈 1 / 0，寄存器为数值）。 */
    val buttonLabel: String = "",
    val writeValue: String = "",
    /** 寄存器表：行数（1..16）和“允许在面板上修改”（默认关）。 */
    val tableRows: String = "4",
    val tableWritable: Boolean = false,
    /** 写入功能码：强制 FC15 / FC16（见 [Widget.forceMultiple]）。 */
    val forceMultiple: Boolean = false,
) {
    /** 按钮可以选 Unit 0（广播）；其余组件要读状态，必须 1..247。 */
    val allowsBroadcast: Boolean get() = kind == WidgetKinds.BUTTON
    val isWriter: Boolean get() = kind in WidgetKinds.writable
    /** 按钮不读设备，没有轮询周期。 */
    val usesPoll: Boolean get() = kind != WidgetKinds.BUTTON
    val isTable: Boolean get() = kind == WidgetKinds.REGISTER_TABLE
    val usesConversion: Boolean get() = kind == WidgetKinds.NUMBER || kind == WidgetKinds.PROGRESS || kind == WidgetKinds.GAUGE || isTable
    val usesRange: Boolean get() = kind == WidgetKinds.PROGRESS || kind == WidgetKinds.GAUGE
    val usesThresholds: Boolean get() = kind == WidgetKinds.NUMBER || kind == WidgetKinds.PROGRESS || kind == WidgetKinds.GAUGE
    val usesUnitLabel: Boolean get() = kind == WidgetKinds.NUMBER || kind == WidgetKinds.PROGRESS || kind == WidgetKinds.GAUGE || isTable

    /** 输入类区域只读；寄存器表只有线圈 / 保持寄存器才能“允许修改”。 */
    val canAllowWrite: Boolean get() = isTable && (area == RegisterArea.COIL || area == RegisterArea.HOLDING_REGISTER)

    /** 会写设备的组件（开关 / 按钮 / 勾了“允许修改”的寄存器表）可以选写入功能码。 */
    val writesDevice: Boolean get() = isWriter || (isTable && tableWritable && canAllowWrite)

    /** 写一次只有一个线圈 / 一个寄存器（多字类型本来就是 FC16，不用选）。 */
    val singleValueWrite: Boolean get() = area == RegisterArea.COIL || (type.registers ?: 1) == 1

    /** 这一类组件在当前区域下能选的数据类型。 */
    fun allowedTypes(): List<DataType> = allowedTypes(kind, area)

    fun withArea(value: RegisterArea): WidgetForm {
        val next = copy(area = value).let { if (it.canAllowWrite) it else it.copy(tableWritable = false) }
        return if (type in next.allowedTypes()) next else next.copy(type = defaultType(kind, value))
    }

    fun withType(value: DataType): WidgetForm {
        if (value !in allowedTypes()) return this
        // 字节 / 字序只对 32 / 64 位有意义：换成别的类型就回到默认，避免留着看不见的设置
        return copy(type = value, order = if ((value.registers ?: 1) >= 2) order else ByteWordOrder.ABCD)
    }

    /**
     * 向导第 2 步「数据」用：显示类字段（名称、小数位、量程、阈值、状态映射，在第 3 步「显示」里填）没填好时换成占位，
     * 只校验“读什么 / 写什么”。实时预览也用它，所以没起名字也能先看到读数。
     */
    fun forPreview(): WidgetForm {
        val okThresholds = thresholds.filter { it.value.trim().toDoubleOrNull()?.isFinite() == true }
        val seen = HashSet<Long>()
        val okStates = states.filter { row ->
            val v = row.value.trim().toLongOrNull()
            v != null && row.label.isNotBlank() && seen.add(v)
        }.take(MAX_STATES)
        val min = rangeMin.trim().toDoubleOrNull()
        val max = rangeMax.trim().toDoubleOrNull()
        val rangeOk = (rangeMin.isBlank() || min?.isFinite() == true) && (rangeMax.isBlank() || max?.isFinite() == true) &&
            (min == null || max == null || min < max)
        return copy(
            name = name.ifBlank { PREVIEW_NAME },
            decimals = decimals.trim().takeIf { it.isEmpty() || it.toIntOrNull() in 0..10 } ?: "",
            rangeMin = if (rangeOk) rangeMin else "",
            rangeMax = if (rangeOk) rangeMax else "",
            thresholds = okThresholds,
            states = if (kind == WidgetKinds.STATUS_TEXT && okStates.isEmpty()) listOf(StateRow(Long.MIN_VALUE.toString(), PREVIEW_NAME)) else okStates,
        )
    }

    /** 第 2 步「数据」的校验结果：显示类字段不算。 */
    fun buildData(widgetId: String, pointId: String): FormResult = forPreview().build(widgetId, pointId)

    fun build(widgetId: String, pointId: String): FormResult {
        val errors = LinkedHashMap<FormField, FormError>()
        val name = name.trim().take(MAX_NAME)
        if (name.isEmpty()) errors[FormField.NAME] = FormError.REQUIRED

        fun int(text: String, field: FormField, range: IntRange): Int? {
            val t = text.trim()
            if (t.isEmpty()) { errors[field] = FormError.REQUIRED; return null }
            val v = t.toIntOrNull()
            if (v == null) { errors[field] = if (t.toDoubleOrNull() != null) FormError.NOT_INTEGER else FormError.NOT_A_NUMBER; return null }
            if (v !in range) { errors[field] = FormError.OUT_OF_RANGE; return null }
            return v
        }

        fun number(text: String, field: FormField, required: Boolean = true): Double? {
            val t = text.trim()
            if (t.isEmpty()) { if (required) errors[field] = FormError.REQUIRED; return null }
            val v = t.toDoubleOrNull()
            if (v == null || !v.isFinite()) { errors[field] = FormError.NOT_A_NUMBER; return null }
            return v
        }

        val unit = int(unitId, FormField.UNIT, if (allowsBroadcast) 0..247 else 1..247)
        val addr = int(address, FormField.ADDRESS, 0..65535)
        val bitIndex = if (type == DataType.BIT) int(bit, FormField.BIT, 0..15) else 0
        val ascii = if (type == DataType.ASCII) int(asciiRegisters, FormField.ASCII, 1..125) else 1
        val poll = int(pollMs, FormField.POLL, PointDefinition.MIN_POLL_MS..PointDefinition.MAX_POLL_MS)

        var conversion = Conversion()
        if (usesConversion) {
            val s = number(scale, FormField.SCALE)
            val o = number(offset, FormField.OFFSET)
            if (s == 0.0) errors[FormField.SCALE] = FormError.ZERO_SCALE
            val d = decimals.trim().let { t ->
                if (t.isEmpty()) null else int(t, FormField.DECIMALS, 0..10)
            }
            if (s != null && o != null) conversion = Conversion(s, o, d)
        }

        var min: Double? = null
        var max: Double? = null
        if (usesRange) {
            min = number(rangeMin, FormField.RANGE_MIN, required = false)
            max = number(rangeMax, FormField.RANGE_MAX, required = false)
            if (min != null && max != null && !(min < max)) errors[FormField.RANGE_MAX] = FormError.RANGE_ORDER
        }

        val bands = ArrayList<ThresholdBand>()
        val badThresholds = LinkedHashSet<Int>()
        if (usesThresholds) {
            thresholds.forEachIndexed { i, row ->
                val v = row.value.trim().toDoubleOrNull()
                if (v == null || !v.isFinite()) {
                    badThresholds += i
                } else {
                    bands += if (row.op == ThresholdOp.GE) ThresholdBand(row.level, min = v) else ThresholdBand(row.level, max = v)
                }
            }
            if (badThresholds.isNotEmpty()) errors[FormField.THRESHOLD] = FormError.NOT_A_NUMBER
        }

        val rows = if (isTable) int(tableRows, FormField.ROWS, 1..WidgetWrites.MAX_TABLE_ROWS) else 0
        var preset: Double? = null
        if (kind == WidgetKinds.BUTTON) {
            preset = number(writeValue, FormField.WRITE_VALUE)
            if (preset != null) {
                val probe = PointDefinition(id = "p", name = "p", unitId = 1, area = area, address = 0, type = type, order = order)
                when {
                    area == RegisterArea.COIL -> if (preset != 0.0 && preset != 1.0) errors[FormField.WRITE_VALUE] = FormError.OUT_OF_RANGE
                    !type.isFloat && preset != Math.rint(preset) -> errors[FormField.WRITE_VALUE] = FormError.NOT_INTEGER
                    WidgetWrites.buttonWords(probe, preset) == null -> errors[FormField.WRITE_VALUE] = FormError.OUT_OF_RANGE
                }
            }
        }

        val mappings = ArrayList<StateMapping>()
        val badStates = LinkedHashSet<Int>()
        if (kind == WidgetKinds.STATUS_TEXT) {
            val seen = HashSet<Long>()
            states.forEachIndexed { i, row ->
                val v = row.value.trim().toLongOrNull()
                val label = row.label.trim()
                if (v == null || label.isEmpty() || !seen.add(v)) badStates += i else mappings += StateMapping(v, label, row.tone)
            }
            if (states.isEmpty()) errors[FormField.STATE] = FormError.REQUIRED
            else if (badStates.isNotEmpty()) errors[FormField.STATE] = if (states.map { it.value.trim() }.distinct().size < states.size) FormError.DUPLICATE else FormError.REQUIRED
            if (mappings.size > MAX_STATES) errors[FormField.STATE] = FormError.TOO_MANY
        }

        if (errors.isNotEmpty() || unit == null || addr == null || bitIndex == null || ascii == null || poll == null || rows == null) {
            return FormResult(null, errors, badThresholds, badStates)
        }
        val point = PointDefinition(
            id = pointId, name = name, unitId = unit, area = area, address = addr, type = type, order = order,
            bit = bitIndex, asciiRegisters = ascii, conversion = conversion,
            unitLabel = if (usesUnitLabel) unitLabel.trim().take(MAX_UNIT_LABEL) else "",
            rangeMin = min, rangeMax = max, thresholds = bands, pollMs = poll,
        )
        for (issue in point.validate(allowBroadcast = allowsBroadcast)) {
            when (issue) {
                PointIssue.SPAN_OVERFLOW -> errors[FormField.ADDRESS] = FormError.SPAN_OVERFLOW
                PointIssue.TOO_MANY_REGISTERS -> errors[FormField.ASCII] = FormError.TOO_MANY
                else -> Unit
            }
        }
        if (isTable && errors.isEmpty() && addr + rows * point.quantity > 65536) errors[FormField.ADDRESS] = FormError.SPAN_OVERFLOW
        if (errors.isNotEmpty()) return FormResult(null, errors)
        val widget = Widget(
            id = widgetId, kind = kind, title = "", point = point,
            spanColumns = if (isTable) 2 else spanColumns.coerceIn(1, 2), spanRows = if (isTable) 1 else spanRows.coerceIn(1, 2),
            onLabel = if (kind == WidgetKinds.LAMP || kind == WidgetKinds.SWITCH) onLabel.trim() else "",
            offLabel = if (kind == WidgetKinds.LAMP || kind == WidgetKinds.SWITCH) offLabel.trim() else "",
            states = mappings,
            buttonLabel = if (kind == WidgetKinds.BUTTON) buttonLabel.trim().take(MAX_BUTTON_LABEL) else "",
            writeValue = if (kind == WidgetKinds.BUTTON) preset else null,
            tableRows = if (isTable) rows else 0,
            tableWritable = isTable && tableWritable && point.isWritable,
            forceMultiple = forceMultiple && writesDevice && singleValueWrite,
        )
        WidgetWrites.problem(widget)?.let { return FormResult(null, mapOf(FormField.ADDRESS to FormError.OUT_OF_RANGE)) }
        return FormResult(widget, emptyMap())
    }

    companion object {
        const val MAX_NAME = 32
        const val MAX_UNIT_LABEL = 8
        const val MAX_BUTTON_LABEL = 12
        const val MAX_STATES = 32

        /** 预览占位名（名称在第 3 步才填）。 */
        const val PREVIEW_NAME = "—"

        /** 第 3 步「显示」管的字段；其余字段（读什么 / 写什么）在第 2 步「数据」。 */
        val DISPLAY_FIELDS: Set<FormField> = setOf(
            FormField.NAME, FormField.DECIMALS, FormField.RANGE_MIN, FormField.RANGE_MAX, FormField.THRESHOLD, FormField.STATE,
        )

        private val numeric = listOf(
            DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.F32, DataType.U64, DataType.S64, DataType.F64,
            DataType.BCD16, DataType.BCD32,
        )

        fun isBitArea(area: RegisterArea) = area == RegisterArea.COIL || area == RegisterArea.DISCRETE_INPUT

        fun allowedTypes(kind: String, area: RegisterArea): List<DataType> = when {
            isBitArea(area) -> listOf(DataType.BOOL)
            kind == WidgetKinds.SWITCH -> listOf(DataType.BIT)
            kind == WidgetKinds.BUTTON -> WidgetWrites.buttonTypes
            kind == WidgetKinds.REGISTER_TABLE -> WidgetWrites.tableTypes
            kind == WidgetKinds.LAMP -> listOf(DataType.BIT, DataType.U16, DataType.S16)
            kind == WidgetKinds.STATUS_TEXT -> listOf(DataType.U16, DataType.S16, DataType.U32, DataType.S32, DataType.BCD16, DataType.BCD32)
            kind == WidgetKinds.PROGRESS || kind == WidgetKinds.GAUGE -> numeric
            else -> numeric + listOf(DataType.BIT, DataType.ASCII)
        }

        fun defaultType(kind: String, area: RegisterArea): DataType =
            if (isBitArea(area)) DataType.BOOL else if (kind == WidgetKinds.LAMP || kind == WidgetKinds.SWITCH) DataType.BIT else DataType.U16

        /** 选了类型后的初始表单：灯 / 状态文本带上合理的默认。 */
        fun forKind(kind: String): WidgetForm = when (kind) {
            WidgetKinds.LAMP -> WidgetForm(kind = kind, type = DataType.BIT, onLabel = "", offLabel = "")
            WidgetKinds.PROGRESS -> WidgetForm(kind = kind, rangeMin = "0", rangeMax = "100", unitLabel = "%")
            WidgetKinds.GAUGE -> WidgetForm(kind = kind, rangeMin = "0", rangeMax = "100")
            WidgetKinds.SWITCH -> WidgetForm(kind = kind, area = RegisterArea.COIL, type = DataType.BOOL)
            WidgetKinds.BUTTON -> WidgetForm(kind = kind, area = RegisterArea.COIL, type = DataType.BOOL, writeValue = "1")
            WidgetKinds.REGISTER_TABLE -> WidgetForm(kind = kind, spanColumns = 2, tableRows = "4")
            else -> WidgetForm(kind = kind)
        }

        fun fromWidget(widget: Widget): WidgetForm? {
            val p = widget.point ?: return null
            if (!widget.isUsable) return null
            fun d(v: Double?) = v?.let { ValueCodec.format(it, null) }.orEmpty()
            return WidgetForm(
                kind = widget.kind, name = widget.title.ifBlank { p.name }, unitId = p.unitId.toString(), area = p.area,
                address = p.address.toString(), type = p.type, order = p.order, bit = p.bit.toString(),
                asciiRegisters = p.asciiRegisters.toString(), scale = d(p.conversion.scale), offset = d(p.conversion.offset),
                decimals = p.conversion.decimals?.toString().orEmpty(), unitLabel = p.unitLabel,
                rangeMin = d(p.rangeMin), rangeMax = d(p.rangeMax),
                thresholds = p.thresholds.mapNotNull { b ->
                    when {
                        b.min != null && b.max == null -> ThresholdRow(ThresholdOp.GE, d(b.min), b.level)
                        b.max != null && b.min == null -> ThresholdRow(ThresholdOp.LT, d(b.max), b.level)
                        else -> null
                    }
                },
                pollMs = p.pollMs.toString(), onLabel = widget.onLabel, offLabel = widget.offLabel,
                states = widget.states.map { StateRow(it.value.toString(), it.label, it.tone) },
                spanColumns = widget.spanColumns, spanRows = widget.spanRows,
                buttonLabel = widget.buttonLabel, writeValue = widget.writeValue?.let { ValueCodec.format(it, null) }.orEmpty(),
                tableRows = if (widget.kind == WidgetKinds.REGISTER_TABLE) widget.tableRows.toString() else "4",
                tableWritable = widget.tableWritable,
                forceMultiple = widget.forceMultiple,
            )
        }
    }
}
