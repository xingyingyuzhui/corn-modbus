package io.github.xingyingyuzhui.modbusmobile.application.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank

/**
 * 一段连续地址及其值（spec §6.1 `slave.units[].regions[]`）。位区（线圈 / 离散输入）的值用 0 / 1。
 */
data class SlaveRegion(val area: RegisterArea, val start: Int, val values: List<Int>) {
    init {
        require(start in 0..0xFFFF) { "起始地址超出 0..65535" }
        require(values.isNotEmpty()) { "地址段不能为空" }
        require(start.toLong() + values.size - 1 <= 0xFFFFL) { "地址段超出 65535" }
        val bit = area.isBitArea()
        require(values.all { if (bit) it == 0 || it == 1 else it in 0..0xFFFF }) { "值超出范围" }
    }
}

data class SlaveUnitSetup(val unitId: Int, val regions: List<SlaveRegion>) {
    init {
        require(unitId in 1..247) { "Unit ID 必须在 1..247" }
    }
}

/**
 * 虚拟从站的配置：有哪些 Unit、每个 Unit 映射了哪些地址段和值、模拟规则。
 *
 * 保存的是“保存那一刻的值”：本地编辑、加地址段、增删 Unit、改模拟规则时整体快照一次；
 * 外部主站写入和模拟器写入不会单独触发保存。下次连接从站时按这里装进寄存器库。
 * Unit 可以一个都没有：从站照常连接，但对任何请求都不应答（广播也不写入）。
 */
data class SlaveSetup(
    val units: List<SlaveUnitSetup>,
    val rules: List<SimulationRule> = emptyList(),
) {
    init {
        require(units.map { it.unitId }.toSet().size == units.size) { "Unit ID 不能重复" }
    }

    val unitIds: List<Int> get() = units.map { it.unitId }

    /** 按配置把值装进寄存器库（来源标 [ChangeSource.INITIAL]）。 */
    fun loadInto(bank: VirtualRegisterBank) {
        for (unit in units) {
            for (region in unit.regions) {
                region.values.forEachIndexed { index, value ->
                    val address = region.start + index
                    if (region.area.isBitArea()) {
                        bank.putBit(unit.unitId, region.area, address, value != 0, ChangeSource.INITIAL)
                    } else {
                        bank.putWord(unit.unitId, region.area, address, value, ChangeSource.INITIAL)
                    }
                }
            }
        }
    }

    companion object {
        /** 第一次用：Unit 1，保持寄存器 0..9 = 0，线圈 0..7 = OFF（和之前写死的预置一样）。 */
        fun default(): SlaveSetup = SlaveSetup(
            listOf(
                SlaveUnitSetup(
                    1,
                    listOf(
                        SlaveRegion(RegisterArea.HOLDING_REGISTER, 0, List(10) { 0 }),
                        SlaveRegion(RegisterArea.COIL, 0, List(8) { 0 }),
                    ),
                ),
            ),
        )

        /** 从寄存器库当前内容生成配置：每个 Unit、每个区里连续的地址合成一段。 */
        fun snapshot(bank: VirtualRegisterBank, unitIds: List<Int>, rules: List<SimulationRule>): SlaveSetup =
            SlaveSetup(
                units = unitIds.map { unit ->
                    SlaveUnitSetup(unit, SlaveAreas.flatMap { area -> regionsOf(bank, unit, area) })
                },
                rules = rules,
            )

        private fun regionsOf(bank: VirtualRegisterBank, unit: Int, area: RegisterArea): List<SlaveRegion> {
            val cells: List<Pair<Int, Int>> = if (area.isBitArea()) {
                bank.bits(unit, area).map { it.address to if (it.value) 1 else 0 }
            } else {
                bank.words(unit, area).map { it.address to it.value }
            }
            val regions = mutableListOf<SlaveRegion>()
            var start = -1
            var values = mutableListOf<Int>()
            for ((address, value) in cells) {
                if (start >= 0 && address == start + values.size) {
                    values += value
                } else {
                    if (start >= 0) regions += SlaveRegion(area, start, values)
                    start = address
                    values = mutableListOf(value)
                }
            }
            if (start >= 0) regions += SlaveRegion(area, start, values)
            return regions
        }
    }
}

/** 从站四个区，界面和配置里的固定顺序。 */
val SlaveAreas: List<RegisterArea> = listOf(
    RegisterArea.HOLDING_REGISTER,
    RegisterArea.INPUT_REGISTER,
    RegisterArea.COIL,
    RegisterArea.DISCRETE_INPUT,
)

fun RegisterArea.isBitArea(): Boolean = this == RegisterArea.COIL || this == RegisterArea.DISCRETE_INPUT
