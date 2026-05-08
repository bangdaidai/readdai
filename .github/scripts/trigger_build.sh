#!/bin/bash

# 云端构建自动化脚本
# 用于本地触发远程构建

set -e

REPO_OWNER="gedoor"
REPO_NAME="legado"
WORKFLOW_FILE="cloud_build.yml"

echo "🔧 云端构建工具"
echo "================"

# 解析命令行参数
BUILD_TYPE=${1:-debug}
PRODUCT=${2:-app}

echo "📦 构建配置:"
echo "  - 类型: $BUILD_TYPE"
echo "  - 产品: $PRODUCT"
echo ""

# 检查 GitHub CLI
if ! command -v gh &> /dev/null; then
    echo "❌ 错误: 需要安装 GitHub CLI"
    echo "安装命令: brew install gh (macOS) 或 参考 https://cli.github.com/"
    exit 1
fi

# 检查登录状态
if ! gh auth status &> /dev/null; then
    echo "❌ 错误: 未登录 GitHub"
    echo "请运行: gh auth login"
    exit 1
fi

echo "🚀 触发云端构建..."

# 触发工作流
gh workflow run "$WORKFLOW_FILE" \
  --field build_type="$BUILD_TYPE" \
  --field product="$PRODUCT"

echo ""
echo "✅ 构建已触发!"
echo ""
echo "📊 查看构建状态:"
echo "  gh run list --workflow=$WORKFLOW_FILE"
echo ""
echo "📥 下载构建产物:"
echo "  gh run download"
