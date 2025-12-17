#!/usr/bin/env python3
"""检查 Redis 中的数据"""

import redis
import json

# 连接 Redis
redis_client = redis.Redis(
    host='10.36.182.187',
    port=6379,
    decode_responses=True
)

print("=" * 60)
print("检查 Redis 连接和数据")
print("=" * 60)
print()

# 测试连接
try:
    redis_client.ping()
    print("✅ Redis 连接成功！")
    print()
except Exception as e:
    print(f"❌ Redis 连接失败: {e}")
    exit(1)

# 检查所有 keys
print("检查所有 keys：")
all_keys = redis_client.keys('*')
print(f"  找到 {len(all_keys)} 个 keys")
if all_keys:
    for key in all_keys[:10]:  # 只显示前10个
        print(f"    - {key}")
print()

# 检查最新生理数据
print("检查最新生理数据 (vital_signs:latest:*)：")
vital_keys = redis_client.keys('vital_signs:latest:*')
print(f"  找到 {len(vital_keys)} 个设备")
for key in vital_keys[:5]:
    data = redis_client.get(key)
    if data:
        print(f"  {key}: {data[:100]}...")
print()

# 检查时间序列数据
print("检查时间序列数据 (vital_signs:series:*)：")
series_keys = redis_client.keys('vital_signs:series:*')
print(f"  找到 {len(series_keys)} 个设备")
for key in series_keys[:5]:
    count = redis_client.zcard(key)
    print(f"  {key}: {count} 条记录")
print()

# 检查尿布数据
print("检查尿布数据 (diaper:latest:*)：")
diaper_keys = redis_client.keys('diaper:latest:*')
print(f"  找到 {len(diaper_keys)} 个设备")
for key in diaper_keys[:5]:
    data = redis_client.get(key)
    if data:
        print(f"  {key}: {data[:100]}...")
print()

print("=" * 60)
print("检查完成")
print("=" * 60)

