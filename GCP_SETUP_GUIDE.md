# GCP 設置和部署指南

完整的 GCP 環境設置和 Dataflow 部署步驟。

## 📋 前置要求

### 1. 安裝工具

```bash
# Google Cloud SDK
# macOS
brew install google-cloud-sdk

# Linux
curl https://sdk.cloud.google.com | bash

# 安裝 Redis CLI（用於測試）
brew install redis  # macOS
apt install redis-tools  # Ubuntu/Debian
```

### 2. 登入 GCP

```bash
# 初始化 gcloud
gcloud init

# 驗證身份
gcloud auth login

# 設置應用程序默認憑證（用於本地開發）
gcloud auth application-default login
```

---

## 🚀 快速開始（3步驟）

### Step 1: 設置環境變量

```bash
cd SeniorCarePlus-Platform

# 設置你的 GCP 項目 ID
export GCP_PROJECT_ID="your-actual-project-id"
export GCP_REGION="asia-east1"  # 或其他區域
```

### Step 2: 運行一鍵設置腳本

```bash
./scripts/setup-gcp.sh
```

這個腳本會自動創建：
- ✅ Pub/Sub Topic 和 Subscription
- ✅ BigQuery Dataset 和兩個表（vital_signs, diaper_status）
- ✅ GCS Bucket 和目錄結構
- ✅ 啟用所有必要的 API

**預計時間：5-10 分鐘**

### Step 3: 驗證設置

```bash
./scripts/verify-setup.sh
```

如果所有檢查都通過 ✅，你就可以繼續部署了！

---

## 🔴 Redis 設置

### 選項 A: Google Cloud Memorystore（推薦生產環境）

```bash
# 創建 Redis 實例（需要 10-15 分鐘）
gcloud redis instances create senior-care-redis \
  --size=5 \
  --region=asia-east1 \
  --zone=asia-east1-a \
  --redis-version=redis_7_0 \
  --project=$GCP_PROJECT_ID

# 獲取 Redis IP
export REDIS_HOST=$(gcloud redis instances describe senior-care-redis \
  --region=asia-east1 \
  --format="get(host)" \
  --project=$GCP_PROJECT_ID)

echo "Redis Host: $REDIS_HOST"
```

### 選項 B: 本地 Redis（開發測試）

```bash
# 使用 Docker
docker run -d -p 6379:6379 --name redis redis:7-alpine

# 設置環境變量
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

### 選項 C: 自己的 Redis 服務器

```bash
export REDIS_HOST="your-redis-ip"
export REDIS_PORT="6379"
export REDIS_PASSWORD="your-password"  # 如果有密碼
```

---

## 🧪 本地測試（可選但強烈建議）

在部署到 GCP 前先本地測試 Pipeline：

```bash
# 1. 構建項目
./gradlew clean build

# 2. 本地運行（使用 DirectRunner）
./scripts/run-local.sh
```

**在另一個終端發送測試數據：**

```bash
# 發送測試數據並驗證
./scripts/test-pipeline.sh
```

**檢查結果：**

```bash
# BigQuery
bq query --project_id=$GCP_PROJECT_ID \
  "SELECT * FROM health.vital_signs ORDER BY timestamp DESC LIMIT 5"

bq query --project_id=$GCP_PROJECT_ID \
  "SELECT * FROM health.diaper_status ORDER BY timestamp DESC LIMIT 5"

# Redis
redis-cli -h $REDIS_HOST GET vitals:1302
redis-cli -h $REDIS_HOST GET diaper:1302
```

---

## 🚀 部署到 GCP Dataflow

### 方法 1: 使用部署腳本（推薦）

```bash
# 確保環境變量已設置
export GCP_PROJECT_ID="your-project"
export REDIS_HOST="10.x.x.x"  # 從 Memorystore 獲取
export REDIS_PASSWORD=""       # 如果有密碼

# 運行部署
./scripts/deploy-to-gcp.sh
```

### 方法 2: 手動部署

```bash
# 1. 構建 JAR
./gradlew clean :dataflow:fatJar

