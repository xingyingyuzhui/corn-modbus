package io.github.xingyingyuzhui.modbusmobile.ui.workbench

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.MasterPoller
import io.github.xingyingyuzhui.modbusmobile.application.NumberFormat
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.core.model.FunctionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResponse
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.ui.UiText
import io.github.xingyingyuzhui.modbusmobile.ui.components.ResultKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.FlashDurationMs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class WorkbenchMode { READ, WRITE }

/** 一次成功读取的结果。位读取（线圈 / 离散）的 [values] 是 0 / 1。 */
data class ReadResult(
    val area: RegisterArea,
    val unit: Int,
    val start: Int,
    val values: List<Int>,
    val isBits: Boolean,
    val elapsedMs: Long,
    /** 与上一次同一段读取相比变化的地址 → 变化时刻（[SystemClock.elapsedRealtime]）。 */
    val changedAt: Map<Int, Long>,
)

sealed interface ReadState {
    data object None : ReadState
    data class Ok(val result: ReadResult) : ReadState

    /** [badge] 是结果头的红色/灰色小标，[message] 是详细说明。 */
    data class Failed(val badge: UiText, val message: UiText, val elapsedMs: Long, val details: UiText? = null) : ReadState
}

/** [SENT]：广播（Unit 0）写入。从站不回应，只能说“已发送”，不能说“成功”。 */
enum class WriteOutcome { OK, SENT, EXCEPTION, TIMEOUT, UNKNOWN, DISCONNECTED, INVALID }

data class RecentWrite(
    val area: RegisterArea,
    val unit: Int,
    val address: Int,
    val words: List<Int>,
    val bits: List<Boolean>,
    val functionCode: Int,
    val outcome: WriteOutcome,
    val exceptionCode: Int?,
    val atMillis: Long,
)

/**
 * 等待用户在确认弹窗里点“发送写入”的写请求。
 *
 * [current] 是写入前已知的当前值（没读过为 null）；这些值来自本次连接里最近一次读取（或确认成功的写入），
 * [currentAgeMs] 是其中最旧的一个距现在多久，[currentFromRead] 表示其中有来自读取的值。广播写入没有“当前值”。
 */
data class PendingWrite(
    val plan: WritePlan,
    val current: List<Int?>,
    val currentAgeMs: Long? = null,
    val currentFromRead: Boolean = true,
) {
    val broadcast: Boolean get() = plan.unit == 0
}

/** 请求卡里能单独标红的输入格（§6.2 / §7.3 字段级错误）。 */
enum class WorkbenchField { UNIT, ADDRESS, QUANTITY }

/**
 * 写入按钮正上方的结果条（§1-P1、§6.9、S2-01）。[plan] 是这次写入的计划，“回读”“重试”都用它。
 * [canReadBack]：未知（可能已执行）时给“回读”；广播没有单个从站可读，不给。
 * [canRetry]：设备明确拒绝（异常响应）时给“重试”——重试也要重新经过确认单，不直接发送。
 */
data class WriteNotice(
    val kind: ResultKind,
    val text: UiText,
    val id: Long,
    val plan: WritePlan,
    val canReadBack: Boolean = false,
    val canRetry: Boolean = false,
)

private data class ValueKey(val unit: Int, val area: RegisterArea, val address: Int)

private data class KnownValue(val value: Int, val atMs: Long, val fromRead: Boolean)

val PollIntervalsMs = listOf(500L, 1_000L, 2_000L, 5_000L)

private const val MaxRecentWrites = 5

/** 轮询里连续收到这么多次无效响应才停；一次噪声帧不该让轮询永久停掉。 */
const val MaxInvalidStreak = 3

