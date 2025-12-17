# 架構文檔

## 系統架構圖

```
┌─────────────────────────────────────────────────────────────────────┐
│                         IoT 設備層                                   │
│  80,000 Tags + 20,000 Gateways                                      │
│  每 5 秒發送一次健康數據                                              │
└────────────────────────┬────────────────────────────────────────────┘
                         │ MQTT Protocol
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      MQTT Broker (HiveMQ Cloud)                      │
│  接收並轉發 IoT 設備消息                                              │
└────────────────────────┬────────────────────────────────────────────┘
                         │ Publish to Pub/Sub
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      GCP Pub/Sub (消息隊列)                          │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐        │
│  │ Topic        │ ──→ │ Subscription │ ──→ │ Dead Letter  │        │
│  │ health-data  │     │              │     │ Queue        │        │
│  └──────────────┘     └──────────────┘     └──────────────┘        │
│                                                                      │
│  吞吐量: ~48,000 messages/second                                     │
└────────────────────────┬────────────────────────────────────────────┘
                         │ Subscribe
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│              Apache Beam Dataflow Pipeline (本項目)                  │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  1. Read from Pub/Sub                                         │ │
│  │     ↓                                                          │ │
│  │  2. Parse & Flatten JSON (4層 → 2層)                          │ │
│  │     ├─→ Valid Data                                            │ │
│  │     └─→ Invalid Data → Dead Letter Queue                      │ │
│  │     ↓                                                          │ │
│  │  3. Validate Health Metrics                                   │ │
│  │     - 心率: 30-220 bpm                                         │ │
│  │     - 血氧: 0-100%                                             │ │
│  │     - 體溫: 30-45°C                                            │ │
│  │     ↓                                                          │ │
│  │  4. Windowing (5秒固定窗口)                                     │ │
│  │     ↓                                                          │ │
│  │  5. Deduplication (去重)                                       │ │
│  │     - 基於 deviceId + gatewayId + dataHash                    │ │
│  │     - 5秒窗口內過濾重複                                         │ │
│  │     ↓                                                          │ │
│  │  6. Parallel Write                                            │ │
│  │     ├─→ BigQuery (批量寫入)                                    │ │
│  │     └─→ Redis (批量寫入)                                       │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  Worker 配置:                                                        │
│  - 類型: n1-standard-2 (2 vCPU, 7.5GB RAM)                          │
│  - 數量: 5-20 (自動擴展)                                             │
│  - 吞吐量: ~10,000 records/second/worker                            │
└─────────┬───────────────────────────────────┬────────────────────────┘
          │                                   │
          ▼                                   ▼
┌──────────────────────┐          ┌──────────────────────┐
│   BigQuery           │          │   Redis              │
│   (歷史數據存儲)      │          │   (即時數據緩存)      │
├──────────────────────┤          ├──────────────────────┤
│ Dataset: health      │          │ 數據結構:             │
│ Table: patient_data  │          │                      │
│                      │          │ 1. 設備最新數據:      │
│ 分區: 按日期         │          │    health:gateway:ID │
│ 聚簇: device_id      │          │                      │
│                      │          │ 2. Gateway 索引:     │
│ 數據保留: 30天       │          │    gateway:ID:devices│
│ 存儲: 列式壓縮       │          │                      │
│                      │          │ 3. 時間序列:         │
│ 查詢性能:            │          │    timeseries:ID     │
│ - 掃描: TB級         │          │                      │
│ - 速度: 秒級         │          │ TTL: 1小時           │
└──────────────────────┘          │ 吞吐量: 100K ops/s   │
          │                       └──────────────────────┘
          │                                   │
          └───────────┬───────────────────────┘
                      │ Query
                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Ktor Backend (REST API)                           │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │ GET  /api/health/:deviceId        (從 Redis 讀取即時數據)      │ │
│  │ GET  /api/health/history/:deviceId (從 BigQuery 讀取歷史)     │ │
│  │ GET  /api/gateway/:gatewayId/devices (從 Redis 讀取列表)      │ │
│  │ GET  /api/analytics/stats          (從 BigQuery 聚合分析)     │ │
│  └───────────────────────────────────────────────────────────────┘ │
└────────────────────────┬────────────────────────────────────────────┘
                         │ HTTP/WebSocket
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         前端應用                                     │
│  ┌─────────────────┐     ┌─────────────────┐                       │
│  │  Web 前端        │     │  Android App    │                       │
│  │  (React+Firebase)│     │  (Kotlin)       │                       │
│  └─────────────────┘     └─────────────────┘                       │
└─────────────────────────────────────────────────────────────────────┘
```

## 數據流詳解

### 1. 數據輸入

```
IoT 設備 (80,000 個)
    ↓
每 5 秒發送一次
    ↓
MQTT Broker (HiveMQ)
    ↓
Pub/Sub Topic
    ↓
預期吞吐量: 80,000 / 5 = 16,000 messages/second
實際峰值: ~48,000 messages/second (考慮突發流量)
```

### 2. 數據轉換

#### 原始格式 (4層嵌套)

```json
{
  "gateway_id": "137205",
  "content": "300B",
  "MAC": "E0:0E:08:36:93:F8",
  "hr": 85,
  "spO2": 96,
  ...
}
```

#### 扁平格式 (2層)

```json
{
  "device_id": "1302",
  "device_type": "gateway",
  "gateway_id": "137205",
  "mac": "E0:0E:08:36:93:F8",
  "heart_rate": 85,
  "spo2": 96,
  "timestamp": "2025-12-17T14:30:00Z",
  ...
}
```

### 3. 去重策略

