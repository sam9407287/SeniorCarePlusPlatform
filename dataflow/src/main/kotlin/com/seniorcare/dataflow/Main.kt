package com.seniorcare.dataflow

import com.seniorcare.dataflow.config.HealthDataPipelineOptions
import com.seniorcare.dataflow.pipeline.HealthDataPipeline
import mu.KotlinLogging
import org.apache.beam.sdk.options.PipelineOptionsFactory

private val logger = KotlinLogging.logger {}

/**
 * 主入口
 * 
 * 使用範例：
 * 
 * 開發環境（本地 DirectRunner）：
 * ```
 * ./gradlew run --args="
 *   --runner=DirectRunner
 *   --inputSubscription=projects/your-project/subscriptions/health-data-sub
 *   --bigQueryTable=your-project:health.patient_data
 *   --redisHost=localhost
 *   --redisPort=6379
 * "
 * ```
 * 
 * 生產環境（GCP Dataflow）：
 * ```
 * ./gradlew fatJar
 * 
 * gcloud dataflow flex-template run health-data-pipeline \
 *   --template-file-gcs-location=gs://your-bucket/templates/health-data-pipeline.json \
 *   --region=asia-east1 \
 *   --parameters inputSubscription=projects/your-project/subscriptions/health-data-sub \
 *   --parameters bigQueryTable=your-project:health.patient_data \
 *   --parameters redisHost=your-redis-host \
 *   --parameters redisPort=6379
 * ```
 */
fun main(args: Array<String>) {
    logger.info { "==================================================" }
    logger.info { "  Senior Care Plus - Health Data Pipeline        " }
    logger.info { "  Version: 1.0.0                                 " }
    logger.info { "==================================================" }
    
    // 解析命令行參數
    val options = PipelineOptionsFactory
        .fromArgs(*args)
        .withValidation()
        .`as`(HealthDataPipelineOptions::class.java)
    
    // 記錄配置
    logger.info { "Pipeline Configuration:" }
    logger.info { "  Runner: ${options.runner.simpleName}" }
    logger.info { "  Project: ${options.project}" }
    logger.info { "  Region: ${options.region}" }
    logger.info { "  Input Subscription: ${options.getInputSubscription()}" }
    logger.info { "  BigQuery Table: ${options.getBigQueryTable()}" }
    logger.info { "  Redis: ${options.getRedisHost()}:${options.getRedisPort()}" }
    logger.info { "  Deduplication Enabled: ${options.getEnableDeduplication()}" }
    logger.info { "  Validation Enabled: ${options.getEnableValidation()}" }
    
    try {
        // 創建並運行 Pipeline
        HealthDataPipeline.run(options)
        
        logger.info { "Pipeline execution completed successfully" }
        
    } catch (e: Exception) {
        logger.error(e) { "Pipeline execution failed" }
        throw e
    }
}

