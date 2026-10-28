package io.github.xingyingyuzhui.modbusmobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.xingyingyuzhui.modbusmobile.application.UiSettings
import io.github.xingyingyuzhui.modbusmobile.ui.components.ChipRow
import io.github.xingyingyuzhui.modbusmobile.ui.theme.ModbusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** chip 的 48dp 触控热区不占布局（E-20261028-ux1 A2）：标题 → chip 只剩卡内 8dp，热区外扩部分照样能点。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h780dp")
class ChipTouchTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chipRowTakesOnlyItsVisualHeightButKeepsA48dpTouchTarget() {
        var picked by mutableStateOf<String?>(null)
        compose.setContent {
            ModbusTheme(UiSettings()) {
                Column(Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("标题", Modifier.testTag("label"))
                    ChipRow(listOf("关", "开", "自动"), { it == "关" }, { it }, { picked = it }, Modifier.testTag("row"))
                    Text("下一行", Modifier.testTag("next"))
                }
            }
        }
        val label = compose.onNodeWithTag("label").getBoundsInRoot()
        val row = compose.onNodeWithTag("row").getBoundsInRoot()
        val next = compose.onNodeWithTag("next").getBoundsInRoot()
        assertEquals("标题底 → chip 行顶", 8f, (row.top - label.bottom).value, 0.6f)
        val rowHeight = (row.bottom - row.top).value
        assertTrue("chip 行只占视觉高（实际 $rowHeight），不再被撑到 48", rowHeight < 40f)
        assertEquals("chip 行底 → 下一行", 8f, (next.top - row.bottom).value, 0.6f)

        compose.onNodeWithText("开").assertTouchHeightIsEqualTo(48.dp)
        // 点在 chip 视觉框上方 6dp（外扩的热区里）也算点中。
        compose.onNodeWithText("开").performTouchInput { click(Offset(centerX, 1.5f.dp.toPx())) }
        compose.waitForIdle()
        assertEquals("开", picked)
    }
}
