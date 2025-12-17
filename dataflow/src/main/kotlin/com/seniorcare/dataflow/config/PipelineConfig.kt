package com.seniorcare.dataflow.config

import org.apache.beam.sdk.options.Default
import org.apache.beam.sdk.options.Description
import org.apache.beam.sdk.options.PipelineOptions
import org.apache.beam.sdk.options.Validation

/**
 * Pipeline 配置選項
 */
interface HealthDataPipelineOptions : PipelineOptions {
    
    // ============ 輸入配置 ============
    
    @Description("Pub/Sub subscription to read from")
    @Validation.Required
    fun getInputSubscription(): String
    fun setInputSubscription(value: String)
    
    @Description("Input topic (alternative to subscription)")
    fun getInputTopic(): String?
    fun setInputTopic(value: String?)
    
    // ============ 輸出配置 ============
    
    @Description("BigQuery dataset name (tables will be created dynamically)")
    @Validation.Required
    fun getBigQueryDataset(): String
    fun setBigQueryDataset(value: String)
    
    @Description("Redis host")
    @Default.String("localhost")
    fun getRedisHost(): String
    fun setRedisHost(value: String)
    
    @Description("Redis port")
    @Default.Integer(6379)
    fun getRedisPort(): Int
    fun setRedisPort(value: Int)
    
    @Description("Redis password (optional)")
    fun getRedisPassword(): String?
    fun setRedisPassword(value: String?)
    
    // ============ 處理配置 ============
    
    @Description("Enable deduplication")
    @Default.Boolean(true)
    fun getEnableDeduplication(): Boolean
    fun setEnableDeduplication(value: Boolean)
    
    @Description("Deduplication window in seconds")
    @Default.Integer(5)
    fun getDeduplicationWindowSeconds(): Int
    fun setDeduplicationWindowSeconds(value: Int)
    
    @Description("Enable validation")
    @Default.Boolean(true)
    fun getEnableValidation(): Boolean
    fun setEnableValidation(value: Boolean)
    
    @Description("Redis TTL in seconds")
    @Default.Integer(3600)
    fun getRedisTtlSeconds(): Int
    fun setRedisTtlSeconds(value: Int)
    
    // ============ 錯誤處理配置 ============
    
    @Description("Dead letter topic for failed messages")
    fun getDeadLetterTopic(): String?
    fun setDeadLetterTopic(value: String?)
    
    @Description("GCS path for invalid data")
    fun getInvalidDataPath(): String?
    fun setInvalidDataPath(value: String?)
}

/**
 * 配置工廠
 */
object ConfigFactory {
    
    /**
     * 開發環境配置
     */
    fun createDevConfig(options: HealthDataPipelineOptions): DevConfig {
        return DevConfig(
            projectId = options.project,
            inputSubscription = options.getInputSubscription(),
            bigQueryDataset = options.getBigQueryDataset(),
            redisHost = options.getRedisHost(),
            redisPort = options.getRedisPort(),
            redisPassword = options.getRedisPassword(),
            enableDeduplication = options.getEnableDeduplication(),
            deduplicationWindowSeconds = options.getDeduplicationWindowSeconds(),
            enableValidation = options.getEnableValidation(),
            redisTtlSeconds = options.getRedisTtlSeconds()
        )
    }
    
    /**
     * 生產環境配置
     */
    fun createProdConfig(options: HealthDataPipelineOptions): ProdConfig {
        return ProdConfig(
            projectId = options.project,
            region = options.region,
            inputSubscription = options.getInputSubscription(),
            bigQueryDataset = options.getBigQueryDataset(),
            redisHost = options.getRedisHost(),
            redisPort = options.getRedisPort(),
            redisPassword = options.getRedisPassword(),
            deadLetterTopic = options.getDeadLetterTopic(),
            invalidDataPath = options.getInvalidDataPath(),
            enableDeduplication = options.getEnableDeduplication(),
            deduplicationWindowSeconds = options.getDeduplicationWindowSeconds(),
            enableValidation = options.getEnableValidation(),
            redisTtlSeconds = options.getRedisTtlSeconds()
        )
    }
}

/**
 * 開發環境配置
 */
data class DevConfig(
    val projectId: String,
    val inputSubscription: String,
    val bigQueryDataset: String,
    val redisHost: String = "localhost",
    val redisPort: Int = 6379,
    val redisPassword: String? = null,
    val enableDeduplication: Boolean = true,
    val deduplicationWindowSeconds: Int = 5,
    val enableValidation: Boolean = true,
    val redisTtlSeconds: Int = 3600
)

/**
 * 生產環境配置
 */
data class ProdConfig(
    val projectId: String,
    val region: String,
    val inputSubscription: String,
    val bigQueryDataset: String,
    val redisHost: String,
    val redisPort: Int = 6379,
    val redisPassword: String? = null,
    val deadLetterTopic: String? = null,
    val invalidDataPath: String? = null,
    val enableDeduplication: Boolean = true,
    val deduplicationWindowSeconds: Int = 5,
    val enableValidation: Boolean = true,
    val redisTtlSeconds: Int = 3600
)

