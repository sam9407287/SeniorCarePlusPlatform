# 🩺 Diaper 数据修复总结

## 📅 修复时间
**2025-12-17 22:50 - 22:55**

---

## 🔍 **问题诊断**

### 1. **Diaper 数据未进入 BigQuery**
- **原因**: 真实 MQTT 数据的 `content` 字段是 `"DV1"`，但代码只匹配 `"diaper dv1"`
- **统计**: MQTT 收到 **56 条 DV1 消息**，但 BigQuery 只有测试数据

### 2. **大量空数据被写入**
- **影响**: BigQuery 中 **128/186 行** (68.8%) 是空数据
- **原因**: 代码会保留必需字段（`device_id`, `timestamp`）即使值为 `None` 或 `'unknown'`

---

## ✅ **修复方案**

### **修复 1: 支持 DV1 格式**
```python
# health_data_pipeline.py Line 46
elif content_type.lower() in ['dv1', 'diaper dv1', 'diaper_dv1']:  # ✅ 支持多种格式
```

### **修复 2: 添加数据验证**
```python
# _parse_flat_vital_data() 和 _parse_flat_diaper_data()
# ✅ 验证必需字段，避免写入空数据
if not result.get('device_id') or result.get('device_id') == 'unknown':
    logger.warning(f"跳过无效数据: device_id 为空或 unknown")
    return None
```

### **修复 3: 过滤 None 数据**
```python
# ParseHealthData.process()
# ✅ 只写入有效数据
if vital_data is not None:
    yield beam.pvalue.TaggedOutput('vital_signs', vital_data)

if diaper_data is not None:
    yield beam.pvalue.TaggedOutput('diaper_status', diaper_data)
```

---

## 🚀 **部署信息**

### **Dataflow Job**
- **Job ID**: `2025-12-17_14_51_45-16151720834382822385`
- **名称**: `health-pipeline-dv1-validated`
- **状态**: ✅ `JOB_STATE_RUNNING`
- **配置**:
  - Project: `seniorcare-platform`
  - Region: `asia-east1`
  - Workers: 1 (max 2)
  - Machine: `n1-standard-1`
  - Network: `default`
  - Redis: `10.186.139.83:6379`

### **修复的参数问题**
1. ✅ **GCS Bucket**: `seniorcare-platform-dataflow` (之前误用 `seniorcare-dataflow-temp`)
2. ✅ **Pub/Sub Subscription**: `health-data-sub` (之前误用 `health-data-subscription`)

---

## 📊 **验证数据 (修复前)**

### **真实 Diaper 数据 (最新 10 条)**
```
Device 1: C5:D6:FB:1B:85:23 - 湿度 67% (偏湿)
Device 2: C5:C6:E3:19:47:43 - 湿度 47% (正常)
最新数据时间: 2025-12-17 22:33:16 UTC
```

### **MQTT 消息统计**
```
2921 config
2717 location
 289 heartbeat
  56 DV1        ← diaper 数据！
  35 topic
  34 300B       ← vital signs 数据
  11 node
   7 info
```

---

## 🎯 **预期结果**

修复后，应该看到：
1. ✅ **DV1 数据正常写入** BigQuery `diaper_status` 表
2. ✅ **不再有空数据** (device_id = null)
3. ✅ **所有字段完整**: `device_id`, `timestamp`, `humidity`, `diaper_status`, `battery_level`
4. ✅ **实时写入**: 每条 DV1 MQTT 消息都会触发写入

---

## 🔧 **后续验证步骤**

### 1. 等待 3-5 分钟后查询
```sql
SELECT 
  device_id,
  timestamp,
  humidity,
  diaper_status,
  processed_at
FROM `seniorcare-platform.health.diaper_status`
WHERE processed_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 10 MINUTE)
ORDER BY processed_at DESC
LIMIT 20;
```

### 2. 检查是否还有空数据
```sql
SELECT 
  COUNT(*) as total,
  COUNTIF(device_id IS NULL OR device_id = 'unknown') as invalid_count
FROM `seniorcare-platform.health.diaper_status`
WHERE processed_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 10 MINUTE);
```
**预期**: `invalid_count` = 0 ✅

### 3. 查看 Dataflow 日志
```bash
gcloud logging read "resource.type=dataflow_step AND textPayload:DV1" \
  --project=seniorcare-platform --limit=10
```

---

## 📝 **相关文件**

- **Pipeline**: `/dataflow-python/health_data_pipeline.py`
- **MQTT Bridge**: `/scripts/mqtt-pubsub-bridge.py`
- **部署日志**: `/tmp/dataflow-deploy-v2.log`

---

## 💡 **关键经验**

1. ✅ **实际数据格式优先**: 真实 MQTT 数据格式可能与前端截图或文档不同
2. ✅ **数据验证很重要**: 必需字段验证可以避免写入无效数据
3. ✅ **统计分析有效**: 通过统计 MQTT 消息类型快速定位问题
4. ✅ **配置名称要准确**: GCS bucket 和 Pub/Sub subscription 名称必须完全匹配

---

**修复完成！** 🎉

