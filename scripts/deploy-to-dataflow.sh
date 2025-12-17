#!/bin/bash
# 部署 Dataflow 到 GCP（最小配置）

set -e

# 加载配置
source "$(dirname "$0")/../gcp-config.env"

echo "====================================="
echo "  部署 Dataflow 到 GCP"
echo "====================================="
echo ""

echo "配置信息："
echo "  项目: $GCP_PROJECT_ID"
echo "  区域: $GCP_REGION"
echo "  订阅: $PUBSUB_SUBSCRIPTION"
echo "  BigQuery: $BIGQUERY_DATASET"
echo "  Redis: $REDIS_HOST:$REDIS_PORT"
echo ""

echo "部署配置："
echo "  Workers: 1 (最多 2)"
echo "  机型: n1-standard-1"
echo "  自动扩展: 启用"
echo "  预估成本: NT$ 2,900/月"
echo ""

read -p "确认部署？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo "部署已取消"
    exit 1
fi

echo ""
echo "开始部署..."
echo ""

cd "$(dirname "$0")/../dataflow-python"

python3 health_data_pipeline.py \
  --runner=DataflowRunner \
  --project=$GCP_PROJECT_ID \
  --region=$GCP_REGION \
  --temp_location=$GCS_TEMP_LOCATION \
  --staging_location=$GCS_STAGING_LOCATION \
  --subscription=projects/$GCP_PROJECT_ID/subscriptions/$PUBSUB_SUBSCRIPTION \
  --bigquery-dataset=$BIGQUERY_DATASET \
  --redis-host=$REDIS_HOST \
  --redis-port=$REDIS_PORT \
  --job_name=seniorcare-health-pipeline \
  --num_workers=1 \
  --max_num_workers=2 \
  --worker_machine_type=n1-standard-1 \
  --autoscaling_algorithm=THROUGHPUT_BASED \
  --save_main_session

echo ""
echo "====================================="
echo "✅ Dataflow Job 已提交！"
echo "====================================="
echo ""
echo "监控链接："
echo "  https://console.cloud.google.com/dataflow/jobs?project=$GCP_PROJECT_ID"
echo ""
echo "Job 启动需要 3-5 分钟，请稍候..."
echo ""

