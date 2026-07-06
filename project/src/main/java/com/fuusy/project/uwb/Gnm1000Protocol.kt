package com.fuusy.project.uwb

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * GNM1000 超宽带定位基站模块通信协议 V4.0（硅传科技）
 * 帧格式：HD(2) + CMDTYPE(1) + SEQ(2) + CMDLEN(2) + PAYLOAD(N) + CHKSUM(1)
 */
object Gnm1000Protocol {

    const val HEADER_B1 = 0x59
    const val HEADER_B2 = 0x4D
    const val MIN_FRAME_SIZE = 8
    const val PDOA_RECORD_SIZE = 14

    val CMD_NAMES = mapOf(
        0x00 to "链路信息",
        0x01 to "TOF定位",
        0x02 to "TDOA定位",
        0x03 to "TDOA主站同步",
        0x04 to "TDOA辅站同步",
        0x05 to "终端报警",
        0x06 to "电量/版本",
        0x07 to "紧急撤离",
        0x08 to "取消撤离",
        0x09 to "工作模式配置",
        0x0A to "模块ID修改",
        0x0B to "模块信息读写",
        0x0C to "升级开始",
        0x0D to "升级数据/结束",
        0x0E to "升级就绪",
        0x0F to "手持机启动",
        0x10 to "手持机休眠",
        0x11 to "搜索终端",
        0x12 to "停止搜索",
        0x13 to "修改终端ID",
        0x14 to "修改终端ID结果",
        0x15 to "终端数据透传",
        0x16 to "平台数据透传",
        0x17 to "PDOA定位",
        0x18 to "语音上报",
        0x19 to "语音下发",
        0x1A to "修改定位周期",
        0x1B to "修改休眠配置"
    )

    data class ParsedFrame(
        val raw: ByteArray,
        val cmdType: Int,
        val seq: Int,
        val payload: ByteArray,
        val checksumValid: Boolean,
        val summary: String
    )

    // ── 对外解析入口 ─────────────────────────────────────────

    /**
     * 解析串口助手日志，支持格式：
     * `[18:02:46.293]收←◆59 4D 17 ...`
     */
    fun parseSerialLog(text: String): Gnm1000ParseBatch {
        val hexLines = Regex("""[0-9A-Fa-f]{2}(?:\s+[0-9A-Fa-f]{2})+""")
            .findAll(text)
            .map { it.value.trim() }
            .toList()
        return parseHexLines(hexLines)
    }

    /** 解析多行 HEX 字符串 */
    fun parseHexLines(lines: List<String>): Gnm1000ParseBatch {
        val frames = lines.mapNotNull { line ->
            parseHex(line)?.let { parseFrameResult(it) }
        }
        val pdoa = frames.flatMap { it.pdoaRecords }
        val summary = pdoa.groupBy { it.tagId }.mapValues { (_, list) ->
            Gnm1000ParseBatch.TagStats(
                tagId = list.first().tagId,
                count = list.size,
                avgDistanceCm = list.map { it.distanceCm }.average().roundToInt(),
                avgXCm = list.map { it.xCm }.average().roundToInt(),
                avgYCm = list.map { it.yCm }.average().roundToInt(),
                lowBattery = list.any { it.statusCode and 0x01 != 0 }
            )
        }
        return Gnm1000ParseBatch(frames = frames, pdoaRecords = pdoa, tagSummary = summary)
    }

    /** 解析单帧，返回结构化结果 */
    fun parseFrameResult(frame: ByteArray): Gnm1000FrameResult? {
        val parsed = parseFrame(frame) ?: return null
        val pdoa = if (parsed.cmdType == 0x17) parsePdoaRecords(parsed.payload) else emptyList()
        return Gnm1000FrameResult(
            rawHex = toHex(parsed.raw),
            cmdType = parsed.cmdType,
            cmdName = CMD_NAMES[parsed.cmdType] ?: "未知",
            frameSeq = parsed.seq,
            checksumValid = parsed.checksumValid,
            pdoaRecords = pdoa,
            summary = parsed.summary
        )
    }

    /** 解析 PDOA 0x17 净荷，每 14 字节一条标签记录 */
    fun parsePdoaRecords(payload: ByteArray): List<UwbPdoaRecord> {
        if (payload.isEmpty() || payload.size % PDOA_RECORD_SIZE != 0) return emptyList()
        return (0 until payload.size / PDOA_RECORD_SIZE).map { i ->
            parsePdoaRecord(payload, i * PDOA_RECORD_SIZE)
        }
    }

