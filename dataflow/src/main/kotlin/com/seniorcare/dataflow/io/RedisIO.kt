package com.seniorcare.dataflow.io

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.seniorcare.shared.models.base.IoTMessage
import mu.KotlinLogging
import org.apache.beam.sdk.transforms.DoFn
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Redis 寫入轉換
 * 
 * 策略：
 * 1. 存儲最新數據：Hash Key = vitals:{deviceId} 或 diaper:{deviceId}
 * 2. 存儲時間序列：Sorted Set Key = timeseries:{messageType}:{deviceId}
 * 3. TTL 自動過期（1小時 = 3600秒）
 * 4. 時間序列只保留最近 720 個數據點（1小時，每5秒1個）
 */
class RedisWriteTransform(
    private val redisHost: String,
    private val redisPort: Int = 6379,
    private val redisPassword: String? = null,
    private val ttlSeconds: Int = 3600 // 1小時
) : DoFn<IoTMessage, Void>() {
    
    @Transient
    private var jedisPool: JedisPool? = null
    
    @Transient
    private var objectMapper = jacksonObjectMapper()
    
    @Setup
    fun setup() {
        logger.info { "Setting up Redis connection pool to $redisHost:$redisPort" }
        
        val poolConfig = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 2
            testOnBorrow = true
            testOnReturn = true
            testWhileIdle = true
            minEvictableIdleTime = Duration.ofMinutes(1)
            timeBetweenEvictionRuns = Duration.ofSeconds(30)
            blockWhenExhausted = true
        }
        
        jedisPool = if (redisPassword != null) {
            JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword)
        } else {
            JedisPool(poolConfig, redisHost, redisPort, 2000)
        }
        
        objectMapper = jacksonObjectMapper().apply {
            findAndRegisterModules()
        }
    }
    
    @ProcessElement
    fun processElement(@Element element: IoTMessage) {
        val pool = jedisPool ?: run {
            logger.error { "Redis pool not initialized" }
            return
        }
        
        var jedis: Jedis? = null
        try {
            jedis = pool.resource
            
            // 將數據轉換為 JSON
            val jsonData = objectMapper.writeValueAsString(element)
            
            // 1. 存儲最新數據（String Key）
            val mainKey = element.redisKey()
            jedis.setex(mainKey, ttlSeconds, jsonData)
            
            // 2. 存儲時間序列（Sorted Set）
            val timeseriesKey = "timeseries:${element.getMessageType()}:${element.deviceId}"
            val score = element.timestamp.toDouble()
            jedis.zadd(timeseriesKey, score, jsonData)
            jedis.expire(timeseriesKey, ttlSeconds)
            
            // 只保留最近 720 個數據點（1小時，5秒/個）
            jedis.zremrangeByRank(timeseriesKey, 0, -721)
            
            // 3. Gateway 索引（可選，用於查詢某個 Gateway 下的所有設備）
            val gatewayIndexKey = "gateway:${element.gatewayId}:devices"
            jedis.sadd(gatewayIndexKey, element.deviceId)
            jedis.expire(gatewayIndexKey, ttlSeconds)
            
            logger.debug { 
                "Wrote to Redis: ${element.getMessageType()} data for device ${element.deviceId}" 
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to write to Redis for device ${element.deviceId}" }
            // 不拋出異常，避免中斷整個 pipeline
        } finally {
            jedis?.close()
        }
    }
    
    @Teardown
    fun teardown() {
        logger.info { "Closing Redis connection pool" }
        jedisPool?.close()
        jedisPool = null
    }
}

/**
 * Redis 批量寫入轉換（提高性能）
 */
class RedisBatchWriteTransform(
    private val redisHost: String,
    private val redisPort: Int = 6379,
    private val redisPassword: String? = null,
    private val ttlSeconds: Int = 3600,
    private val batchSize: Int = 100
) : DoFn<IoTMessage, Void>() {
    
    @Transient
    private var jedisPool: JedisPool? = null
    
    @Transient
    private var objectMapper = jacksonObjectMapper()
    
    @Transient
    private var batch: MutableList<IoTMessage>? = null
    
    @Setup
    fun setup() {
        logger.info { "Setting up Redis batch writer to $redisHost:$redisPort" }
        
        val poolConfig = JedisPoolConfig().apply {
            maxTotal = 20
            maxIdle = 10
            minIdle = 2
        }
        
        jedisPool = if (redisPassword != null) {
            JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword)
        } else {
            JedisPool(poolConfig, redisHost, redisPort, 2000)
        }
        
        objectMapper = jacksonObjectMapper().apply {
            findAndRegisterModules()
        }
        
        batch = mutableListOf()
    }
    
    @ProcessElement
    fun processElement(@Element element: IoTMessage) {
        val currentBatch = batch ?: mutableListOf()
        currentBatch.add(element)
        
        if (currentBatch.size >= batchSize) {
            flushBatch(currentBatch)
            currentBatch.clear()
        }
    }
    
    @FinishBundle
    fun finishBundle() {
        val currentBatch = batch ?: return
        if (currentBatch.isNotEmpty()) {
            flushBatch(currentBatch)
            currentBatch.clear()
        }
    }
    
    private fun flushBatch(items: List<IoTMessage>) {
        val pool = jedisPool ?: return
        var jedis: Jedis? = null
        
        try {
            jedis = pool.resource
            val pipeline = jedis.pipelined()
            
            items.forEach { element ->
                val jsonData = objectMapper.writeValueAsString(element)
                val mainKey = element.redisKey()
                
                // 最新數據
                pipeline.setex(mainKey, ttlSeconds, jsonData)
                
                // 時間序列
                val timeseriesKey = "timeseries:${element.getMessageType()}:${element.deviceId}"
                pipeline.zadd(timeseriesKey, element.timestamp.toDouble(), jsonData)
                pipeline.expire(timeseriesKey, ttlSeconds)
                pipeline.zremrangeByRank(timeseriesKey, 0, -721)
                
                // Gateway 索引
                val gatewayIndexKey = "gateway:${element.gatewayId}:devices"
                pipeline.sadd(gatewayIndexKey, element.deviceId)
                pipeline.expire(gatewayIndexKey, ttlSeconds)
            }
            
            pipeline.sync()
            logger.info { "Flushed ${items.size} items to Redis" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to flush batch to Redis" }
        } finally {
            jedis?.close()
        }
    }
    
    @Teardown
    fun teardown() {
        jedisPool?.close()
        jedisPool = null
    }
}
