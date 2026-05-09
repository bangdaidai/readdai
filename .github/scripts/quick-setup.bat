@echo off
REM GitHub Actions 快速设置脚本 (Windows版本)
REM 帮助你在几分钟内部署云端构建

echo =========================================
echo   GitHub Actions 云端构建快速设置
echo =========================================
echo.

REM 检查是否在 Git 仓库中
git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: 当前目录不是 Git 仓库
    echo 请先初始化 Git: git init
    pause
    exit /b 1
)

echo ✅ Git 仓库检查通过
echo.

REM 检查工作流文件
echo 📋 检查 GitHub Actions 工作流文件...
if not exist ".github\workflows" (
    echo ❌ 错误: 未找到 .github\workflows 目录
    pause
    exit /b 1
)

echo ✅ 工作流目录存在
echo.

REM 列出可用的工作流
echo 📝 可用的工作流:
dir /b .github\workflows\*.yml
echo.

REM 签名配置提示
echo 🔐 签名密钥配置检查:
echo.
echo 要进行 Release 构建，你需要在 GitHub Secrets 中配置以下变量:
echo.
echo   1. ANDROID_KEYSTORE_BASE64  (签名密钥的 base64 编码^)
echo   2. RELEASE_STORE_PASSWORD   (密钥库密码^)
echo   3. RELEASE_KEY_ALIAS        (密钥别名^)
echo   4. RELEASE_KEY_PASSWORD     (密钥密码^)
echo.
echo 📖 详细配置指南请查看: .github\SIGNING_SETUP.md
echo.

set /p CONFIGURED="是否已配置签名密钥？(y/n/skip): "
if /i "%CONFIGURED%"=="y" (
    echo ✅ 签名密钥已配置
) else (
    echo ℹ️  你可以稍后配置，现在将只进行 Debug 构建测试
)
echo.

REM 提交并推送
echo 🚀 准备推送到 GitHub...
echo.

REM 检查是否有未提交的更改
git diff-index --quiet HEAD --
if errorlevel 1 (
    echo 📝 检测到未提交的更改
    set /p COMMIT="是否提交并推送？(y/n): "
    if /i "%COMMIT%"=="y" (
        git add .
        git commit -m "chore: add GitHub Actions CI/CD configuration"
        echo ✅ 更改已提交
    )
) else (
    echo ✅ 没有未提交的更改
)
echo.

REM 推送
echo 📤 推送到 GitHub...
git push origin HEAD
if errorlevel 1 (
    echo ❌ 推送失败，请检查网络连接和远程仓库配置
    pause
    exit /b 1
)
echo ✅ 代码已推送
echo.

REM 提供下一步指引
echo =========================================
echo   🎉 设置完成！
echo =========================================
echo.
echo 📱 下一步操作:
echo.
echo 1️⃣  访问你的 GitHub 仓库
echo    → 点击 'Actions' 标签页
echo    → 查看工作流运行状态
echo.
echo 2️⃣  首次构建（推荐）
echo    → 选择 'Android CI/CD Pipeline'
echo    → 点击 'Run workflow'
echo    → 选择 build_type: debug
echo    → 等待构建完成
echo.
echo 3️⃣  配置签名密钥（Release 构建必需）
echo    → Settings → Secrets and variables → Actions
echo    → 添加 ANDROID_KEYSTORE_BASE64 等 Secrets
echo    → 查看详细指南: .github\SIGNING_SETUP.md
echo.
echo 4️⃣  发布正式版本
echo    → 选择 'Auto Version ^& Release'
echo    → 选择版本类型 (patch/minor/major^)
echo    → 点击 'Run workflow'
echo.
echo 📖 完整文档: .github\CLOUD_BUILD_GUIDE.md
echo.
echo 💡 提示: 首次构建可能需要 5-10 分钟（下载依赖）
echo.
pause