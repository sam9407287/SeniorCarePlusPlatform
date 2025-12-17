# 🔄 实时数据流状态报告

**检查时间**: 2025-12-17 20:48

---

## ✅ 已完成配置

### 1. MQTT → Pub/Sub 桥接 ✅
- **状态**: 运行中 (PID: 39582)
- **MQTT Broker**: `067ec32ef1344d3bb20c4e53abdde99a.s1.eu.hivemq.cloud:8883`
- **认证**: testweb1 / Aa000000
- **订阅 Topic**: `UWB/+/Health`
- **目标**: GCP Pub/Sub `health-data-topic`

### 2. Dataflow Pipeline ✅
- **Job ID**: `2025-12-17_12_44_45-5259759328657375321`
- **状态**: Running
- **配置**: 1 worker, n1-standard-1
- **功能**: 支持真实格式（带空格字段名）

### 3. BigQuery 验证 ✅
**查询结果**:
```sql
SELECT device_id, timestamp, heart_rate, systolic_bp, spo2
FROM vital_signs
ORDER BY timestamp DESC LIMIT 2;
```

| device_id | heart_rate | systolic_bp | spo2 |
|-----------|------------|-------------|------|
| FINAL_TEST_VITAL | 88 | 132 | 98 |
| TEST_BRIDGE_001 | 88 | 128 | 97 |

✅ **数据流已通！** 测试消息成功写入 BigQuery

---

## 🔍 当前状态

### 数据流路径：
```
真实 IoT 设备 (300B 手表)
    ↓
HiveMQ Cloud MQTT Broker
    ↓ Topic: UWB/+/Health
[MQTT Bridge 进程: 39582] ✅ 运行中
    ↓
GCP Pub/Sub (health-data-topic)
    ↓
Dataflow Job: 2025-12-17_12_44_45... ✅ 运行中
    ↓
BigQuery (vital_signs 表) ✅ 数据已到达
```

---

## 📋 验证清单

- ✅ MQTT 桥接脚本运行中
- ✅ Dataflow Job 运行中
- ✅ 测试消息成功写入 BigQuery
- ✅ 带空格字段名正确解析（`bp syst` → `systolic_bp`）
- ⏳ **等待真实设备数据**

---

## 🔍 如何查看真实设备数据

### 查询特定 MAC 地址：
```bash
bq query --use_legacy_sql=false \
"SELECT * FROM \`seniorcare-platform.health.vital_signs\`
WHERE device_id = 'E0:0E:08:36:93:F8'  -- 替换为真实MAC
ORDER BY timestamp DESC LIMIT 10"
```

### 查询最新的所有数据：
```bash
bq query --use_legacy_sql=false \
"SELECT 
  device_id,
  FORMAT_TIMESTAMP('%Y-%m-%d %H:%M:%S', timestamp) as time,
  heart_rate,
  systolic_bp,
  spo2
FROM \`seniorcare-platform.health.vital_signs\`
ORDER BY timestamp DESC
LIMIT 10"
```

### 查看所有设备列表：
```bash
bq query --use_legacy_sql=false \
"SELECT 
  device_id,
  COUNT(*) as record_count,
  MAX(timestamp) as last_seen
FROM \`seniorcare-platform.health.vital_signs\`
GROUP BY device_id
ORDER BY last_seen DESC"
```

---

## 🐛 故障排查

### 如果没有看到真实设备数据：

#### 1. 检查 MQTT 桥接日志
```bash
ps aux | grep mqtt-pubsub-bridge  # 确认进程运行
kill -USR1 39582  # 发送信号（如果支持）
```

#### 2. 检查 Pub/Sub 消息
```bash
gcloud pubsub subscriptions describe health-data-sub \
  --project=seniorcare-platform
```

#### 3. 检查 Dataflow 日志
```bash
gcloud logging read \
  "resource.type=dataflow_step AND resource.labels.job_id=2025-12-17_12_44_45-5259759328657375321" \
  --limit=20 \
  --project=seniorcare-platform
```

#### 4. 手动拉取 Pub/Sub 消息测试
```bash
gcloud pubsub subscriptions pull health-data-sub \
  --project=seniorcare-platform \
  --limit=5 \
  --auto-ack
```

---

## 📊 监控链接

- **Dataflow Job**: https://console.cloud.google.com/dataflow/jobs/asia-east1/2025-12-17_12_44_45-5259759328657375321?project=seniorcare-platform
- **BigQuery 表**: https://console.cloud.google.com/bigquery?project=seniorcare-platform&d=health&t=vital_signs
- **Pub/Sub Topic**: https://console.cloud.google.com/cloudpubsub/topic/detail/health-data-topic?project=seniorcare-platform

---

## ✅ 下一步

1. **等待真实设备发送数据**
   - 确保设备开机并连接
   - 数据会自动流入系统

2. **查询验证**
   - 每隔几分钟查询 BigQuery
   - 寻找新的 MAC 地址

3. **如果需要重启桥接**:
```bash
# 停止当前桥接
kill 39582

# 重新启动
cd /Users/sam/Desktop/work/SeniorCarePlus-Platform/scripts
python3 mqtt-pubsub-bridge.py > /tmp/mqtt-bridge.log 2>&1 &
```

---

**系统已就绪！等待真实设备数据中...** 🎯

