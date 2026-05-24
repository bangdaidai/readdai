# GitHub Actions 签名密钥配置助手
# 此脚本帮助你准备 keystore 的 base64 编码

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "GitHub Actions 签名密钥配置助手" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 keystore 文件是否存在
$keystorePath = Read-Host "请输入 keystore 文件路径（默认: release.keystore）"
if ([string]::IsNullOrWhiteSpace($keystorePath)) {
    $keystorePath = "release.keystore"
}

if (-not (Test-Path $keystorePath)) {
    Write-Host "❌ 错误: 找不到文件 $keystorePath" -ForegroundColor Red
    Write-Host ""
    Write-Host "提示: 如果还没有 keystore，请先运行以下命令生成：" -ForegroundColor Yellow
    Write-Host "keytool -genkeypair -v -keystore release.keystore -alias my-release-key -keyalg RSA -keysize 2048 -validity 10000" -ForegroundColor Gray
    exit 1
}

Write-Host "✅ 找到 keystore 文件: $keystorePath" -ForegroundColor Green
Write-Host ""

# 读取文件并转换为 base64
try {
    $bytes = [IO.File]::ReadAllBytes($keystorePath)
    $base64 = [Convert]::ToBase64String($bytes)
    
    # 输出到文件
    $outputFile = "release.keystore.base64"
    $base64 | Out-File -Encoding ASCII -NoNewline $outputFile
    
    Write-Host "✅ Base64 编码完成" -ForegroundColor Green
    Write-Host "   输出文件: $outputFile" -ForegroundColor Gray
    Write-Host "   文件大小: $([math]::Round($base64.Length / 1KB, 2)) KB" -ForegroundColor Gray
    Write-Host ""
    
    # 显示前 100 个字符用于验证
    Write-Host "Base64 内容预览（前 100 字符）:" -ForegroundColor Cyan
    Write-Host $($base64.Substring(0, [Math]::Min(100, $base64.Length))) -ForegroundColor Gray
    Write-Host ""
    
    # 复制到剪贴板
    try {
        $base64 | Set-Clipboard
        Write-Host "✅ Base64 内容已复制到剪贴板" -ForegroundColor Green
        Write-Host ""
    } catch {
        Write-Host "⚠️ 无法复制到剪贴板，请手动复制文件内容" -ForegroundColor Yellow
        Write-Host ""
    }
    
    # 显示配置说明
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "下一步：配置 GitHub Secrets" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "1. 进入 GitHub 仓库 → Settings → Secrets and variables → Actions" -ForegroundColor White
    Write-Host "2. 点击 'New repository secret'" -ForegroundColor White
    Write-Host "3. 添加以下 4 个 Secrets：" -ForegroundColor White
    Write-Host ""
    Write-Host "   Secret 名称                    值" -ForegroundColor Yellow
    Write-Host "   ─────────────────────────────────────────────────────" -ForegroundColor Gray
    Write-Host "   RELEASE_KEY_STORE              $(Get-Content $outputFile)" -ForegroundColor Gray
    Write-Host "   RELEASE_STORE_PASSWORD         你的密钥库密码" -ForegroundColor Gray
    Write-Host "   RELEASE_KEY_ALIAS              你的密钥别名（如: my-release-key）" -ForegroundColor Gray
    Write-Host "   RELEASE_KEY_PASSWORD           你的密钥密码" -ForegroundColor Gray
    Write-Host ""
    Write-Host "⚠️  重要提示：" -ForegroundColor Yellow
    Write-Host "   - RELEASE_KEY_STORE 的值必须是完整的 base64 字符串" -ForegroundColor Gray
    Write-Host "   - 可以从 $outputFile 文件中复制全部内容" -ForegroundColor Gray
    Write-Host "   - 确保没有额外的空格或换行符" -ForegroundColor Gray
    Write-Host ""
    
    # 询问是否打开文件
    $openFile = Read-Host "是否打开 base64 文件？(y/n)"
    if ($openFile -eq "y" -or $openFile -eq "Y") {
        Invoke-Item $outputFile
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "配置完成后，可以测试构建：" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "1. 进入 Actions → Android CI/CD Pipeline" -ForegroundColor White
    Write-Host "2. 点击 'Run workflow'" -ForegroundColor White
    Write-Host "3. 选择 build_type: release" -ForegroundColor White
    Write-Host "4. 等待构建完成并检查日志" -ForegroundColor White
    Write-Host ""
    Write-Host "成功标志：看到 '✅ Keystore loaded successfully' 消息" -ForegroundColor Green
    Write-Host ""
    
} catch {
    Write-Host "❌ 错误: $_" -ForegroundColor Red
    exit 1
}
