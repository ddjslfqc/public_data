package com.fuusy.project.bean

import com.google.gson.annotations.SerializedName

/**
 * WebSocket 消息基类
 */
sealed class WebSocketMessage {
    data class GasData(
        @SerializedName("alarm")
        val alarm: AlarmData,
        @SerializedName("camera")
        val camera: String,
        @SerializedName("device")
        val device: String,
        @SerializedName("floor")
        val floor: String,
        @SerializedName("gas")
        val gas: GasValues,
        @SerializedName("item")
        val item: String,
        @SerializedName("name")
        val name: List<String>,
        @SerializedName("region")
        val region: String,
        @SerializedName("time")
        val time: String,
        @SerializedName("wendu")
        val wendu: Double
    ) : WebSocketMessage()

    data class AlarmData(
        @SerializedName("EX")
        val ex: Int,
        @SerializedName("O2")
        val o2: Int,
        @SerializedName("H2S")
        val h2s: Int,
        @SerializedName("CO")
        val co: Int,
        @SerializedName("TEM")
        val tem: Int
    )

    data class GasValues(
        @SerializedName("CO")
        val co: Double,
        @SerializedName("H2S")
        val h2s: Double,
        @SerializedName("O2")
        val o2: Double,
        @SerializedName("EX")
        val ex: Double,
        @SerializedName("TEM")
        val tem: Double
    )

    data class Alert(
        @SerializedName("type")
        val type: String,
        @SerializedName("message")
        val message: String,
        @SerializedName("level")
        val level: String,
        @SerializedName("timestamp")
        val timestamp: Long
    ) : WebSocketMessage()

    data class Status(
        @SerializedName("status")
        val status: String,
        @SerializedName("message")
        val message: String
    ) : WebSocketMessage()
} 