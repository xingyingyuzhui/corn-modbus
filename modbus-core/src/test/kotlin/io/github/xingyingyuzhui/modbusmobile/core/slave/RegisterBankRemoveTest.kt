package io.github.xingyingyuzhui.modbusmobile.core.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.ExceptionCode
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RegisterBankRemoveTest {
    @Test
    fun removeDropsOnlyThatRangeAndItsSource() {
        val bank = InMemoryRegisterBank()
        (0..9).forEach { bank.putWord(1, RegisterArea.HOLDING_REGISTER, it, it) }
        bank.putWord(1, RegisterArea.INPUT_REGISTER, 3, 7)

        // 2..4 删掉，99 不存在不算
        assertEquals(3, bank.remove(1, RegisterArea.HOLDING_REGISTER, 2, 3))
        assertEquals(0, bank.remove(1, RegisterArea.HOLDING_REGISTER, 99, 1))

        assertEquals(listOf(0, 1, 5, 6, 7, 8, 9), bank.words(1, RegisterArea.HOLDING_REGISTER).map { it.address })
        assertNull(bank.lastChange(1, RegisterArea.HOLDING_REGISTER, 3))
        // 外部主站再访问删掉的地址：非法数据地址
        assertIs<BankAccess.Fail>(bank.readWords(1, RegisterArea.HOLDING_REGISTER, 2, 1))
        assertEquals(ExceptionCode.ILLEGAL_DATA_ADDRESS, bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 4, listOf(1)))
        // 别的区、同地址不受影响
        assertEquals(7, bank.words(1, RegisterArea.INPUT_REGISTER).single().value)
    }

    @Test
    fun removeBitsUsesTheBitArea() {
        val bank = InMemoryRegisterBank()
        (0..7).forEach { bank.putBit(1, RegisterArea.COIL, it, true) }
        assertEquals(8, bank.remove(1, RegisterArea.COIL, 0, 8))
        assertEquals(emptyList(), bank.bits(1, RegisterArea.COIL))
    }

    @Test
    fun removeUnitClearsEveryAreaOfThatUnitOnly() {
        val bank = InMemoryRegisterBank()
        bank.putWord(1, RegisterArea.HOLDING_REGISTER, 0, 1)
        bank.putWord(2, RegisterArea.HOLDING_REGISTER, 0, 2)
        bank.putBit(2, RegisterArea.COIL, 0, true)
        bank.putWord(2, RegisterArea.INPUT_REGISTER, 5, 3)

        assertEquals(3, bank.removeUnit(2))

        for (area in RegisterArea.entries) {
            assertEquals(emptyList(), bank.bits(2, area) + bank.words(2, area))
        }
        assertNull(bank.lastChange(2, RegisterArea.HOLDING_REGISTER, 0))
        assertEquals(1, bank.words(1, RegisterArea.HOLDING_REGISTER).single().value)
    }
}