# 2. 上傳到 GCS
gsutil cp dataflow/build/libs/dataflow-1.0.0-all.jar \
  gs://${GCP_PROJECT_ID}-dataflow/jars/

# 3. 啟動 Dataflow Job
gcloud dataflow jobs run health-data-pipeline-$(date +%Y%m%d-%H%M%S) \
  --gcs-location=gs://${GCP_PROJECT_ID}-dataflow/jars/dataflow-1.0.0-all.jar \
  --region=asia-east1 \
  --project=$GCP_PROJECT_ID \
  --staging-location=gs://${GCP_PROJECT_ID}-dataflow/staging \
  --temp-location=gs://${GCP_PROJECT_ID}-dataflow/temp \
  --max-num-workers=10 \
  --worker-machine-type=n1-standard-2 \
  --parameters="runner=DataflowRunner,project=$GCP_PROJECT_ID,region=asia-east1,inputSubscription=projects/$GCP_PROJECT_ID/subscriptions/health-data-sub,bigQueryDataset=health,redisHost=$REDIS_HOST,redisPort=6379,redisPassword=$REDIS_PASSWORD"
```

---

## 🔍 監控和驗證

### 1. 查看 Dataflow Job 狀態

```bash
# 在瀏覽器中打開
https://console.cloud.google.com/dataflow/jobs?project=$GCP_PROJECT_ID
```

或使用命令行：

```bash
# 列出所有 Jobs
gcloud dataflow jobs list --project=$GCP_PROJECT_ID --region=asia-east1

# 查看特定 Job 詳情
gcloud dataflow jobs describe JOB_ID --project=$GCP_PROJECT_ID --region=asia-east1
```

### 2. 發送真實數據測試

```bash
# 發送測試數據
./scripts/test-pipeline.sh

# 等待 15 秒後檢查結果
```

### 3. 查看日誌

```bash
# Dataflow 日誌
gcloud dataflow jobs show JOB_ID \
  --project=$GCP_PROJECT_ID \
  --region=asia-east1

# 或在 Cloud Console 查看：
# https://console.cloud.google.com/logs/query
```

---

## 📊 驗證數據流

### BigQuery 查詢

```sql
-- 生理數據統計
SELECT 
  DATE(timestamp) as date,
  COUNT(*) as count,
  AVG(heart_rate) as avg_hr,
  AVG(sp_o2) as avg_spo2
FROM `health.vital_signs`
WHERE timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
GROUP BY date
ORDER BY date DESC;

-- 尿布數據統計
SELECT 
  DATE(timestamp) as date,
  COUNT(*) as count,
  AVG(humidity) as avg_humidity,
  AVG(temperature) as avg_temp
FROM `health.diaper_status`
WHERE timestamp >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
GROUP BY date
ORDER BY date DESC;
```

### Redis 查詢

```bash
# 最新生理數據
redis-cli -h $REDIS_HOST GET vitals:1302

# 最新尿布數據
redis-cli -h $REDIS_HOST GET diaper:1302

# 時間序列數據（最近1小時）
redis-cli -h $REDIS_HOST ZRANGE timeseries:VITAL_SIGN:1302 0 -1 WITHSCORES

# 查看某個 Gateway 下的所有設備
redis-cli -h $REDIS_HOST SMEMBERS gateway:GW0001:devices
```

---

## 🛠️ 故障排除

### 問題 1: Dataflow Job 啟動失敗

**檢查：**
```bash
# 1. 檢查 API 是否啟用
gcloud services list --enabled --project=$GCP_PROJECT_ID | grep dataflow

# 2. 檢查權限
gcloud projects get-iam-policy $GCP_PROJECT_ID

