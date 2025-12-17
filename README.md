# Senior Care Plus - Kotlin Dataflow Pipeline

🚀 基於 Apache Beam 和 Kotlin 的即時健康數據處理 Pipeline

## 📋 目錄

- [功能特性](#功能特性)
- [架構設計](#架構設計)
- [快速開始](#快速開始)
- [配置說明](#配置說明)
- [部署指南](#部署指南)
- [監控和維護](#監控和維護)

---

## 功能特性

### ✅ 核心功能

- **即時數據處理**：從 GCP Pub/Sub 接收病患健康數據
- **智能去重**：5秒窗口內自動過濾重複數據
- **數據驗證**：驗證心率、血氧、血壓等指標範圍
- **雙重存儲**：
  - BigQuery：歷史數據分析和長期存儲
  - Redis：即時數據查詢和快速訪問
- **可擴展性**：支持數萬病患同時發送數據（每5秒一次）
- **容錯機制**：失敗數據自動重試或發送到死信隊列

### 🎯 技術亮點

- **Kotlin + Apache Beam**：類型安全、函數式編程
- **流式處理**：真正的即時數據處理
- **狀態管理**：使用 Beam State API 實現高效去重
- **自動擴展**：GCP Dataflow 自動調整 Worker 數量

---

## 架構設計

```
                                            ┌─────────────────┐
                    病患設備                │   80,000 Tags   │
                   (每5秒發送)              │  20,000 Gateways│
                        │                   └────────┬────────┘
                        │ MQTT                       │
                        ▼                            │
                ┌──────────────┐                     │
                │ MQTT Broker  │                     │
                │  (HiveMQ)    │                     │
                └──────┬───────┘                     │
                       │ Publish                     │
                       ▼                             │
                ┌──────────────┐                     │
                │  Pub/Sub     │◀────────────────────┘
                │   Topic      │  (48,000 RPS)
                └──────┬───────┘
                       │ Subscribe
                       ▼
        ┌──────────────────────────────────┐
        │  Dataflow Pipeline (本項目)      │
        ├──────────────────────────────────┤
        │ 1. Parse & Flatten               │
        │ 2. Validate                      │
        │ 3. Deduplicate (5s window)       │
        │ 4. Write to Storage              │
        └──────┬───────────────────┬───────┘
               │                   │
       ┌───────▼──────┐    ┌──────▼─────┐
       │  BigQuery    │    │   Redis    │
       │ (歷史分析)    │    │ (即時查詢)  │
       └──────────────┘    └────────────┘
```

### 數據流程

```
原始 JSON (Pub/Sub)
    ↓
[Parse & Flatten Transform]
    ├─→ Valid Data
    │       ↓
    │   [Validation Transform]
    │       ↓
    │   [Window: 5s Fixed]
    │       ↓
    │   [Deduplication Transform]
    │       ↓
    │   ┌───┴───┐
    │   ▼       ▼
    │ Redis  BigQuery
    │
    └─→ Invalid Data → Dead Letter Queue
```

---

## 快速開始

### 1️⃣ 環境要求

- **Java**: JDK 17+
- **Kotlin**: 1.9+
- **Gradle**: 8.0+
- **GCP SDK**: 已安裝並配置
- **Redis**: 本地或雲端實例

### 2️⃣ 克隆和構建

```bash
cd SeniorCarePlusDataFlowKotlin

# 構建項目
./gradlew build

# 運行測試
./gradlew test
```

### 3️⃣ 本地運行

```bash
# 啟動 Redis (Docker)
docker run -d -p 6379:6379 redis:7

# 設置環境變量
export GCP_PROJECT_ID="your-project-id"

# 運行 Pipeline
./scripts/run-local.sh
```

### 4️⃣ 設置 GCP 環境

```bash
# 設置項目 ID
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="asia-east1"

# 運行設置腳本（創建 Pub/Sub、BigQuery 等）
./scripts/setup-gcp.sh
```

---

## 配置說明

### Pipeline 參數

| 參數 | 必需 | 默認值 | 說明 |
|------|------|--------|------|
| `inputSubscription` | ✅ | - | Pub/Sub 訂閱路徑 |
| `bigQueryTable` | ✅ | - | BigQuery 表（格式：`project:dataset.table`） |
| `redisHost` | ⭕ | localhost | Redis 主機地址 |
| `redisPort` | ⭕ | 6379 | Redis 端口 |
| `redisPassword` | ⭕ | - | Redis 密碼（可選） |
| `enableDeduplication` | ⭕ | true | 啟用去重 |
| `deduplicationWindowSeconds` | ⭕ | 5 | 去重窗口（秒） |
| `enableValidation` | ⭕ | true | 啟用數據驗證 |
| `redisTtlSeconds` | ⭕ | 3600 | Redis TTL（秒） |

### 數據格式

#### 輸入（Pub/Sub JSON）

```json
{
  "gateway_id": "137205",
  "content": "300B",
  "MAC": "E0:0E:08:36:93:F8",
  "hr": 85,
  "spO2": 96,
  "bp syst": 130,
  "bp diast": 87,
  "skin temp": 33.5,
  "room temp": 24.5,
  "steps": 3857,
  "battery level": 86,
  "serial no": 1302
}
```

#### 輸出（BigQuery / Redis）

```json
{
  "device_id": "1302",
  "device_type": "gateway",
  "gateway_id": "137205",
  "mac": "E0:0E:08:36:93:F8",
  "heart_rate": 85,
  "spo2": 96,
  "bp_systolic": 130,
  "bp_diastolic": 87,
  "skin_temp": 33.5,
  "room_temp": 24.5,
  "steps": 3857,
  "battery_level": 86,
  "serial_no": 1302,
  "timestamp": "2025-12-17T14:30:00Z",
  "processing_time": "2025-12-17T14:30:00.123Z"
}
```

---

## 部署指南

### 方式 1：使用部署腳本

```bash
# 設置環境變量
export GCP_PROJECT_ID="your-project-id"
export GCP_REGION="asia-east1"
export REDIS_HOST="your-redis-host"
export REDIS_PORT="6379"

# 部署到 GCP Dataflow
./scripts/deploy-to-gcp.sh
```

### 方式 2：手動部署

```bash
# 1. 構建 JAR
./gradlew clean fatJar

# 2. 上傳到 GCS
gsutil cp build/libs/SeniorCarePlusDataFlowKotlin-1.0.0-all.jar \
  gs://your-bucket/jars/

# 3. 啟動 Dataflow Job
gcloud dataflow jobs run health-data-pipeline \
  --gcs-location=gs://your-bucket/jars/SeniorCarePlusDataFlowKotlin-1.0.0-all.jar \
  --region=asia-east1 \
  --staging-location=gs://your-bucket/staging \
  --temp-location=gs://your-bucket/temp \
  --max-num-workers=10 \
  --parameters="inputSubscription=projects/your-project/subscriptions/health-data-sub,bigQueryTable=your-project:health.patient_data,redisHost=your-redis-host"
```

---

## 監控和維護

### 查看 Pipeline 狀態

```bash
# 列出所有 Dataflow Jobs
gcloud dataflow jobs list --region=asia-east1

# 查看特定 Job 詳情
gcloud dataflow jobs describe <JOB_ID> --region=asia-east1
```

### 監控 Dashboard

- **Dataflow Console**: https://console.cloud.google.com/dataflow
- **BigQuery Console**: https://console.cloud.google.com/bigquery
- **Pub/Sub Console**: https://console.cloud.google.com/cloudpubsub

### 常見查詢

#### 查詢 BigQuery 數據

```sql
-- 最近 1 小時的心率數據
SELECT 
  device_id,
  heart_rate,
  spo2,
  timestamp
FROM `your-project.health.patient_data`
WHERE timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
ORDER BY timestamp DESC
LIMIT 100;

-- 按設備統計數據量
SELECT 
  device_id,
  COUNT(*) as count,
  AVG(heart_rate) as avg_hr,
  AVG(spo2) as avg_spo2
FROM `your-project.health.patient_data`
WHERE DATE(timestamp) = CURRENT_DATE()
GROUP BY device_id
ORDER BY count DESC;
```

#### 查詢 Redis 數據

```bash
# 連接到 Redis
redis-cli -h your-redis-host -p 6379

# 查看設備最新數據
GET health:gateway:1302

# 查看 Gateway 下的所有設備
SMEMBERS gateway:137205:devices

# 查看設備的時間序列數據
ZRANGE timeseries:1302 -10 -1 WITHSCORES
```

### 效能調優

#### Dataflow Worker 配置

```bash
# 增加 Worker 數量（高峰期）
--max-num-workers=20
--num-workers=5

# 調整機器類型
--worker-machine-type=n1-standard-4  # 更強大
--worker-machine-type=n1-standard-1  # 省成本
```

#### Redis 優化

```bash
# 調整 TTL（減少記憶體使用）
--redisTtlSeconds=1800  # 30 分鐘

# 增加批次大小（提高吞吐量）
batchSize = 200  # 在 RedisBatchWriteTransform 中設置
```

---

## 故障排除

### 問題 1：Pipeline 無法啟動

**症狀**: `Failed to create Dataflow job`

**解決方案**:
1. 檢查 GCP 權限
2. 確認 API 已啟用
3. 檢查 Pub/Sub 訂閱存在

### 問題 2：數據未寫入 Redis

**症狀**: Redis 中沒有數據

**解決方案**:
1. 檢查 Redis 連接：`redis-cli -h HOST ping`
2. 查看 Dataflow 日誌中的錯誤
3. 確認防火牆規則

### 問題 3：重複數據過多

**症狀**: 看到很多重複數據

**解決方案**:
1. 調整去重窗口：`--deduplicationWindowSeconds=10`
2. 檢查數據來源是否重複發送
3. 查看日誌確認去重邏輯運行

---

## 成本估算

### GCP Dataflow

- **Worker**: n1-standard-2 @ $0.095/小時
- **估計**：5 Workers × 24小時 × 30天 = ~$340/月

### BigQuery

- **存儲**: $0.02/GB/月
- **查詢**: $5/TB
- **估計**：1TB 存儲 + 100GB 查詢 = ~$20/月

### Pub/Sub

- **消息**: $0.06/GB
- **估計**：100GB/月 = ~$6/月

**總計**: ~$366/月（可根據實際使用調整）

---

## 開發指南

### 添加新的數據字段

1. 更新 `HealthData.kt` 中的數據模型
2. 更新 `BigQueryIO.kt` 中的表結構
3. 更新 `ParseTransform.kt` 中的解析邏輯
4. 運行測試

### 添加新的轉換

1. 在 `transforms/` 創建新的 `DoFn`
2. 在 `HealthDataPipeline.kt` 中添加轉換步驟
3. 添加單元測試
4. 更新文檔

---

## 授權

MIT License

## 聯繫方式

Senior Care Plus Team - support@seniorcare.com

---

**祝使用愉快！** 🎉