```kotlin
// 去重鍵生成
fun deduplicationKey(): String {
    val dataHash = listOf(
        heartRate, spO2, bpSystolic, bpDiastolic,
        skinTemp, roomTemp, steps, batteryLevel
    ).hashCode()
    
    return "$deviceId:$gatewayId:$dataHash"
}

// 5秒窗口內，相同鍵只保留一條
Window: [T, T+5s)
Key: "1302:137205:123456789"
Action: Keep first occurrence, drop duplicates
```

### 4. 存儲策略

#### BigQuery 存儲

```sql
-- 表結構
CREATE TABLE health.patient_data (
  device_id STRING NOT NULL,
  device_type STRING NOT NULL,
  gateway_id STRING NOT NULL,
  heart_rate INT64,
  spo2 INT64,
  timestamp TIMESTAMP NOT NULL,
  ...
)
PARTITION BY DATE(timestamp)
CLUSTER BY device_id;

-- 查詢示例: 最近1小時心率
SELECT device_id, AVG(heart_rate) as avg_hr
FROM health.patient_data
WHERE timestamp > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
GROUP BY device_id;
```

#### Redis 存儲

```bash
# 1. 設備最新數據 (Hash)
HSET health:gateway:1302 
  heart_rate 85
  spo2 96
  timestamp "2025-12-17T14:30:00Z"
  ...
EXPIRE health:gateway:1302 3600

# 2. Gateway 索引 (Set)
SADD gateway:137205:devices 1302 1303 1304
EXPIRE gateway:137205:devices 3600

# 3. 時間序列 (Sorted Set)
ZADD timeseries:1302 1702818600 "{...json...}"
ZREMRANGEBYRANK timeseries:1302 0 -101  # 保留最近100個
```

## 性能分析

### 吞吐量計算

```
輸入: 16,000 messages/second
去重率: ~20% (假設)
有效數據: 12,800 messages/second

每個 Worker:
- 處理能力: ~2,000 messages/second
- 需要 Workers: 12,800 / 2,000 = 7 個

加上緩衝 (2x): 14 個 Workers
配置範圍: 5-20 Workers (自動擴展)
```

### 延遲分析

```
階段                     延遲 (P50)    延遲 (P99)
────────────────────────────────────────────────
Pub/Sub → Dataflow      10ms         50ms
Parse & Flatten          5ms         20ms
Validation               2ms         10ms
Deduplication            3ms         15ms
Write to BigQuery       50ms        200ms
Write to Redis          10ms         50ms
────────────────────────────────────────────────
總延遲                   80ms        345ms
```

### 成本估算

#### Dataflow

```
Worker: n1-standard-2
價格: $0.095/小時
配置: 平均 10 Workers

月成本: 10 * 24 * 30 * $0.095 = $684
```

#### BigQuery

```
存儲: 每條 ~1KB
月數據量: 16,000 * 60 * 60 * 24 * 30 * 1KB = 41TB
存儲成本: 41TB * $0.02 = $820

查詢: 估計 1TB/月
查詢成本: 1TB * $5 = $5

月成本: $825
```

#### Redis (Memorystore)

```
實例: 5GB
價格: $0.054/GB/小時
月成本: 5 * 24 * 30 * $0.054 = $194
```

#### Pub/Sub

```
數據量: 估計 100GB/月
價格: $0.06/GB
月成本: 100 * $0.06 = $6
```

#### 總計

```
Dataflow:    $684
BigQuery:    $825
Redis:       $194
Pub/Sub:     $6
────────────────
總計:        $1,709/月
```

優化方案:
- 使用 Committed Use Discounts: 節省 25%
- 使用 Preemptible Workers: 節省 60-80%
- 調整 BigQuery 保留期: 從 30 天減少到 7 天

**優化後成本: ~$850/月**

## 可擴展性

### 水平擴展

```
當前配置: 16,000 messages/second
最大配置: 100,000 messages/second

擴展方式:
1. 增加 Dataflow Workers (最多 100 個)
2. Redis 讀寫分離 (主從複製)
3. BigQuery 自動分區和聚簇
4. Pub/Sub 自動擴展 (無需配置)
```

### 災難恢復

```
1. Pub/Sub 消息保留: 7 天
2. BigQuery 快照: 每日備份
3. Redis 持久化: AOF + RDB
4. Dataflow 自動重試: 3 次
5. Dead Letter Queue: 失敗消息保留 7 天
```

## 監控指標

### 關鍵指標

```
1. 吞吐量 (Throughput)
   - messages_received_per_second
   - messages_processed_per_second
   - backlog_size

2. 延遲 (Latency)
   - end_to_end_latency_p50
   - end_to_end_latency_p99
   - write_latency_bigquery
   - write_latency_redis

3. 錯誤率 (Error Rate)
   - failed_messages_percentage
   - validation_failure_rate
   - dead_letter_queue_size

4. 資源使用 (Resource Usage)
   - worker_cpu_utilization
   - worker_memory_utilization
   - redis_memory_usage
   - bigquery_storage_size
```

### 告警規則

```yaml
alerts:
  - name: High Backlog
    condition: backlog_size > 100000
    duration: 5m
    action: Scale up workers
    
  - name: High Error Rate
    condition: error_rate > 5%
    duration: 2m
    action: Send notification
    
  - name: High Latency
    condition: latency_p99 > 1000ms
    duration: 5m
    action: Investigate bottleneck
    
  - name: Redis Memory Full
    condition: redis_memory_usage > 90%
    duration: 1m
    action: Scale up Redis
```

---

**版本**: 1.0.0  
**最後更新**: 2025-12-17  
**維護者**: Senior Care Plus Team

