#!/usr/bin/env python3
"""
MQTT → Pub/Sub 桥接
从 HiveMQ Cloud 接收真实设备数据，转发到 GCP Pub/Sub
专注处理 UWB/+/Health Topic
"""

import paho.mqtt.client as mqtt
from google.cloud import pubsub_v1
import json
import ssl
import time
from datetime import datetime

# ===== MQTT 配置（HiveMQ Cloud）=====
MQTT_BROKER = "067ec32ef1344d3bb20c4e53abdde99a.s1.eu.hivemq.cloud"
MQTT_PORT = 8883  # SSL/TLS 端口
MQTT_USERNAME = "testweb1"
MQTT_PASSWORD = "Aa000000"

# 只订阅健康数据 Topic
MQTT_TOPICS = [
    ("UWB/+/Health", 0),  # QoS 0，订阅所有 Gateway 的健康数据
]

# ===== GCP Pub/Sub 配置 =====
GCP_PROJECT = "seniorcare-platform"
PUBSUB_TOPIC = "health-data-topic"

# 初始化 Pub/Sub Publisher
publisher = pubsub_v1.PublisherClient()
topic_path = publisher.topic_path(GCP_PROJECT, PUBSUB_TOPIC)

# 统计数据
stats = {
    'received': 0,
    'forwarded': 0,
    'errors': 0,
    'start_time': datetime.now()
}

def on_connect(client, userdata, flags, rc):
    """MQTT 连接回调"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    if rc == 0:
        print(f"\n{'='*70}")
        print(f"[{timestamp}] ✅ 已连接到 HiveMQ Cloud MQTT Broker")
        print(f"{'='*70}")
        print(f"📡 Broker: {MQTT_BROKER}:{MQTT_PORT}")
        print(f"👤 用户: {MQTT_USERNAME}")
        print(f"🔐 认证: ✅ 成功")
        print(f"🎯 目标 Pub/Sub: projects/{GCP_PROJECT}/topics/{PUBSUB_TOPIC}")
        print()
        
        # 订阅所有 Topics
        for topic, qos in MQTT_TOPICS:
            result, mid = client.subscribe(topic, qos)
            if result == mqtt.MQTT_ERR_SUCCESS:
                print(f"✅ 已订阅: {topic} (QoS {qos})")
            else:
                print(f"❌ 订阅失败: {topic} (错误码: {result})")
        
        print()
        print("👂 开始监听真实设备数据...")
        print(f"{'='*70}")
        print()
        
    else:
        print(f"\n❌ MQTT 连接失败 (code: {rc})")
        error_messages = {
            1: "协议版本错误",
            2: "客户端 ID 无效",
            3: "服务器不可用",
            4: "用户名或密码错误",
            5: "未授权"
        }
        print(f"   原因: {error_messages.get(rc, '未知错误')}")

def on_disconnect(client, userdata, rc):
    """MQTT 断开连接回调"""
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    if rc != 0:
        print(f"\n[{timestamp}] ⚠️ 意外断开连接 (code: {rc})，尝试重连...")
    else:
        print(f"\n[{timestamp}] 🔌 正常断开连接")

def on_message(client, userdata, msg):
    """MQTT 消息回调 - 转发到 Pub/Sub"""
    global stats
    stats['received'] += 1
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    try:
        # 解码消息
        payload = msg.payload.decode('utf-8')
        data = json.loads(payload)
        
        # 提取关键信息
        mac = data.get('MAC', 'unknown')
        content = data.get('content', 'unknown')
        gateway_id = data.get('gateway id', data.get('gateway_id', 'N/A'))
        hr = data.get('hr', 'N/A')
        spo2 = data.get('SpO2', data.get('Spo2', 'N/A'))
        bp_syst = data.get('bp syst', data.get('bp_syst', 'N/A'))
        
        print(f"[{timestamp}] 📨 收到 MQTT 消息")
        print(f"  Topic: {msg.topic}")
        print(f"  MAC: {mac}")
        print(f"  Content: {content}")
        print(f"  Gateway: {gateway_id}")
        print(f"  生理数据: HR={hr}, SpO2={spo2}, BP_Syst={bp_syst}")
        
        # 转发到 Pub/Sub
        future = publisher.publish(topic_path, payload.encode('utf-8'))
        message_id = future.result(timeout=10.0)
        
        stats['forwarded'] += 1
        print(f"  ✅ 已转发到 Pub/Sub (Message ID: {message_id})")
        
        # 运行时统计
        elapsed = (datetime.now() - stats['start_time']).total_seconds()
        print(f"  📊 统计: 接收 {stats['received']} | 转发 {stats['forwarded']} | 错误 {stats['errors']} | 运行 {elapsed:.0f}秒")
        print()
        
    except json.JSONDecodeError as e:
        stats['errors'] += 1
        print(f"[{timestamp}] ❌ JSON 解析错误: {e}")
        print(f"  原始数据: {msg.payload[:200]}...")
        print()
    except Exception as e:
        stats['errors'] += 1
        print(f"[{timestamp}] ❌ 转发错误: {e}")
        import traceback
        traceback.print_exc()
        print()

def main():
    """主函数"""
    print("\n" + "="*70)
    print("🚀 MQTT → Pub/Sub 桥接启动")
    print("="*70)
    print(f"📅 启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()
    print("配置信息:")
    print(f"  📡 MQTT Broker: {MQTT_BROKER}:{MQTT_PORT}")
    print(f"  👤 用户名: {MQTT_USERNAME}")
    print(f"  🔐 密码: {'*' * len(MQTT_PASSWORD)}")
    print(f"  📝 订阅 Topics: {[t[0] for t in MQTT_TOPICS]}")
    print()
    print(f"  🎯 GCP Project: {GCP_PROJECT}")
    print(f"  📤 Pub/Sub Topic: {PUBSUB_TOPIC}")
    print("="*70)
    print()
    
    # 创建 MQTT 客户端
    client_id = f"gcp-bridge-{int(time.time())}"
    client = mqtt.Client(client_id=client_id, clean_session=True)
    
    # 设置回调
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message
    
    # 配置 SSL/TLS（HiveMQ Cloud 强制要求）
    client.tls_set(
        ca_certs=None,
        certfile=None,
        keyfile=None,
        cert_reqs=ssl.CERT_REQUIRED,
        tls_version=ssl.PROTOCOL_TLS,
        ciphers=None
    )
    
    # 设置认证
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    
    # 设置重连参数
    client.reconnect_delay_set(min_delay=1, max_delay=60)
    
    try:
        # 连接到 MQTT Broker
        print("🔌 正在连接到 HiveMQ Cloud...")
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        
        # 开始监听（阻塞模式）
        client.loop_forever()
        
    except KeyboardInterrupt:
        print("\n" + "="*70)
        print("⏹️ 用户中断，正在关闭...")
        print("="*70)
    except Exception as e:
        print(f"\n❌ 发生错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        # 显示最终统计
        elapsed = (datetime.now() - stats['start_time']).total_seconds()
        print()
        print("="*70)
        print("📊 最终统计")
        print("="*70)
        print(f"  运行时间: {elapsed:.0f} 秒")
        print(f"  接收消息: {stats['received']}")
        print(f"  成功转发: {stats['forwarded']}")
        print(f"  错误数量: {stats['errors']}")
        if stats['received'] > 0:
            success_rate = (stats['forwarded'] / stats['received']) * 100
            print(f"  成功率: {success_rate:.1f}%")
        print("="*70)
        
        # 断开连接
        client.disconnect()
        print("\n👋 程序结束")

if __name__ == "__main__":
    main()

