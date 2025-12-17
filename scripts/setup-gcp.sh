#!/bin/bash
# 設置 GCP 環境

set -e

PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
REGION="${GCP_REGION:-asia-east1}"
DATASET_NAME="health"
TABLE_NAME="patient_data"
TOPIC_NAME="health-data-topic"
SUBSCRIPTION_NAME="health-data-sub"

echo "====================================="
echo "  設置 GCP 環境"
echo "====================================="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo "====================================="

if [ "$PROJECT_ID" == "your-gcp-project-id" ]; then
    echo "❌ 請設置 GCP_PROJECT_ID 環境變量"
    exit 1
fi

# 設置當前項目
gcloud config set project $PROJECT_ID

echo "✅ 啟用必要的 API..."
gcloud services enable dataflow.googleapis.com
gcloud services enable pubsub.googleapis.com
gcloud services enable bigquery.googleapis.com
gcloud services enable storage-api.googleapis.com

echo "✅ 創建 Pub/Sub Topic..."
gcloud pubsub topics create $TOPIC_NAME --project=$PROJECT_ID || echo "Topic 已存在"

echo "✅ 創建 Pub/Sub Subscription..."
gcloud pubsub subscriptions create $SUBSCRIPTION_NAME \
  --topic=$TOPIC_NAME \
  --project=$PROJECT_ID \
  --ack-deadline=60 \
  --message-retention-duration=7d || echo "Subscription 已存在"

echo "✅ 創建 BigQuery Dataset..."
bq --project_id=$PROJECT_ID mk -d \
  --location=$REGION \
  --description="健康數據分析" \
  $DATASET_NAME || echo "Dataset 已存在"

echo "✅ 創建 BigQuery Table..."
bq --project_id=$PROJECT_ID mk -t \
  ${DATASET_NAME}.${TABLE_NAME} \
  device_id:STRING,device_type:STRING,gateway_id:STRING,mac:STRING,serial_no:INTEGER,\
content:STRING,sos:INTEGER,heart_rate:INTEGER,spo2:INTEGER,bp_systolic:INTEGER,\
bp_diastolic:INTEGER,skin_temp:FLOAT,room_temp:FLOAT,steps:INTEGER,sleep_time:STRING,\
wake_time:STRING,light_sleep_min:INTEGER,deep_sleep_min:INTEGER,move:INTEGER,\
wear:INTEGER,battery_level:INTEGER,timestamp:TIMESTAMP,processing_time:TIMESTAMP \
  || echo "Table 已存在"

echo "✅ 創建 GCS Bucket..."
BUCKET_NAME="${PROJECT_ID}-dataflow"
gsutil mb -p $PROJECT_ID -l $REGION gs://$BUCKET_NAME/ || echo "Bucket 已存在"

echo ""
echo "====================================="
echo "✅ GCP 環境設置完成！"
echo "====================================="
echo "Pub/Sub Topic: projects/$PROJECT_ID/topics/$TOPIC_NAME"
echo "Pub/Sub Subscription: projects/$PROJECT_ID/subscriptions/$SUBSCRIPTION_NAME"
echo "BigQuery Table: $PROJECT_ID:$DATASET_NAME.$TABLE_NAME"
echo "GCS Bucket: gs://$BUCKET_NAME"
echo "====================================="

