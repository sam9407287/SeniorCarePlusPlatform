#!/usr/bin/env python3
"""测试真实 MQTT 格式"""

import json
from health_data_pipeline import ParseHealthData

# 真实 MQTT 数据（从前端截图）
real_mqtt_data = json.dumps({
    "content": "300B",
    "topic": "UWB/GW3C7C_Health",
    "gateway": "",
    "MAC": "D4:5D:0B:35:72:F7",
    "receivedAt": "2025-12-17T20:25:37.669Z",
    "hr": 87,
    "Spo2": 96,
    "bp_syst": 118,
    "bp_diast": 77,
    "skin_temp": 0,
    "room_temp": 23.2,
    "steps": 1021,
    "light_sleep": 0,
    "deep_sleep": 0,
    "battery_level": 90
})

# 测试格式（之前的）
test_format_data = json.dumps({
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

print("=" * 70)
print("测试真实 MQTT 格式和测试格式")
print("=" * 70)

parser = ParseHealthData()

print("\n1️⃣  测试真实 MQTT 格式:")
print("-" * 70)
print(f"输入: {real_mqtt_data}")
print()
try:
    results = list(parser.process(real_mqtt_data))
    for r in results:
        print(f"✅ 输出标签: {r.tag}")
        print(f"✅ 输出值:")
        print(json.dumps(r.value, indent=2, ensure_ascii=False))
except Exception as e:
    print(f"❌ 错误: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 70)
print("\n2️⃣  测试原测试格式（确保向后兼容）:")
print("-" * 70)
print(f"输入: {test_format_data}")
print()
try:
    results = list(parser.process(test_format_data))
    for r in results:
        print(f"✅ 输出标签: {r.tag}")
        print(f"✅ 输出值:")
        print(json.dumps(r.value, indent=2, ensure_ascii=False))
except Exception as e:
    print(f"❌ 错误: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 70)
print("测试完成！")
print("=" * 70)

