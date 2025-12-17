#!/bin/bash
# 部署到 GCP Dataflow

set -e

# 配置
PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
REGION="${GCP_REGION:-asia-east1}"
BUCKET_NAME="${GCS_BUCKET:-gs://${PROJECT_ID}-dataflow}"
SUBSCRIPTION="projects/${PROJECT_ID}/subscriptions/health-data-sub"
BIGQUERY_DATASET="health"
REDIS_HOST="${REDIS_HOST:-your-redis-host}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
JOB_NAME="health-data-pipeline-$(date +%Y%m%d-%H%M%S)"

echo "====================================="
echo "  部署 Health Data Pipeline 到 GCP"
echo "====================================="
echo "Project: $PROJECT_ID"
echo "Region: $REGION"
echo "Job Name: $JOB_NAME"
echo "Bucket: $BUCKET_NAME"
echo "====================================="

# 檢查必要的環境變量
if [ "$PROJECT_ID" == "your-gcp-project-id" ]; then
    echo "❌ 請設置 GCP_PROJECT_ID 環境變量"
    exit 1
fi

if [ "$REDIS_HOST" == "your-redis-host" ]; then
    echo "❌ 請設置 REDIS_HOST 環境變量"
    exit 1
fi

# 構建 JAR
echo "構建 JAR..."
./gradlew clean fatJar

JAR_PATH="dataflow/build/libs/dataflow-1.0.0-all.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ JAR 文件不存在: $JAR_PATH"
    exit 1
fi

echo "✅ JAR 構建完成: $JAR_PATH"

# 上傳 JAR 到 GCS
GCS_JAR_PATH="${BUCKET_NAME}/jars/health-data-pipeline-1.0.0.jar"
echo "上傳 JAR 到 GCS..."
gsutil cp $JAR_PATH $GCS_JAR_PATH
echo "✅ JAR 已上傳: $GCS_JAR_PATH"

# 啟動 Dataflow Job
echo "啟動 Dataflow Job..."

gcloud dataflow jobs run $JOB_NAME \
  --gcs-location=$GCS_JAR_PATH \
  --region=$REGION \
  --project=$PROJECT_ID \
  --staging-location=${BUCKET_NAME}/staging \
  --temp-location=${BUCKET_NAME}/temp \
  --max-num-workers=10 \
  --worker-machine-type=n1-standard-2 \
  --parameters="runner=DataflowRunner,project=$PROJECT_ID,region=$REGION,inputSubscription=$SUBSCRIPTION,bigQueryDataset=$BIGQUERY_DATASET,redisHost=$REDIS_HOST,redisPort=$REDIS_PORT,redisPassword=$REDIS_PASSWORD,enableDeduplication=true,deduplicationWindowSeconds=5,enableValidation=true,redisTtlSeconds=3600"

echo "✅ Dataflow Job 已啟動: $JOB_NAME"
echo "監控 Job:"
echo "  https://console.cloud.google.com/dataflow/jobs/$REGION/$JOB_NAME?project=$PROJECT_ID"