class WorkbenchViewModel(
    private val session: SessionManager,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    /**
     * 应用级轮询（AppContainer.masterPoller，T-20261018-qa-02）。null = 自己建一个挂在 viewModelScope 上的（只给测试和预览用），
     * VM 清掉时一起关。
     */
    poller: MasterPoller? = null,
) : ViewModel(), MasterPoller.Client {
    private val ownsPoller = poller == null
    private val poller: MasterPoller = poller ?: MasterPoller(session, viewModelScope, clock)
    val sessionState = session.state

    var mode by mutableStateOf(WorkbenchMode.READ)
        private set

    /** 见 [restampUnseenWriteChanges]。放在 init 之前，epoch 观察协程可能立即调用重置。 */
    private val unseenWriteChanges = HashSet<Int>()

    /** 读取用的区域。 */
    var area by mutableStateOf(RegisterArea.HOLDING_REGISTER)

    /** 写入用的区域，只有线圈和保持寄存器。 */
    var writeArea by mutableStateOf(RegisterArea.HOLDING_REGISTER)
        private set
    var unit by mutableStateOf("1")
    var address by mutableStateOf("0")
    var quantity by mutableStateOf("10")

    var writeFormat by mutableStateOf(WriteFormat.DEC)
        private set
    var writeWords by mutableStateOf(listOf("0"))
        private set
    var writeCoils by mutableStateOf(listOf(false))
        private set
    var forceMultiple by mutableStateOf(false)
        private set

    /** 用户在结果头里点过的格式；null 表示跟随设置里的“默认数值格式”。 */
    var formatOverride by mutableStateOf<NumberFormat?>(null)

    var readState by mutableStateOf<ReadState>(ReadState.None)
        private set

    /** 输入有误时显示在请求卡下面的一行红字。 */
    var formError by mutableStateOf<UiText?>(null)
        private set

    /** 轮询是否在跑：镜像 [MasterPoller.state]（唯一状态源），这里只订阅。 */
    var polling by mutableStateOf(this.poller.state.value.running)
        private set
    var pollIntervalMs by mutableLongStateOf(this.poller.state.value.intervalMs)
        private set

    var pending by mutableStateOf<PendingWrite?>(null)
        private set
    var recentWrites by mutableStateOf<List<RecentWrite>>(emptyList())
        private set

    /** 写入在途（确认后到设备回应前）。写入按钮显示“正在写入…”，读取、回读、再次写入都不可点（§7.4）。 */
    var writing by mutableStateOf(false)
        private set

    /** 手动读取在途。轮询的读不算（每秒闪一次 loading 没有意义），轮询期间“读取”仍可点但不会并发。 */
    var reading by mutableStateOf(false)
        private set

    /** “回读”在途。 */
    var readingBack by mutableStateOf(false)
        private set

    /** 写入 / 读取 / 回读任一在途时，所有会发请求的按钮都不可点，输入只读。 */
    val busy: Boolean get() = writing || reading || readingBack

    /** 最近一次写入的结果条；切换会话、新的写入会替换它。 */
    var writeNotice by mutableStateOf<WriteNotice?>(null)
        private set
    private var noticeSeq = 0L

    /** 字段级错误：哪个输入格标红、格下写什么（§10.4“{字段}应在 {min}–{max} 之间”）。 */
    var fieldErrors by mutableStateOf<Map<WorkbenchField, UiText>>(emptyMap())
        private set

    /** 最近一次成功读取的结果和时间（墙钟）。读取失败时界面继续显示它，表头标“上次成功 hh:mm:ss”（S2-04）。 */
    var lastGood by mutableStateOf<ReadResult?>(null)
        private set
    var lastGoodAtMillis by mutableStateOf<Long?>(null)
        private set

    private val known = HashMap<ValueKey, KnownValue>()
    private var invalidStreak = 0

    /** 取构造时的 epoch：VM 重建（如 Activity 重建）时不把还在跑的应用级轮询和回填的表单当成“新会话”清掉。 */
    private var lastEpoch: Long? = (session.state.value as? SessionState.Running)?.epoch
    private val detachPoller: () -> Unit
    private var operationGeneration = 0L
    private var manualJob: Job? = null

    init {
        // 应用级轮询还在跑（工作台 VM 重建了）：表单回填成正在轮询的请求，按钮和结果都接得上。
        this.poller.state.value.request?.takeIf { this.poller.state.value.running }?.let(::adoptRequest)
        detachPoller = this.poller.attach(this)
        viewModelScope.launch {
            this@WorkbenchViewModel.poller.state.collect { s ->
                polling = s.running
                pollIntervalMs = s.intervalMs
            }
        }
        // 换了一次连接（epoch 变化）或断开：上一次连接读到的“当前值”、结果表和待确认的写入都不再可信，清掉。
        viewModelScope.launch {
            session.state.collect { state ->
                val epoch = (state as? SessionState.Running)?.epoch
                if (epoch != lastEpoch) {
                    lastEpoch = epoch
                    resetForNewSession()
                }
            }
        }
    }

    /**
     * 断开、重连或切换角色（epoch 变化）：表单、读取结果、最近写入、待确认的写入和“当前值”缓存都属于上一次会话，全部清空。
     * 轮询由 [MasterPoller] 自己在 epoch 变化时停掉，这里不用管。
     */
    private fun resetForNewSession() {
        operationGeneration++
        manualJob?.cancel()
        manualJob = null
        reading = false
        writing = false
        readingBack = false
        known.clear()
        invalidStreak = 0
        readState = ReadState.None
        unseenWriteChanges.clear()
        pending = null
        recentWrites = emptyList()
        mode = WorkbenchMode.READ
        area = RegisterArea.HOLDING_REGISTER
        writeArea = RegisterArea.HOLDING_REGISTER
        unit = "1"
        address = "0"
        quantity = "10"
        writeFormat = WriteFormat.DEC
        writeWords = listOf("0")
        writeCoils = listOf(false)
        forceMultiple = false
        formOverrideReset()
        formError = null
        fieldErrors = emptyMap()
        writeNotice = null
        lastGood = null
        lastGoodAtMillis = null
    }

    /** StateFlow 的收集可能晚于点击或 execute 返回；同步核对避免把旧连接的请求/结果带进新连接。 */
    private fun syncSession() {
        val epoch = (session.state.value as? SessionState.Running)?.epoch
        if (epoch != lastEpoch) {
            lastEpoch = epoch
            resetForNewSession()
        }
    }

    private fun operationIsCurrent(generation: Long): Boolean {
        syncSession()
        return generation == operationGeneration
    }

    private fun launchManual(block: suspend (Long) -> Unit) {
        val generation = operationGeneration
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (operationIsCurrent(generation)) block(generation)
            } finally {
                if (generation == operationGeneration) {
                    reading = false
                    writing = false
                    readingBack = false
                    manualJob = null
                }
            }
        }
        manualJob = job
        job.start()
    }

    private fun formOverrideReset() {
        formatOverride = null
    }

    // ---- 模式、区域、值格 ----

    fun selectMode(next: WorkbenchMode) {
        mode = next
        formError = null
        fieldErrors = emptyMap()
        if (next == WorkbenchMode.READ) restampUnseenWriteChanges()
    }

    /**
     * 写入成功会改结果表里的值并闪烁 2 秒，但写入页（竖屏和横屏）不显示结果表：
     * 用户回到读取页时 2 秒早过了，闪烁永远看不到（PR #4 实机复验 §16 d 项的真实原因之一）。
     * 所以在写入页完成的写入先记在 [unseenWriteChanges]，回到读取页的那一刻再把变化时刻重置为“现在”，闪烁从表可见时开始。
     * 值本身在写入成功时已经更新，这里只动闪烁的起点。
     */
    private fun restampUnseenWriteChanges() {
        if (unseenWriteChanges.isEmpty()) return
        val current = (readState as? ReadState.Ok)?.result
        if (current == null) {
            unseenWriteChanges.clear()
            return
        }
        val now = clock()
        val changed = HashMap(current.changedAt)
        unseenWriteChanges.forEach { address ->
            if (address in current.start until current.start + current.values.size) changed[address] = now
        }
        unseenWriteChanges.clear()
        readState = ReadState.Ok(current.copy(changedAt = changed))
    }

    fun selectWriteArea(next: RegisterArea) {
        if (next != RegisterArea.COIL && next != RegisterArea.HOLDING_REGISTER) return
        if (next == writeArea) return
        writeArea = next
        writeWords = listOf("0")
        writeCoils = listOf(false)
        forceMultiple = false
        formError = null
    }

    fun selectWriteFormat(next: WriteFormat) {
        if (next == writeFormat) return
        // 已经能解析的值跟着换算，免得用户看到数字“变了”。解析不了的原样保留。
        writeWords = writeWords.map { text ->
            val word = parseWord(text, writeFormat)
            when {
                word == null -> text
                next == WriteFormat.HEX -> "%X".format(word)
                else -> word.toString()
            }
        }
        writeFormat = next
    }

    fun setWordText(index: Int, text: String) {
        writeWords = writeWords.toMutableList().also { if (index in it.indices) it[index] = text }
    }

    fun toggleCoil(index: Int) {
        writeCoils = writeCoils.toMutableList().also { if (index in it.indices) it[index] = !it[index] }
    }

    fun addValue() {
        if (writeArea == RegisterArea.COIL) {
            if (writeCoils.size < MaxFormValues) writeCoils = writeCoils + false
        } else if (writeWords.size < MaxFormValues) {
            writeWords = writeWords + "0"
        }
    }

    fun removeValue() {
        if (writeArea == RegisterArea.COIL) {
            if (writeCoils.size > 1) writeCoils = writeCoils.dropLast(1)
        } else if (writeWords.size > 1) {
            writeWords = writeWords.dropLast(1)
        }
    }

    fun toggleForceMultiple() {
        forceMultiple = !forceMultiple
    }

    // ---- 读取与轮询 ----

    fun read() {
        syncSession()
        if (busy) return
        val params = readParams() ?: return
        reading = true
        launchManual { generation ->
            performRead(params, generation)
        }
    }

    /**
     * 开 / 停轮询。轮询本身在应用级 [MasterPoller] 里跑：离开工作台、退到后台都不停，
     * 只有这里停止、断开连接、poller close() 才停（T-20261018-qa-02）。
     */
    fun changePolling(enabled: Boolean) {
        if (!enabled) {
            poller.stop()
        } else {
            syncSession()
            val params = readParams() ?: return
            invalidStreak = 0
            poller.start(params.request, pollIntervalMs)
        }
        syncPollState()
    }

    fun setPollInterval(ms: Long) {
        poller.setInterval(ms)
        syncPollState()
    }

    /** 自己的操作立刻反映到按钮上（不等 StateFlow 收集）。 */
    private fun syncPollState() {
        val s = poller.state.value
        polling = s.running
        pollIntervalMs = s.intervalMs
    }

    // ---- MasterPoller.Client ----

    /** 手动写入优先：写模式、有待确认的写入、写入在途时，轮询让路。 */
    override fun holdPolling(): Boolean = mode == WorkbenchMode.WRITE || pending != null || busy

    /** 每一拍跟随当前表单；表单非法时返回 null（同时显示表单错误），轮询停。 */
    override fun nextRequest(): ModbusRequest? = readParams()?.request

    override fun onPollResult(outcome: MasterPoller.Outcome) {
        val params = paramsOf(outcome.request) ?: return
        showRead(params, outcome.result, outcome.elapsedMs)
    }

    private fun adoptRequest(request: ModbusRequest) {
        val params = paramsOf(request) ?: return
        area = params.area
        unit = params.unit.toString()
        address = params.start.toString()
        quantity = when (request) {
            is ModbusRequest.ReadCoils -> request.quantity
            is ModbusRequest.ReadDiscreteInputs -> request.quantity
            is ModbusRequest.ReadHoldingRegisters -> request.quantity
            is ModbusRequest.ReadInputRegisters -> request.quantity
            else -> return
        }.toString()
    }

    private fun paramsOf(request: ModbusRequest): ReadParams? = when (request) {
        is ModbusRequest.ReadCoils -> ReadParams(request, RegisterArea.COIL, request.unitId, request.address)
        is ModbusRequest.ReadDiscreteInputs -> ReadParams(request, RegisterArea.DISCRETE_INPUT, request.unitId, request.address)
        is ModbusRequest.ReadHoldingRegisters -> ReadParams(request, RegisterArea.HOLDING_REGISTER, request.unitId, request.address)
        is ModbusRequest.ReadInputRegisters -> ReadParams(request, RegisterArea.INPUT_REGISTER, request.unitId, request.address)
        else -> null
    }

    override fun onCleared() {
        detachPoller()
        if (ownsPoller) poller.close()
    }

    fun clearResult() {
        readState = ReadState.None
        lastGood = null
        lastGoodAtMillis = null
    }

    private data class ReadParams(val request: ModbusRequest, val area: RegisterArea, val unit: Int, val start: Int)

    private fun readParams(): ReadParams? {
        val unitId = unit.trim().toIntOrNull()
        val start = address.trim().toIntOrNull()
        val count = quantity.trim().toIntOrNull()
        val maxCount = if (area == RegisterArea.COIL || area == RegisterArea.DISCRETE_INPUT) ModbusRequest.MAX_READ_BITS else ModbusRequest.MAX_READ_REGISTERS
        val errors = readFieldErrors(unitId, start, count, maxCount)
        fieldErrors = errors
        if (errors.isNotEmpty() || unitId == null || start == null || count == null) {
            formError = errors.values.first()
            return null
        }
        val request = try {
            when (area) {
                RegisterArea.COIL -> ModbusRequest.ReadCoils(unitId, start, count)
                RegisterArea.DISCRETE_INPUT -> ModbusRequest.ReadDiscreteInputs(unitId, start, count)
                RegisterArea.HOLDING_REGISTER -> ModbusRequest.ReadHoldingRegisters(unitId, start, count)
                RegisterArea.INPUT_REGISTER -> ModbusRequest.ReadInputRegisters(unitId, start, count)
            }
        } catch (error: IllegalArgumentException) {
            // 上面的字段校验已覆盖 core 的全部范围检查；万一漏了，也不把异常原文给用户看（§10.4）。
            formError = UiText.Of(R.string.wb_err_request_invalid)
            return null
        }
        formError = null
        return ReadParams(request, area, unitId, start)
    }

    /** 返回 true 表示轮询应该停（连接已断开，或连续多次无效响应）。 */
    private suspend fun performRead(params: ReadParams, generation: Long): Boolean {
        val began = clock()
        val result = session.execute(params.request)
        val elapsed = clock() - began
        return if (operationIsCurrent(generation)) showRead(params, result, elapsed) else true
    }

    private fun showRead(params: ReadParams, result: ModbusResult, elapsed: Long): Boolean {
        when (result) {
            is ModbusResult.Success -> {
                val response = result.response
                val (values, isBits) = when (response) {
                    is ModbusResponse.ReadRegisters -> response.values to false
                    is ModbusResponse.ReadBits -> response.values.map { if (it) 1 else 0 } to true
                    else -> {
                        readState = ReadState.Failed(UiText.Of(R.string.badge_invalid), UiText.Of(R.string.result_done), elapsed)
                        return noteInvalid()
                    }
                }
                val now = clock()
                unseenWriteChanges.clear()
                val previous = (readState as? ReadState.Ok)?.result
                val changed = buildMap<Int, Long> {
                    if (previous != null && previous.area == params.area && previous.unit == params.unit &&
                        previous.start == params.start
                    ) {
                        values.forEachIndexed { index, value ->
                            val address = params.start + index
                            val before = previous.values.getOrNull(index)
                            when {
                                before != null && before != value -> put(address, now)
                                else -> previous.changedAt[address]?.let { at ->
                                    if (now - at < FlashDurationMs) put(address, at)
                                }
                            }
                        }
                    }
                }
                invalidStreak = 0
                values.forEachIndexed { index, value ->
                    known[ValueKey(params.unit, params.area, params.start + index)] = KnownValue(value, now, fromRead = true)
                }
                readState = if (values.isEmpty()) {
                    ReadState.Failed(UiText.Of(R.string.badge_empty), UiText.Of(R.string.result_empty), elapsed)
                } else {
                    val ok = ReadResult(params.area, params.unit, params.start, values, isBits, elapsed, changed)
                    lastGood = ok
                    lastGoodAtMillis = wallClock()
                    ReadState.Ok(ok)
                }
                return false
            }
            is ModbusResult.Exception -> {
                readState = ReadState.Failed(
                    UiText.Of(R.string.badge_exception, listOf(result.code.code)),
                    UiText.Of(R.string.wb_read_exception, listOf(exceptionText(result.code.code))),
                    elapsed,
                )
            }
            ModbusResult.Timeout -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_timeout), UiText.Of(R.string.wb_read_timeout), elapsed)
            }
            ModbusResult.UnknownOutcome -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_unknown), UiText.Of(R.string.wb_read_timeout), elapsed)
            }
            ModbusResult.Disconnected -> {
                readState = ReadState.Failed(UiText.Of(R.string.badge_disconnected), UiText.Of(R.string.wb_read_disconnected), elapsed)
                return true
            }
            is ModbusResult.InvalidResponse -> {
                // 技术细节只进 ErrorBanner 的“详情”折叠（§7.3）。
                readState = ReadState.Failed(UiText.Of(R.string.badge_invalid), UiText.Of(R.string.wb_read_invalid), elapsed, UiText.Plain(result.detail))
                return noteInvalid()
            }
        }
        invalidStreak = 0
        return false
    }

    /** 记一次无效响应；连续 [MaxInvalidStreak] 次才让轮询停。 */
    private fun noteInvalid(): Boolean {
        invalidStreak += 1
        return invalidStreak >= MaxInvalidStreak
    }

    // ---- 写入 ----

    /** 点“预览并写入”：校验输入，生成写入计划，交给确认弹窗。**不会**直接发送。 */
    fun requestWrite() {
        syncSession()
        if (busy) return
        val errors = writeFieldErrors()
        fieldErrors = errors
        if (errors.isNotEmpty()) {
            formError = errors.values.first()
            return
        }
        when (val planned = currentPlan()) {
            is PlanResult.Invalid -> formError = planned.message
            is PlanResult.Ok -> {
                formError = null
                pending = pendingFor(planned.plan)
            }
        }
    }

    /** 写入的 Unit（0 = 广播）和地址范围；值格的错误由 [planWrite] 给出。 */
    private fun writeFieldErrors(): Map<WorkbenchField, UiText> {
        val errors = LinkedHashMap<WorkbenchField, UiText>()
        val unitId = unit.trim().toIntOrNull()
        val start = address.trim().toIntOrNull()
        rangeError(R.string.field_unit, unitId, 0, MaxUnit)?.let { errors[WorkbenchField.UNIT] = it }
        rangeError(R.string.field_address, start, 0, MaxAddress)?.let { errors[WorkbenchField.ADDRESS] = it }
        val count = if (writeArea == RegisterArea.COIL) writeCoils.size else writeWords.size
        if (start != null && start in 0..MaxAddress && start.toLong() + count > AddressSpace) {
            errors[WorkbenchField.ADDRESS] = UiText.Of(R.string.wb_err_span)
        }
        return errors
    }

    fun currentPlan(): PlanResult = planWrite(writeArea, unit, address, writeWords, writeCoils, writeFormat, forceMultiple)

    /** 确认弹窗里改单个线圈的新值（稿面“OFF | ON”分段）。 */
    fun setPendingCoil(value: Boolean) {
        val current = pending ?: return
        val plan = current.plan
        if (plan.area != RegisterArea.COIL || plan.bits.size != 1) return
        val request = if (plan.request is ModbusRequest.WriteSingleCoil) {
            ModbusRequest.WriteSingleCoil(plan.unit, plan.address, value)
        } else {
            ModbusRequest.WriteMultipleCoils(plan.unit, plan.address, listOf(value))
        }
        val next = WritePlan(request, plan.area, plan.unit, plan.address, emptyList(), listOf(value))
        pending = current.copy(plan = next)
        writeCoils = listOf(value)
    }

    fun dismissPending() {
        pending = null
    }

    /** 用户在确认弹窗里点“发送写入”。 */
    fun confirmWrite() {
        syncSession()
        // 写入在途时再点确认（连点、弹层关闭动画期间的第二次点击）一律忽略：execute 只调用一次（验收 A6）。
        if (busy) return
        val ready = pending ?: return
        pending = null
        writing = true
        writeNotice = null
        launchManual { generation ->
            val result = session.execute(ready.plan.request)
            if (operationIsCurrent(generation)) recordWrite(ready.plan, result)
        }
    }

    fun dismissWriteNotice() {
        writeNotice = null
    }

    /** 结果条的“重试”：重新打开确认单（不绕过确认，§1-P1）。 */
    fun retryWrite() {
        syncSession()
        val notice = writeNotice ?: return
        if (!notice.canRetry || busy) return
        pending = pendingFor(notice.plan)
    }

    /**
     * 结果条的“回读”：对同一个 Unit、同一段地址、同样个数发一次读请求（FC03 / FC01），把读到的值填回结果表，
     * 并按“读到的值是否等于写入的值”把结果条改成成功或失败。回读本身没有回应时，结果条保持“未知”。
     * 只在用户点按钮时发；未知结果从不自动重试（契约 v1.2 §4）。
     */
    fun readBack() {
        syncSession()
        val notice = writeNotice ?: return
        if (!notice.canReadBack || busy) return
        val plan = notice.plan
        val count = plan.words.size + plan.bits.size
        val request = try {
            if (plan.area == RegisterArea.COIL) {
                ModbusRequest.ReadCoils(plan.unit, plan.address, count)
            } else {
                ModbusRequest.ReadHoldingRegisters(plan.unit, plan.address, count)
            }
        } catch (error: IllegalArgumentException) {
            return
        }
        readingBack = true
        launchManual { generation ->
            val result = session.execute(request)
            if (operationIsCurrent(generation)) applyReadBack(notice, result)
        }
    }

    private fun applyReadBack(notice: WriteNotice, result: ModbusResult) {
        // 回读期间换了会话或又写了一次：这条回读结果已经不属于当前结果条。
        if (writeNotice?.id != notice.id) return
        val plan = notice.plan
        val count = plan.words.size + plan.bits.size
        val expected = if (plan.area == RegisterArea.COIL) plan.bits.map { if (it) 1 else 0 } else plan.words
        val values: List<Int>? = when (val response = (result as? ModbusResult.Success)?.response) {
            is ModbusResponse.ReadRegisters -> response.values.takeIf {
                plan.area == RegisterArea.HOLDING_REGISTER && response.unitId == plan.unit &&
                    response.function == FunctionCode.READ_HOLDING_REGISTERS
            }
            is ModbusResponse.ReadBits -> response.values.takeIf {
                plan.area == RegisterArea.COIL && response.unitId == plan.unit &&
                    response.function == FunctionCode.READ_COILS
            }?.map { if (it) 1 else 0 }
            else -> null
        }
        if (values == null || values.size != count) {
            writeNotice = notice.copy(
                text = UiText.Of(R.string.wb_readback_failed, listOf(readBackReason(result))),
                id = ++noticeSeq,
            )
            return
        }
        val now = clock()
        values.forEachIndexed { index, value ->
            known[ValueKey(plan.unit, plan.area, plan.address + index)] = KnownValue(value, now, fromRead = true)
        }
        applyValuesToResult(plan.area, plan.unit, plan.address, values, now)
        val shown = valuesText(plan.area, values)
        writeNotice = if (values == expected) {
            WriteNotice(ResultKind.SUCCESS, UiText.Of(R.string.wb_readback_match, listOf(shown)), ++noticeSeq, plan)
        } else {
            WriteNotice(ResultKind.FAILURE, UiText.Of(R.string.wb_readback_mismatch, listOf(shown)), ++noticeSeq, plan, canRetry = true)
        }
    }

    fun clearRecentWrites() {
        recentWrites = emptyList()
    }

    /** 点“最近写入”里的一条：把它的参数填回写入表单（不会直接发送）。 */
    fun reuse(entry: RecentWrite) {
        mode = WorkbenchMode.WRITE
        writeArea = entry.area
        unit = entry.unit.toString()
        address = entry.address.toString()
        writeFormat = WriteFormat.DEC
        if (entry.area == RegisterArea.COIL) {
            writeCoils = entry.bits.ifEmpty { listOf(false) }
            writeWords = listOf("0")
        } else {
            writeWords = entry.words.map { it.toString() }.ifEmpty { listOf("0") }
            writeCoils = listOf(false)
        }
        forceMultiple = false
        formError = null
    }

    /** 点结果表里一行的 ✎：切到写入模式，并把这一行的地址和当前值填进去。 */
    fun editRow(result: ReadResult, rowAddress: Int) {
        if (result.area != RegisterArea.COIL && result.area != RegisterArea.HOLDING_REGISTER) return
        val value = result.values.getOrNull(rowAddress - result.start) ?: return
        mode = WorkbenchMode.WRITE
        writeArea = result.area
        unit = result.unit.toString()
        address = rowAddress.toString()
        writeFormat = WriteFormat.DEC
        if (result.area == RegisterArea.COIL) {
            writeCoils = listOf(value != 0)
            writeWords = listOf("0")
        } else {
            writeWords = listOf(value.toString())
            writeCoils = listOf(false)
        }
        forceMultiple = false
        formError = null
    }

    /**
     * 写入成功后，如果被写的地址落在当前结果表的范围内，就把表里对应的值改成新值并标记变化时刻（复用 2 秒闪烁）。
     * 这是设备已经确认的值（写响应回显），不是猜的；**不会**自动再发读取。
     */
    private fun applyWriteToResult(plan: WritePlan, now: Long) {
        val written = if (plan.area == RegisterArea.COIL) plan.bits.map { if (it) 1 else 0 } else plan.words
        applyValuesToResult(plan.area, plan.unit, plan.address, written, now)
    }

    /** 把设备确认过的值（写响应回显或回读）填进当前结果表；值真的变了才标记变化时刻（复用 2 秒闪烁）。 */
    private fun applyValuesToResult(area: RegisterArea, unitId: Int, start: Int, written: List<Int>, now: Long) {
        val current = (readState as? ReadState.Ok)?.result ?: return
        if (current.area != area || current.unit != unitId) return
        val values = current.values.toMutableList()
        val changed = HashMap(current.changedAt)
        var touched = false
        written.forEachIndexed { index, value ->
            val address = start + index
            val slot = address - current.start
            if (slot in values.indices) {
                values[slot] = value
                changed[address] = now
                touched = true
                if (mode == WorkbenchMode.WRITE) unseenWriteChanges += address
            }
        }
        if (touched) {
            val next = current.copy(values = values, changedAt = changed)
            readState = ReadState.Ok(next)
            lastGood = next
        }
    }

    private fun pendingFor(plan: WritePlan): PendingWrite {
        val count = plan.words.size + plan.bits.size
        // 广播写入对应 0 个具体从站，没有“当前值”。
        if (plan.unit == 0) return PendingWrite(plan, List(count) { null })
        val entries = List(count) { index -> known[ValueKey(plan.unit, plan.area, plan.address + index)] }
        val now = clock()
        val oldest = entries.filterNotNull().maxOfOrNull { now - it.atMs }
        return PendingWrite(
            plan = plan,
            current = entries.map { it?.value },
            currentAgeMs = oldest,
            currentFromRead = entries.any { it?.fromRead == true },
        )
    }

    private fun recordWrite(plan: WritePlan, result: ModbusResult) {
        var exceptionCode: Int? = null
        val outcome = when (result) {
            // 广播没有响应：引擎返回的是合成的成功，界面上只能写“已发送”。
            is ModbusResult.Success -> if (plan.unit == 0) WriteOutcome.SENT else WriteOutcome.OK
            is ModbusResult.Exception -> {
                exceptionCode = result.code.code
                WriteOutcome.EXCEPTION
            }
            ModbusResult.Timeout -> WriteOutcome.TIMEOUT
            ModbusResult.UnknownOutcome -> WriteOutcome.UNKNOWN
            ModbusResult.Disconnected -> WriteOutcome.DISCONNECTED
            is ModbusResult.InvalidResponse -> WriteOutcome.INVALID
        }
        if (outcome == WriteOutcome.OK) {
            val now = clock()
            applyWriteToResult(plan, now)
            plan.words.forEachIndexed { index, word ->
                known[ValueKey(plan.unit, plan.area, plan.address + index)] = KnownValue(word, now, fromRead = false)
            }
            plan.bits.forEachIndexed { index, bit ->
                known[ValueKey(plan.unit, plan.area, plan.address + index)] = KnownValue(if (bit) 1 else 0, now, fromRead = false)
            }
        }
        writeNotice = noticeFor(plan, outcome, exceptionCode)
        val entry = RecentWrite(
            area = plan.area,
            unit = plan.unit,
            address = plan.address,
            words = plan.words,
            bits = plan.bits,
            functionCode = plan.functionCode,
            outcome = outcome,
            exceptionCode = exceptionCode,
            atMillis = wallClock(),
        )
        recentWrites = (listOf(entry) + recentWrites).take(MaxRecentWrites)
    }

    /**
     * 写入结果 → 结果条（§10.3：超时 / 无回应 / 响应无法解析的写入都是“未知（可能已执行）”，不说失败）。
     * 广播（Unit 0）没有回应也没有单个从站可回读：用成功色说“已发送”，文案写清“从站不回应”。
     */
    private fun noticeFor(plan: WritePlan, outcome: WriteOutcome, exceptionCode: Int?): WriteNotice {
        val id = ++noticeSeq
        val summary = valuesText(plan.area, if (plan.area == RegisterArea.COIL) plan.bits.map { if (it) 1 else 0 } else plan.words)
        return when (outcome) {
            WriteOutcome.OK -> WriteNotice(ResultKind.SUCCESS, UiText.Of(R.string.wb_write_ok, listOf(summary)), id, plan)
            WriteOutcome.SENT -> WriteNotice(ResultKind.SUCCESS, UiText.Of(R.string.wb_write_sent), id, plan)
            WriteOutcome.EXCEPTION -> WriteNotice(
                ResultKind.FAILURE,
                UiText.Of(R.string.wb_write_exception, listOf(exceptionText(exceptionCode ?: 0))),
                id,
                plan,
                canRetry = true,
            )
            WriteOutcome.DISCONNECTED -> WriteNotice(ResultKind.FAILURE, UiText.Of(R.string.wb_write_disconnected), id, plan)
            WriteOutcome.TIMEOUT, WriteOutcome.UNKNOWN, WriteOutcome.INVALID ->
                WriteNotice(ResultKind.UNKNOWN, UiText.Of(R.string.wb_write_unknown), id, plan, canReadBack = plan.unit != 0)
        }
    }

    private companion object {
        /** 写入表单里的值格上限。协议上限更大（123 个寄存器 / 1968 个线圈），但一屏手写这么多没有意义。 */
        const val MaxFormValues = 16
        const val MaxUnit = 247
        const val MaxAddress = 0xFFFF
        const val AddressSpace = 0x10000L
    }
}

