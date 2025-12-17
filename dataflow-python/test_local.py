#!/usr/bin/env python3
"""本地测试脚本"""

import json
from datetime import datetime
from health_data_pipeline import ParseHealthData
import apache_beam as beam

# 测试数据
test_vital = json.dumps({
    "device_id": "TEST001",
    "timestamp": "2025-12-17T20:20:00Z",
    "content": "300B",
    "data": {
        "heart_rate": 85,
        "systolic_bp": 130,
        "diastolic_bp": 85,
        "spo2": 96,
        "temperature": 37.0,
        "battery_level": 75
    }
})

test_diaper = json.dumps({
    "device_id": "TEST002",
    "timestamp": "2025-12-17T20:20:05Z",
    "content": "diaper DV1",
    "data": {
        "humidity": 65,
        "temperature": 32,
        "button_status": "0x03"
    }
})

print("测试解析器...")
print("=" * 60)

parser = ParseHealthData()

print("\n1. 测试生理数据(300B):")
print(f"输入: {test_vital}")
try:
    results = list(parser.process(test_vital))
    for r in results:
        print(f"输出标签: {r.tag}")
        print(f"输出值: {json.dumps(r.value, indent=2, ensure_ascii=False)}")
except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()

print("\n2. 测试尿布数据(diaper DV1):")
print(f"输入: {test_diaper}")
try:
    results = list(parser.process(test_diaper))
    for r in results:
        print(f"输出标签: {r.tag}")
        print(f"输出值: {json.dumps(r.value, indent=2, ensure_ascii=False)}")
except Exception as e:
    print(f"错误: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 60)
print("测试完成")

