#!/usr/bin/env python3
"""
SeniorCare Health Data Pipeline - Python version
处理来自 Pub/Sub 的健康数据并写入 BigQuery 和 Redis
"""

import apache_beam as beam
from apache_beam.options.pipeline_options import PipelineOptions, StandardOptions
from apache_beam.io import ReadFromPubSub, WriteToBigQuery
from apache_beam.transforms import window
import json
import redis
import logging
from datetime import datetime
from typing import Dict, Any

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ParseHealthData(beam.DoFn):
    """解析健康数据 JSON"""
    
    def process(self, element):
        try:
            # 解析 JSON
            data = json.loads(element.decode('utf-8') if isinstance(element, bytes) else element)
            content_type = data.get('content', '')
            device_id = data.get('device_id')
            timestamp = data.get('timestamp')
            
            # 添加处理时间
            processed_at = datetime.utcnow().isoformat()
            
            # 根据 content 类型打标签
            if content_type == '300B':
                # 合并外层字段和内层数据
                vital_data = data['data'].copy()
                vital_data['device_id'] = device_id
                vital_data['timestamp'] = timestamp
                vital_data['processed_at'] = processed_at
                # 重命名字段以匹配 BigQuery schema
                if 'temperature' in vital_data:
                    vital_data['body_temp'] = vital_data.pop('temperature')
                yield beam.pvalue.TaggedOutput('vital_signs', vital_data)
            elif content_type == 'diaper DV1':
                # 合并外层字段和内层数据
                diaper_data = self._process_diaper_data(data['data'])
                diaper_data['device_id'] = device_id
                diaper_data['timestamp'] = timestamp
                yield beam.pvalue.TaggedOutput('diaper_status', diaper_data)
            else:
                yield beam.pvalue.TaggedOutput('invalid', data)
                
        except Exception as e:
            logger.error(f"Error parsing data: {e}")
            yield beam.pvalue.TaggedOutput('invalid', {'error': str(e), 'raw_data': str(element)})
    
    def _process_diaper_data(self, data: Dict[str, Any]) -> Dict[str, Any]:
        """处理尿布数据，添加状态推断"""
        humidity = data.get('humidity', 0)
        
        # 推断尿布状态
        if humidity > 60:
            status = 'wet'
        elif humidity > 40:
            status = 'damp'
        else:
            status = 'dry'
        
        data['diaper_status'] = status
        data['processed_at'] = datetime.utcnow().isoformat()
        
        # 转换 button_status 从十六进制字符串到整数
        if 'button_status' in data and isinstance(data['button_status'], str):
            try:
                data['button_status'] = int(data['button_status'], 16)
            except ValueError:
                data['button_status'] = 0
        
        # 移除不在 schema 中的字段
        data.pop('temperature', None)
        
        return data


class WriteToRedis(beam.DoFn):
    """写入 Redis"""
    
    def __init__(self, redis_host: str, redis_port: int, redis_password: str = None, ttl: int = 3600):
        self.redis_host = redis_host
        self.redis_port = redis_port
        self.redis_password = redis_password
        self.ttl = ttl
        self.redis_client = None
    
    def setup(self):
        """初始化 Redis 连接"""
        self.redis_client = redis.Redis(
            host=self.redis_host,
            port=self.redis_port,
            password=self.redis_password if self.redis_password else None,
            decode_responses=True
        )
    
    def process(self, element, data_type):
        try:
            device_id = element.get('device_id')
            timestamp = element.get('timestamp')
            
            # 存储最新数据
            latest_key = f"{data_type}:latest:{device_id}"
            self.redis_client.setex(latest_key, self.ttl, json.dumps(element))
            
            # 存储时间序列数据 (Sorted Set)
            timeseries_key = f"{data_type}:timeseries:{device_id}"
            score = datetime.fromisoformat(timestamp.replace('Z', '+00:00')).timestamp()
            self.redis_client.zadd(timeseries_key, {json.dumps(element): score})
            self.redis_client.expire(timeseries_key, self.ttl)
            
            # 保留最近 720 条记录（1小时，每5秒一条）
            self.redis_client.zremrangebyrank(timeseries_key, 0, -721)
            
            logger.info(f"Written to Redis: {data_type}/{device_id}")
            yield element
            
        except Exception as e:
            logger.error(f"Error writing to Redis: {e}")
            yield element


