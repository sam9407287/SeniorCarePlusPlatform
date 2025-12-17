#!/bin/bash
# 本地运行 Dataflow 管道（测试用）

set -e

# 加载配置
source "$(dirname "$0")/../gcp-config.env"

echo "====================================="
echo "  运行 Dataflow 管道（本地测试）"
echo "====================================="
echo ""

# 检查 Python 环境
if ! command -v python3 &> /dev/null; then
    echo "❌ 错误: 未找到 python3"
    exit 1
fi

# 进入 dataflow-python 目录
cd "$(dirname "$0")/../dataflow-python"

# 安装依赖
echo "步骤 1: 安装 Python 依赖..."
pip3 install -r requirements.txt --quiet

echo ""
echo "步骤 2: 启动 Dataflow 管道..."
echo "  项目: $GCP_PROJECT_ID"
echo "  订阅: projects/$GCP_PROJECT_ID/subscriptions/$PUBSUB_SUBSCRIPTION"
echo "  BigQuery: $BIGQUERY_DATASET"
echo "  Redis: $REDIS_HOST:$REDIS_PORT"
echo ""

python3 health_data_pipeline.py \
  --project=$GCP_PROJECT_ID \
  --subscription=projects/$GCP_PROJECT_ID/subscriptions/$PUBSUB_SUBSCRIPTION \
  --bigquery-dataset=$BIGQUERY_DATASET \
  --redis-host=$REDIS_HOST \
  --redis-port=$REDIS_PORT

echo ""
echo "====================================="
echo "✅ Dataflow 管道已启动！"
echo "====================================="

