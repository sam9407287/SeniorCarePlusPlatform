#!/bin/bash
# 測試數據流：發送測試數據並驗證

set -e

PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
TOPIC_NAME="health-data-topic"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"

echo "====================================="
echo "  測試數據流"
echo "====================================="
echo "Project: $PROJECT_ID"
echo "Topic: $TOPIC_NAME"
echo "Redis: $REDIS_HOST:$REDIS_PORT"
echo "====================================="
echo ""

if [ "$PROJECT_ID" == "your-gcp-project-id" ]; then
    echo "❌ 請設置 GCP_PROJECT_ID 環境變量"
    exit 1
fi

# 檢查測試數據文件
if [ ! -f "test-data/300B-sample.json" ] || [ ! -f "test-data/diaper-sample.json" ]; then
    echo "❌ 測試數據文件不存在"
    exit 1
fi

echo "📤 發送測試數據到 Pub/Sub..."
echo ""

# 發送 300B 生理數據
echo "1️⃣  發送 300B 生理數據..."
gcloud pubsub topics publish $TOPIC_NAME \
  --project=$PROJECT_ID \
  --message="$(cat test-data/300B-sample.json)"
echo "✅ 300B 數據已發送"

sleep 2

# 發送 Diaper DV1 數據
echo "2️⃣  發送 Diaper DV1 數據..."
gcloud pubsub topics publish $TOPIC_NAME \
  --project=$PROJECT_ID \
  --message="$(cat test-data/diaper-sample.json)"
echo "✅ Diaper DV1 數據已發送"

echo ""
echo "⏳ 等待數據處理（15秒）..."
sleep 15

echo ""
echo "====================================="
echo "🔍 驗證數據..."
echo "====================================="
echo ""

# 檢查 BigQuery - vital_signs
echo "📊 檢查 BigQuery - vital_signs..."
VITAL_COUNT=$(bq query --project_id=$PROJECT_ID --use_legacy_sql=false --format=csv \
  "SELECT COUNT(*) as count FROM health.vital_signs WHERE device_id='1302' AND timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 MINUTE)" \
  2>/dev/null | tail -n 1)

if [ "$VITAL_COUNT" -gt 0 ]; then
    echo "✅ 找到 $VITAL_COUNT 筆生理數據"
    echo ""
    echo "最新數據："
    bq query --project_id=$PROJECT_ID --use_legacy_sql=false \
      "SELECT device_id, heart_rate, sp_o2, timestamp FROM health.vital_signs WHERE device_id='1302' ORDER BY timestamp DESC LIMIT 1"
else
    echo "❌ 未找到生理數據"
fi

echo ""

# 檢查 BigQuery - diaper_status
echo "📊 檢查 BigQuery - diaper_status..."
DIAPER_COUNT=$(bq query --project_id=$PROJECT_ID --use_legacy_sql=false --format=csv \
  "SELECT COUNT(*) as count FROM health.diaper_status WHERE device_id='1302' AND timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 MINUTE)" \
  2>/dev/null | tail -n 1)

if [ "$DIAPER_COUNT" -gt 0 ]; then
    echo "✅ 找到 $DIAPER_COUNT 筆尿布數據"
    echo ""
    echo "最新數據："
    bq query --project_id=$PROJECT_ID --use_legacy_sql=false \
      "SELECT device_id, humidity, temperature, diaper_status, timestamp FROM health.diaper_status WHERE device_id='1302' ORDER BY timestamp DESC LIMIT 1"
else
    echo "❌ 未找到尿布數據"
fi

echo ""

# 檢查 Redis
if command -v redis-cli &>/dev/null; then
    echo "🔴 檢查 Redis..."
    
    # 檢查最新生理數據
    VITAL_REDIS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT GET "vitals:1302" 2>/dev/null || echo "")
    if [ -n "$VITAL_REDIS" ]; then
        echo "✅ Redis 中找到生理數據"
        echo "   Key: vitals:1302"
        echo "   Data: ${VITAL_REDIS:0:100}..."
    else
        echo "❌ Redis 中未找到生理數據"
    fi
    
    # 檢查最新尿布數據
    DIAPER_REDIS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT GET "diaper:1302" 2>/dev/null || echo "")
    if [ -n "$DIAPER_REDIS" ]; then
        echo "✅ Redis 中找到尿布數據"
        echo "   Key: diaper:1302"
        echo "   Data: ${DIAPER_REDIS:0:100}..."
    else
        echo "❌ Redis 中未找到尿布數據"
    fi
    
    # 檢查時間序列數據
    TIMESERIES_COUNT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT ZCARD "timeseries:VITAL_SIGN:1302" 2>/dev/null || echo "0")
    echo "ℹ️  時間序列數據點數: $TIMESERIES_COUNT"
else
    echo "ℹ️  redis-cli 未安裝，跳過 Redis 檢查"
    echo "   安裝: brew install redis (macOS) 或 apt install redis-tools (Linux)"
fi

echo ""
echo "====================================="
echo "✅ 測試完成！"
echo "====================================="
echo ""
echo "💡 提示："
echo "  - 如果數據未出現，請檢查 Dataflow Job 是否運行"
echo "  - 查看 Dataflow 日誌: https://console.cloud.google.com/dataflow/jobs?project=$PROJECT_ID"
echo "  - 本地測試: ./scripts/run-local.sh"
echo ""

