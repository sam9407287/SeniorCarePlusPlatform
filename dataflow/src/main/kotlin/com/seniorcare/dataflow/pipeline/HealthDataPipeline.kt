package com.seniorcare.dataflow.pipeline

import com.seniorcare.dataflow.config.HealthDataPipelineOptions
import com.seniorcare.dataflow.io.BigQueryIO
import com.seniorcare.dataflow.io.RedisBatchWriteTransform
import com.seniorcare.dataflow.transforms.GlobalDeduplicationTransform
import com.seniorcare.dataflow.transforms.ParseAndRouteTransform
import com.seniorcare.shared.models.base.IoTMessage
import mu.KotlinLogging
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO
import org.apache.beam.sdk.transforms.ParDo
import org.apache.beam.sdk.transforms.windowing.FixedWindows
import org.apache.beam.sdk.transforms.windowing.Window
import org.joda.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * 健康數據處理 Pipeline
 * 
 * 數據流：
 * Pub/Sub → Parse & Route → Window(5s) → Deduplicate → [Redis + BigQuery]
 * 
 * MVP 支持兩種數據類型：
 * - 300B: 生理數據（心率、血壓、血氧等）
 * - diaper DV1: 尿布數據（溫度、濕度等）
 */
class HealthDataPipeline(
    private val options: HealthDataPipelineOptions
) {
    
    fun build(): Pipeline {
        logger.info { "Building health data pipeline" }
        logger.info { "Input: ${options.getInputSubscription()}" }
        logger.info { "BigQuery: ${options.getBigQueryDataset()}" }
        logger.info { "Redis: ${options.getRedisHost()}:${options.getRedisPort()}" }
        
        val pipeline = Pipeline.create(options)
        
        // 1. 從 Pub/Sub 讀取數據
        val rawMessages = pipeline
            .apply(
                "ReadFromPubSub",
                PubsubIO.readStrings()
                    .fromSubscription(options.getInputSubscription())
            )
        
        // 2. 解析和路由（根據 content 字段自動識別類型）
        val parseResult = rawMessages
            .apply(
                "ParseAndRoute",
                ParDo.of(ParseAndRouteTransform())
                    .withOutputTags(
                        ParseAndRouteTransform.VALID_TAG,
                        org.apache.beam.sdk.values.TupleTagList.of(ParseAndRouteTransform.INVALID_TAG)
                    )
            )
        
        val validData = parseResult.get(ParseAndRouteTransform.VALID_TAG)
        val invalidData = parseResult.get(ParseAndRouteTransform.INVALID_TAG)
        
        // 3. 窗口化（5秒固定窗口，匹配數據發送頻率）
        val windowedData = validData.apply(
            "WindowInto5Seconds",
            Window.into<IoTMessage>(
                FixedWindows.of(Duration.standardSeconds(5))
            )
        )
        
        // 4. 去重
        val dedupedData = if (options.getEnableDeduplication()) {
            windowedData.apply(
                "DeduplicateData",
                ParDo.of(
                    GlobalDeduplicationTransform(
                        dedupWindowSeconds = options.getDeduplicationWindowSeconds().toLong()
                    )
                )
            )
        } else {
            windowedData
        }
        
        // 5a. 寫入 BigQuery（動態表路由）
        dedupedData.apply(
            "WriteToBigQuery",
            BigQueryIO.WriteDynamic(
                projectId = options.project,
                dataset = options.getBigQueryDataset()
            )
        )
        
        // 5b. 寫入 Redis（最新數據 + 時間序列）
        dedupedData.apply(
            "WriteToRedis",
            ParDo.of(
                RedisBatchWriteTransform(
                    redisHost = options.getRedisHost(),
                    redisPort = options.getRedisPort(),
                    redisPassword = options.getRedisPassword(),
                    ttlSeconds = options.getRedisTtlSeconds(),
                    batchSize = 100
                )
            )
        )
        
        // 6. 處理無效數據（發送到 Dead Letter Topic）
        val deadLetterTopic = options.getDeadLetterTopic()
        if (deadLetterTopic != null) {
            invalidData.apply(
                "WriteToDeadLetter",
                PubsubIO.writeStrings()
                    .to(deadLetterTopic)
            )
            logger.info { "Invalid data will be sent to: $deadLetterTopic" }
        }
        
        logger.info { "Pipeline built successfully" }
        return pipeline
    }
    
    companion object {
        /**
         * 創建並運行 Pipeline
         */
        fun run(options: HealthDataPipelineOptions) {
            val pipeline = HealthDataPipeline(options).build()
            val result = pipeline.run()
            
            logger.info { "Pipeline started" }
            
            // 等待 Pipeline 完成（僅在 DirectRunner 時）
            if (options.runner.simpleName == "DirectRunner") {
                result.waitUntilFinish()
                logger.info { "Pipeline finished" }
            }
        }
    }
}
