package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.xingyingyuzhui.modbusmobile.application.ConnectionTarget
import io.github.xingyingyuzhui.modbusmobile.application.MemorySettingsRepository
import io.github.xingyingyuzhui.modbusmobile.application.SerialSettings
import io.github.xingyingyuzhui.modbusmobile.application.SessionManager
import io.github.xingyingyuzhui.modbusmobile.application.SessionState
import io.github.xingyingyuzhui.modbusmobile.application.ThemeMode
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.application.UsbDeviceSummary
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusRequest
import io.github.xingyingyuzhui.modbusmobile.core.model.ModbusResult
import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.model.Role
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficEvent
import io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.VirtualRegisterBank
import io.github.xingyingyuzhui.modbusmobile.ui.shell.Destination
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 从站多 Unit、增删 Unit 的页内浮层、最近来源徽标、本地改值后保存配置。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class SlaveUnitsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class UnitsSession(val bank: InMemoryRegisterBank) : SessionManager {
        override val state = MutableStateFlow<SessionState>(SessionState.Running(Role.SLAVE, ConnectionTarget.Usb(1, 0), 1))
        override val traffic = MutableSharedFlow<TrafficEvent>(replay = 1)
        override val slaveBank = MutableStateFlow<VirtualRegisterBank?>(bank)
        override val usbDevices = MutableStateFlow<List<UsbDeviceSummary>>(emptyList())
        override val echoDetected = MutableStateFlow<Boolean?>(null)
        override val serial = MutableStateFlow<SerialSettings?>(SerialSettings())
        override val slaveUnits = MutableStateFlow(listOf(1, 2))
        var saves = 0
        val unitChanges = mutableListOf<List<Int>>()

        override suspend fun setSlaveUnits(units: List<Int>): Boolean {
            unitChanges += units
            slaveUnits.value = units
            return true
        }

        override fun saveSlaveSetup(): Boolean {
            saves += 1
            return true
        }

        override suspend fun connect(target: ConnectionTarget, serial: SerialSettings, role: Role) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun execute(request: ModbusRequest): ModbusResult = ModbusResult.Disconnected
    }

    private val bank = InMemoryRegisterBank().apply {
        listOf(10, 11, 12).forEachIndexed { i, v -> putWord(1, RegisterArea.HOLDING_REGISTER, i, v, ChangeSource.INITIAL) }
        putWord(2, RegisterArea.HOLDING_REGISTER, 100, 222, ChangeSource.INITIAL)
    }
    private val session = UnitsSession(bank)

    private fun show() {
        val settings = UiSettings(theme = ThemeMode.DARK)
        compose.setContent {
            CompositionLocalProvider(
                LocalSession provides session,
                LocalSettingsRepository provides MemorySettingsRepository(settings),
            ) {
                ModbusTheme(settings) { ModbusApp(startRoute = Destination.Workbench.route) }
            }
        }
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performTextReplacement(text)
        compose.waitForIdle()
    }

    @Test
    fun unitChipsSwitchTheTable() {
        show()
        compose.onNodeWithText("Unit 1").assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
        compose.onNodeWithText("Unit 2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("222").assertIsDisplayed()
        compose.onNodeWithText("10").assertDoesNotExist()
    }

    @Test
    fun recentSourceBadgesFollowTheChangeSource() {
        show()
        compose.onNodeWithText("最近来源").assertIsDisplayed()
        bank.writeWords(1, RegisterArea.HOLDING_REGISTER, 0, listOf(500))
        bank.putWord(1, RegisterArea.HOLDING_REGISTER, 1, 7)
        compose.waitForIdle()
        compose.onNodeWithText("外部写 · ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("本地编辑").assertIsDisplayed()
    }

    @Test
    fun addUnitValidatesInThePageSheetAndAppliesTheNewSet() {
        show()
        compose.onNodeWithText("＋").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("slave-add-unit").assertIsDisplayed()
        type("slave-add-unit-value", "2")
        compose.onNodeWithTag("slave-add-unit-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Unit 2 已经存在").assertIsDisplayed()
        type("slave-add-unit-value", "0")
        compose.onNodeWithTag("slave-add-unit-confirm").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Unit ID 必须是 1–247 的整数").assertIsDisplayed()
        type("slave-add-unit-value", "5")
        compose.onNodeWithTag("slave-add-unit-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf(1, 2, 5)), session.unitChanges)
        compose.onNodeWithTag("slave-add-unit").assertDoesNotExist()
        compose.onNodeWithText("Unit 5 还没有地址", substring = true).assertIsDisplayed()
    }

    @Test
    fun removeUnitNeedsConfirmation() {
        show()
        compose.onNodeWithTag("slave-remove-unit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("slave-remove-title", useUnmergedTree = true).assertTextContains("删除 Unit 1？")
        compose.onNodeWithTag("slave-remove-cancel").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<List<Int>>(), session.unitChanges)
        compose.onNodeWithTag("slave-remove-unit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("slave-remove-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(listOf(2)), session.unitChanges)
    }

    @Test
    fun removingTheLastUnitShowsTheEmptyStateAndPlusAddsOneBack() {
        session.slaveUnits.value = listOf(1)
        show()
        compose.onNodeWithTag("slave-remove-unit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("slave-remove-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(emptyList<Int>()), session.unitChanges)
        assertEquals("Unit 1 的地址一并删掉", emptyList<Any>(), bank.words(1, RegisterArea.HOLDING_REGISTER))
        compose.onNodeWithTag("slave-no-units").assertIsDisplayed()
        compose.onNodeWithTag("slave-remove-unit").assertDoesNotExist()
        compose.onNodeWithTag("slave-add-range-open").assertDoesNotExist()
        compose.onNodeWithText("Unit 1").assertDoesNotExist()
        compose.onNodeWithText("＋").performClick()
        compose.waitForIdle()
        type("slave-add-unit-value", "1")
        compose.onNodeWithTag("slave-add-unit-confirm").performClick()
        compose.waitForIdle()
        assertEquals(listOf(emptyList(), listOf(1)), session.unitChanges)
        compose.onNodeWithTag("slave-no-units").assertDoesNotExist()
        compose.onNodeWithText("Unit 1 还没有地址", substring = true).assertIsDisplayed()
    }

    @Test
    fun editingAValueInThePageSheetSavesTheSetup() {
        show()
        compose.onNodeWithText("11").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("slave-edit").assertIsDisplayed()
        type("slave-edit-value", "4321")
        compose.onNodeWithTag("slave-edit-confirm").performClick()
        compose.waitForIdle()
        assertEquals(4321, bank.words(1, RegisterArea.HOLDING_REGISTER)[1].value)
        assertEquals(1, session.saves)
        compose.onNodeWithTag("slave-edit").assertDoesNotExist()
    }
}
