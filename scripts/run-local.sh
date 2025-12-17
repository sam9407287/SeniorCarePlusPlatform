#!/bin/bash
# 本地運行 Dataflow Pipeline

set -e

# 配置
PROJECT_ID="your-gcp-project-id"
SUBSCRIPTION="projects/${PROJECT_ID}/subscriptions/health-data-sub"
BIGQUERY_TABLE="${PROJECT_ID}:health.patient_data"
REDIS_HOST="localhost"
REDIS_PORT="6379"

echo "====================================="
echo "  本地運行 Health Data Pipeline"
echo "====================================="
echo "Project: $PROJECT_ID"
echo "Subscription: $SUBSCRIPTION"
echo "BigQuery: $BIGQUERY_TABLE"
echo "Redis: $REDIS_HOST:$REDIS_PORT"
echo "====================================="

# 確保 Redis 正在運行
if ! nc -z $REDIS_HOST $REDIS_PORT 2>/dev/null; then
    echo "❌ Redis 未運行！請先啟動 Redis："
    echo "   docker run -d -p 6379:6379 redis:7"
    exit 1
fi

echo "✅ Redis 正在運行"

# 確保 BigQuery 表存在（可選）
echo "檢查 BigQuery 表..."
bq show ${BIGQUERY_TABLE} 2>/dev/null || {
    echo "⚠️  BigQuery 表不存在，將自動創建"
}

# 運行 Pipeline
echo "啟動 Pipeline..."
./gradlew run --args="\
  --runner=DirectRunner \
  --inputSubscription=$SUBSCRIPTION \
  --bigQueryTable=$BIGQUERY_TABLE \
  --redisHost=$REDIS_HOST \
  --redisPort=$REDIS_PORT \
  --enableDeduplication=true \
  --deduplicationWindowSeconds=5 \
  --enableValidation=true \
  --redisTtlSeconds=3600 \
"

echo "✅ Pipeline 已完成"