def run_pipeline(
    project_id: str,
    subscription: str,
    bigquery_dataset: str,
    redis_host: str,
    redis_port: int = 6379,
    redis_password: str = None
):
    """运行数据管道"""
    
    # Pipeline 选项（使用命令行参数 + 必需的项目设置）
    options = PipelineOptions()
    options.view_as(StandardOptions).streaming = True
    
    # 确保 project 设置正确
    from apache_beam.options.pipeline_options import GoogleCloudOptions
    google_cloud_options = options.view_as(GoogleCloudOptions)
    if not google_cloud_options.project:
        google_cloud_options.project = project_id
    
    # BigQuery 表定义
    vital_signs_schema = {
        'fields': [
            {'name': 'device_id', 'type': 'STRING'},
            {'name': 'timestamp', 'type': 'TIMESTAMP'},
            {'name': 'heart_rate', 'type': 'INTEGER'},
            {'name': 'systolic_bp', 'type': 'INTEGER'},
            {'name': 'diastolic_bp', 'type': 'INTEGER'},
            {'name': 'spo2', 'type': 'INTEGER'},
            {'name': 'body_temp', 'type': 'FLOAT'},
            {'name': 'steps', 'type': 'INTEGER'},
            {'name': 'battery_level', 'type': 'INTEGER'},
            {'name': 'processed_at', 'type': 'TIMESTAMP'},
        ]
    }
    
    diaper_schema = {
        'fields': [
            {'name': 'device_id', 'type': 'STRING'},
            {'name': 'timestamp', 'type': 'TIMESTAMP'},
            {'name': 'humidity', 'type': 'INTEGER'},
            {'name': 'button_status', 'type': 'INTEGER'},
            {'name': 'battery_level', 'type': 'INTEGER'},
            {'name': 'diaper_status', 'type': 'STRING'},
            {'name': 'processed_at', 'type': 'TIMESTAMP'},
        ]
    }
    
    with beam.Pipeline(options=options) as pipeline:
        # 读取 Pub/Sub
        messages = (
            pipeline
            | 'ReadFromPubSub' >> ReadFromPubSub(subscription=subscription)
        )
        
        # 解析和分类
        parsed = (
            messages
            | 'ParseData' >> beam.ParDo(ParseHealthData()).with_outputs(
                'vital_signs', 'diaper_status', 'invalid'
            )
        )
        
        # 处理生理数据
        (
            parsed.vital_signs
            | 'WriteVitalSignsToBQ' >> WriteToBigQuery(
                table=f'{project_id}:{bigquery_dataset}.vital_signs',
                schema=vital_signs_schema,
                write_disposition=beam.io.BigQueryDisposition.WRITE_APPEND,
                create_disposition=beam.io.BigQueryDisposition.CREATE_IF_NEEDED,
            )
        )
        
        (
            parsed.vital_signs
            | 'WriteVitalSignsToRedis' >> beam.ParDo(
                WriteToRedis(redis_host, redis_port, redis_password),
                data_type='vital_signs'
            )
        )
        
        # 处理尿布数据
        (
            parsed.diaper_status
            | 'WriteDiaperToBQ' >> WriteToBigQuery(
                table=f'{project_id}:{bigquery_dataset}.diaper_status',
                schema=diaper_schema,
                write_disposition=beam.io.BigQueryDisposition.WRITE_APPEND,
                create_disposition=beam.io.BigQueryDisposition.CREATE_IF_NEEDED,
            )
        )
        
        (
            parsed.diaper_status
            | 'WriteDiaperToRedis' >> beam.ParDo(
                WriteToRedis(redis_host, redis_port, redis_password),
                data_type='diaper_status'
            )
        )
        
        # 记录无效数据
        (
            parsed.invalid
            | 'LogInvalid' >> beam.Map(lambda x: logger.warning(f"Invalid data: {x}"))
        )


if __name__ == '__main__':
    import argparse
    import sys
    
    # 创建自定义参数解析器（只解析我们的参数，其余传给 Beam）
    parser = argparse.ArgumentParser(description='SeniorCare Health Data Pipeline')
    parser.add_argument('--project', required=True, help='GCP Project ID')
    parser.add_argument('--subscription', required=True, help='Pub/Sub subscription')
    parser.add_argument('--bigquery-dataset', required=True, help='BigQuery dataset')
    parser.add_argument('--redis-host', required=True, help='Redis host')
    parser.add_argument('--redis-port', type=int, default=6379, help='Redis port')
    parser.add_argument('--redis-password', default=None, help='Redis password')
    
    # 解析已知参数，其余的交给 Beam
    args, pipeline_args = parser.parse_known_args()
    
    logger.info("Starting Health Data Pipeline...")
    logger.info(f"Project: {args.project}")
    logger.info(f"Subscription: {args.subscription}")
    logger.info(f"BigQuery Dataset: {args.bigquery_dataset}")
    logger.info(f"Redis: {args.redis_host}:{args.redis_port}")
    logger.info(f"Pipeline args: {pipeline_args}")
    
    # 将 Beam 参数添加到 sys.argv
    sys.argv = [sys.argv[0]] + pipeline_args
    
    run_pipeline(
        project_id=args.project,
        subscription=args.subscription,
        bigquery_dataset=args.bigquery_dataset,
        redis_host=args.redis_host,
        redis_port=args.redis_port,
        redis_password=args.redis_password
    )

