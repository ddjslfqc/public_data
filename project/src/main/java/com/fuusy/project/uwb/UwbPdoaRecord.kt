package com.fuusy.project.uwb

/**
 * PDOA 0x17 单标签定位记录（14 字节净荷）
 *
 * 字段布局（协议 V4.0 + 实测对齐）：
 * - 0~2   标签 ID（小端 24 位 → P14801）
 * - 3~4   标签帧序号
 * - 5~6   距离 cm
 * - 7~8   角度 °（有符号，-90~+270）
 * - 9     定位周期码
 * - 10    RSSI dBm（有符号）
 * - 11    状态位
 * - 12~13 保留/扩展
 */
data class UwbPdoaRecord(
    val tagId: String,
    val tagIdRaw: Int,
    val tagSeq: Int,
    val distanceCm: Int,
    val angleDeg: Int,
    val xCm: Int,
    val yCm: Int,
    val periodCode: Int,
    val periodText: String,
    val rssiDbm: Int,
    val statusCode: Int,
    val statusText: String,
    val extraHex: String
) {
    fun toReadableLine(): String = buildString {
        append("标签 $tagId")
        append("  X=${xCm}cm Y=${yCm}cm")
        append("  距离=${distanceCm}cm(${distanceM}m)")
        append("  角度=${angleDeg}°")
        append("  周期=$periodText")
        append("  RSSI=${rssiDbm}dBm")
        append("  状态=$statusText")
    }

    val distanceM: String get() = "%.2f".format(distanceCm / 100.0)
}

data class Gnm1000FrameResult(
    val rawHex: String,
    val cmdType: Int,
    val cmdName: String,
    val frameSeq: Int,
    val checksumValid: Boolean,
    val pdoaRecords: List<UwbPdoaRecord> = emptyList(),
    val summary: String
)

data class Gnm1000ParseBatch(
    val frames: List<Gnm1000FrameResult>,
    val pdoaRecords: List<UwbPdoaRecord>,
    val tagSummary: Map<String, TagStats>
) {
    data class TagStats(
        val tagId: String,
        val count: Int,
        val avgDistanceCm: Int,
        val avgXCm: Int,
        val avgYCm: Int,
        val lowBattery: Boolean
    )
}
