# 🎉 項目準備完成！下一步操作指南

你的 `SeniorCarePlus-Platform` 項目已經準備就緒！

## ✅ 已完成的工作

### 1. 項目結構 ✅
- ✅ Multi-module Gradle 項目（shared-models + dataflow）
- ✅ 共用數據模型：支持 300B 生理數據和 Diaper DV1 尿布數據
- ✅ 完整的 Dataflow Pipeline 實現
- ✅ 動態路由到不同的 BigQuery 表
- ✅ Redis 時間序列存儲（最近1小時）

### 2. GitHub 集成 ✅
- ✅ 代碼已推送到: https://github.com/sam9407287/SeniorCarePlusPlatform.git
- ✅ CI/CD Workflows 已配置：
  - `build-and-test.yml` - 自動構建和測試
  - `deploy-dataflow.yml` - 手動部署到 GCP

### 3. 部署腳本 ✅
- ✅ `setup-gcp.sh` - 一鍵創建所有 GCP 資源
- ✅ `verify-setup.sh` - 驗證環境設置
- ✅ `test-pipeline.sh` - 端到端測試
- ✅ `deploy-to-gcp.sh` - 部署到 Dataflow
- ✅ `run-local.sh` - 本地測試

### 4. 文檔 ✅
- ✅ `GCP_SETUP_GUIDE.md` - 完整的部署指南
- ✅ `README.md` - 項目概述
- ✅ `ARCHITECTURE.md` - 架構文檔

---

## 🚀 現在你需要做什麼？

### 階段 1: 設置 GCP 環境（必須！）

**你說得對，必須先設置 GCP 環境才能部署。**

#### Step 1: 登入 GCP

```bash
# 如果還沒安裝 gcloud
brew install google-cloud-sdk  # macOS

# 登入
gcloud auth login
gcloud auth application-default login
```

#### Step 2: 設置環境變量

```bash
cd /Users/sam/Desktop/work/SeniorCarePlus-Platform

# 設置你的實際項目 ID
export GCP_PROJECT_ID="your-actual-project-id"
export GCP_REGION="asia-east1"
```

#### Step 3: 運行一鍵設置腳本

```bash
./scripts/setup-gcp.sh
```

**這個腳本會創建：**
- Pub/Sub Topic 和 Subscription
- BigQuery Dataset 和兩個表
- GCS Bucket 和目錄
- 啟用所有必要的 API

**預計時間：5-10 分鐘**

#### Step 4: 驗證設置

```bash
./scripts/verify-setup.sh
```

如果所有檢查都是 ✅，繼續下一步！

---

### 階段 2: 設置 Redis

你有三個選擇：

#### 選項 A: Google Cloud Memorystore（推薦生產）

```bash
# 創建實例（需要 10-15 分鐘）
gcloud redis instances create senior-care-redis \
  --size=5 \
  --region=asia-east1 \
  --zone=asia-east1-a \
  --redis-version=redis_7_0 \
  --project=$GCP_PROJECT_ID

# 獲取 IP
export REDIS_HOST=$(gcloud redis instances describe senior-care-redis \
  --region=asia-east1 \
  --format="get(host)" \
  --project=$GCP_PROJECT_ID)
```

#### 選項 B: 本地測試

```bash
# 使用 Docker
docker run -d -p 6379:6379 redis:7-alpine

export REDIS_HOST="localhost"
```

#### 選項 C: 自己的 Redis

```bash
export REDIS_HOST="your-redis-ip"
export REDIS_PASSWORD="your-password"
```

---

### 階段 3: 本地測試（可選但強烈建議）

```bash
# 1. 構建
./gradlew clean build

# 2. 本地運行（DirectRunner）
./scripts/run-local.sh

# 3. 在另一個終端發送測試數據
./scripts/test-pipeline.sh
```

---

### 階段 4: 部署到 GCP Dataflow

```bash
# 確保環境變量已設置
echo "Project: $GCP_PROJECT_ID"
echo "Redis: $REDIS_HOST"

# 部署
./scripts/deploy-to-gcp.sh
```

**部署成功後，你會看到：**
- Job Name 和 ID
- 監控 URL

---

### 階段 5: 驗證數據流

```bash
# 發送測試數據
./scripts/test-pipeline.sh

# 查看 BigQuery
bq query --project_id=$GCP_PROJECT_ID \
  "SELECT * FROM health.vital_signs ORDER BY timestamp DESC LIMIT 5"

bq query --project_id=$GCP_PROJECT_ID \
  "SELECT * FROM health.diaper_status ORDER BY timestamp DESC LIMIT 5"

# 查看 Redis
redis-cli -h $REDIS_HOST GET vitals:1302
redis-cli -h $REDIS_HOST GET diaper:1302
```

---

