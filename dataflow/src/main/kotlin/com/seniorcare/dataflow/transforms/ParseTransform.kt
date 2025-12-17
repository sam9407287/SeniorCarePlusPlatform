package com.seniorcare.dataflow.transforms

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.seniorcare.shared.models.base.IoTMessage
import com.seniorcare.shared.models.base.MessageType
import com.seniorcare.shared.models.diaper.DiaperData
import com.seniorcare.shared.models.vitals.VitalSignData
import mu.KotlinLogging
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.values.TupleTag

private val logger = KotlinLogging.logger {}

/**
 * 解析和路由 JSON 數據
 * 根據 content 字段自動識別消息類型（300B 或 diaper DV1）
 */
class ParseAndRouteTransform : DoFn<String, IoTMessage>() {
    
    companion object {
        val VALID_TAG = TupleTag<IoTMessage>("valid")
        val INVALID_TAG = TupleTag<String>("invalid")
        
        private val objectMapper = jacksonObjectMapper().apply {
            findAndRegisterModules()
        }
    }
    
    @ProcessElement
    fun processElement(
        @Element element: String,
        outputReceiver: MultiOutputReceiver
    ) {
        try {
            // 1. 先解析為 JsonNode 以讀取 content 字段
            val jsonNode: JsonNode = objectMapper.readTree(element)
            val content = jsonNode.get("content")?.asText()
            
            if (content == null) {
                logger.warn { "Missing 'content' field in JSON: $element" }
                outputReceiver.get(INVALID_TAG).output(element)
                return
            }
            
            // 2. 根據 content 判斷消息類型
            val messageType = MessageType.fromContent(content)
            
            if (messageType == null) {
                logger.warn { "Unknown message type for content '$content': $element" }
                outputReceiver.get(INVALID_TAG).output(element)
                return
            }
            
            // 3. 解析為對應的數據類型
            val message: IoTMessage = when (messageType) {
                MessageType.VITAL_SIGN -> parseAsVitalSign(element)
                MessageType.DIAPER -> parseAsDiaper(element)
            }
            
            // 4. 驗證並輸出
            if (message.isValid()) {
                outputReceiver.get(VALID_TAG).output(message)
                logger.debug { 
                    "Successfully parsed ${message.getMessageType()} message for device ${message.deviceId}" 
                }
            } else {
                outputReceiver.get(INVALID_TAG).output(element)
                logger.warn { "Invalid ${messageType} data: $element" }
            }
            
        } catch (e: Exception) {
            outputReceiver.get(INVALID_TAG).output(element)
            logger.error(e) { "Failed to parse JSON: $element" }
        }
    }
    
    /**
     * 解析為 VitalSignData（300B）
     */
    private fun parseAsVitalSign(json: String): VitalSignData {
        return objectMapper.readValue(json)
    }
    
    /**
     * 解析為 DiaperData（diaper DV1）
     */
    private fun parseAsDiaper(json: String): DiaperData {
        return objectMapper.readValue(json)
    }
}
