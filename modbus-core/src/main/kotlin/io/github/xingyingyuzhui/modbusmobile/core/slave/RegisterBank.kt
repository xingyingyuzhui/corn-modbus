package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.transport.MonotonicClock
import io.github.xingyingyuzhui.modbusmobile.core.transport.SystemMonotonicClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class BitCell(val address: Int, val value: Boolean)

data class WordCell(val address: Int, val value: Int)

/** 一次改值的来源（spec §4.2）。 */
enum class ChangeSource {
    /** 外部主站发给本机 Unit 的写请求。 */
    EXTERNAL_WRITE,

    /** 外部主站的广播写（Unit 0）。 */
    BROADCAST,

    /** 手机界面上手动改的值。 */
    LOCAL_EDIT,

    /** 模拟规则写的值。 */
    SIMULATION,

    /** 会话开始时按保存的配置 / 预置装进来的初始值（界面不当成“谁改过”）。 */
    INITIAL,
}

/**
 * 一个单元被改了。线圈 / 离散输入的 [oldValue] / [newValue] 用 0 / 1；新建的单元 [oldValue] 为 null。
 * [monotonicNs] 是改值时刻（单调时钟），用来判断“外部写入是不是发生在模拟规则上次写之后”。
 */
data class RegisterChange(
    val unitId: Int,
    val area: RegisterArea,
    val address: Int,
    val source: ChangeSource = ChangeSource.LOCAL_EDIT,
    val oldValue: Int? = null,
    val newValue: Int? = null,
    val monotonicNs: Long = 0L,
)

sealed interface BankAccess<out T> {
    data class Ok<T>(val value: T) : BankAccess<T>
    data class Fail(val code: ExceptionCode) : BankAccess<Nothing>
}

