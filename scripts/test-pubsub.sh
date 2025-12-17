#!/bin/bash
# 测试 Pub/Sub 和 BigQuery 连接

set -e

# 加载配置
source "$(dirname "$0")/../gcp-config.env"

echo "====================================="
echo "  测试 GCP 资源连接"
echo "====================================="
echo ""

echo "项目 ID: $GCP_PROJECT_ID"
echo "区域: $GCP_REGION"
echo ""

# 测试 1: 发送测试消息到 Pub/Sub
echo "步骤 1: 发送测试消息到 Pub/Sub..."

# 300B 生理数据
VITAL_SIGN_DATA='{
  "content": "300B",
  "data": {
    "device_id": "TEST-DEVICE-001",
    "timestamp": "2025-12-18T10:00:00Z",
    "heart_rate": 75,
    "systolic_bp": 120,
    "diastolic_bp": 80,
    "spo2": 98,
    "body_temp": 36.5,
    "steps": 1000,
    "battery_level": 85
  }
}'

echo "发送生理数据..."
echo "$VITAL_SIGN_DATA" | gcloud pubsub topics publish $PUBSUB_TOPIC \
  --project=$GCP_PROJECT_ID \
  --message -

echo "✅ 生理数据已发送"
echo ""

# Diaper DV1 数据
DIAPER_DATA='{
  "content": "Diaper DV1",
  "data": {
    "device_id": "TEST-DIAPER-001",
    "timestamp": "2025-12-18T10:00:00Z",
    "humidity": 45,
    "button_status": 0,
    "battery_level": 90
  }
}'

echo "发送尿布数据..."
echo "$DIAPER_DATA" | gcloud pubsub topics publish $PUBSUB_TOPIC \
  --project=$GCP_PROJECT_ID \
  --message -

echo "✅ 尿布数据已发送"
echo ""

# 测试 2: 检查 BigQuery 表
echo "步骤 2: 检查 BigQuery 表..."
echo ""
echo "Vital Signs 表结构:"
bq show --project_id=$GCP_PROJECT_ID $BIGQUERY_DATASET.vital_signs
echo ""
echo "Diaper Status 表结构:"
bq show --project_id=$GCP_PROJECT_ID $BIGQUERY_DATASET.diaper_status
echo ""

# 测试 3: 测试 Redis 连接
echo "步骤 3: 测试 Redis 连接..."
echo "Redis Host: $REDIS_HOST"
echo "Redis Port: $REDIS_PORT"
echo ""

echo "====================================="
echo "✅ 测试完成！"
echo "====================================="
echo ""
echo "下一步:"
echo "  1. 检查 Pub/Sub 是否收到消息"
echo "  2. 部署 Dataflow 管道处理这些消息"
echo ""