private fun exceptionText(code: Int): UiText = when (code) {
    0x01 -> UiText.Of(R.string.ex_01)
    0x02 -> UiText.Of(R.string.ex_02)
    0x03 -> UiText.Of(R.string.ex_03)
    0x04 -> UiText.Of(R.string.ex_04)
    0x05 -> UiText.Of(R.string.ex_05)
    0x06 -> UiText.Of(R.string.ex_06)
    0x0A -> UiText.Of(R.string.ex_0a)
    0x0B -> UiText.Of(R.string.ex_0b)
    else -> UiText.Of(R.string.ex_unknown, listOf(code))
}

/** 读取参数的字段级校验（S2-05）：范围都来自 Modbus 协议，与 core 的 validateRead 一致；返回空表示全部合法。 */
internal fun readFieldErrors(unitId: Int?, start: Int?, count: Int?, maxCount: Int): Map<WorkbenchField, UiText> {
    val errors = LinkedHashMap<WorkbenchField, UiText>()
    rangeError(R.string.field_unit, unitId, 1, 247)?.let { errors[WorkbenchField.UNIT] = it }
    rangeError(R.string.field_address, start, 0, 0xFFFF)?.let { errors[WorkbenchField.ADDRESS] = it }
    rangeError(R.string.field_quantity, count, 1, maxCount)?.let { errors[WorkbenchField.QUANTITY] = it }
    if (start != null && count != null && start in 0..0xFFFF && count in 1..maxCount && start.toLong() + count > 0x10000L) {
        errors[WorkbenchField.QUANTITY] = UiText.Of(R.string.wb_err_span)
    }
    return errors
}

