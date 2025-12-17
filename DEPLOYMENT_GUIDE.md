# 部署指南

## 📋 目錄

1. [前置準備](#前置準備)
2. [本地開發環境](#本地開發環境)
3. [GCP 環境設置](#gcp-環境設置)
4. [部署到生產環境](#部署到生產環境)
5. [驗證和測試](#驗證和測試)
6. [故障排除](#故障排除)

---

## 前置準備

### 1. 安裝必要工具

```bash
# Java 17
java -version

# Kotlin (通過 Gradle)
./gradlew --version

# Google Cloud SDK
gcloud version

# Redis CLI（用於測試）
redis-cli --version
```

### 2. GCP 權限

確保你的 GCP 帳號具有以下權限：

- Dataflow Admin
- Pub/Sub Admin
- BigQuery Admin
- Storage Admin
- Service Account User

### 3. 環境變量

```bash
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="asia-east1"
export REDIS_HOST="your-redis-host"
export REDIS_PORT="6379"
export REDIS_PASSWORD="your-redis-password"  # 可選
```

---

## 本地開發環境

### 1. 啟動 Redis

```bash
# 使用 Docker
docker run -d -p 6379:6379 --name redis-dev redis:7

# 驗證
redis-cli ping
# 應該返回: PONG
```

### 2. 創建 Pub/Sub 模擬器（可選）

```bash
# 安裝 Pub/Sub 模擬器
gcloud components install pubsub-emulator

# 啟動模擬器
gcloud beta emulators pubsub start --port=8085

# 在另一個終端設置環境變量
export PUBSUB_EMULATOR_HOST=localhost:8085
```

### 3. 運行 Pipeline

```bash
# 方式 1: 使用腳本
./scripts/run-local.sh

# 方式 2: 直接運行
./gradlew run --args="\
  --runner=DirectRunner \
  --inputSubscription=projects/$GCP_PROJECT_ID/subscriptions/health-data-sub \
  --bigQueryTable=$GCP_PROJECT_ID:health.patient_data \
  --redisHost=localhost \
  --redisPort=6379
"
```

### 4. 發送測試數據

```bash
# 發送單條測試數據
gcloud pubsub topics publish health-data-topic \
  --message="$(cat test-data/sample-health-data.json)"

# 批量發送測試數據（模擬多個病患）
for i in {1..100}; do
  sed "s/1302/$i/g" test-data/sample-health-data.json | \
  gcloud pubsub topics publish health-data-topic --message=-
done
```

---

## GCP 環境設置

### 1. 運行設置腳本

```bash
# 自動創建所有必要資源
./scripts/setup-gcp.sh
```

腳本會創建：
- ✅ Pub/Sub Topic 和 Subscription
- ✅ BigQuery Dataset 和 Table
- ✅ GCS Bucket（用於 Dataflow staging）
- ✅ 啟用必要的 API

### 2. 手動設置（可選）

如果需要自定義配置：

#### 2.1 創建 Pub/Sub

```bash
# Topic
gcloud pubsub topics create health-data-topic

# Subscription
gcloud pubsub subscriptions create health-data-sub \
  --topic=health-data-topic \
  --ack-deadline=60 \
  --message-retention-duration=7d

# Dead Letter Queue
gcloud pubsub topics create health-data-dlq
```

#### 2.2 創建 BigQuery

```bash
# Dataset
bq mk -d --location=asia-east1 health

# Table
bq mk -t health.patient_data \
  device_id:STRING,device_type:STRING,gateway_id:STRING,\
  mac:STRING,serial_no:INTEGER,content:STRING,\
  sos:INTEGER,heart_rate:INTEGER,spo2:INTEGER,\
  bp_systolic:INTEGER,bp_diastolic:INTEGER,\
  skin_temp:FLOAT,room_temp:FLOAT,steps:INTEGER,\
  sleep_time:STRING,wake_time:STRING,\
  light_sleep_min:INTEGER,deep_sleep_min:INTEGER,\
  move:INTEGER,wear:INTEGER,battery_level:INTEGER,\
  timestamp:TIMESTAMP,processing_time:TIMESTAMP
```

#### 2.3 創建 GCS Bucket

```bash
gsutil mb -l asia-east1 gs://$GCP_PROJECT_ID-dataflow
```

### 3. 設置 Redis

選擇以下方式之一：

#### 方式 A: Google Cloud Memorystore

```bash
gcloud redis instances create health-redis \
  --size=5 \
  --region=asia-east1 \
  --redis-version=redis_7_0
```

#### 方式 B: 自建 Redis (GCE)

```bash
# 創建 VM
gcloud compute instances create redis-server \
  --zone=asia-east1-a \
  --machine-type=n1-standard-2 \
  --image-family=ubuntu-2004-lts \
  --image-project=ubuntu-os-cloud

# SSH 到 VM 並安裝 Redis
gcloud compute ssh redis-server --zone=asia-east1-a
sudo apt update && sudo apt install -y redis-server
sudo systemctl enable redis-server
```

#### 方式 C: 使用 Railway/Upstash

訪問 https://railway.app 或 https://upstash.com 創建 Redis 實例

---

## 部署到生產環境

### 1. 構建項目

```bash
# 清理並構建
./gradlew clean build

# 創建 Fat JAR
./gradlew fatJar

# 驗證 JAR
ls -lh build/libs/SeniorCarePlusDataFlowKotlin-1.0.0-all.jar
```

### 2. 使用部署腳本

```bash
# 設置環境變量
export GCP_PROJECT_ID="your-prod-project"
export GCP_REGION="asia-east1"
export REDIS_HOST="your-redis-prod-host"
export REDIS_PORT="6379"
export REDIS_PASSWORD="your-password"

# 運行部署腳本
./scripts/deploy-to-gcp.sh
```

### 3. 手動部署

```bash
# 1. 上傳 JAR 到 GCS
gsutil cp build/libs/SeniorCarePlusDataFlowKotlin-1.0.0-all.jar \
  gs://$GCP_PROJECT_ID-dataflow/jars/

# 2. 啟動 Dataflow Job
gcloud dataflow jobs run health-data-pipeline-$(date +%Y%m%d-%H%M%S) \
  --gcs-location=gs://$GCP_PROJECT_ID-dataflow/jars/SeniorCarePlusDataFlowKotlin-1.0.0-all.jar \
  --region=$GCP_REGION \
  --project=$GCP_PROJECT_ID \
  --staging-location=gs://$GCP_PROJECT_ID-dataflow/staging \
  --temp-location=gs://$GCP_PROJECT_ID-dataflow/temp \
  --max-num-workers=20 \
  --num-workers=5 \
  --worker-machine-type=n1-standard-2 \
  --enable-streaming-engine \
  --parameters="
runner=DataflowRunner,
project=$GCP_PROJECT_ID,
region=$GCP_REGION,
inputSubscription=projects/$GCP_PROJECT_ID/subscriptions/health-data-sub,
bigQueryTable=$GCP_PROJECT_ID:health.patient_data,
redisHost=$REDIS_HOST,
redisPort=$REDIS_PORT,
redisPassword=$REDIS_PASSWORD,
enableDeduplication=true,
deduplicationWindowSeconds=5,
enableValidation=true,
redisTtlSeconds=3600
"
```

### 4. 使用 CI/CD（GitHub Actions 示例）

創建 `.github/workflows/deploy.yml`:

```yaml
name: Deploy to GCP Dataflow

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build JAR
        run: ./gradlew clean fatJar
      
      - name: Authenticate to GCP
        uses: google-github-actions/auth@v1
        with:
          credentials_json: ${{ secrets.GCP_CREDENTIALS }}
      
      - name: Deploy to Dataflow
        run: ./scripts/deploy-to-gcp.sh
        env:
          GCP_PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
          REDIS_HOST: ${{ secrets.REDIS_HOST }}
          REDIS_PASSWORD: ${{ secrets.REDIS_PASSWORD }}
```

---

## 驗證和測試

### 1. 檢查 Pipeline 狀態

```bash
# 列出所有運行中的 Jobs
gcloud dataflow jobs list --region=$GCP_REGION --status=active

# 查看特定 Job
JOB_ID="your-job-id"
gcloud dataflow jobs describe $JOB_ID --region=$GCP_REGION
```

### 2. 查看日誌

```bash
# Dataflow 日誌
gcloud dataflow jobs show $JOB_ID --region=$GCP_REGION

# 或在 Console 中查看
echo "https://console.cloud.google.com/dataflow/jobs/$GCP_REGION/$JOB_ID?project=$GCP_PROJECT_ID"
```

### 3. 測試數據流

```bash
# 發送測試消息
gcloud pubsub topics publish health-data-topic \
  --message='{
    "gateway_id": "TEST001",
    "content": "TEST",
    "hr": 75,
    "spO2": 98,
    "serial no": 9999
  }'

# 等待幾秒後查詢 BigQuery
bq query --use_legacy_sql=false \
  "SELECT * FROM \`$GCP_PROJECT_ID.health.patient_data\` 
   WHERE serial_no = 9999 
   ORDER BY timestamp DESC 
   LIMIT 1"

# 查詢 Redis
redis-cli -h $REDIS_HOST -p $REDIS_PORT GET health:gateway:9999
```

### 4. 性能測試

```bash
# 發送大量測試數據
python3 << 'EOF'
import json
import subprocess
from concurrent.futures import ThreadPoolExecutor

def send_message(i):
    data = {
        "gateway_id": f"PERF{i:05d}",
        "hr": 70 + (i % 30),
        "spO2": 95 + (i % 5),
        "serial no": i
    }
    subprocess.run([
        "gcloud", "pubsub", "topics", "publish", "health-data-topic",
        "--message", json.dumps(data)
    ], capture_output=True)

# 發送 1000 條消息
with ThreadPoolExecutor(max_workers=10) as executor:
    executor.map(send_message, range(1000))
EOF

# 查看處理延遲
bq query --use_legacy_sql=false \
  "SELECT 
    AVG(TIMESTAMP_DIFF(processing_time, timestamp, MILLISECOND)) as avg_latency_ms
   FROM \`$GCP_PROJECT_ID.health.patient_data\`
   WHERE timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 5 MINUTE)"
```

---

## 故障排除

### 問題 1: Pipeline 啟動失敗

**錯誤**: `Permission denied`

**解決**:
```bash
# 檢查權限
gcloud projects get-iam-policy $GCP_PROJECT_ID

# 添加必要角色
gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="user:your-email@example.com" \
  --role="roles/dataflow.admin"
```

### 問題 2: 無法連接 Redis

**錯誤**: `Connection refused`

**解決**:
```bash
# 測試連接
telnet $REDIS_HOST $REDIS_PORT

# 檢查防火牆規則
gcloud compute firewall-rules list

# 創建防火牆規則
gcloud compute firewall-rules create allow-redis \
  --allow tcp:6379 \
  --source-ranges=10.0.0.0/8
```

### 問題 3: BigQuery 寫入失敗

**錯誤**: `Access Denied`

**解決**:
```bash
# 檢查 Dataflow Service Account
SA=$(gcloud dataflow jobs describe $JOB_ID --region=$GCP_REGION --format="value(serviceAccount)")

# 授予權限
bq show --format=prettyjson $GCP_PROJECT_ID:health | \
  jq -r '.access += [{"role": "WRITER", "userByEmail": "'$SA'"}]' | \
  bq update --source=/dev/stdin $GCP_PROJECT_ID:health
```

### 問題 4: Worker 數量不擴展

**錯誤**: Workers stuck at minimum

**解決**:
```bash
# 檢查配額
gcloud compute project-info describe --project=$GCP_PROJECT_ID

# 更新 Job 配置
gcloud dataflow jobs update-options $JOB_ID \
  --region=$GCP_REGION \
  --max-num-workers=30
```

---

## 監控和告警

### 設置 Cloud Monitoring 告警

```bash
# CPU 使用率告警
gcloud alpha monitoring policies create \
  --notification-channels=CHANNEL_ID \
  --display-name="Dataflow High CPU" \
  --condition-display-name="CPU > 80%" \
  --condition-threshold-value=0.8 \
  --condition-threshold-duration=300s
```

### 設置日誌導出

```bash
# 導出錯誤日誌到 BigQuery
gcloud logging sinks create dataflow-errors \
  bigquery.googleapis.com/projects/$GCP_PROJECT_ID/datasets/logs \
  --log-filter='resource.type="dataflow_step" AND severity>=ERROR'
```

---

## 維護和升級

### 滾動更新

```bash
# 1. 構建新版本
./gradlew clean fatJar

# 2. 上傳新 JAR
gsutil cp build/libs/SeniorCarePlusDataFlowKotlin-1.0.0-all.jar \
  gs://$GCP_PROJECT_ID-dataflow/jars/health-data-pipeline-v2.jar

# 3. 更新 Job（Dataflow 會自動滾動更新）
gcloud dataflow jobs run health-data-pipeline-v2 \
  --gcs-location=gs://$GCP_PROJECT_ID-dataflow/jars/health-data-pipeline-v2.jar \
  --region=$GCP_REGION \
  --update
```

### 清理舊資源

```bash
# 刪除舊的 Job
gcloud dataflow jobs list --region=$GCP_REGION --status=done | \
  awk 'NR>1 {print $1}' | \
  xargs -I {} gcloud dataflow jobs delete {} --region=$GCP_REGION

# 清理舊的 GCS 文件
gsutil rm -r gs://$GCP_PROJECT_ID-dataflow/temp/*
gsutil rm -r gs://$GCP_PROJECT_ID-dataflow/staging/*
```

---

**部署完成！** 🎉

如有問題，請參考 [README.md](README.md) 或聯繫技術支持。