# 3. 檢查 GCS Bucket 是否存在
gsutil ls gs://${GCP_PROJECT_ID}-dataflow
```

**解決：**
```bash
# 重新運行設置腳本
./scripts/setup-gcp.sh
```

### 問題 2: 數據未出現在 BigQuery

**檢查：**
```bash
# 1. 檢查 Pub/Sub 是否有消息
gcloud pubsub subscriptions pull health-data-sub \
  --project=$GCP_PROJECT_ID \
  --limit=5

# 2. 查看 Dataflow 日誌是否有錯誤

# 3. 檢查 Dead Letter Queue
gcloud pubsub subscriptions pull health-data-dead-letter-sub \
  --project=$GCP_PROJECT_ID \
  --limit=5
```

### 問題 3: Redis 連接失敗

**檢查：**
```bash
# 1. 確認 Redis IP
echo $REDIS_HOST

# 2. 測試連接
redis-cli -h $REDIS_HOST ping

# 3. 檢查 Memorystore 狀態
gcloud redis instances describe senior-care-redis \
  --region=asia-east1 \
  --project=$GCP_PROJECT_ID
```

### 問題 4: JAR 文件未找到

```bash
# 重新構建
./gradlew clean :dataflow:fatJar

# 檢查是否存在
ls -lh dataflow/build/libs/
```

---

## 💰 成本優化建議

### 開發環境

```bash
# 使用小型 worker
--worker-machine-type=n1-standard-1
--max-num-workers=2

# 使用小型 Redis
--size=1  # 1GB Memorystore
```

### 生產環境

```bash
# 啟用自動擴展
--autoscaling-algorithm=THROUGHPUT_BASED
--max-num-workers=10
--num-workers=2  # 起始數量

# 使用 Streaming Engine（更便宜）
--enable-streaming-engine
```

### 成本監控

```bash
# 查看當月成本
https://console.cloud.google.com/billing/reports?project=$GCP_PROJECT_ID
```

---

## 🔄 更新和維護

### 更新 Pipeline 代碼

```bash
# 1. 修改代碼
# 2. 構建新 JAR
./gradlew clean :dataflow:fatJar

# 3. 停止舊 Job
gcloud dataflow jobs cancel OLD_JOB_ID \
  --project=$GCP_PROJECT_ID \
  --region=asia-east1

# 4. 部署新 Job
./scripts/deploy-to-gcp.sh
```

### 清理資源（小心！）

```bash
# ⚠️ 這會刪除所有資源！

# 刪除 Dataflow Job
gcloud dataflow jobs cancel JOB_ID --region=asia-east1

# 刪除 Pub/Sub
gcloud pubsub subscriptions delete health-data-sub
gcloud pubsub topics delete health-data-topic

# 刪除 BigQuery Dataset（包含所有表）
bq rm -r -f health

# 刪除 GCS Bucket
gsutil -m rm -r gs://${GCP_PROJECT_ID}-dataflow

# 刪除 Redis
gcloud redis instances delete senior-care-redis --region=asia-east1
```

---

## 📞 支持

遇到問題？

1. **查看日誌**: Dataflow Console > 你的 Job > Logs
2. **檢查設置**: `./scripts/verify-setup.sh`
3. **本地測試**: `./scripts/run-local.sh`
4. **查看文檔**: `README.md`, `ARCHITECTURE.md`

---

## ✅ 檢查清單

部署前確認：

- [ ] GCP 項目已創建
- [ ] 已安裝 gcloud CLI
- [ ] 已運行 `gcloud auth login`
- [ ] 環境變量已設置（`GCP_PROJECT_ID`, `REDIS_HOST`）
- [ ] 已運行 `./scripts/setup-gcp.sh`
- [ ] 已運行 `./scripts/verify-setup.sh`（所有檢查通過）
- [ ] Redis 已設置並可連接
- [ ] 已本地測試（可選）
- [ ] 準備好監控和驗證

部署後確認：

- [ ] Dataflow Job 狀態為 Running
- [ ] 測試數據成功寫入 BigQuery
- [ ] 測試數據成功寫入 Redis
- [ ] 無錯誤日誌
- [ ] 成本監控已設置

