package com.seniorcare.shared.models.base

/**
 * 消息类型枚举（MVP：只有两种）
 * 未来可扩展：LOCATION, ALERT, CONFIG 等
 */
enum class MessageType(
    /**
     * content 字段中的关键字
     */
    val contentKeyword: String,
    
    /**
     * BigQuery 表名
     */
    val bigQueryTable: String,
    
    /**
     * Redis Key 前缀
     */
    val redisPrefix: String
) {
    /**
     * 生理数据（心率、血压、血氧等）
     */
    VITAL_SIGN("300B", "vital_signs", "vitals"),
    
    /**
     * 尿布数据（温度、湿度、按钮状态）
     */
    DIAPER("diaper DV1", "diaper_status", "diaper");

    companion object {
        /**
         * 根据 content 字段识别消息类型
         * 
         * @param content JSON 中的 content 字段值
         * @return 匹配的消息类型，如果无法识别则返回 null
         */
        fun fromContent(content: String): MessageType? {
            return values().find {
                content.contains(it.contentKeyword, ignoreCase = true)
            }
        }
    }
}

