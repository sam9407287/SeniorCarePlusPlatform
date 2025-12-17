package com.seniorcare.shared.models.diaper

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.seniorcare.shared.models.base.IoTMessage
import com.seniorcare.shared.models.base.MessageType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Diaper DV1 尿布数据模型
 * 包含温度、湿度、按钮状态等
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class DiaperData(
    @SerialName("content")
    @JsonProperty("content")
    override val content: String = "diaper DV1",
    
    @SerialName("gateway id")
    @JsonProperty("gateway id")
    override val gatewayId: String,
    
    @SerialName("MAC")
    @JsonProperty("MAC")
    val mac: String,
    
    @SerialName("name")
    @JsonProperty("name")
    val name: String? = null,
    
    @SerialName("fw ver")
    @JsonProperty("fw ver")
    val firmwareVersion: String? = null,
    
    @SerialName("temp")
    @JsonProperty("temp")
    val temperature: Double? = null,
    
    @SerialName("humi")
    @JsonProperty("humi")
    val humidity: Double? = null,
    
    @SerialName("button")
    @JsonProperty("button")
    val button: Int? = null,
    
    @SerialName("mssg idx")
    @JsonProperty("mssg idx")
    val messageIndex: Int? = null,
    
    @SerialName("ack")
    @JsonProperty("ack")
    val acknowledgement: Int? = null,
    
    @SerialName("battery level")
    @JsonProperty("battery level")
    val batteryLevel: Int? = null,
    
    @SerialName("serial no")
    @JsonProperty("serial no")
    val serialNo: Int? = null,
    
    @SerialName("timestamp")
    @JsonProperty("timestamp")
    @kotlinx.serialization.Transient
    override val timestamp: Long = System.currentTimeMillis()
) : IoTMessage {
    
    override val deviceId: String
        get() = mac
    
    /**
     * 按钮状态解释
     */
    fun getButtonStatus(): String {
        return when (button) {
            0 -> "未按下"
            1 -> "短按"
            2 -> "长按"
            else -> "未知"
        }
    }
    
    /**
     * 尿布状态推断（基于湿度）
     */
    fun getDiaperStatus(): String {
        return when {
            humidity == null -> "未知"
            humidity < 40.0 -> "干燥"
            humidity < 60.0 -> "正常"
            humidity < 80.0 -> "潮湿"
            else -> "需要更换"
        }
    }
    
    /**
     * 验证数据有效性
     */
    override fun isValid(): Boolean {
        // 基本字段验证
        if (gatewayId.isBlank() || mac.isBlank()) {
            return false
        }
        
        // 至少要有温度或湿度数据
        if (temperature == null && humidity == null) {
            return false
        }
        
        // 温度范围验证（如果存在）
        if (temperature != null && (temperature < 0.0 || temperature > 60.0)) {
            return false
        }
        
        // 湿度范围验证（如果存在）
        if (humidity != null && (humidity < 0.0 || humidity > 100.0)) {
            return false
        }
        
        // 按钮状态验证（如果存在）
        if (button != null && (button < 0 || button > 2)) {
            return false
        }
        
        return true
    }
    
    /**
     * 转换为 BigQuery Map
     */
    override fun toBigQueryMap(): Map<String, Any?> {
        return mapOf(
            "content" to content,
            "gateway_id" to gatewayId,
            "device_id" to mac,
            "mac" to mac,
            "name" to name,
            "firmware_version" to firmwareVersion,
            "temperature" to temperature,
            "humidity" to humidity,
            "button" to button,
            "button_status" to getButtonStatus(),
            "diaper_status" to getDiaperStatus(),
            "message_index" to messageIndex,
            "acknowledgement" to acknowledgement,
            "battery_level" to batteryLevel,
            "serial_no" to serialNo,
            "timestamp" to Instant.ofEpochMilli(timestamp).toString(),
            "message_type" to "DIAPER"
        )
    }
    
    /**
     * Redis Key（用于存储最新数据）
     */
    override fun redisKey(): String {
        return "${getMessageType().redisPrefix}:$mac"
    }
    
    /**
     * 去重 Key（基于设备ID和数据哈希）
     */
    override fun deduplicationKey(): String {
        val dataHash = listOf(
            temperature, humidity, button, batteryLevel
        ).hashCode()
        
        return "$gatewayId:$mac:$dataHash"
    }
    
    /**
     * 获取消息类型
     */
    override fun getMessageType(): MessageType = MessageType.DIAPER
}

