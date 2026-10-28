package io.github.xingyingyuzhui.modbusmobile.ui.traffic

import io.github.xingyingyuzhui.modbusmobile.application.FieldKind
import io.github.xingyingyuzhui.modbusmobile.application.FrameDirection
import io.github.xingyingyuzhui.modbusmobile.application.FrameField
import io.github.xingyingyuzhui.modbusmobile.application.FrameInfo
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.xingyingyuzhui.modbusmobile.R
import io.github.xingyingyuzhui.modbusmobile.application.modiconLabel
import io.github.xingyingyuzhui.modbusmobile.core.model.TrafficKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.SemanticKind
import io.github.xingyingyuzhui.modbusmobile.ui.components.semanticKind

/** 日志里的文字：把 [FrameInfo] 的数字翻成界面语言。解码本身在 FrameInfo.kt，这里只管措辞。 */

@StringRes
private fun fcShortRes(fc: Int): Int? = when (fc) {
    0x01 -> R.string.fc_short_01
    0x02 -> R.string.fc_short_02
    0x03 -> R.string.fc_short_03
    0x04 -> R.string.fc_short_04
    0x05 -> R.string.fc_short_05
    0x06 -> R.string.fc_short_06
    0x0F -> R.string.fc_short_0f
    0x10 -> R.string.fc_short_10
    else -> null
}

@StringRes
internal fun fcLongRes(fc: Int): Int = when (fc) {
    0x01 -> R.string.fc_long_01
    0x02 -> R.string.fc_long_02
    0x03 -> R.string.fc_long_03
    0x04 -> R.string.fc_long_04
    0x05 -> R.string.fc_long_05
    0x06 -> R.string.fc_long_06
    0x0F -> R.string.fc_long_0f
    0x10 -> R.string.fc_long_10
    else -> R.string.fc_unknown
}

@StringRes
private fun exceptionRes(code: Int): Int? = when (code) {
    0x01 -> R.string.exc_01
    0x02 -> R.string.exc_02
    0x03 -> R.string.exc_03
    0x04 -> R.string.exc_04
    0x05 -> R.string.exc_05
    0x06 -> R.string.exc_06
    0x0A -> R.string.exc_0a
    0x0B -> R.string.exc_0b
    else -> null
}

/** “FC16 (0x10)”：功能码十进制为主，同时给十六进制（老板拍板：帧详情两种都标）。 */
fun functionCodeLabel(fc: Int): String = "FC%02d (0x%02X)".format(fc, fc)

@Composable
private fun exceptionName(code: Int): String =
    exceptionRes(code)?.let { stringResource(it) } ?: "${stringResource(R.string.exc_other)} 0x%02X".format(code)

@Composable
private fun singleValue(info: FrameInfo): String {
    val value = info.value ?: return ""
    return if (info.function == 0x05) {
        stringResource(if (value == 1) R.string.value_on else R.string.value_off)
    } else {
        value.toString()
    }
}

/** 日志行的摘要：“读保持 U1 0×10”“10 个寄存器”“非法数据地址”。 */
@Composable
fun summaryText(info: FrameInfo, kind: TrafficKind): String {
    val fc = info.function
    val unit = info.unit
    if (info.size < 4 || fc == null || unit == null) return stringResource(R.string.log_summary_short)
    if (info.isBadCrc) return stringResource(R.string.log_summary_crc_bad, info.size)
    if (info.isException) return exceptionName(info.exceptionCode ?: 0)
    val short = fcShortRes(fc)?.let { stringResource(it) } ?: "FC%02X".format(fc)
    val address = info.address
    val core: String? = when (info.direction) {
        FrameDirection.REQUEST -> when {
            address != null && info.quantity != null ->
                stringResource(R.string.log_summary_request, short, unit, address, info.quantity)
            address != null && info.value != null ->
                stringResource(R.string.log_summary_write_single, short, unit, address, singleValue(info))
            else -> null
        }
        FrameDirection.RESPONSE -> when {
            info.dataCount != null -> if (info.isRegisterRead) {
                stringResource(R.string.log_summary_regs, info.dataCount)
            } else {
                stringResource(R.string.log_summary_bits, info.dataCount)
            }
            address != null && info.value != null ->
                stringResource(R.string.log_summary_ack_single, address, singleValue(info))
            address != null && info.quantity != null ->
                stringResource(R.string.log_summary_ack_multi, address, info.quantity)
            else -> null
        }
        FrameDirection.UNKNOWN -> null
    }
    return when {
        core == null && info.direction == FrameDirection.UNKNOWN -> stringResource(R.string.log_summary_generic, unit, short)
        core == null -> stringResource(R.string.log_summary_mismatch, unit, short)
        info.direction == FrameDirection.RESPONSE &&
            (kind == TrafficKind.RX_FOREIGN_RESPONSE) ->
            stringResource(R.string.log_summary_foreign_prefix, unit, core)
        else -> core
    }
}

