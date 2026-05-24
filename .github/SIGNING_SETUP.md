# GitHub Actions 签名密钥配置指南

## 🔐 安全说明

**永远不要将签名密钥直接提交到代码仓库！** 使用 GitHub Secrets 安全存储敏感信息。

## 📋 配置步骤

### 1. 准备签名密钥文件

如果你还没有签名密钥，可以生成一个新的：

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias my-release-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=Your Name, OU=Your Org, O=Your Company, L=Your City, S=Your State, C=Your Country"
```

### 2. 将密钥转换为 Base64

在本地执行以下命令（Linux/Mac）：

```bash
base64 -i release.keystore -o release.keystore.base64
```

或者在 Windows PowerShell 中：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64
```

### 3. 在 GitHub 中配置 Secrets

进入你的 GitHub 仓库：
1. 点击 **Settings** → **Secrets and variables** → **Actions**
2. 点击 **New repository secret**
3. 添加以下 Secrets（**注意：secret名称必须完全匹配**）：

| Secret 名称 | 值 | 说明 |
|------------|-----|------|
| `RELEASE_KEY_STORE` | 第2步生成的 base64 内容 | 签名密钥文件的 base64 编码 |
| `RELEASE_STORE_PASSWORD` | 你的密钥库密码 | keystore 的密码 |
| `RELEASE_KEY_ALIAS` | 你的密钥别名 | 例如：my-release-key |
| `RELEASE_KEY_PASSWORD` | 你的密钥密码 | key 的密码 |

⚠️ **重要提示**：
- 确保 secret 名称为 `RELEASE_KEY_STORE`（不是 `ANDROID_KEYSTORE_BASE64`）
- secret 值必须是 keystore 文件的 **Base64 编码**，而不是文件名或路径
- 如果 secret 配置错误或不匹配，每次构建会使用不同的签名，导致无法覆盖安装

### 4. 验证签名配置

配置完 secrets 后，手动触发 workflow 测试：

1. 进入 **Actions** 标签页
2. 选择 **Android CI/CD Pipeline**
3. 点击 **Run workflow**
4. 等待构建完成
5. 检查构建日志中是否显示 "✅ Keystore loaded successfully"
6. 下载生成的 APK，验证签名是否一致

如果签名配置正确，每次构建的 APK 都会有相同的签名，可以直接覆盖安装旧版本。

## 🔒 安全最佳实践

### ✅ 推荐做法

1. **使用强密码**：至少 12 位，包含大小写字母、数字和特殊字符
2. **定期轮换密钥**：建议每年更新一次签名密钥
3. **限制访问权限**：只给必要的团队成员访问 Secrets 的权限
4. **备份密钥**：在安全的地方备份原始 keystore 文件
5. **使用双因素认证**：为 GitHub 账户启用 2FA

### ❌ 避免的做法

1. **不要**在代码中硬编码密码
2. **不要**将 keystore 文件提交到 Git
3. **不要**在日志中打印密钥信息
4. **不要**共享密钥文件给不可信的人
5. **不要**使用弱密码或默认密码

## 🛠️ 故障排除

### 问题：每次构建都是不同的签名，无法覆盖安装

**原因分析：**
每次构建签名不同通常是因为：
1. ❌ 使用的 secret 名称不一致（如 `ANDROID_KEYSTORE_BASE64` vs `RELEASE_KEY_STORE`）
2. ❌ secret 的值不是 Base64 编码，而是文件名或其他内容
3. ❌ secret 配置了多个不同的值
4. ❌ 没有配置 secret，构建使用了 debug 签名

**解决方案：**
1. 检查 GitHub Secrets 配置，确保使用正确的 secret 名称：
   - `RELEASE_KEY_STORE`（不是 `ANDROID_KEYSTORE_BASE64`）
2. 验证 Base64 编码是否正确：
   ```bash
   # 在本地验证
   base64 -i release.keystore > temp.base64
   # 检查是否有多行或特殊字符
   head -c 100 temp.base64
   ```
3. 确保 GitHub Secrets 中只有一个 `RELEASE_KEY_STORE` 的值
4. 检查 workflow 日志，确保看到 "✅ Keystore loaded successfully"

### 问题：构建失败，提示签名错误

**解决方案：**
1. 检查所有 Secrets 是否正确配置
2. 确认 base64 编码是否完整（没有换行符）
3. 验证密码是否正确
4. 检查工作流日志中的详细错误信息

### 问题：找不到 keystore 文件

**解决方案：**
确保在工作流中正确设置了环境变量：
```yaml
echo "RELEASE_STORE_FILE=./release.keystore" >> gradle.properties
```

### 问题：权限被拒绝

**解决方案：**
确认你有仓库的写入权限，并且 Secrets 已正确配置。

## 📝 本地测试签名

在上传到 GitHub 之前，可以在本地测试签名配置：

```bash
# 在 gradle.properties 中添加（仅本地测试）
RELEASE_STORE_FILE=./release.keystore
RELEASE_STORE_PASSWORD=your_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_key_password

# 构建签名的 APK
./gradlew assembleAppRelease
```

## 🔄 更新密钥

如果需要更换签名密钥：

1. 生成新的 keystore
2. 转换为 base64
3. 更新 GitHub Secrets 中的 `ANDROID_KEYSTORE_BASE64`
4. 更新相关密码和别名
5. 重新构建并测试

## 📞 需要帮助？

如果遇到问题，请查看：
- GitHub Actions 日志
- [GitHub Secrets 文档](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Android 应用签名文档](https://developer.android.com/studio/publish/app-signing)