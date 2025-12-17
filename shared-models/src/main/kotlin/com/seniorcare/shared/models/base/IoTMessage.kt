package com.seniorcare.shared.models.base

/**
 * IoT 消息基础接口
 * 所有消息类型（生理、尿布等）都实现此接口
 */
interface IoTMessage : java.io.Serializable {
    /**
     * 消息内容标识（如 "300B", "diaper DV1"）
     */
    val content: String
    
    /**
     * Gateway ID
     */
    val gatewayId: String
    
    /**
     * 设备 ID（MAC 地址或序列号）
     */
    val deviceId: String
    
    /**
     * 时间戳（Unix 毫秒）
     */
    val timestamp: Long
    
    /**
     * 验证数据有效性
     */
    fun isValid(): Boolean
    
    /**
     * 转换为 BigQuery Map
     */
    fun toBigQueryMap(): Map<String, Any?>
    
    /**
     * Redis Key（用于存储最新数据）
     */
    fun redisKey(): String
    
    /**
     * 去重 Key（用于判断是否为重复数据）
     */
    fun deduplicationKey(): String
    
    /**
     * 获取消息类型
     */
    fun getMessageType(): MessageType
}

