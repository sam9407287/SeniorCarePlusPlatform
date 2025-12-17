# 快速開始指南 🚀

這份指南將幫助你在 **10 分鐘內** 啟動並運行 Dataflow Pipeline。

## 📋 前置條件

- ✅ Java 17+ 已安裝
- ✅ GCP 帳號已設置
- ✅ Redis 可訪問（本地或雲端）

---

## 🎯 三步驟快速啟動

### 步驟 1: 設置環境變量 (1 分鐘)

```bash
# 設置你的 GCP 項目
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="asia-east1"

# 設置 Redis
export REDIS_HOST="localhost"  # 或你的 Redis 主機
export REDIS_PORT="6379"
```

### 步驟 2: 設置 GCP 資源 (5 分鐘)

```bash
# 運行自動設置腳本
cd SeniorCarePlusDataFlowKotlin
./scripts/setup-gcp.sh
```

這會自動創建：
- ✅ Pub/Sub Topic 和 Subscription
- ✅ BigQuery Dataset 和 Table
- ✅ GCS Bucket

### 步驟 3: 啟動 Pipeline (1 分鐘)

#### 選項 A: 本地運行（開發測試）

```bash
# 確保 Redis 正在運行
docker run -d -p 6379:6379 redis:7

# 運行 Pipeline
./scripts/run-local.sh
```

#### 選項 B: 部署到 GCP（生產環境）

```bash
./scripts/deploy-to-gcp.sh
```

---

## ✅ 驗證運行

### 1. 發送測試數據

```bash
gcloud pubsub topics publish health-data-topic \
  --message='{
    "gateway_id": "TEST001",
    "content": "TEST",
    "hr": 75,
    "spO2": 98,
    "serial no": 1111
  }'
```

### 2. 查詢 BigQuery

```bash
bq query --use_legacy_sql=false \
  "SELECT * FROM \`$GCP_PROJECT_ID.health.patient_data\` 
   WHERE serial_no = 1111 
   LIMIT 1"
```

期望輸出：
```
+------------+-------------+-------------+------+------+
| device_id  | device_type | gateway_id  | hr   | spo2 |
+------------+-------------+-------------+------+------+
| 1111       | gateway     | TEST001     | 75   | 98   |
+------------+-------------+-------------+------+------+
```

### 3. 查詢 Redis

```bash
redis-cli -h $REDIS_HOST GET health:gateway:1111
```

期望輸出：
```json
{
  "device_id": "1111",
  "heart_rate": 75,
  "spo2": 98,
  ...
}
```

---

## 🎉 成功！

如果你看到了上面的輸出，恭喜！你的 Pipeline 已經成功運行。

### 下一步

1. 📖 閱讀 [README.md](README.md) 了解完整功能
2. 🏗️ 閱讀 [ARCHITECTURE.md](ARCHITECTURE.md) 了解架構設計
3. 🚀 閱讀 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) 了解部署詳情

### 監控 Dashboard

- **Dataflow**: https://console.cloud.google.com/dataflow
- **BigQuery**: https://console.cloud.google.com/bigquery
- **Pub/Sub**: https://console.cloud.google.com/cloudpubsub

---

## ⚠️ 故障排除

### 問題: `Permission denied`

**解決**:
```bash
gcloud auth login
gcloud config set project $GCP_PROJECT_ID
```

### 問題: `Redis connection refused`

**解決**:
```bash
# 檢查 Redis 是否運行
redis-cli ping

# 如果沒運行，啟動 Redis
docker run -d -p 6379:6379 redis:7
```

### 問題: `BigQuery table not found`

**解決**:
```bash
# 重新運行設置腳本
./scripts/setup-gcp.sh
```

---

## 💬 需要幫助？

- 📧 Email: support@seniorcare.com
- 📚 文檔: [README.md](README.md)
- 🐛 報告問題: GitHub Issues

---

**開始使用吧！** 🚀

