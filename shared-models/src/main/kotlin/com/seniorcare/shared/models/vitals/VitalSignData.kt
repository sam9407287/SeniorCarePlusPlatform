package com.seniorcare.shared.models.vitals

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.seniorcare.shared.models.base.IoTMessage
import com.seniorcare.shared.models.base.MessageType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 300B 生理数据模型
 * 包含心率、血压、血氧、体温、运动等数据
 */
@Serializable
@JsonIgnoreProperties(ignoreUnknown = true)
data class VitalSignData(
    @SerialName("content")
    @JsonProperty("content")
    override val content: String = "300B",
    
    @SerialName("gateway id")
    @JsonProperty("gateway id")
    override val gatewayId: String,
    
    @SerialName("MAC")
    @JsonProperty("MAC")
    val mac: String,
    
    @SerialName("SOS")
    @JsonProperty("SOS")
    val sos: Int? = null,
    
    @SerialName("hr")
    @JsonProperty("hr")
    val heartRate: Int? = null,
    
    @SerialName("spO2")
    @JsonProperty("spO2")
    val spO2: Int? = null,
    
    @SerialName("bp syst")
    @JsonProperty("bp syst")
    val bpSystolic: Int? = null,
    
    @SerialName("bp diast")
    @JsonProperty("bp diast")
    val bpDiastolic: Int? = null,
    
    @SerialName("skin temp")
    @JsonProperty("skin temp")
    val skinTemp: Double? = null,
    
    @SerialName("room temp")
    @JsonProperty("room temp")
    val roomTemp: Double? = null,
    
    @SerialName("steps")
    @JsonProperty("steps")
    val steps: Int? = null,
    
    @SerialName("sleep time")
    @JsonProperty("sleep time")
    val sleepTime: String? = null,
    
    @SerialName("wake time")
    @JsonProperty("wake time")
    val wakeTime: String? = null,
    
    @SerialName("light sleep (min)")
    @JsonProperty("light sleep (min)")
    val lightSleepMin: Int? = null,
    
    @SerialName("deep sleep (min)")
    @JsonProperty("deep sleep (min)")
    val deepSleepMin: Int? = null,
    
    @SerialName("move")
    @JsonProperty("move")
    val move: Int? = null,
    
    @SerialName("wear")
    @JsonProperty("wear")
    val wear: Int? = null,
    
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
     * 验证数据有效性
     */
    override fun isValid(): Boolean {
        // 基本字段验证
        if (gatewayId.isBlank() || mac.isBlank()) {
            return false
        }
        
        // 至少要有一个生理指标
        val hasAnyVital = heartRate != null || spO2 != null || 
                          bpSystolic != null || steps != null
        if (!hasAnyVital) {
            return false
        }
        
        // 心率范围验证（如果存在）
        if (heartRate != null && (heartRate < 30 || heartRate > 220)) {
            return false
        }
        
        // 血氧范围验证（如果存在）
        if (spO2 != null && (spO2 < 70 || spO2 > 100)) {
            return false
        }
        
        // 血压范围验证（如果存在）
        if (bpSystolic != null && (bpSystolic < 50 || bpSystolic > 250)) {
            return false
        }
        if (bpDiastolic != null && (bpDiastolic < 30 || bpDiastolic > 150)) {
            return false
        }
        
        // 体温范围验证（如果存在）
        if (skinTemp != null && (skinTemp < 30.0 || skinTemp > 45.0)) {
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
            "sos" to sos,
            "heart_rate" to heartRate,
            "sp_o2" to spO2,
            "bp_systolic" to bpSystolic,
            "bp_diastolic" to bpDiastolic,
            "skin_temp" to skinTemp,
            "room_temp" to roomTemp,
            "steps" to steps,
            "sleep_time" to sleepTime,
            "wake_time" to wakeTime,
            "light_sleep_min" to lightSleepMin,
            "deep_sleep_min" to deepSleepMin,
            "move" to move,
            "wear" to wear,
            "battery_level" to batteryLevel,
            "serial_no" to serialNo,
            "timestamp" to Instant.ofEpochMilli(timestamp).toString(),
            "message_type" to "VITAL_SIGN"
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
            heartRate, spO2, bpSystolic, bpDiastolic,
            skinTemp, roomTemp, steps, batteryLevel
        ).hashCode()
        
        return "$gatewayId:$mac:$dataHash"
    }
    
    /**
     * 获取消息类型
     */
    override fun getMessageType(): MessageType = MessageType.VITAL_SIGN
}

