#!/bin/bash
# 設置 GCP 環境 - 增強版
# 支持 MVP 的兩種數據類型：300B 生理數據 & Diaper DV1 尿布數據

set -e

PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
REGION="${GCP_REGION:-asia-east1}"
DATASET_NAME="health"
TOPIC_NAME="health-data-topic"
SUBSCRIPTION_NAME="health-data-sub"
DEAD_LETTER_TOPIC="health-data-dead-letter"

echo "====================================="
echo "  設置 GCP 環境 - SeniorCarePlus"
echo "====================================="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo "Dataset: $DATASET_NAME"
echo "====================================="

if [ "$PROJECT_ID" == "your-gcp-project-id" ]; then
    echo "❌ 請設置 GCP_PROJECT_ID 環境變量"
    echo "   export GCP_PROJECT_ID=your-actual-project-id"
    exit 1
fi

# 設置當前項目
echo "設置當前項目..."
gcloud config set project $PROJECT_ID

echo ""
echo "✅ 啟用必要的 API（可能需要 5-10 分鐘）..."
gcloud services enable dataflow.googleapis.com
gcloud services enable pubsub.googleapis.com
gcloud services enable bigquery.googleapis.com
gcloud services enable storage-api.googleapis.com
gcloud services enable compute.googleapis.com
echo "✅ API 啟用完成"

echo ""
echo "✅ 創建 Pub/Sub Topic..."
gcloud pubsub topics create $TOPIC_NAME --project=$PROJECT_ID || echo "ℹ️  Topic 已存在"

echo ""
echo "✅ 創建 Pub/Sub Subscription..."
gcloud pubsub subscriptions create $SUBSCRIPTION_NAME \
  --topic=$TOPIC_NAME \
  --project=$PROJECT_ID \
  --ack-deadline=60 \
  --message-retention-duration=7d || echo "ℹ️  Subscription 已存在"

echo ""
echo "✅ 創建 Dead Letter Topic（用於無效數據）..."
gcloud pubsub topics create $DEAD_LETTER_TOPIC --project=$PROJECT_ID || echo "ℹ️  Dead Letter Topic 已存在"

echo ""
echo "✅ 創建 BigQuery Dataset..."
bq --project_id=$PROJECT_ID mk -d \
  --location=$REGION \
  --description="健康數據分析 - 生理數據和尿布數據" \
  $DATASET_NAME || echo "ℹ️  Dataset 已存在"

echo ""
echo "✅ 創建 BigQuery 表 - vital_signs（生理數據：300B）..."
bq --project_id=$PROJECT_ID mk -t \
  --time_partitioning_field=timestamp \
  --time_partitioning_type=DAY \
  --require_partition_filter=false \
  ${DATASET_NAME}.vital_signs \
  content:STRING,gateway_id:STRING,device_id:STRING,mac:STRING,\
sos:INTEGER,heart_rate:INTEGER,sp_o2:INTEGER,\
bp_systolic:INTEGER,bp_diastolic:INTEGER,\
skin_temp:FLOAT,room_temp:FLOAT,\
steps:INTEGER,sleep_time:STRING,wake_time:STRING,\
light_sleep_min:INTEGER,deep_sleep_min:INTEGER,\
move:INTEGER,wear:INTEGER,\
battery_level:INTEGER,serial_no:INTEGER,\
timestamp:TIMESTAMP,message_type:STRING \
  || echo "ℹ️  vital_signs 表已存在"

echo ""
echo "✅ 創建 BigQuery 表 - diaper_status（尿布數據：Diaper DV1）..."
bq --project_id=$PROJECT_ID mk -t \
  --time_partitioning_field=timestamp \
  --time_partitioning_type=DAY \
  --require_partition_filter=false \
  ${DATASET_NAME}.diaper_status \
  content:STRING,gateway_id:STRING,device_id:STRING,mac:STRING,\
name:STRING,firmware_version:STRING,\
temperature:FLOAT,humidity:FLOAT,\
button:INTEGER,button_status:STRING,diaper_status:STRING,\
message_index:INTEGER,acknowledgement:INTEGER,\
battery_level:INTEGER,serial_no:INTEGER,\
timestamp:TIMESTAMP,message_type:STRING \
  || echo "ℹ️  diaper_status 表已存在"

echo ""
echo "✅ 創建 GCS Bucket..."
BUCKET_NAME="${PROJECT_ID}-dataflow"
gsutil mb -p $PROJECT_ID -l $REGION gs://$BUCKET_NAME/ || echo "ℹ️  Bucket 已存在"

echo ""
echo "✅ 創建 GCS 子目錄..."
gsutil -m mkdir -p gs://$BUCKET_NAME/jars/ || true
gsutil -m mkdir -p gs://$BUCKET_NAME/staging/ || true
gsutil -m mkdir -p gs://$BUCKET_NAME/temp/ || true
gsutil -m mkdir -p gs://$BUCKET_NAME/invalid-data/ || true

echo ""
echo "====================================="
echo "✅ GCP 環境設置完成！"
echo "====================================="
echo ""
echo "📋 創建的資源："
echo ""
echo "Pub/Sub:"
echo "  - Topic: projects/$PROJECT_ID/topics/$TOPIC_NAME"
echo "  - Subscription: projects/$PROJECT_ID/subscriptions/$SUBSCRIPTION_NAME"
echo "  - Dead Letter: projects/$PROJECT_ID/topics/$DEAD_LETTER_TOPIC"
echo ""
echo "BigQuery:"
echo "  - Dataset: $PROJECT_ID:$DATASET_NAME"
echo "  - Table 1: $PROJECT_ID:${DATASET_NAME}.vital_signs (300B 生理數據)"
echo "  - Table 2: $PROJECT_ID:${DATASET_NAME}.diaper_status (Diaper DV1 尿布數據)"
echo ""
echo "GCS:"
echo "  - Bucket: gs://$BUCKET_NAME"
echo "  - JAR Path: gs://$BUCKET_NAME/jars/"
echo "  - Staging: gs://$BUCKET_NAME/staging/"
echo "  - Temp: gs://$BUCKET_NAME/temp/"
echo ""
echo "====================================="
echo ""
echo "🔗 快速鏈接："
echo "  - Pub/Sub Console: https://console.cloud.google.com/cloudpubsub/topic/list?project=$PROJECT_ID"
echo "  - BigQuery Console: https://console.cloud.google.com/bigquery?project=$PROJECT_ID&d=$DATASET_NAME"
echo "  - GCS Console: https://console.cloud.google.com/storage/browser/$BUCKET_NAME?project=$PROJECT_ID"
echo ""
echo "⏭️  下一步："
echo "  1. 設置 Redis（Memorystore 或自己的服務器）"
echo "  2. 運行測試：./scripts/verify-setup.sh"
echo "  3. 部署 Dataflow：./scripts/deploy-to-gcp.sh"
echo ""
