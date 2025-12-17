# 🎉 SeniorCare Platform 部署状态

## ✅ 已完成的配置

### 1. GCP 项目设置
- ✅ 项目 ID: `seniorcare-platform`
- ✅ 区域: `asia-east1`（台湾）
- ✅ 计费账户: 已绑定
- ✅ 所有必要的 API 已启用

### 2. Pub/Sub 配置
- ✅ 主题: `health-data-topic`
- ✅ 订阅: `health-data-sub`
- ✅ 死信队列: `health-data-deadletter`
- ✅ 测试消息: 已成功发送

### 3. BigQuery 配置
- ✅ 数据集: `health`
- ✅ 表: `vital_signs`（生理数据 300B）
- ✅ 表: `diaper_status`（尿布状态 Diaper DV1）
- ✅ 表结构: 已创建并验证

### 4. Cloud Storage 配置
- ✅ 存储桶: `seniorcare-platform-dataflow`
- ✅ 临时文件位置: 已配置
- ✅ 暂存位置: 已配置

### 5. Redis 配置
- ✅ 实例名称: `seniorcare-redis`
- ✅ 类型: Basic 1GB
- ✅ IP 地址: `10.36.182.187`
- ✅ 端口: `6379`
- ✅ 位置: `asia-east1-b`
- ✅ 预估费用: $46.72/月

### 6. 代码和配置文件
- ✅ Python Dataflow 管道: `dataflow-python/health_data_pipeline.py`
- ✅ GCP 配置文件: `gcp-config.env`
- ✅ 测试脚本: `scripts/test-pubsub.sh`
- ✅ Python 依赖: 已安装

---

## 📋 数据流架构

```
IoT 设备 → MQTT Broker → Pub/Sub (health-data-topic)
                              ↓
                         [Dataflow 管道]
                         (解析 + 验证 + 去重)
                              ↓
                    ┌─────────┴─────────┐
                    ↓                   ↓
              Redis (热数据)        BigQuery (冷数据)
           - 最新数据 (TTL 1h)    - 历史数据 (30天+)
           - 时间序列 (720条)     - 分区表
                    ↓                   ↓
            WebSocket 推送        REST API 查询
                    ↓                   ↓
                  前端 React 应用
```

---

## 🚀 如何运行 Dataflow 管道

### 方法 1：本地测试（DirectRunner）

```bash
cd /Users/sam/Desktop/work/SeniorCarePlus-Platform
./scripts/run-dataflow-local.sh
```

**注意**: 本地运行会一直监听 Pub/Sub，按 `Ctrl+C` 停止。

### 方法 2：部署到 GCP（DataflowRunner）

```bash
cd dataflow-python

python3 health_data_pipeline.py \
  --project=seniorcare-platform \
  --subscription=projects/seniorcare-platform/subscriptions/health-data-sub \
  --bigquery-dataset=health \
  --redis-host=10.36.182.187 \
  --redis-port=6379 \
  --runner=DataflowRunner \
  --region=asia-east1 \
  --temp_location=gs://seniorcare-platform-dataflow/temp \
  --staging_location=gs://seniorcare-platform-dataflow/staging \
  --job_name=seniorcare-health-pipeline
```

---

## 🧪 验证数据流

### 1. 发送测试数据
```bash
./scripts/test-pubsub.sh
```

### 2. 检查 BigQuery 数据
```bash
# 查询生理数据
bq query --project_id=seniorcare-platform \
  "SELECT * FROM health.vital_signs ORDER BY timestamp DESC LIMIT 10"

# 查询尿布数据
bq query --project_id=seniorcare-platform \
  "SELECT * FROM health.diaper_status ORDER BY timestamp DESC LIMIT 10"
```

### 3. 检查 Redis 数据
需要连接到 Redis 实例（10.36.182.187:6379）并查询：
```
# 获取最新生理数据
GET vital_signs:latest:TEST-DEVICE-001

# 获取时间序列数据
ZRANGE vital_signs:timeseries:TEST-DEVICE-001 0 -1 WITHSCORES
```

---

## 💰 成本估算（每月）

| 服务 | 配置 | 预估成本 |
|------|------|----------|
| Redis (Memorystore) | Basic 1GB | $46.72 |
| Dataflow | 1-3 workers | $50-150 |
| Pub/Sub | 100 设备 × 5秒 | ~$10 |
| BigQuery | 存储 + 查询 | ~$10-30 |
| Cloud Storage | 临时文件 | ~$1 |
| **总计** | | **$120-240** |

**免费试用**: 你有 $300 免费额度（90天），足够测试使用！

---

## 📝 下一步

### 立即可做：
1. ✅ **运行本地 Dataflow 测试**
   ```bash
   ./scripts/run-dataflow-local.sh
   ```
   
2. ✅ **发送更多测试数据**
   ```bash
   ./scripts/test-pubsub.sh
   ```

3. ✅ **查看 BigQuery 中的数据**
   - 访问：https://console.cloud.google.com/bigquery?project=seniorcare-platform
   - 查询 `health.vital_signs` 和 `health.diaper_status`

### 后续优化：
- [ ] 修复 Kotlin Dataflow 代码编译问题
- [ ] 部署到 GCP Dataflow（生产环境）
- [ ] 设置监控和告警
- [ ] 配置自动扩展
- [ ] 集成前端 WebSocket
- [ ] 添加数据验证规则

---

## 🆘 故障排除

### Dataflow 无法连接 Redis
- 检查 Redis IP: `10.36.182.187`
- 检查 VPC 网络配置
- 确认 Dataflow workers 在同一网络

### BigQuery 写入失败
- 检查表结构是否匹配
- 查看 Dataflow 日志
- 确认 IAM 权限

### Pub/Sub 消息未处理
- 检查订阅是否有积压消息
- 确认 Dataflow 管道正在运行
- 查看死信队列

---

## 📞 联系信息

- **GCP 项目**: seniorcare-platform
- **GCP Console**: https://console.cloud.google.com/?project=seniorcare-platform
- **Memorystore**: https://console.cloud.google.com/memorystore/redis/instances?project=seniorcare-platform

---

**生成时间**: 2025-12-18  
**状态**: 🟢 就绪，等待 Dataflow 部署

