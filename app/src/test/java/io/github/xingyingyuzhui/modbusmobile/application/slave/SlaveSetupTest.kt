package io.github.xingyingyuzhui.modbusmobile.application.slave

import io.github.xingyingyuzhui.modbusmobile.core.model.RegisterArea
import io.github.xingyingyuzhui.modbusmobile.core.slave.ChangeSource
import io.github.xingyingyuzhui.modbusmobile.core.slave.ExternalWritePolicy
import io.github.xingyingyuzhui.modbusmobile.core.slave.InMemoryRegisterBank
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationRule
import io.github.xingyingyuzhui.modbusmobile.core.slave.SimulationTarget
import io.github.xingyingyuzhui.modbusmobile.core.slave.Waveform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** org.json 在 JVM 单测里是桩，用 Robolectric。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SlaveSetupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val rules = listOf(
        SimulationRule("sine", SimulationTarget(1, RegisterArea.HOLDING_REGISTER, 0), Waveform.Sine(236.0, 40.0, 10_000, 30.0), refreshMs = 500),
        SimulationRule("ramp", SimulationTarget(1, RegisterArea.HOLDING_REGISTER, 10, 2), Waveform.Ramp(0.0, 1.0, 0.0, 65_535.0, wrap = true)),
        SimulationRule("sq", SimulationTarget(2, RegisterArea.COIL, 0), Waveform.Square(0.0, 1.0, 2_000, 0.25), onExternalWrite = ExternalWritePolicy.OVERRIDE),
        SimulationRule("rnd", SimulationTarget(2, RegisterArea.INPUT_REGISTER, 5), Waveform.RandomUniform(10.0, 20.0), noise = 1.5, clampMin = 5.0, clampMax = 30.0),
        SimulationRule("seq", SimulationTarget(1, RegisterArea.DISCRETE_INPUT, 3), Waveform.Sequence(listOf(0.0, 1.0, 1.0)), enabled = false),
        SimulationRule("c", SimulationTarget(1, RegisterArea.HOLDING_REGISTER, 20), Waveform.Constant(7.0)),
    )

    private val setup = SlaveSetup(
        units = listOf(
            SlaveUnitSetup(1, listOf(SlaveRegion(RegisterArea.HOLDING_REGISTER, 0, listOf(1, 2, 3)), SlaveRegion(RegisterArea.COIL, 4, listOf(1, 0)))),
            SlaveUnitSetup(2, listOf(SlaveRegion(RegisterArea.INPUT_REGISTER, 100, listOf(65_535)))),
        ),
        rules = rules,
    )

    @Test
    fun snapshotMergesContiguousAddressesPerUnitAndArea() {
        val bank = InMemoryRegisterBank()
        listOf(0, 1, 2, 5, 6).forEach { bank.putWord(1, RegisterArea.HOLDING_REGISTER, it, it * 10) }
        bank.putBit(1, RegisterArea.COIL, 0, true)
        bank.putWord(2, RegisterArea.INPUT_REGISTER, 9, 1)
        val snap = SlaveSetup.snapshot(bank, listOf(1, 2), emptyList())
        assertEquals(
            listOf(
                SlaveRegion(RegisterArea.HOLDING_REGISTER, 0, listOf(0, 10, 20)),
                SlaveRegion(RegisterArea.HOLDING_REGISTER, 5, listOf(50, 60)),
                SlaveRegion(RegisterArea.COIL, 0, listOf(1)),
            ),
            snap.units[0].regions,
        )
        assertEquals(listOf(SlaveRegion(RegisterArea.INPUT_REGISTER, 9, listOf(1))), snap.units[1].regions)
    }

    @Test
    fun loadIntoMarksValuesAsInitial() {
        val bank = InMemoryRegisterBank()
        setup.loadInto(bank)
        assertEquals(listOf(1, 2, 3), bank.words(1, RegisterArea.HOLDING_REGISTER).map { it.value })
        assertEquals(listOf(true, false), bank.bits(1, RegisterArea.COIL).map { it.value })
        assertEquals(ChangeSource.INITIAL, bank.lastChange(2, RegisterArea.INPUT_REGISTER, 100)?.source)
    }

    @Test
    fun jsonRoundTripKeepsUnitsRegionsAndEveryRuleKind() {
        val parsed = SlaveSetupJson.decode(SlaveSetupJson.encode(setup))
        assertEquals(setup, parsed.setup)
        assertEquals(SlaveSetupJson.FORMAT_VERSION, parsed.version)
        assertTrue(parsed.warnings.isEmpty())
    }

    @Test
    fun unreadableRulesUnitsAndUnknownFieldsArePreservedVerbatim() {
        val root = JSONObject(SlaveSetupJson.encode(setup))
        root.put("futureField", JSONObject().put("x", 1))
        root.getJSONArray("rules").put(JSONObject().put("id", "z").put("waveform", JSONObject().put("type", "chirp")))
        root.getJSONArray("units").put(JSONObject().put("unitId", 999))
        val parsed = SlaveSetupJson.decode(root.toString())
        assertEquals(setup, parsed.setup)
        assertEquals(2, parsed.warnings.size)
        val written = JSONObject(SlaveSetupJson.encode(parsed.setup, parsed.preserved))
        assertEquals(1, written.getJSONObject("futureField").getInt("x"))
        assertEquals("chirp", written.getJSONArray("rules").getJSONObject(rules.size).getJSONObject("waveform").getString("type"))
        assertEquals(999, written.getJSONArray("units").getJSONObject(2).getInt("unitId"))
    }

    @Test(expected = SlaveSetupJson.FormatException::class)
    fun fileWithoutAnyUsableUnitIsAFormatError() {
        SlaveSetupJson.decode("""{"version":1,"units":[{"unitId":999}]}""")
    }

    @Test(expected = SlaveSetupJson.FormatException::class)
    fun fileWithoutUnitsKeyIsAFormatError() {
        SlaveSetupJson.decode("""{"version":1}""")
    }

    @Test
    fun zeroUnitsIsAValidSetupAndSurvivesTheFileStore() {
        val empty = SlaveSetup(emptyList())
        val decoded = SlaveSetupJson.decode(SlaveSetupJson.encode(empty))
        assertEquals(empty, decoded.setup)
        assertEquals(emptyList<String>(), decoded.warnings)
        val store = FileSlaveSetupStore(tmp.newFolder())
        assertTrue(store.save(empty))
        assertEquals("删光的 Unit 不会被默认配置补回来", empty, store.load())
    }

    @Test
    fun fileStoreDefaultsSavesAndFallsBackToBackup() {
        val dir = tmp.newFolder()
        val store = FileSlaveSetupStore(dir)
        assertEquals(SlaveSetup.default(), store.load())
        assertTrue(store.save(setup))
        assertEquals(setup, FileSlaveSetupStore(dir).load())
        val second = setup.copy(rules = emptyList())
        assertTrue(store.save(second))
        File(dir, "slave.json").writeText("{坏了")
        val recovered = FileSlaveSetupStore(dir)
        assertEquals("主文件坏了用 .bak（上一次保存的内容）", setup, recovered.load())
        assertTrue(recovered.warnings.any { it.contains("备份") })
    }

    @Test
    fun corruptFilesAreCopiedAsideNeverDeleted() {
        val dir = tmp.newFolder()
        File(dir, "slave.json").writeText("{坏了")
        val store = FileSlaveSetupStore(dir, clock = { 42L })
        assertEquals(SlaveSetup.default(), store.load())
        assertEquals("{坏了", File(dir, "slave.json.corrupt-42").readText())
        assertTrue("副本已另存，之后可以正常保存", store.save(setup))
    }

    @Test
    fun newerVersionIsReadOnly() {
        val dir = tmp.newFolder()
        val newer = JSONObject(SlaveSetupJson.encode(setup)).put("version", 99).toString()
        File(dir, "slave.json").writeText(newer)
        val store = FileSlaveSetupStore(dir)
        assertEquals(setup, store.load())
        assertFalse(store.save(SlaveSetup.default()))
        assertEquals(newer, File(dir, "slave.json").readText())
    }
}