/** null = 不是整数：“请输入整数”；超范围：“{字段}应在 {min}–{max} 之间”（§10.4）。字段名是嵌套的 UiText，由界面逐层解析。 */
private fun rangeError(@androidx.annotation.StringRes label: Int, value: Int?, min: Int, max: Int): UiText? = when {
    value == null -> UiText.Of(R.string.need_integer)
    value !in min..max -> UiText.Of(R.string.wb_err_range, listOf(UiText.Of(label), min, max))
    else -> null
}

/**
 * 结果条里的值：寄存器写十进制，线圈写“开 / 关”（§10.3 术语）；最多列 [MaxNoticeValues] 个，其余写“等 n 个”。
 * 返回的 UiText 可能带嵌套参数（List<UiText>），界面用 WorkbenchScreen 里的 deep 解析。
 */
internal fun valuesText(area: RegisterArea, values: List<Int>): UiText {
    val shown = values.take(MaxNoticeValues)
    val parts: List<UiText> = if (area == RegisterArea.COIL) {
        shown.map { UiText.Of(if (it != 0) R.string.coil_on else R.string.coil_off) }
    } else {
        shown.map { UiText.Plain(it.toString()) }
    }
    return if (values.size > shown.size) {
        UiText.Of(R.string.wb_values_more, listOf(parts, values.size))
    } else {
        UiText.Of(R.string.wb_values, listOf(parts))
    }
}

private const val MaxNoticeValues = 4

/** 回读没拿到值的原因（一句话）。 */
private fun readBackReason(result: ModbusResult): UiText = when (result) {
    is ModbusResult.Exception -> exceptionText(result.code.code)
    ModbusResult.Timeout, ModbusResult.UnknownOutcome -> UiText.Of(R.string.wb_reason_no_answer)
    ModbusResult.Disconnected -> UiText.Of(R.string.result_disconnected)
    else -> UiText.Of(R.string.wb_reason_invalid)
}