    fun parsePdoaRecord(payload: ByteArray, offset: Int = 0): UwbPdoaRecord {
        val tagIdRaw = u24le(payload, offset)
        val distanceCm = u16le(payload, offset + 5)
        val angleDeg = u16leSigned(payload, offset + 7)
        val (xCm, yCm) = polarToXY(distanceCm, angleDeg)
        val periodCode = payload[offset + 9].toInt() and 0xFF
        val rssi = payload[offset + 10].toInt().let { if (it > 127) it - 256 else it }
        val status = payload[offset + 11].toInt() and 0xFF
        val extra = payload.copyOfRange(offset + 12, offset + 14)
        return UwbPdoaRecord(
            tagId = formatTagId(tagIdRaw),
            tagIdRaw = tagIdRaw,
            tagSeq = u16le(payload, offset + 3),
            distanceCm = distanceCm,
            angleDeg = angleDeg,
            xCm = xCm,
            yCm = yCm,
            periodCode = periodCode,
            periodText = formatPeriod(periodCode),
            rssiDbm = rssi,
            statusCode = status,
            statusText = decodeStatus(status),
            extraHex = toHex(extra)
        )
    }

    /** 距离+角度 → 直角坐标（基站为原点，0° 沿 Y 轴正方向，与硅传上位机一致） */
    fun polarToXY(distanceCm: Int, angleDeg: Int): Pair<Int, Int> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val x = (distanceCm * sin(rad)).roundToInt()
        val y = (distanceCm * cos(rad)).roundToInt()
        return x to y
    }

    fun formatTagId(id: Int): String = "P$id"

    fun formatPeriod(code: Int): String = when (code) {
        0 -> "1S"
        1 -> "5S"
        2 -> "0.5S"
        3 -> "0.1S"
        else -> "code=$code"
    }

    // ── 组帧 / 基础工具 ─────────────────────────────────────

    fun buildFrame(cmdType: Int, seq: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val frame = ByteArray(MIN_FRAME_SIZE + payload.size)
        frame[0] = HEADER_B1.toByte()
        frame[1] = HEADER_B2.toByte()
        frame[2] = cmdType.toByte()
        frame[3] = (seq and 0xFF).toByte()
        frame[4] = ((seq shr 8) and 0xFF).toByte()
        frame[5] = (payload.size and 0xFF).toByte()
        frame[6] = ((payload.size shr 8) and 0xFF).toByte()
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, frame, 7, payload.size)
        }
        frame[frame.size - 1] = checksum(frame, frame.size - 1).toByte()
        return frame
    }

    fun parseHex(hex: String): ByteArray? {
        val cleaned = hex.replace(Regex("[\\s,:-]"), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        if (!cleaned.matches(Regex("[0-9A-Fa-f]+"))) return null
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun toHex(data: ByteArray): String =
        data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    fun checksum(data: ByteArray, length: Int = data.size): Int {
        var sum = 0
        for (i in 0 until length) {
            sum = (sum + (data[i].toInt() and 0xFF)) and 0xFF
        }
        return sum
    }

    fun parseFrame(frame: ByteArray): ParsedFrame? {
        if (frame.size < MIN_FRAME_SIZE) return null
        if ((frame[0].toInt() and 0xFF) != HEADER_B1 || (frame[1].toInt() and 0xFF) != HEADER_B2) {
            return null
        }
        val cmdType = frame[2].toInt() and 0xFF
        val seq = u16le(frame, 3)
        val payloadLen = u16le(frame, 5)
        if (frame.size != MIN_FRAME_SIZE + payloadLen) return null
        val payload = frame.copyOfRange(7, 7 + payloadLen)
        val expected = frame[frame.size - 1].toInt() and 0xFF
        val actual = checksum(frame, frame.size - 1)
        val cmdName = CMD_NAMES[cmdType] ?: "未知命令"
        val summary = buildString {
            append("↑ $cmdName (0x${cmdType.toString(16).uppercase()}) seq=$seq")
            if (expected != actual) append(" [校验失败]")
            append('\n')
            append(describePayload(cmdType, payload))
        }
        return ParsedFrame(
            raw = frame,
            cmdType = cmdType,
            seq = seq,
            payload = payload,
            checksumValid = expected == actual,
            summary = summary.trim()
        )
    }

    class FrameBuffer {
        private val buffer = ArrayList<Byte>(512)

        fun append(data: ByteArray): List<ParsedFrame> {
            for (b in data) buffer.add(b)
            return drainFrames()
        }

        fun clear() = buffer.clear()

        private fun drainFrames(): List<ParsedFrame> {
            val frames = mutableListOf<ParsedFrame>()
            while (buffer.size >= MIN_FRAME_SIZE) {
                val headerIndex = findHeaderIndex()
                if (headerIndex < 0) {
                    if (buffer.size > 2048) buffer.clear()
                    break
                }
                if (headerIndex > 0) {
                    buffer.subList(0, headerIndex).clear()
                }
                if (buffer.size < MIN_FRAME_SIZE) break
                val payloadLen = u16le(buffer, 5)
                val frameLen = MIN_FRAME_SIZE + payloadLen
                if (buffer.size < frameLen) break
                val frameBytes = ByteArray(frameLen) { buffer[it] }
                repeat(frameLen) { buffer.removeAt(0) }
                parseFrame(frameBytes)?.let { frames.add(it) }
            }
            return frames
        }

        private fun findHeaderIndex(): Int {
            for (i in 0 until buffer.size - 1) {
                if ((buffer[i].toInt() and 0xFF) == HEADER_B1 &&
                    (buffer[i + 1].toInt() and 0xFF) == HEADER_B2
                ) {
                    return i
                }
            }
            return -1
        }

        private fun u16le(source: List<Byte>, offset: Int): Int {
            val low = source[offset].toInt() and 0xFF
            val high = source[offset + 1].toInt() and 0xFF
            return low or (high shl 8)
        }
    }

    private fun u16le(data: ByteArray, offset: Int): Int {
        val low = data[offset].toInt() and 0xFF
        val high = data[offset + 1].toInt() and 0xFF
        return low or (high shl 8)
    }

    private fun u24le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16)

    private fun u16leSigned(data: ByteArray, offset: Int): Int {
        return u16le(data, offset).let { if (it > 0x7FFF) it - 0x10000 else it }
    }

    private fun describePayload(cmdType: Int, payload: ByteArray): String = when (cmdType) {
        0x17 -> parsePdoaRecords(payload).joinToString("\n") { it.toReadableLine() }
            .ifEmpty { "PDOA 原始(${payload.size}B): ${toHex(payload)}" }
        0x01 -> parseTofPayload(payload)
        0x05 -> parseAlarmPayload(payload)
        0x06 -> parseBatteryPayload(payload)
        0x07 -> parseModuleInfoPayload(payload)
        0x15 -> parsePassthroughPayload(payload, uplink = true)
        0x16 -> parsePassthroughPayload(payload, uplink = false)
        0x00 -> "链路状态数据 ${payload.size} 字节"
        0x02 -> "TDOA 定位数据 ${payload.size} 字节\n${toHex(payload)}"
        else -> if (payload.isEmpty()) "无净荷" else toHex(payload)
    }

    private fun parseTofPayload(payload: ByteArray): String {
        if (payload.size < 7) return "TOF 原始: ${toHex(payload)}"
        val tagId = formatTagId(u24le(payload, 0))
        val distanceCm = u16le(payload, 5)
        return "标签 $tagId  距离 ${distanceCm}cm"
    }

    private fun parseAlarmPayload(payload: ByteArray): String {
        if (payload.size < 3) return "报警原始: ${toHex(payload)}"
        val tagId = formatTagId(u24le(payload, 0))
        val alarmType = payload.getOrNull(3)?.toInt()?.and(0xFF) ?: -1
        return "标签 $tagId  报警类型=$alarmType"
    }

    private fun parseBatteryPayload(payload: ByteArray): String {
        if (payload.size < 4) return "电量/版本: ${toHex(payload)}"
        val tagId = formatTagId(u24le(payload, 0))
        val battery = payload.getOrNull(3)?.toInt()?.and(0xFF) ?: -1
        return "终端 $tagId  电量 ${battery}%"
    }

    private fun parseModuleInfoPayload(payload: ByteArray): String {
        if (payload.size < 6) return "模块信息: ${toHex(payload)}"
        val moduleId = formatTagId(u24le(payload, 0))
        val version = u16le(payload, 3)
        val periodCode = payload.getOrNull(5)?.toInt()?.and(0xFF) ?: -1
        return "模块ID $moduleId  版本 $version  定位周期 ${formatPeriod(periodCode)}"
    }

    private fun parsePassthroughPayload(payload: ByteArray, uplink: Boolean): String {
        if (payload.size < 3) return "透传: ${toHex(payload)}"
        val tagId = formatTagId(u24le(payload, 0))
        val data = payload.copyOfRange(3, payload.size)
        val direction = if (uplink) "终端→平台" else "平台→终端"
        return "$direction  标签 $tagId  数据(${data.size}B): ${toHex(data)}"
    }

    fun decodeStatus(status: Int): String = buildList {
        if (status and 0x01 != 0) add("低电")
        if (status and 0x02 != 0) add("被激励")
        if (status and 0x04 != 0) add("求救")
        if (status and 0x08 != 0) add("撤离中")
        if (status and 0x10 != 0) add("撤离ACK")
        if (status and 0x20 != 0) add("运动中")
        if (status and 0x40 != 0) add("姿态异常")
        if (status and 0x80 != 0) add("有加速度计")
    }.joinToString(", ").ifEmpty { "正常" }
}
