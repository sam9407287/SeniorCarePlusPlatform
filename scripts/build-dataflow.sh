#!/bin/bash
# 构建 Dataflow JAR 文件

set -e

echo "====================================="
echo "  构建 Dataflow 项目"
echo "====================================="
echo ""

# 进入项目目录
cd "$(dirname "$0")/.."

echo "步骤 1: 清理旧的构建文件..."
./gradlew clean

echo ""
echo "步骤 2: 编译 shared-models..."
./gradlew :shared-models:build

echo ""
echo "步骤 3: 编译 dataflow..."
./gradlew :dataflow:build -x test

echo ""
echo "步骤 4: 创建 fat JAR..."
./gradlew :dataflow:shadowJar

echo ""
echo "====================================="
echo "✅ 构建完成！"
echo "====================================="
echo ""
echo "JAR 文件位置:"
echo "  dataflow/build/libs/dataflow-1.0.0-all.jar"
echo ""

