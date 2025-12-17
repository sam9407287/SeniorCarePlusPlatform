package com.seniorcare.dataflow.io

import com.google.api.services.bigquery.model.TableFieldSchema
import com.google.api.services.bigquery.model.TableRow
import com.google.api.services.bigquery.model.TableSchema
import com.seniorcare.shared.models.base.IoTMessage
import com.seniorcare.shared.models.base.MessageType
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO as BeamBigQueryIO
import org.apache.beam.sdk.io.gcp.bigquery.DynamicDestinations
import org.apache.beam.sdk.io.gcp.bigquery.TableDestination
import org.apache.beam.sdk.transforms.DoFn
import org.apache.beam.sdk.transforms.PTransform
import org.apache.beam.sdk.transforms.SerializableFunction
import org.apache.beam.sdk.values.PCollection
import org.apache.beam.sdk.values.ValueInSingleWindow

/**
 * BigQuery 寫入轉換
 * 支持動態表路由：根據消息類型寫入不同的表
 */
object BigQueryIO {
    
    /**
     * 創建 VitalSign 表結構
     */
    fun createVitalSignTableSchema(): TableSchema {
        return TableSchema().setFields(
            listOf(
                TableFieldSchema().setName("content").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("gateway_id").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("device_id").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("mac").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("sos").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("heart_rate").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("sp_o2").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("bp_systolic").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("bp_diastolic").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("skin_temp").setType("FLOAT").setMode("NULLABLE"),
                TableFieldSchema().setName("room_temp").setType("FLOAT").setMode("NULLABLE"),
                TableFieldSchema().setName("steps").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("sleep_time").setType("STRING").setMode("NULLABLE"),
                TableFieldSchema().setName("wake_time").setType("STRING").setMode("NULLABLE"),
                TableFieldSchema().setName("light_sleep_min").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("deep_sleep_min").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("move").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("wear").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("battery_level").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("serial_no").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("timestamp").setType("TIMESTAMP").setMode("REQUIRED"),
                TableFieldSchema().setName("message_type").setType("STRING").setMode("REQUIRED")
            )
        )
    }
    
    /**
     * 創建 Diaper 表結構
     */
    fun createDiaperTableSchema(): TableSchema {
        return TableSchema().setFields(
            listOf(
                TableFieldSchema().setName("content").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("gateway_id").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("device_id").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("mac").setType("STRING").setMode("REQUIRED"),
                TableFieldSchema().setName("name").setType("STRING").setMode("NULLABLE"),
                TableFieldSchema().setName("firmware_version").setType("STRING").setMode("NULLABLE"),
                TableFieldSchema().setName("temperature").setType("FLOAT").setMode("NULLABLE"),
                TableFieldSchema().setName("humidity").setType("FLOAT").setMode("NULLABLE"),
                TableFieldSchema().setName("button").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("button_status").setType("STRING").setMode("NULLABLE"),
                TableFieldSchema().setName("diaper_status").setType("STRING").setMode("NULLABLE"),
                TableFieldSchema().setName("message_index").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("acknowledgement").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("battery_level").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("serial_no").setType("INTEGER").setMode("NULLABLE"),
                TableFieldSchema().setName("timestamp").setType("TIMESTAMP").setMode("REQUIRED"),
                TableFieldSchema().setName("message_type").setType("STRING").setMode("REQUIRED")
            )
        )
    }
    
    /**
     * 轉換為 TableRow
     */
    class ConvertToTableRow : DoFn<IoTMessage, TableRow>() {
        @ProcessElement
        fun processElement(
            @Element element: IoTMessage,
            outputReceiver: OutputReceiver<TableRow>
        ) {
            val dataMap = element.toBigQueryMap()
            val row = TableRow()
            dataMap.forEach { (key, value) ->
                row.set(key, value)
            }
            outputReceiver.output(row)
        }
    }
    
    /**
     * 動態目標路由
     */
    class IoTMessageDynamicDestinations(
        private val projectId: String,
        private val dataset: String
    ) : DynamicDestinations<IoTMessage, MessageType>() {
        
        override fun getDestination(element: ValueInSingleWindow<IoTMessage>): MessageType {
            return element.value.getMessageType()
        }
        
        override fun getTable(destination: MessageType): TableDestination {
            val tableName = destination.bigQueryTable
            val tableSpec = "$projectId:$dataset.$tableName"
            return TableDestination(tableSpec, "IoT ${destination.name} data")
        }
        
        override fun getSchema(destination: MessageType): TableSchema {
            return when (destination) {
                MessageType.VITAL_SIGN -> createVitalSignTableSchema()
                MessageType.DIAPER -> createDiaperTableSchema()
            }
        }
    }
    
    /**
     * 寫入 BigQuery 的 PTransform（動態路由版本）
     */
    class WriteDynamic(
        private val projectId: String,
        private val dataset: String,
        private val writeDisposition: BeamBigQueryIO.Write.WriteDisposition = 
            BeamBigQueryIO.Write.WriteDisposition.WRITE_APPEND,
        private val createDisposition: BeamBigQueryIO.Write.CreateDisposition = 
            BeamBigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED
    ) : PTransform<PCollection<IoTMessage>, org.apache.beam.sdk.values.PDone>() {
        
        override fun expand(input: PCollection<IoTMessage>): org.apache.beam.sdk.values.PDone {
            return input
                .apply("ConvertToTableRow", org.apache.beam.sdk.transforms.ParDo.of(ConvertToTableRow()))
                .apply(
                    "WriteToBigQuery",
                    BeamBigQueryIO.writeTableRows()
                        .to { row ->
                            val messageType = row.get("message_type") as String
                            val destination = MessageType.valueOf(messageType)
                            val tableName = destination.bigQueryTable
                            "$projectId:$dataset.$tableName"
                        }
                        .withFormatFunction(SerializableFunction { it })
                        .withWriteDisposition(writeDisposition)
                        .withCreateDisposition(createDisposition)
                        .withMethod(BeamBigQueryIO.Write.Method.STREAMING_INSERTS)
                )
        }
    }
}