/** 日志行的语义色：异常帧 / CRC 错误一律红，其余按报文类型。 */
fun LogEntry.semantic(): SemanticKind =
    if (info.isException || info.isBadCrc) SemanticKind.ERROR else event.kind.semanticKind()

@Composable
fun LogEntry.badgeText(kindLabel: String): String = when {
    info.isException -> stringResource(R.string.badge_exception, info.exceptionCode ?: 0)
    info.isBadCrc -> stringResource(R.string.badge_crc_bad)
    else -> kindLabel
}

/** 帧详情一行的名字。 */
@Composable
fun fieldName(field: FrameField, info: FrameInfo): String = stringResource(
    when (field.kind) {
        FieldKind.UNIT -> R.string.frame_field_unit
        FieldKind.FUNCTION -> R.string.field_function
        FieldKind.ADDRESS -> if (info.function == 0x05 || info.function == 0x06) R.string.field_address_single else R.string.frame_field_address
        FieldKind.QUANTITY -> R.string.frame_field_quantity
        FieldKind.BYTE_COUNT -> R.string.field_byte_count
        FieldKind.DATA -> R.string.field_data
        FieldKind.VALUE -> R.string.field_value
        FieldKind.EXCEPTION -> R.string.field_exception
        FieldKind.CRC -> R.string.field_crc
    },
)

/** 帧详情一行的值。 */
@Composable
fun fieldValue(field: FrameField, info: FrameInfo): String {
    val n = field.number ?: 0
    return when (field.kind) {
        FieldKind.UNIT, FieldKind.QUANTITY, FieldKind.BYTE_COUNT -> n.toString()
        FieldKind.FUNCTION -> {
            val name = stringResource(fcLongRes(n))
            val exception = if (field.number2 == 1) " · " + stringResource(R.string.badge_exception, info.exceptionCode ?: 0) else ""
            "${functionCodeLabel(n)} · $name$exception"
        }
        FieldKind.ADDRESS -> info.area?.let { "$n · ${modiconLabel(it, n)}" } ?: n.toString()
        FieldKind.DATA -> when {
            info.isRegisterRead || info.function == 0x10 -> stringResource(R.string.field_data_regs, n)
            info.direction == FrameDirection.REQUEST -> stringResource(R.string.field_data_coils, n)
            else -> stringResource(R.string.frame_field_data_bits, n)
        }
        FieldKind.VALUE -> if (field.number2 == 0x05) {
            stringResource(if (n == 1) R.string.value_on else R.string.value_off)
        } else {
            "$n (0x%04X)".format(n)
        }
        FieldKind.EXCEPTION -> "%02X · %s".format(n, exceptionName(n))
        FieldKind.CRC -> {
            val shown = "0x%04X".format(n)
            if (info.crcOk == true) {
                stringResource(R.string.field_crc_ok, shown)
            } else {
                stringResource(R.string.field_crc_bad, shown, "0x%04X".format(info.crcExpected ?: 0))
            }
        }
    }
}
