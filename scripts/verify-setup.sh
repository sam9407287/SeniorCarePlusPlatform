#!/bin/bash
# 驗證 GCP 環境設置是否正確

set -e

PROJECT_ID="${GCP_PROJECT_ID:-your-gcp-project-id}"
REGION="${GCP_REGION:-asia-east1}"

echo "====================================="
echo "  驗證 GCP 環境設置"
echo "====================================="
echo "Project: $PROJECT_ID"
echo "====================================="
echo ""

if [ "$PROJECT_ID" == "your-gcp-project-id" ]; then
    echo "❌ 請設置 GCP_PROJECT_ID 環境變量"
    exit 1
fi

ERRORS=0

# 檢查 Pub/Sub Topic
echo "🔍 檢查 Pub/Sub Topic..."
if gcloud pubsub topics describe health-data-topic --project=$PROJECT_ID &>/dev/null; then
    echo "✅ Topic 存在: health-data-topic"
else
    echo "❌ Topic 不存在: health-data-topic"
    ERRORS=$((ERRORS + 1))
fi

# 檢查 Pub/Sub Subscription
echo "🔍 檢查 Pub/Sub Subscription..."
if gcloud pubsub subscriptions describe health-data-sub --project=$PROJECT_ID &>/dev/null; then
    echo "✅ Subscription 存在: health-data-sub"
else
    echo "❌ Subscription 不存在: health-data-sub"
    ERRORS=$((ERRORS + 1))
fi

# 檢查 BigQuery Dataset
echo "🔍 檢查 BigQuery Dataset..."
if bq --project_id=$PROJECT_ID show health &>/dev/null; then
    echo "✅ Dataset 存在: health"
else
    echo "❌ Dataset 不存在: health"
    ERRORS=$((ERRORS + 1))
fi

# 檢查 BigQuery 表 - vital_signs
echo "🔍 檢查 BigQuery 表 - vital_signs..."
if bq --project_id=$PROJECT_ID show health.vital_signs &>/dev/null; then
    echo "✅ 表存在: health.vital_signs"
    ROW_COUNT=$(bq query --project_id=$PROJECT_ID --use_legacy_sql=false --format=csv "SELECT COUNT(*) as count FROM health.vital_signs" 2>/dev/null | tail -n 1)
    echo "   數據行數: $ROW_COUNT"
else
    echo "❌ 表不存在: health.vital_signs"
    ERRORS=$((ERRORS + 1))
fi

# 檢查 BigQuery 表 - diaper_status
echo "🔍 檢查 BigQuery 表 - diaper_status..."
if bq --project_id=$PROJECT_ID show health.diaper_status &>/dev/null; then
    echo "✅ 表存在: health.diaper_status"
    ROW_COUNT=$(bq query --project_id=$PROJECT_ID --use_legacy_sql=false --format=csv "SELECT COUNT(*) as count FROM health.diaper_status" 2>/dev/null | tail -n 1)
    echo "   數據行數: $ROW_COUNT"
else
    echo "❌ 表不存在: health.diaper_status"
    ERRORS=$((ERRORS + 1))
fi

# 檢查 GCS Bucket
echo "🔍 檢查 GCS Bucket..."
BUCKET_NAME="${PROJECT_ID}-dataflow"
if gsutil ls gs://$BUCKET_NAME &>/dev/null; then
    echo "✅ Bucket 存在: gs://$BUCKET_NAME"
    echo "   目錄結構:"
    gsutil ls gs://$BUCKET_NAME/ || true
else
    echo "❌ Bucket 不存在: gs://$BUCKET_NAME"
    ERRORS=$((ERRORS + 1))
fi

# 檢查 Redis（可選）
echo "🔍 檢查 Redis..."
if [ -n "$REDIS_HOST" ]; then
    echo "   Redis Host: $REDIS_HOST"
    if command -v redis-cli &>/dev/null; then
        if redis-cli -h $REDIS_HOST ping &>/dev/null; then
            echo "✅ Redis 連接成功"
        else
            echo "⚠️  無法連接到 Redis（可能需要網絡權限或密碼）"
        fi
    else
        echo "ℹ️  redis-cli 未安裝，跳過 Redis 連接測試"
    fi
else
    echo "ℹ️  REDIS_HOST 未設置，跳過 Redis 檢查"
fi

echo ""
echo "====================================="
if [ $ERRORS -eq 0 ]; then
    echo "✅ 所有檢查通過！環境設置正確。"
    echo ""
    echo "⏭️  下一步："
    echo "  1. 設置 Redis: export REDIS_HOST=your-redis-ip"
    echo "  2. 測試數據流: ./scripts/test-pipeline.sh"
    echo "  3. 部署到 Dataflow: ./scripts/deploy-to-gcp.sh"
else
    echo "❌ 發現 $ERRORS 個問題。請先修復這些問題。"
    echo ""
    echo "💡 修復建議："
    echo "  運行: ./scripts/setup-gcp.sh"
fi
echo "====================================="

exit $ERRORS

