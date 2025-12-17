package com.seniorcare.dataflow.transforms

import com.seniorcare.shared.models.base.IoTMessage
import mu.KotlinLogging
import org.apache.beam.sdk.state.*
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.windowing.BoundedWindow
import org.apache.beam.sdk.values.KV
import org.joda.time.Duration
import org.joda.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * 去重轉換
 * 使用有狀態處理去除重複數據
 * 
 * 策略：
 * 1. 使用 5 秒窗口去重（因為病患每 5 秒發送一次）
 * 2. 基於設備ID + 數據哈希值進行去重
 * 3. 保留最新的數據
 */
class DeduplicationTransform : DoFn<KV<String, IoTMessage>, IoTMessage>() {
    
    companion object {
        // 去重窗口時間（秒）
        private const val DEDUP_WINDOW_SECONDS = 5L
        
        // 狀態過期時間（保留1小時的歷史）
        private val STATE_TTL = Duration.standardHours(1)
    }
    
    // 狀態規範：存儲最後看到的數據時間戳
    @StateId("lastSeen")
    private val lastSeenSpec = StateSpecs.value(
        StringUtf8Coder.of()
    )
    
    // 計時器規範：用於清理過期狀態
    @TimerId("cleanup")
    private val cleanupTimerSpec = TimerSpecs.timer(TimeDomain.PROCESSING_TIME)
    
    @ProcessElement
    fun processElement(
        @Element element: KV<String, IoTMessage>,
        @StateId("lastSeen") lastSeenState: ValueState<String>,
        @TimerId("cleanup") cleanupTimer: Timer,
        outputReceiver: OutputReceiver<IoTMessage>,
        window: BoundedWindow
    ) {
        val key = element.key
        val data = element.value
        val currentTime = Instant.now()
        
        // 讀取上次看到的時間戳
        val lastSeenTimestamp = lastSeenState.read()
        
        val shouldOutput = if (lastSeenTimestamp == null) {
            // 第一次看到這個鍵，輸出數據
            logger.debug { "First occurrence of key: $key" }
            true
        } else {
            // 檢查是否在去重窗口內
            val lastSeen = Instant.parse(lastSeenTimestamp)
            val timeDiff = Duration(lastSeen, currentTime)
            
            if (timeDiff.standardSeconds >= DEDUP_WINDOW_SECONDS) {
                // 超過去重窗口，輸出數據
                logger.debug { "Key $key outside dedup window (${timeDiff.standardSeconds}s)" }
                true
            } else {
                // 在去重窗口內，過濾重複數據
                logger.debug { "Filtering duplicate key: $key (${timeDiff.standardSeconds}s)" }
                false
            }
        }
        
        if (shouldOutput) {
            // 更新狀態
            lastSeenState.write(currentTime.toString())
            
            // 設置清理計時器
            cleanupTimer.offset(STATE_TTL).setRelative()
            
            // 輸出數據
            outputReceiver.output(data)
            
            logger.info { 
                "Outputting ${data.getMessageType()} data for device ${data.deviceId}" 
            }
        }
    }
    
    @OnTimer("cleanup")
    fun onCleanup(
        @StateId("lastSeen") lastSeenState: ValueState<String>,
        @TimerId("cleanup") cleanupTimer: Timer
    ) {
        // 清理過期狀態
        lastSeenState.clear()
        logger.debug { "Cleaned up expired state" }
    }
}

/**
 * 全局去重轉換（使用內存緩存）
 * 適用於需要跨窗口去重的場景
 */
class GlobalDeduplicationTransform(
    private val dedupWindowSeconds: Long = 5L
) : DoFn<IoTMessage, IoTMessage>() {
    
    // 使用簡單的 Map 進行去重
    @Transient
    private var seenKeys: MutableMap<String, Long>? = null
    
    @Setup
    fun setup() {
        seenKeys = mutableMapOf()
    }
    
    @ProcessElement
    fun processElement(
        @Element element: IoTMessage,
        outputReceiver: OutputReceiver<IoTMessage>
    ) {
        val key = element.deduplicationKey()
        val currentTimeMs = System.currentTimeMillis()
        
        val cache = seenKeys ?: mutableMapOf()
        val lastSeenMs = cache[key]
        
        val shouldOutput = if (lastSeenMs == null) {
            true
        } else {
            val timeDiffSeconds = (currentTimeMs - lastSeenMs) / 1000
            timeDiffSeconds >= dedupWindowSeconds
        }
        
        if (shouldOutput) {
            cache[key] = currentTimeMs
            outputReceiver.output(element)
            
            logger.info { 
                "Output ${element.getMessageType()} data: device=${element.deviceId}, type=${element.getMessageType()}" 
            }
        } else {
            logger.debug { "Filtered duplicate: $key" }
        }
        
        // 清理過期的鍵（簡單的清理策略）
        if (cache.size > 100000) {
            val expireTimeMs = currentTimeMs - (dedupWindowSeconds * 1000 * 10) // 保留 10 倍窗口時間
            cache.entries.removeIf { it.value < expireTimeMs }
            logger.info { "Cleaned cache, remaining size: ${cache.size}" }
        }
    }
    
    @Teardown
    fun teardown() {
        seenKeys?.clear()
        seenKeys = null
    }
}
