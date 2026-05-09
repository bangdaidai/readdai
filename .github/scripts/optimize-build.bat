@echo off
REM Gradle 构建优化脚本 (Windows版本)
REM 用于本地测试和调试构建性能

echo 🚀 开始优化 Gradle 构建...

REM 清理旧的构建文件
echo 🧹 清理构建目录...
call gradlew.bat clean --no-daemon

REM 预下载依赖
echo 📦 预下载依赖...
call gradlew.bat dependencies --no-daemon

REM 显示构建环境信息
echo 💻 构建环境信息:
java -version 2>&1 | findstr "version"
call gradlew.bat --version | findstr "Gradle"
echo 操作系统: %OS%
wmic cpu get NumberOfCores /value 2>nul || echo CPU核心数: unknown
systeminfo | findstr "Total Physical Memory" || echo 可用内存: unknown

REM 执行构建（带详细日志）
echo 🔨 开始构建 Debug APK...
call gradlew.bat assembleAppDebug ^
  --no-daemon ^
  --info ^
  --stacktrace ^
  -Dorg.gradle.parallel=true ^
  -Dorg.gradle.daemon=false ^
  -Dorg.gradle.caching=true ^
  -Dorg.gradle.configureondemand=true

echo ✅ 构建完成！
echo 📁 APK位置: app\build\outputs\apk\app\debug\
dir app\build\outputs\apk\app\debug\*.apk 2>nul || echo 未找到APK文件

echo 🎉 所有步骤完成！
pause