package com.fuusy.project.ui

/**
 * 解析 ZLM 等直播地址，并生成按优先级排列的播放候选（VLC RTSP → VLC FLV → Exo HLS）。
 */
object StreamUrlResolver {

    data class PlaybackTarget(
        val url: String,
        /** true=ExoPlayer(HLS/HTTP)，false=VLC(RTSP/FLV 等) */
        val useExo: Boolean,
        val label: String = if (useExo) "Exo" else "VLC"
    )

    /** 兼容旧调用：返回首选候选 */
    fun resolve(url: String?): PlaybackTarget? = fallbackTargets(url).firstOrNull()

    /**
     * 按优先级返回播放候选。HTTP-FLV 先试 RTSP（与 liangjiang 一致），再试原始 FLV，最后 Exo HLS。
     */
    fun fallbackTargets(url: String?): List<PlaybackTarget> {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()

        return when {
            raw.contains(".m3u8", ignoreCase = true) ->
                listOf(PlaybackTarget(raw, useExo = true, label = "Exo-HLS"))

            raw.contains(".live.flv", ignoreCase = true) -> {
                val candidates = mutableListOf<PlaybackTarget>()
                flvToRtsp(raw)?.let {
                    candidates += PlaybackTarget(it, useExo = false, label = "VLC-RTSP")
                }
                candidates += PlaybackTarget(raw, useExo = false, label = "VLC-FLV")
                candidates += PlaybackTarget(flvToHls(raw), useExo = true, label = "Exo-HLS")
                candidates
            }

            raw.startsWith("rtsp://", ignoreCase = true) ->
                listOf(PlaybackTarget(raw, useExo = false, label = "VLC-RTSP"))

            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) ->
                listOf(PlaybackTarget(raw, useExo = true, label = "Exo-HTTP"))

            else -> listOf(PlaybackTarget(raw, useExo = false, label = "VLC"))
        }
    }

    /** ZLM: .../xxx.live.flv → .../xxx/hls.m3u8 */
    fun flvToHls(flvUrl: String): String {
        return flvUrl.replace(Regex("\\.live\\.flv$", RegexOption.IGNORE_CASE), "/hls.m3u8")
    }

    /** ZLM: http://host:8080/rtp/id.live.flv → rtsp://host:554/rtp/id */
    fun flvToRtsp(flvUrl: String): String? {
        val match = FLV_PATTERN.find(flvUrl) ?: return null
        val host = match.groupValues[1]
        val path = match.groupValues[2]
        return "rtsp://$host:554/$path"
    }

    private val FLV_PATTERN = Regex(
        """https?://([^:/]+)(?::\d+)?/(.+?)\.live\.flv$""",
        RegexOption.IGNORE_CASE
    )
}
