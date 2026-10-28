package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.slave.isBitArea
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource
import io.github.xingyingyuzhui.modbusmobile.core.slave.RuleState
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 最近一次改值的来源（界面徽标用）。初始装载的值没有来源。 */
enum class RowSource { EXTERNAL, BROADCAST, LOCAL, SIMULATION }

/** 模拟规则的波形种类（徽标里写“模拟 · 正弦”）。 */
enum class WaveKind { CONSTANT, RAMP, SINE, SQUARE, RANDOM, SEQUENCE }

/**
 * 虚拟从站的一行。位区的 [value] 是 0 / 1。[changedAt] 是值最近一次变化的时刻（[SystemClock.elapsedRealtime]），用来闪动 2 秒。
 * [source] / [ageSeconds]：最近一次改值的来源和距今秒数；[wave]：来源是模拟时，是哪种波形。
 */
data class SlaveRow(
    val address: Int,
    val value: Int,
    val changedAt: Long?,
    val source: RowSource? = null,
    val ageSeconds: Long? = null,
    val wave: WaveKind? = null,
    /** 改值时刻（墙钟毫秒）；超过 1 分钟的来源显示这个时刻而不是秒数。 */
    val changedWallMs: Long? = null,
)

/** 正在改值的那一格（只有字区；位区点一下直接翻转）。 */
data class SlaveEdit(val area: RegisterArea, val address: Int, val draft: String, val error: UiText? = null)

/** “＋ 添加地址段”浮层；“删除地址段”浮层也用它（默认填当前区的第一段）。 */
data class SlaveAddRange(
    val area: RegisterArea,
    val start: String = "0",
    val count: String = "10",
    val error: UiText? = null,
)

/** “＋ 添加 Unit”浮层。 */
data class SlaveAddUnit(val draft: String = "", val error: UiText? = null)

/**
 * 从站工作台（b1-5）：多个虚拟 Unit、四个区、每行的最近来源。
 * 本地改值、加地址段、增删 Unit 之后都调 [SessionManager.saveSlaveSetup]，下次连接从站时按保存的配置装载。
 */