interface VirtualRegisterBank {
    val changes: SharedFlow<RegisterChange>
    fun readBits(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Boolean>>
    fun readWords(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Int>>

    /** 外部写入走这里：整段校验后原子提交，任一地址未映射就整段不写。 */
    fun writeBits(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        values: List<Boolean>,
        source: ChangeSource = ChangeSource.EXTERNAL_WRITE,
    ): ExceptionCode?

    fun writeWords(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        values: List<Int>,
        source: ChangeSource = ChangeSource.EXTERNAL_WRITE,
    ): ExceptionCode?

    /** 本地设值：地址不存在时会新建（加地址段、预置用）。 */
    fun putBit(unitId: Int, area: RegisterArea, address: Int, value: Boolean, source: ChangeSource = ChangeSource.LOCAL_EDIT)
    fun putWord(unitId: Int, area: RegisterArea, address: Int, value: Int, source: ChangeSource = ChangeSource.LOCAL_EDIT)
    fun bits(unitId: Int, area: RegisterArea): List<BitCell>
    fun words(unitId: Int, area: RegisterArea): List<WordCell>

    /**
     * 本地删除：去掉 [unitId] 的 [area] 里 [address] 起 [count] 个地址（不存在的跳过），连同“最近来源”。
     * 删掉后外部主站再读写这些地址会收到“非法数据地址”。返回实际删掉的个数。
     */
    fun remove(unitId: Int, area: RegisterArea, address: Int, count: Int): Int

    /** 去掉 [unitId] 在四个区里的全部地址和来源（删 Unit 时用）。返回删掉的个数。 */
    fun removeUnit(unitId: Int): Int

    /** 这个单元最近一次改值；从来没改过（或实现不记录）时为 null。不经过 [changes]，所以不会因为缓冲溢出丢失。 */
    fun lastChange(unitId: Int, area: RegisterArea, address: Int): RegisterChange? = null
}

class InMemoryRegisterBank(
    private val clock: MonotonicClock = SystemMonotonicClock,
) : VirtualRegisterBank {
    private val lock = Any()
    private val bits = mutableMapOf<Key, Boolean>()
    private val words = mutableMapOf<Key, Int>()
    private val last = mutableMapOf<Key, RegisterChange>()
    private val updates = MutableSharedFlow<RegisterChange>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val changes: SharedFlow<RegisterChange> = updates.asSharedFlow()

    override fun readBits(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Boolean>> {
        if (!area.isBit) return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
        return synchronized(lock) {
            val values = (0 until quantity).map { offset ->
                bits[Key(unitId, area, address + offset)] ?: return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
            }
            BankAccess.Ok(values)
        }
    }

    override fun readWords(unitId: Int, area: RegisterArea, address: Int, quantity: Int): BankAccess<List<Int>> {
        if (area.isBit) return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
        return synchronized(lock) {
            val values = (0 until quantity).map { offset ->
                words[Key(unitId, area, address + offset)] ?: return BankAccess.Fail(ExceptionCode.ILLEGAL_DATA_ADDRESS)
            }
            BankAccess.Ok(values)
        }
    }

    override fun writeBits(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        values: List<Boolean>,
        source: ChangeSource,
    ): ExceptionCode? =
        synchronized(lock) {
            if (!area.isBit) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            val keys = values.indices.map { Key(unitId, area, address + it) }
            if (keys.any { it !in bits }) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            val now = clock.nowNs()
            keys.zip(values).forEach { (key, value) ->
                val old = bits.put(key, value)
                record(key, source, old?.toBit(), value.toBit(), now)
            }
            null
        }

    override fun writeWords(
        unitId: Int,
        area: RegisterArea,
        address: Int,
        values: List<Int>,
        source: ChangeSource,
    ): ExceptionCode? =
        synchronized(lock) {
            if (area.isBit) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            if (values.any { it !in 0..0xFFFF }) return ExceptionCode.ILLEGAL_DATA_VALUE
            val keys = values.indices.map { Key(unitId, area, address + it) }
            if (keys.any { it !in words }) return ExceptionCode.ILLEGAL_DATA_ADDRESS
            val now = clock.nowNs()
            keys.zip(values).forEach { (key, value) ->
                val old = words.put(key, value)
                record(key, source, old, value, now)
            }
            null
        }

    override fun putBit(unitId: Int, area: RegisterArea, address: Int, value: Boolean, source: ChangeSource) {
        require(area.isBit)
        synchronized(lock) {
            val key = Key(unitId, area, address)
            val old = bits.put(key, value)
            record(key, source, old?.toBit(), value.toBit(), clock.nowNs())
        }
    }

    override fun putWord(unitId: Int, area: RegisterArea, address: Int, value: Int, source: ChangeSource) {
        require(!area.isBit)
        require(value in 0..0xFFFF)
        synchronized(lock) {
            val key = Key(unitId, area, address)
            val old = words.put(key, value)
            record(key, source, old, value, clock.nowNs())
        }
    }

    override fun lastChange(unitId: Int, area: RegisterArea, address: Int): RegisterChange? =
        synchronized(lock) { last[Key(unitId, area, address)] }

    override fun bits(unitId: Int, area: RegisterArea): List<BitCell> = synchronized(lock) {
        bits.filterKeys { it.unitId == unitId && it.area == area }
            .map { BitCell(it.key.address, it.value) }
            .sortedBy { it.address }
    }

    override fun words(unitId: Int, area: RegisterArea): List<WordCell> = synchronized(lock) {
        words.filterKeys { it.unitId == unitId && it.area == area }
            .map { WordCell(it.key.address, it.value) }
            .sortedBy { it.address }
    }

    override fun remove(unitId: Int, area: RegisterArea, address: Int, count: Int): Int = synchronized(lock) {
        val cells = if (area.isBit) bits else words
        var removed = 0
        for (offset in 0 until count.coerceAtLeast(0)) {
            val key = Key(unitId, area, address + offset)
            last.remove(key)
            if (cells.remove(key) != null) removed++
        }
        removed
    }

    override fun removeUnit(unitId: Int): Int = synchronized(lock) {
        val before = bits.size + words.size
        bits.keys.removeAll { it.unitId == unitId }
        words.keys.removeAll { it.unitId == unitId }
        last.keys.removeAll { it.unitId == unitId }
        before - bits.size - words.size
    }

    /** 在 [lock] 内调用：记下最近一次改值，并发布变化事件。 */
    private fun record(key: Key, source: ChangeSource, old: Int?, new: Int, nowNs: Long) {
        val change = RegisterChange(key.unitId, key.area, key.address, source, old, new, nowNs)
        last[key] = change
        updates.tryEmit(change)
    }

    private data class Key(val unitId: Int, val area: RegisterArea, val address: Int)
}

private fun Boolean.toBit(): Int = if (this) 1 else 0

private val RegisterArea.isBit: Boolean
    get() = this == RegisterArea.COIL || this == RegisterArea.DISCRETE_INPUT
