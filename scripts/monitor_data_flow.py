#!/usr/bin/env python3
"""
实时监控对比工具
同时监控 MQTT 和 BigQuery，验证数据是否正确流入
"""
import time
import subprocess
import json
from datetime import datetime, timedelta
from collections import defaultdict

# 颜色代码
GREEN = '\033[92m'
YELLOW = '\033[93m'
RED = '\033[91m'
BLUE = '\033[94m'
RESET = '\033[0m'

def log(msg, color=''):
    """带颜色的日志输出"""
    timestamp = datetime.now().strftime('%H:%M:%S')
    print(f"{color}[{timestamp}] {msg}{RESET}", flush=True)

def get_mqtt_messages():
    """从 MQTT 测试日志中提取收到的 MAC 地址"""
    try:
        with open('/tmp/mqtt-test-output.log', 'r') as f:
            content = f.read()
        
        # 简单解析收到的消息数
        count = content.count('📨 收到消息')
        
        # 提取 MAC 地址
        macs = []
        for line in content.split('\n'):
            if 'MAC:' in line and 'N/A' not in line:
                mac = line.split('MAC:')[1].strip().split()[0]
                if mac and mac != 'N/A':
                    macs.append(mac)
        
        return count, list(set(macs))
    except FileNotFoundError:
        return 0, []

def get_bigquery_data():
    """从 BigQuery 查询最近的数据"""
    try:
        cmd = [
            'bq', 'query',
            '--use_legacy_sql=false',
            '--project_id=seniorcare-platform',
            '--format=json',
            '''
            SELECT 
              device_id,
              COUNT(*) as count,
              MAX(processed_at) as last_time
            FROM `seniorcare-platform.health.vital_signs`
            WHERE processed_at > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 5 MINUTE)
            GROUP BY device_id
            '''
        ]
        
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0 and result.stdout.strip():
            data = json.loads(result.stdout)
            return data
        return []
    except Exception as e:
        log(f"查询 BigQuery 失败: {e}", RED)
        return []

def check_mqtt_bridge_status():
    """检查 MQTT 桥接进程状态"""
    try:
        result = subprocess.run(
            ['ps', 'aux'],
            capture_output=True,
            text=True
        )
        
        mqtt_bridge_running = 'mqtt-pubsub-bridge' in result.stdout
        mqtt_test_running = 'test_mqtt_direct' in result.stdout
        
        return mqtt_bridge_running, mqtt_test_running
    except:
        return False, False

def main():
    """主监控循环"""
    print("\n" + "="*80)
    print("🔍 实时数据流监控工具")
    print("="*80)
    log(f"启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", BLUE)
    print()
    print("监控内容:")
    print("  1. MQTT Broker 收到的设备消息")
    print("  2. BigQuery 中的数据记录")
    print("  3. 两者的对比验证")
    print("="*80)
    print()
    
    mqtt_last_count = 0
    bq_last_count = 0
    check_interval = 10  # 每10秒检查一次
    
    try:
        while True:
            print(f"\n{'='*80}")
            log("📊 检查状态...", BLUE)
            print(f"{'='*80}\n")
            
            # 1. 检查进程状态
            bridge_running, test_running = check_mqtt_bridge_status()
            
            print("🔧 进程状态:")
            if bridge_running:
                log("  ✅ MQTT → Pub/Sub 桥接: 运行中", GREEN)
            else:
                log("  ⚠️ MQTT → Pub/Sub 桥接: 未运行", YELLOW)
            
            if test_running:
                log("  ✅ MQTT 测试订阅: 运行中", GREEN)
            else:
                log("  ❌ MQTT 测试订阅: 未运行", RED)
            
            print()
            
            # 2. MQTT 收到的消息
            mqtt_count, mqtt_macs = get_mqtt_messages()
            
            print("📡 MQTT Broker 收到的消息:")
            if mqtt_count > 0:
                new_messages = mqtt_count - mqtt_last_count
                if new_messages > 0:
                    log(f"  ✅ 总计: {mqtt_count} 条 (新增 {new_messages} 条)", GREEN)
                else:
                    log(f"  📌 总计: {mqtt_count} 条 (无新消息)", YELLOW)
                
                if mqtt_macs:
                    print(f"  📱 设备列表: {', '.join(mqtt_macs)}")
            else:
                log("  ⚠️ 未收到任何 MQTT 消息", YELLOW)
            
            mqtt_last_count = mqtt_count
            print()
            
            # 3. BigQuery 中的数据
            bq_data = get_bigquery_data()
            
            print("💾 BigQuery 最近5分钟的数据:")
            if bq_data:
                total_count = sum(row.get('count', 0) for row in bq_data)
                new_records = total_count - bq_last_count
                
                if new_records > 0:
                    log(f"  ✅ 总计: {total_count} 条 (新增 {new_records} 条)", GREEN)
                else:
                    log(f"  📌 总计: {total_count} 条 (无新数据)", YELLOW)
                
                for row in bq_data:
                    device_id = row.get('device_id', 'N/A')
                    count = row.get('count', 0)
                    last_time = row.get('last_time', 'N/A')
                    print(f"     • {device_id}: {count} 条记录 (最后: {last_time})")
                
                bq_last_count = total_count
            else:
                log("  ⚠️ BigQuery 中没有数据", YELLOW)
            
            print()
            
            # 4. 对比分析
            print("🔍 数据流验证:")
            if mqtt_count > 0 and bq_data:
                # 检查 MAC 地址是否匹配
                bq_macs = [row.get('device_id', '') for row in bq_data]
                
                matched_macs = set(mqtt_macs) & set(bq_macs)
                
                if matched_macs:
                    log(f"  ✅ 数据流通畅！找到匹配设备: {', '.join(matched_macs)}", GREEN)
                else:
                    log("  ⚠️ MQTT 收到消息，但 BigQuery 中没有对应设备数据", YELLOW)
                    log(f"     MQTT 设备: {mqtt_macs}", YELLOW)
                    log(f"     BigQuery 设备: {bq_macs}", YELLOW)
            elif mqtt_count > 0 and not bq_data:
                log("  ⚠️ MQTT 收到消息，但 BigQuery 没有数据", YELLOW)
                log("     可能原因: Dataflow 处理延迟或出错", YELLOW)
            elif mqtt_count == 0:
                log("  ⚠️ MQTT 没有收到任何设备消息", YELLOW)
                log("     可能原因: 设备离线或 Topic 不匹配", YELLOW)
            
            print(f"\n{'='*80}")
            log(f"下次检查: {check_interval} 秒后...", BLUE)
            print(f"{'='*80}")
            
            time.sleep(check_interval)
            
    except KeyboardInterrupt:
        print("\n\n" + "="*80)
        log("⏹️ 监控已停止", BLUE)
        print("="*80)
        print()
        
        # 最终统计
        print("📊 最终统计:")
        print(f"  MQTT 收到消息: {mqtt_last_count} 条")
        print(f"  BigQuery 数据: {bq_last_count} 条")
        
        if mqtt_macs:
            print(f"  MQTT 设备列表: {', '.join(mqtt_macs)}")
        
        print()
        log("👋 监控结束", BLUE)

if __name__ == "__main__":
    main()

