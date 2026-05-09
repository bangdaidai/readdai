#!/bin/bash
# Gradle 构建优化脚本
# 用于本地测试和调试构建性能

set -e

echo "🚀 开始优化 Gradle 构建..."

# 清理旧的构建文件
echo "🧹 清理构建目录..."
./gradlew clean --no-daemon

# 预下载依赖
echo "📦 预下载依赖..."
./gradlew dependencies --no-daemon

# 显示构建环境信息
echo "💻 构建环境信息:"
echo "Java版本: $(java -version 2>&1 | head -n 1)"
echo "Gradle版本: $(./gradlew --version | grep 'Gradle' | head -n 1)"
echo "操作系统: $(uname -s)"
echo "CPU核心数: $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 'unknown')"
echo "可用内存: $(free -h 2>/dev/null | grep Mem | awk '{print $7}' || echo 'unknown')"

# 执行构建（带详细日志）
echo "🔨 开始构建 Debug APK..."
time ./gradlew assembleAppDebug \
  --no-daemon \
  --info \
  --stacktrace \
  -Dorg.gradle.parallel=true \
  -Dorg.gradle.daemon=false \
  -Dorg.gradle.caching=true \
  -Dorg.gradle.configureondemand=true

echo "✅ 构建完成！"
echo "📁 APK位置: app/build/outputs/apk/app/debug/"
ls -lh app/build/outputs/apk/app/debug/*.apk 2>/dev/null || echo "未找到APK文件"

# 显示构建统计
echo "📊 构建统计:"
if [ -f "app/build/reports/profile/profile*.html" ]; then
  echo "构建报告已生成: app/build/reports/profile/"
fi

echo "🎉 所有步骤完成！"