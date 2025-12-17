package com.seniorcare.dataflow.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import java.time.Instant

/**
 * 原始 Gateway 數據 (4層結構)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GatewayRawData(
    @JsonProperty("gateway_id") val gatewayId: String,
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("MAC") val mac: String? = null,
    @JsonProperty("SOS") val sos: Int? = null,
    @JsonProperty("hr") val heartRate: Int? = null,
    @JsonProperty("spO2") val spO2: Int? = null,
    @JsonProperty("bp syst") val bpSystolic: Int? = null,
    @JsonProperty("bp diast") val bpDiastolic: Int? = null,
    @JsonProperty("skin temp") val skinTemp: Double? = null,
    @JsonProperty("room temp") val roomTemp: Double? = null,
    @JsonProperty("steps") val steps: Int? = null,
    @JsonProperty("sleep time") val sleepTime: String? = null,
    @JsonProperty("wake time") val wakeTime: String? = null,
    @JsonProperty("light sleep (min)") val lightSleepMin: Int? = null,
    @JsonProperty("deep sleep (min)") val deepSleepMin: Int? = null,
    @JsonProperty("move") val move: Int? = null,
    @JsonProperty("wear") val wear: Int? = null,
    @JsonProperty("battery level") val batteryLevel: Int? = null,
    @JsonProperty("serial no") val serialNo: Int? = null
) : Serializable

/**
 * 扁平化的健康數據 (2層結構)
 * 用於存儲到 BigQuery 和 Redis
 */
data class FlattenedHealthData(
    // 第1層：設備基本信息
    val deviceId: String,
    val deviceType: String, // "gateway" or "anchor"
    val gatewayId: String,
    val mac: String? = null,
    val serialNo: Int? = null,
    
    // 第2層：健康數據
    val content: String? = null,
    val sos: Int? = null,
    val heartRate: Int? = null,
    val spO2: Int? = null,
    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,
    val skinTemp: Double? = null,
    val roomTemp: Double? = null,
    val steps: Int? = null,
    val sleepTime: String? = null,
    val wakeTime: String? = null,
    val lightSleepMin: Int? = null,
    val deepSleepMin: Int? = null,
    val move: Int? = null,
    val wear: Int? = null,
    val batteryLevel: Int? = null,
    
    // 元數據
    val timestamp: String = Instant.now().toString(),
    val processingTime: String = Instant.now().toString()
) : Serializable {
    
    /**
     * 生成去重鍵
     * 基於 deviceId + gatewayId + 數據內容的哈希
     */
    fun deduplicationKey(): String {
        val dataHash = listOf(
            heartRate, spO2, bpSystolic, bpDiastolic,
            skinTemp, roomTemp, steps, batteryLevel
        ).hashCode()
        
        return "$deviceId:$gatewayId:$dataHash"
    }
    
    /**
     * 轉換為 Redis 鍵
     */
    fun redisKey(): String = "health:$deviceType:$deviceId"
    
    /**
     * 檢查數據是否有效
     */
    fun isValid(): Boolean {
        return deviceId.isNotBlank() && 
               gatewayId.isNotBlank() &&
               (heartRate != null || spO2 != null || steps != null)
    }
    
    /**
     * 轉換為 Map 用於 BigQuery
     */
    fun toBigQueryMap(): Map<String, Any?> {
        return mapOf(
            "device_id" to deviceId,
            "device_type" to deviceType,
            "gateway_id" to gatewayId,
            "mac" to mac,
            "serial_no" to serialNo,
            "content" to content,
            "sos" to sos,
            "heart_rate" to heartRate,
            "spo2" to spO2,
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
            "timestamp" to timestamp,
            "processing_time" to processingTime
        )
    }
}

/**
 * BigQuery 表結構
 */
object BigQuerySchema {
    val HEALTH_DATA_SCHEMA = """
        [
          {"name": "device_id", "type": "STRING", "mode": "REQUIRED"},
          {"name": "device_type", "type": "STRING", "mode": "REQUIRED"},
          {"name": "gateway_id", "type": "STRING", "mode": "REQUIRED"},
          {"name": "mac", "type": "STRING", "mode": "NULLABLE"},
          {"name": "serial_no", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "content", "type": "STRING", "mode": "NULLABLE"},
          {"name": "sos", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "heart_rate", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "spo2", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "bp_systolic", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "bp_diastolic", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "skin_temp", "type": "FLOAT", "mode": "NULLABLE"},
          {"name": "room_temp", "type": "FLOAT", "mode": "NULLABLE"},
          {"name": "steps", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "sleep_time", "type": "STRING", "mode": "NULLABLE"},
          {"name": "wake_time", "type": "STRING", "mode": "NULLABLE"},
          {"name": "light_sleep_min", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "deep_sleep_min", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "move", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "wear", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "battery_level", "type": "INTEGER", "mode": "NULLABLE"},
          {"name": "timestamp", "type": "TIMESTAMP", "mode": "REQUIRED"},
          {"name": "processing_time", "type": "TIMESTAMP", "mode": "REQUIRED"}
        ]
    """.trimIndent()
}

