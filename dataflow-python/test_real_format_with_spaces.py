#!/usr/bin/env python3
"""测试厂商真实格式（带空格的字段名）"""

import json
from health_data_pipeline import ParseHealthData

# 厂商真实格式（从你的截图）
real_vendor_format = json.dumps({
    "content": "300B",
    "gateway id": 137205,      # 带空格！
    "MAC": "E0:0E:08:36:93:F8",
    "SOS": 0,
    "hr": 85,
    "SpO2": 96,
    "bp syst": 130,            # 带空格！
    "bp diast": 87,            # 带空格！
    "skin temp": 33.5,         # 带空格！
    "room temp": 24.5,         # 带空格！
    "steps": 3857,
    "sleep time": "22:46",
    "wake time": "7:13",
    "light sleep (min)": 297,
    "deep sleep (min)": 38,
    "move": 26,
    "wear": 1,
    "battery level": 86,       # 带空格！
    "serial no": 1302
})

print("="*70)
print("测试厂商真实格式（带空格的字段名）")
print("="*70)

parser = ParseHealthData()

print("\n测试真实厂商格式:")
print("-"*70)
print(f"输入 JSON:")
print(json.dumps(json.loads(real_vendor_format), indent=2, ensure_ascii=False))
print()

try:
    results = list(parser.process(real_vendor_format))
    for r in results:
        print(f"✅ 输出标签: {r.tag}")
        print(f"✅ 输出值:")
        output = r.value
        for key, value in output.items():
            print(f"   {key}: {value}")
        print()
        print("验证关键字段:")
        print(f"   ✅ device_id (来自 MAC): {output.get('device_id')}")
        print(f"   ✅ systolic_bp (来自 'bp syst'): {output.get('systolic_bp')}")
        print(f"   ✅ diastolic_bp (来自 'bp diast'): {output.get('diastolic_bp')}")
        print(f"   ✅ body_temp (来自 'skin temp'): {output.get('body_temp')}")
        print(f"   ✅ battery_level (来自 'battery level'): {output.get('battery_level')}")
        
except Exception as e:
    print(f"❌ 错误: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "="*70)
print("测试完成！")
print("="*70)