## 📊 監控和維護

### 查看 Dataflow Job

```bash
# 瀏覽器
https://console.cloud.google.com/dataflow/jobs?project=$GCP_PROJECT_ID

# 命令行
gcloud dataflow jobs list --project=$GCP_PROJECT_ID --region=asia-east1
```

### 查看日誌

```bash
# Cloud Logging
https://console.cloud.google.com/logs/query?project=$GCP_PROJECT_ID
```

### 查看成本

```bash
# Billing
https://console.cloud.google.com/billing/reports?project=$GCP_PROJECT_ID
```

---

## 🔧 GitHub Actions 設置（用於 CI/CD）

如果你想使用 GitHub Actions 自動部署，需要設置 Secrets：

### 1. 創建 Service Account

```bash
# 創建 Service Account
gcloud iam service-accounts create dataflow-deployer \
  --display-name="Dataflow Deployer" \
  --project=$GCP_PROJECT_ID

# 授予權限
gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:dataflow-deployer@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/dataflow.admin"

gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:dataflow-deployer@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/storage.admin"

# 創建 Key
gcloud iam service-accounts keys create ~/dataflow-deployer-key.json \
  --iam-account=dataflow-deployer@${GCP_PROJECT_ID}.iam.gserviceaccount.com \
  --project=$GCP_PROJECT_ID
```

### 2. 在 GitHub 設置 Secrets

前往：https://github.com/sam9407287/SeniorCarePlusPlatform/settings/secrets/actions

添加以下 Secrets：

| Secret Name | Value |
|-------------|-------|
| `GCP_SA_KEY` | `~/dataflow-deployer-key.json` 的完整內容 |
| `GCP_PROJECT_ID` | 你的 GCP 項目 ID |
| `GCP_REGION` | `asia-east1` （或你的區域）|
| `REDIS_HOST` | Redis IP 地址 |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | Redis 密碼（如果有） |

### 3. 手動觸發部署

前往：https://github.com/sam9407287/SeniorCarePlusPlatform/actions/workflows/deploy-dataflow.yml

點擊 "Run workflow"

---

## 💰 預估成本

根據 10 萬人規模（20,000 Gateway + 80,000 Tag，每5秒發送）：

| 服務 | 月成本估算 |
|------|-----------|
| Pub/Sub | $5-10 |
| Dataflow | $100-200 |
| BigQuery | $20-50 |
| Redis (Memorystore 5GB) | $50 |
| **總計** | **$175-310** |

**開發環境省錢建議：**
- 使用小型 Redis (1GB): ~$10
- 減少 Dataflow workers: 1-2 個
- 使用自動縮放

---

## 📚 參考文檔

- **GCP 設置**: `GCP_SETUP_GUIDE.md`（完整部署指南）
- **架構設計**: `ARCHITECTURE.md`
- **快速開始**: `QUICK_START.md`
- **項目概述**: `README.md`

---

## 🆘 遇到問題？

### 常見問題

1. **Dataflow Job 啟動失敗**
   ```bash
   # 檢查 API 是否啟用
   ./scripts/verify-setup.sh
   ```

2. **數據未出現**
   ```bash
   # 查看 Dataflow 日誌
   gcloud dataflow jobs describe JOB_ID --region=asia-east1
   ```

3. **Redis 連接失敗**
   ```bash
   # 測試連接
   redis-cli -h $REDIS_HOST ping
   ```

### 檢查清單

部署前：
- [ ] 已運行 `./scripts/setup-gcp.sh`
- [ ] 已運行 `./scripts/verify-setup.sh`（全部通過）
- [ ] Redis 已設置
- [ ] 環境變量已設置（GCP_PROJECT_ID, REDIS_HOST）

部署後：
- [ ] Dataflow Job 狀態為 Running
- [ ] 測試數據成功寫入 BigQuery
- [ ] 測試數據成功寫入 Redis
- [ ] 無錯誤日誌

---

## 🎯 下一階段（Phase 2: Backend 開發）

當 Dataflow 部署成功並驗證後，你可以開始 Backend 開發：

1. **創建 backend 模塊**
2. **使用 shared-models**（已經有了！）
3. **實現 WebSocket**（實時推送 Redis 數據）
4. **實現 REST API**（歷史數據查詢 BigQuery）
5. **BI Engine 集成**（1-48小時溫數據加速）

但現在，先專注於讓 Dataflow 跑起來！

---

## 🚀 立即開始

```bash
# 1. 設置環境變量
export GCP_PROJECT_ID="your-project-id"

# 2. 運行設置腳本
./scripts/setup-gcp.sh

# 3. 驗證
./scripts/verify-setup.sh

# 4. 部署
./scripts/deploy-to-gcp.sh
```

**祝你部署順利！** 🎉