class SlaveViewModel(
    private val session: SessionManager,
    /** 单调时钟（纳秒），和寄存器库记录改值时间用的是同一种。 */
    private val monotonicNs: () -> Long = { System.nanoTime() },
    private val wallMs: () -> Long = { System.currentTimeMillis() },
    /** 放在最后：测试用尾随 lambda 传它。 */
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {
    val sessionState = session.state
    val units = session.slaveUnits

    /** 当前查看的 Unit；会话的 Unit 列表里没有它时自动换成第一个。 */
    var unit by mutableStateOf(1)
        private set

    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)
        private set

    /** 当前 Unit 四个区各自的全部行（按地址升序）。 */
    var rows by mutableStateOf<Map<RegisterArea, List<SlaveRow>>>(emptyMap())
        private set

    var edit by mutableStateOf<SlaveEdit?>(null)
        private set

    var addRange by mutableStateOf<SlaveAddRange?>(null)
        private set

    var addUnit by mutableStateOf<SlaveAddUnit?>(null)
        private set

    /** “删除地址段”浮层。 */
    var removeRange by mutableStateOf<SlaveAddRange?>(null)
        private set

    /** 待确认删除的 Unit。 */
    var removeUnit by mutableStateOf<Int?>(null)
        private set

    /** 模拟规则及其运行状态（b5-4 列表）。 */
    var ruleStates by mutableStateOf<List<RuleState>>(emptyList())
        private set

    /** 正在编辑的模拟规则（b5-5）；null = 没打开编辑页。 */
    var ruleDraft by mutableStateOf<RuleDraft?>(null)
        private set

    private var initialRuleDraft: RuleDraft? = null

    val ruleDirty: Boolean
        get() = ruleDraft?.copy(errors = emptyMap()) != initialRuleDraft?.copy(errors = emptyMap())

    var confirmDiscardRule by mutableStateOf(false)
        private set

    /** 最近一次保存失败时为 true（文件只读 / 写失败），界面提示一行。 */
    var saveFailed by mutableStateOf(false)
        private set

    private val changedAt = HashMap<Triple<Int, RegisterArea, Int>, Long>()
    private var bank: VirtualRegisterBank? = null

    // 放在 init 之前：init 里的协程可能立刻跑到 publish → ensureTicker。
    private var ticker: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            session.slaveBank.collectLatest { next ->
                bank = next
                changedAt.clear()
                edit = null
                addRange = null
                addUnit = null
                removeUnit = null
                removeRange = null
                if (next == null) {
                    rows = emptyMap()
                    return@collectLatest
                }
                publish(next)
                next.changes.collect { change ->
                    if (change.source != ChangeSource.INITIAL) {
                        changedAt[Triple(change.unitId, change.area, change.address)] = clock()
                    }
                    publish(next)
                }
            }
        }
        viewModelScope.launch {
            session.simulation.collectLatest { engine ->
                if (engine == null) {
                    ruleStates = emptyList()
                    discardRule()
                    return@collectLatest
                }
                engine.states.collect { states ->
                    ruleStates = states
                    bank?.let { publish(it) }
                }
            }
        }
        viewModelScope.launch {
            session.slaveUnits.collect { list ->
                if (list.isNotEmpty() && unit !in list) unit = list.first()
                bank?.let { publish(it) }
            }
        }
    }

    /**
     * “外部写 · 2 s”里的秒数：只在有不到 1 分钟的来源时每秒刷新，全部超过 1 分钟就停（之后显示改值时刻，不需要再走）。
     * 不用常驻的无限循环：页面不需要时不唤醒，测试里的 advanceUntilIdle 也能结束。
     */
    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        if (rows.values.none { list -> list.any { (it.ageSeconds ?: Long.MAX_VALUE) < FreshSeconds } }) return
        // 固定最多走 60 拍就停（按拍数而不是按时钟判断，测试里的虚拟时间也一定能走完）；之后有新的改值会再起。
        ticker = viewModelScope.launch {
            repeat(FreshSeconds.toInt()) {
                delay(1_000)
                bank?.let { publish(it) }
            }
        }
    }

    fun selectUnit(next: Int) {
        if (next !in units.value) return
        unit = next
        edit = null
        bank?.let { publish(it) }
    }

    fun selectArea(next: RegisterArea) {
        area = next
    }

    fun rowsOf(area: RegisterArea): List<SlaveRow> = rows[area].orEmpty()

    // ---- 改值 ----

    fun startEdit(area: RegisterArea, address: Int) {
        val current = rowsOf(area).firstOrNull { it.address == address } ?: return
        edit = SlaveEdit(area, address, current.value.toString())
    }

    fun setDraft(text: String) {
        edit = edit?.copy(draft = text, error = null)
    }

    fun cancelEdit() {
        edit = null
    }

    fun commitEdit() {
        val current = edit ?: return
        val value = parseWord(current.draft, WriteFormat.DEC)
        if (value == null) {
            edit = current.copy(error = UiText.Of(R.string.error_slave_value))
            return
        }
        bank?.putWord(unit, current.area, current.address, value)
        edit = null
        save()
    }

    fun toggleBit(area: RegisterArea, address: Int) {
        val current = rowsOf(area).firstOrNull { it.address == address } ?: return
        bank?.putBit(unit, area, address, current.value == 0)
        save()
    }

    // ---- 添加地址段 ----

    fun openAddRange() {
        if (unit !in units.value) return
        addRange = SlaveAddRange(area)
    }

    fun updateAddRange(update: SlaveAddRange.() -> SlaveAddRange) {
        addRange = addRange?.update()?.copy(error = null)
    }

    fun cancelAddRange() {
        addRange = null
    }

    /** 已有的地址不动（不覆盖现值），只补上缺的，初始值 0 / OFF。 */
    fun commitAddRange() {
        val request = addRange ?: return
        val start = request.start.trim().toIntOrNull()
        val count = request.count.trim().toIntOrNull()
        if (start == null || count == null || start !in 0..MaxAddress || count !in 1..MaxRangeCount || start + count - 1 > MaxAddress) {
            addRange = request.copy(error = UiText.Of(R.string.error_slave_range, listOf(MaxRangeCount)))
            return
        }
        val target = bank ?: return
        val existing = rowsOf(request.area).map { it.address }.toSet()
        for (address in start until start + count) {
            if (address in existing) continue
            if (request.area.isBitArea()) {
                target.putBit(unit, request.area, address, false)
            } else {
                target.putWord(unit, request.area, address, 0)
            }
        }
        area = request.area
        addRange = null
        save()
    }

    // ---- 删除地址段 ----

    /** 默认填当前区的第一段连续地址；当前区没有地址时不打开。 */
    fun openRemoveRange() {
        val addresses = rowsOf(area).map { it.address }
        val first = addresses.firstOrNull() ?: return
        val count = addresses.zipWithNext().takeWhile { (a, b) -> b == a + 1 }.size + 1
        removeRange = SlaveAddRange(area, first.toString(), count.coerceAtMost(MaxRangeCount).toString())
    }

    fun updateRemoveRange(update: SlaveAddRange.() -> SlaveAddRange) {
        removeRange = removeRange?.update()?.copy(error = null)
    }

    fun cancelRemoveRange() {
        removeRange = null
    }

    /**
     * 删掉这段里已有的地址（没有的跳过）；目标和这段重叠的模拟规则一起删，否则规则留着却写不进去。
     * 这段里一个已有地址都没有时报错，浮层留着。
     */
    fun commitRemoveRange() {
        val request = removeRange ?: return
        val start = request.start.trim().toIntOrNull()
        val count = request.count.trim().toIntOrNull()
        if (start == null || count == null || start !in 0..MaxAddress || count !in 1..MaxRemoveCount || start + count - 1 > MaxAddress) {
            removeRange = request.copy(error = UiText.Of(R.string.error_slave_range, listOf(MaxRemoveCount)))
            return
        }
        val target = bank ?: return
        val end = start + count - 1
        if (rowsOf(request.area).none { it.address in start..end }) {
            removeRange = request.copy(error = UiText.Of(R.string.error_slave_remove_none))
            return
        }
        dropRules { it.unitId == unit && it.area == request.area && it.address <= end && it.address + it.count - 1 >= start }
        target.remove(unit, request.area, start, count)
        edit = null
        area = request.area
        removeRange = null
        publish(target)
        save()
    }

    /** 删掉目标满足 [matches] 的模拟规则；打开着的规则编辑页如果是被删的那条，也一起关掉。 */
    private fun dropRules(matches: (io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget) -> Boolean) {
        val engine = session.simulation.value ?: return
        val current = engine.states.value.map { it.rule }
        val kept = current.filterNot { matches(it.target) }
        if (kept.size == current.size) return
        engine.setRules(kept)
        if (ruleDraft?.id != null && kept.none { it.id == ruleDraft?.id }) discardRule()
    }

    // ---- 增删 Unit ----

    fun openAddUnit() {
        addUnit = SlaveAddUnit()
    }

    fun setUnitDraft(text: String) {
        addUnit = addUnit?.copy(draft = text.filter { it.isDigit() }.take(3), error = null)
    }

    fun cancelAddUnit() {
        addUnit = null
    }

    /** 新 Unit 没有任何地址，外部主站读它会收到“非法地址”，界面提示去加地址段。 */
    fun commitAddUnit() {
        val request = addUnit ?: return
        val id = request.draft.toIntOrNull()
        val current = units.value
        val error = when {
            id == null || id !in 1..247 -> UiText.Of(R.string.error_slave_unit_range)
            id in current -> UiText.Of(R.string.error_slave_unit_exists, listOf(id))
            else -> null
        }
        if (error != null) {
            addUnit = request.copy(error = error)
            return
        }
        addUnit = null
        viewModelScope.launch {
            if (session.setSlaveUnits(current + id!!)) {
                unit = id
                bank?.let { publish(it) }
            } else {
                saveFailed = true
            }
        }
    }

    fun askRemoveUnit() {
        if (unit in units.value) removeUnit = unit
    }

    fun cancelRemoveUnit() {
        removeUnit = null
    }

    /**
     * 删掉的 Unit 不再响应；它的地址、值和以它为目标的模拟规则一起删掉、不再保存。
     * 寄存器库里也清掉：同一次会话里再加回这个 Unit 时是空的，不会冒出旧值。
     */
    fun confirmRemoveUnit() {
        val target = removeUnit ?: return
        removeUnit = null
        val next = units.value - target
        viewModelScope.launch {
            if (!session.setSlaveUnits(next)) {
                saveFailed = true
                return@launch
            }
            dropRules { it.unitId == target }
            bank?.removeUnit(target)
            if (ruleDraft?.unit == target) discardRule()
            bank?.let { publish(it) }
            save()
        }
    }

    // ---- 模拟规则 ----

    fun openNewRule() {
        if (unit !in units.value) return
        val first = rowsOf(area).firstOrNull()?.address ?: 0
        beginRule(RuleDraft.newFor(unit, area, first))
    }

    fun openRule(id: String) {
        val rule = session.simulation.value?.states?.value?.firstOrNull { it.rule.id == id }?.rule ?: return
        beginRule(RuleDraft.from(rule))
    }

    fun updateRule(update: RuleDraft.() -> RuleDraft) {
        ruleDraft = ruleDraft?.update()
    }

    private fun beginRule(draft: RuleDraft) {
        ruleDraft = draft
        initialRuleDraft = draft
        confirmDiscardRule = false
        saveFailed = false
    }

    fun cancelRule() {
        if (ruleDirty) confirmDiscardRule = true else discardRule()
    }

    fun keepEditingRule() {
        confirmDiscardRule = false
    }

    fun discardRule() {
        ruleDraft = null
        initialRuleDraft = null
        confirmDiscardRule = false
    }

    /** 校验通过才保存：替换同 id 的规则或追加，交给模拟器并保存从站配置。 */
    fun saveRule() {
        val draft = ruleDraft ?: return
        val engine = session.simulation.value ?: return
        if (draft.unit !in units.value) {
            ruleDraft = draft.copy(errors = draft.errors + (RuleField.UNIT to UiText.Of(R.string.error_slave_unit_range)))
            return
        }
        val (rule, checked) = draft.build { "r" + monotonicNs().toString(36) }
        if (rule == null) {
            ruleDraft = checked
            return
        }
        val current = engine.states.value.map { it.rule }
        val next = if (current.any { it.id == rule.id }) current.map { if (it.id == rule.id) rule else it } else current + rule
        engine.setRules(next)
        // “保存并运行”作用于本次会话；enabled 只决定下次连接时是否自动启动。
        engine.resume(rule.id)
        save()
        if (saveFailed) ruleDraft = checked.copy(id = rule.id) else discardRule()
    }

    fun deleteRule() {
        val id = ruleDraft?.id ?: return
        val engine = session.simulation.value ?: return
        engine.setRules(engine.states.value.map { it.rule }.filter { it.id != id })
        save()
        if (!saveFailed) discardRule()
    }

    /** 列表里点“暂停 / 恢复”。这是运行时状态，不改保存的“启动从站时自动运行”。 */
    fun toggleRule(id: String) {
        val engine = session.simulation.value ?: return
        val state = engine.states.value.firstOrNull { it.rule.id == id } ?: return
        if (state.status == io.github.xingyingyuzhui.modbusmobile.core.slave.RuleStatus.RUNNING) engine.pause(id) else engine.resume(id)
    }

    fun pauseAllRules() {
        session.simulation.value?.pauseAll()
    }

    fun resumeAllRules() {
        session.simulation.value?.resumeAll()
    }

    private fun save() {
        saveFailed = !session.saveSlaveSetup()
    }

    private fun publish(bank: VirtualRegisterBank) {
        val now = clock()
        val nowNs = monotonicNs()
        val nowWall = wallMs()
        val rules = session.simulation.value?.states?.value.orEmpty()
        val u = unit
        fun row(area: RegisterArea, address: Int, value: Int): SlaveRow {
            val change = bank.lastChange(u, area, address)?.takeIf { it.source != ChangeSource.INITIAL }
            val source = when (change?.source) {
                ChangeSource.EXTERNAL_WRITE -> RowSource.EXTERNAL
                ChangeSource.BROADCAST -> RowSource.BROADCAST
                ChangeSource.LOCAL_EDIT -> RowSource.LOCAL
                ChangeSource.SIMULATION -> RowSource.SIMULATION
                ChangeSource.INITIAL, null -> null
            }
            return SlaveRow(
                address = address,
                value = value,
                changedAt = changedAt[Triple(u, area, address)]?.takeIf { now - it < FlashWindowMs },
                source = source,
                ageSeconds = change?.let { ((nowNs - it.monotonicNs) / 1_000_000_000L).coerceAtLeast(0) },
                changedWallMs = change?.let { nowWall - ((nowNs - it.monotonicNs) / 1_000_000L).coerceAtLeast(0) },
                wave = if (source == RowSource.SIMULATION) waveAt(rules, u, area, address) else null,
            )
        }
        rows = SlaveAreasInOrder.associateWith { area ->
            if (area.isBitArea()) {
                bank.bits(u, area).map { row(area, it.address, if (it.value) 1 else 0) }
            } else {
                bank.words(u, area).map { row(area, it.address, it.value) }
            }
        }
        ensureTicker()
    }

    companion object {
        const val MaxAddress = 0xFFFF
        const val MaxRangeCount = 125

        /** 删除一次最多 2000 个地址（覆盖一个区里常见的整段配置）。 */
        const val MaxRemoveCount = 2000
        private const val FlashWindowMs = 2_000L
        private const val FreshSeconds = 60L
        private val SlaveAreasInOrder = io.github.xingyingyuzhui.modbusmobile.application.slave.SlaveAreas
    }
}

private fun waveAt(rules: List<RuleState>, unit: Int, area: RegisterArea, address: Int): WaveKind? {
    val rule = rules.firstOrNull { state ->
        val t = state.rule.target
        t.unitId == unit && t.area == area && address in t.address until t.address + t.count
    }?.rule ?: return null
    return when (rule.waveform) {
        is Waveform.Constant -> WaveKind.CONSTANT
        is Waveform.Ramp -> WaveKind.RAMP
        is Waveform.Sine -> WaveKind.SINE
        is Waveform.Square -> WaveKind.SQUARE
        is Waveform.RandomUniform -> WaveKind.RANDOM
        is Waveform.Sequence -> WaveKind.SEQUENCE
    }
}
