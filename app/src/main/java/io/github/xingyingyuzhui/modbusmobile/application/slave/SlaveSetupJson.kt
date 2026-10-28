package io.github.xingyingyuzhui.modbusmobile.application.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.ExternalWritePolicy
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget
import io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 从站配置文件的 JSON（org.json）。格式见 docs/project-format.md 的“从站配置”一节。
 *
 * 读不懂的东西不丢：不认识的顶层字段、解析不了的规则 / Unit 都原样记在 [Parsed.preserved] 里，保存时逐字写回。
 */
object SlaveSetupJson {
    const val FORMAT_VERSION = 1

    class FormatException(message: String) : Exception(message)

    /** 原样保留、写回的内容。 */
    class Preserved(
        val topLevel: Map<String, Any> = emptyMap(),
        val units: List<String> = emptyList(),
        val rules: List<String> = emptyList(),
    ) {
        companion object {
            val None = Preserved()
        }
    }

    class Parsed(val setup: SlaveSetup, val version: Int, val preserved: Preserved, val warnings: List<String>)

    private val known = setOf("version", "units", "rules")

    fun encode(setup: SlaveSetup, preserved: Preserved = Preserved.None): String {
        val root = JSONObject()
        preserved.topLevel.forEach { (key, value) -> root.put(key, value) }
        root.put("version", FORMAT_VERSION)
        root.put(
            "units",
            JSONArray().also { array ->
                setup.units.forEach { array.put(unit(it)) }
                preserved.units.forEach { array.put(JSONObject(it)) }
            },
        )
        root.put(
            "rules",
            JSONArray().also { array ->
                setup.rules.forEach { array.put(rule(it)) }
                preserved.rules.forEach { array.put(JSONObject(it)) }
            },
        )
        return root.toString(2)
    }

