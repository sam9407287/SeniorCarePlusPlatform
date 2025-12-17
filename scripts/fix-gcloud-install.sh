#!/bin/bash
# 修复 gcloud 安装权限问题并重新安装

set -e

echo "====================================="
echo "  修复 gcloud 安装权限问题"
echo "====================================="
echo ""

# 修复 .config 目录权限（需要密码）
echo "步骤 1: 修复 ~/.config 目录权限..."
echo "（需要输入你的 macOS 密码）"
sudo chown -R $USER ~/.config
sudo chmod 755 ~/.config

echo "✅ 权限修复完成"
echo ""

# 清理可能存在的旧安装
echo "步骤 2: 清理旧的 gcloud 安装..."
brew uninstall --cask gcloud-cli 2>/dev/null || echo "没有旧安装需要清理"

echo ""
echo "步骤 3: 重新安装 gcloud-cli..."
brew install --cask gcloud-cli

echo ""
echo "====================================="
echo "✅ gcloud 安装完成！"
echo "====================================="
echo ""
echo "下一步："
echo "  1. 登录 GCP: gcloud auth login"
echo "  2. 设置应用默认凭证: gcloud auth application-default login"
echo "  3. 初始化配置: gcloud init"
echo ""

