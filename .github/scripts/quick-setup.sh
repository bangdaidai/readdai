#!/bin/bash
# GitHub Actions 快速设置脚本
# 帮助你在几分钟内部署云端构建

set -e

echo "========================================="
echo "  GitHub Actions 云端构建快速设置"
echo "========================================="
echo ""

# 检查是否在 Git 仓库中
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    echo "❌ 错误: 当前目录不是 Git 仓库"
    echo "请先初始化 Git: git init"
    exit 1
fi

# 检查是否有 GitHub 远程仓库
if ! git remote -v | grep -q github.com; then
    echo "⚠️  警告: 未检测到 GitHub 远程仓库"
    echo "请先添加远程仓库: git remote add origin https://github.com/USERNAME/REPO.git"
    read -p "是否继续？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "✅ Git 仓库检查通过"
echo ""

# 检查工作流文件
echo "📋 检查 GitHub Actions 工作流文件..."
WORKFLOW_DIR=".github/workflows"

if [ ! -d "$WORKFLOW_DIR" ]; then
    echo "❌ 错误: 未找到 $WORKFLOW_DIR 目录"
    exit 1
fi

WORKFLOW_COUNT=$(ls -1 "$WORKFLOW_DIR"/*.yml 2>/dev/null | wc -l)
echo "✅ 找到 $WORKFLOW_COUNT 个工作流文件"
echo ""

# 列出可用的工作流
echo "📝 可用的工作流:"
for file in "$WORKFLOW_DIR"/*.yml; do
    if [ -f "$file" ]; then
        basename "$file"
    fi
done
echo ""

# 检查签名配置
echo "🔐 签名密钥配置检查:"
echo ""
echo "要进行 Release 构建，你需要在 GitHub Secrets 中配置以下变量:"
echo ""
echo "  1. ANDROID_KEYSTORE_BASE64  (签名密钥的 base64 编码)"
echo "  2. RELEASE_STORE_PASSWORD   (密钥库密码)"
echo "  3. RELEASE_KEY_ALIAS        (密钥别名)"
echo "  4. RELEASE_KEY_PASSWORD     (密钥密码)"
echo ""
echo "📖 详细配置指南请查看: .github/SIGNING_SETUP.md"
echo ""

read -p "是否已配置签名密钥？(y/n/skip) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "✅ 签名密钥已配置"
elif [[ $REPLY =~ ^[Ss]$ ]]; then
    echo "ℹ️  你可以稍后配置，现在将只进行 Debug 构建测试"
else
    echo "ℹ️  你可以稍后配置，现在将只进行 Debug 构建测试"
fi
echo ""

# 提交并推送
echo "🚀 准备推送到 GitHub..."
echo ""

# 检查是否有未提交的更改
if ! git diff-index --quiet HEAD --; then
    echo "📝 检测到未提交的更改"
    read -p "是否提交并推送？(y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        git add .
        git commit -m "chore: add GitHub Actions CI/CD configuration
        
- Add Android CI/CD pipeline workflow
- Add auto version and release workflow
- Add signing setup guide
- Add cloud build documentation
- Add build optimization scripts"
        echo "✅ 更改已提交"
    fi
else
    echo "✅ 没有未提交的更改"
fi
echo ""

# 推送
echo "📤 推送到 GitHub..."
git push origin HEAD
echo "✅ 代码已推送"
echo ""

# 提供下一步指引
echo "========================================="
echo "  🎉 设置完成！"
echo "========================================="
echo ""
echo "📱 下一步操作:"
echo ""
echo "1️⃣  访问你的 GitHub 仓库"
echo "   → 点击 'Actions' 标签页"
echo "   → 查看工作流运行状态"
echo ""
echo "2️⃣  首次构建（推荐）"
echo "   → 选择 'Android CI/CD Pipeline'"
echo "   → 点击 'Run workflow'"
echo "   → 选择 build_type: debug"
echo "   → 等待构建完成"
echo ""
echo "3️⃣  配置签名密钥（Release 构建必需）"
echo "   → Settings → Secrets and variables → Actions"
echo "   → 添加 ANDROID_KEYSTORE_BASE64 等 Secrets"
echo "   → 查看详细指南: .github/SIGNING_SETUP.md"
echo ""
echo "4️⃣  发布正式版本"
echo "   → 选择 'Auto Version & Release'"
echo "   → 选择版本类型 (patch/minor/major)"
echo "   → 点击 'Run workflow'"
echo ""
echo "📖 完整文档: .github/CLOUD_BUILD_GUIDE.md"
echo ""
echo "💡 提示: 首次构建可能需要 5-10 分钟（下载依赖）"
echo ""