    /** 顶层坏了抛 [FormatException]；单个 Unit / 规则坏了只记警告并原样保留。一个能用的 Unit 都没有时也抛。 */
    fun decode(text: String): Parsed {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw FormatException("不是有效的 JSON")
        }
        val version = root.optInt("version", -1)
        if (version < 1) throw FormatException("缺少版本号")
        val warnings = mutableListOf<String>()
        val units = mutableListOf<SlaveUnitSetup>()
        val keptUnits = mutableListOf<String>()
        val unitArray = root.optJSONArray("units") ?: throw FormatException("缺少 units")
        for (i in 0 until unitArray.length()) {
            val obj = unitArray.optJSONObject(i)
            val decoded = obj?.let { runCatching { decodeUnit(it) }.getOrNull() }
            when {
                decoded == null -> {
                    warnings += "第 ${i + 1} 个 Unit 读不懂，已原样保留"
                    keptUnits += (obj ?: JSONObject().put("raw", unitArray.opt(i))).toString()
                }
                units.any { it.unitId == decoded.unitId } -> {
                    warnings += "Unit ${decoded.unitId} 重复，后一个已原样保留"
                    keptUnits += obj.toString()
                }
                else -> units += decoded
            }
        }
        // "units": [] 是合法的（用户删光了 Unit）；有条目但一个都读不懂才算坏文件。
        if (units.isEmpty() && keptUnits.isNotEmpty()) throw FormatException("没有可用的 Unit")
        val rules = mutableListOf<SimulationRule>()
        val keptRules = mutableListOf<String>()
        val ruleArray = root.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until ruleArray.length()) {
            val obj = ruleArray.optJSONObject(i)
            val decoded = obj?.let { runCatching { decodeRule(it) }.getOrNull() }
            if (decoded == null || rules.any { it.id == decoded.id }) {
                warnings += "第 ${i + 1} 条模拟规则读不懂，已原样保留"
                keptRules += (obj ?: JSONObject().put("raw", ruleArray.opt(i))).toString()
            } else {
                rules += decoded
            }
        }
        val topLevel = root.keys().asSequence().filter { it !in known }.associateWith { root.get(it) }
        return Parsed(SlaveSetup(units, rules), version, Preserved(topLevel, keptUnits, keptRules), warnings)
    }

    private fun unit(u: SlaveUnitSetup) = JSONObject().apply {
        put("unitId", u.unitId)
        put(
            "regions",
            JSONArray().also { array ->
                u.regions.forEach { r ->
                    array.put(
                        JSONObject().apply {
                            put("area", r.area.name)
                            put("start", r.start)
                            put("values", JSONArray(r.values))
                        },
                    )
                }
            },
        )
    }

    private fun decodeUnit(o: JSONObject): SlaveUnitSetup {
        val regions = o.getJSONArray("regions")
        return SlaveUnitSetup(
            o.getInt("unitId"),
            (0 until regions.length()).map { index ->
                val r = regions.getJSONObject(index)
                val values = r.getJSONArray("values")
                SlaveRegion(
                    RegisterArea.valueOf(r.getString("area")),
                    r.getInt("start"),
                    (0 until values.length()).map { values.getInt(it) },
                )
            },
        )
    }

    private fun rule(r: SimulationRule) = JSONObject().apply {
        put("id", r.id)
        put("unitId", r.target.unitId)
        put("area", r.target.area.name)
        put("address", r.target.address)
        put("count", r.target.count)
        put("refreshMs", r.refreshMs)
        put("noise", r.noise)
        put("clampMin", r.clampMin)
        put("clampMax", r.clampMax)
        put("onExternalWrite", r.onExternalWrite.name)
        put("enabled", r.enabled)
        put("waveform", waveform(r.waveform))
    }

    private fun waveform(w: Waveform) = JSONObject().apply {
        when (w) {
            is Waveform.Constant -> { put("type", "constant"); put("value", w.value) }
            is Waveform.Ramp -> {
                put("type", "ramp"); put("start", w.start); put("step", w.step); put("min", w.min); put("max", w.max); put("wrap", w.wrap)
            }
            is Waveform.Sine -> {
                put("type", "sine"); put("center", w.center); put("amplitude", w.amplitude); put("periodMs", w.periodMs); put("phaseDeg", w.phaseDeg)
            }
            is Waveform.Square -> {
                put("type", "square"); put("low", w.low); put("high", w.high); put("periodMs", w.periodMs); put("duty", w.duty)
            }
            is Waveform.RandomUniform -> { put("type", "random"); put("min", w.min); put("max", w.max) }
            is Waveform.Sequence -> { put("type", "sequence"); put("values", JSONArray(w.values)) }
        }
    }

    private fun decodeRule(o: JSONObject): SimulationRule = SimulationRule(
        id = o.getString("id").also { require(it.isNotBlank()) },
        target = SimulationTarget(o.getInt("unitId"), RegisterArea.valueOf(o.getString("area")), o.getInt("address"), o.optInt("count", 1)),
        waveform = decodeWaveform(o.getJSONObject("waveform")),
        refreshMs = o.optLong("refreshMs", 1_000),
        noise = o.optDouble("noise", 0.0),
        clampMin = o.optDouble("clampMin", 0.0),
        clampMax = o.optDouble("clampMax", 65_535.0),
        onExternalWrite = ExternalWritePolicy.valueOf(o.optString("onExternalWrite", ExternalWritePolicy.PAUSE.name)),
        enabled = o.optBoolean("enabled", true),
    )

    private fun decodeWaveform(w: JSONObject): Waveform = when (w.getString("type")) {
        "constant" -> Waveform.Constant(w.getDouble("value"))
        "ramp" -> Waveform.Ramp(w.getDouble("start"), w.getDouble("step"), w.getDouble("min"), w.getDouble("max"), w.optBoolean("wrap", true))
        "sine" -> Waveform.Sine(w.getDouble("center"), w.getDouble("amplitude"), w.getLong("periodMs"), w.optDouble("phaseDeg", 0.0))
        "square" -> Waveform.Square(w.getDouble("low"), w.getDouble("high"), w.getLong("periodMs"), w.optDouble("duty", 0.5))
        "random" -> Waveform.RandomUniform(w.getDouble("min"), w.getDouble("max"))
        "sequence" -> w.getJSONArray("values").let { a -> Waveform.Sequence((0 until a.length()).map { a.getDouble(it) }) }
        else -> throw IllegalArgumentException("未知波形")
    }
}
