#!/usr/bin/env python3
"""
纯 MQTT 订阅测试 - 直接验证是否能收到设备数据
"""
import paho.mqtt.client as mqtt
import ssl
import json
from datetime import datetime
import sys

# MQTT 配置
MQTT_BROKER = "067ec32ef1344d3bb20c4e53abdde99a.s1.eu.hivemq.cloud"
MQTT_PORT = 8883
MQTT_USERNAME = "testweb1"
MQTT_PASSWORD = "Aa000000"

# 统计
received_count = 0
last_messages = []

def log(msg):
    """带时间戳的日志"""
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)

def on_connect(client, userdata, flags, rc):
    """连接回调"""
    print("="*70, flush=True)
    if rc == 0:
        log("✅ 成功连接到 HiveMQ Cloud MQTT Broker")
        log(f"📡 Broker: {MQTT_BROKER}:{MQTT_PORT}")
        log(f"👤 用户: {MQTT_USERNAME}")
        print(flush=True)
        
        # 订阅所有 UWB Topics（用通配符）
        topics = [
            ("UWB/+/Health", 0),      # 健康数据
            ("UWB/+/Location", 0),    # 位置数据
            ("UWB/#", 0)              # 所有 UWB 开头的 topic
        ]
        
        for topic, qos in topics:
            result = client.subscribe(topic, qos)
            log(f"✅ 已订阅: {topic} (QoS {qos}) - Result: {result}")
        
        print(flush=True)
        log("👂 开始监听设备消息...")
        print("=" * 70, flush=True)
        print(flush=True)
    else:
        error_msg = {
            1: "协议版本错误",
            2: "客户端ID无效",
            3: "服务器不可用",
            4: "用户名或密码错误",
            5: "未授权"
        }
        log(f"❌ MQTT 连接失败 (code: {rc})")
        log(f"   原因: {error_msg.get(rc, '未知错误')}")

def on_disconnect(client, userdata, rc):
    """断开连接回调"""
    if rc != 0:
        log(f"⚠️ 意外断开连接 (code: {rc})，尝试重连...")

def on_message(client, userdata, msg):
    """消息回调"""
    global received_count, last_messages
    received_count += 1
    
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    try:
        # 解码并解析 JSON
        payload = msg.payload.decode('utf-8')
        data = json.loads(payload)
        
        # 提取关键字段
        mac = data.get('MAC', 'N/A')
        content = data.get('content', 'N/A')
        gateway_id = data.get('gateway id', 'N/A')
        hr = data.get('hr', 'N/A')
        spo2 = data.get('SpO2', 'N/A')
        
        # 打印消息
        print("=" * 70, flush=True)
        log(f"📨 收到消息 #{received_count}")
        print("=" * 70, flush=True)
        print(f"  Topic: {msg.topic}", flush=True)
        print(f"  MAC: {mac}", flush=True)
        print(f"  Content: {content}", flush=True)
        print(f"  Gateway ID: {gateway_id}", flush=True)
        if hr != 'N/A':
            print(f"  心率: {hr} bpm", flush=True)
        if spo2 != 'N/A':
            print(f"  血氧: {spo2}%", flush=True)
        print(flush=True)
        print("  完整 JSON:", flush=True)
        print(json.dumps(data, indent=2, ensure_ascii=False), flush=True)
        print("=" * 70, flush=True)
        print(flush=True)
        
        # 保存最近的消息
        last_messages.append({
            'timestamp': timestamp,
            'topic': msg.topic,
            'mac': mac,
            'data': data
        })
        if len(last_messages) > 10:
            last_messages.pop(0)
            
    except json.JSONDecodeError:
        log(f"⚠️ 收到非JSON消息:")
        print(f"  Topic: {msg.topic}", flush=True)
        print(f"  Raw: {msg.payload[:200]}", flush=True)
        print(flush=True)
    except Exception as e:
        log(f"❌ 处理消息时出错: {e}")
        print(f"  Topic: {msg.topic}", flush=True)
        print(flush=True)

def main():
    """主函数"""
    print("\n" + "="*70, flush=True)
    print("🧪 MQTT 订阅测试工具", flush=True)
    print("="*70, flush=True)
    log(f"启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(flush=True)
    print("🎯 测试目标:", flush=True)
    print("  1. 验证是否能连接到 MQTT Broker", flush=True)
    print("  2. 验证是否能收到真实设备消息", flush=True)
    print("  3. 显示收到的完整消息内容", flush=True)
    print("="*70, flush=True)
    print(flush=True)
    
    # 创建 MQTT 客户端
    client = mqtt.Client(
        client_id=f"test-client-{int(datetime.now().timestamp())}",
        clean_session=True
    )
    
    # 设置回调
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message
    
    # 配置 SSL/TLS
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
    
    try:
        # 连接
        log("正在连接到 MQTT Broker...")
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        
        # 开始监听
        client.loop_forever()
        
    except KeyboardInterrupt:
        print("\n" + "="*70, flush=True)
        log("⏹️ 用户中断")
        print("="*70, flush=True)
    except Exception as e:
        log(f"❌ 发生错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        print(flush=True)
        print("="*70, flush=True)
        print("📊 测试统计", flush=True)
        print("="*70, flush=True)
        print(f"  收到消息总数: {received_count}", flush=True)
        if last_messages:
            print(f"  最后一条消息:", flush=True)
            last = last_messages[-1]
            print(f"    时间: {last['timestamp']}", flush=True)
            print(f"    Topic: {last['topic']}", flush=True)
            print(f"    MAC: {last['mac']}", flush=True)
        else:
            print("  ⚠️ 未收到任何消息", flush=True)
        print("="*70, flush=True)
        client.disconnect()
        log("测试结束")

if __name__ == "__main__":
    main()

