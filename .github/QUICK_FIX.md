# GitHub Actions 快速配置指南

## 🚀 5 分钟快速解决签名和 Release 问题

### 问题症状
- ❌ 每次构建的 APK 签名不同，无法覆盖安装
- ❌ GitHub Releases 中没有发布文件

### 解决方案（3 步）

#### 步骤 1: 准备签名密钥（如果没有）

**Windows 用户：**
```powershell
# 运行配置助手脚本
.\.github\setup_signing.ps1
```

**或者手动生成：**
```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias my-release-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

然后转换为 base64：
```bash
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64

# Linux/Mac
base64 -w 0 -i release.keystore > release.keystore.base64
```

#### 步骤 2: 配置 GitHub Secrets

1. 进入 **GitHub 仓库 → Settings → Secrets and variables → Actions**
2. 点击 **New repository secret**
3. 添加以下 4 个 Secrets：

| Secret 名称 | 值 | 说明 |
|------------|-----|------|
| `RELEASE_KEY_STORE` | release.keystore.base64 的完整内容 | ⚠️ 必须是 base64 字符串 |
| `RELEASE_STORE_PASSWORD` | 你的密钥库密码 | 生成 keystore 时设置的密码 |
| `RELEASE_KEY_ALIAS` | my-release-key | 生成 keystore 时设置的别名 |
| `RELEASE_KEY_PASSWORD` | 你的密钥密码 | 生成 keystore 时设置的密钥密码 |

**⚠️ 重要：**
- ✅ Secret 名称必须是 `RELEASE_KEY_STORE`（不是 `ANDROID_KEYSTORE_BASE64`）
- ✅ 如果有旧的 `ANDROID_KEYSTORE_BASE64`，请删除它
- ✅ `RELEASE_KEY_STORE` 的值是 base64 编码的内容，不是文件名

#### 步骤 3: 测试构建

1. 进入 **Actions** 标签页
2. 选择 **Android CI/CD Pipeline**
3. 点击 **Run workflow**
4. 选择 `build_type: release`
5. 点击 **Run workflow**
6. 等待构建完成（约 5-10 分钟）

**✅ 成功标志：**
在构建日志中看到：
```
✅ Using RELEASE_KEY_STORE secret for consistent signing
Setting up signing keys...
✅ Keystore loaded successfully at ./keystore/release.keystore
```

**❌ 失败标志：**
如果看到：
```
⚠️ No signing keys configured, build will use debug signature
⚠️ This will cause different signatures for each build!
```
说明 Secret 配置有误，请检查步骤 2。

### 创建 Release

#### 方法 1: 自动创建（推荐）

推送 Git 标签后自动创建 Release：
```bash
git tag v$(date -u +3.%y%m%d%H%M)
git push origin --tags
```

#### 方法 2: 手动触发

1. 进入 **Actions → Android CI/CD Pipeline**
2. 点击 **Run workflow**
3. 选择 `build_type: release`
4. 构建完成后会自动创建 Release

### 验证结果

1. **检查签名一致性：**
   - 下载两次不同时间构建的 APK
   - 使用 `apksigner verify --verbose app.apk` 验证
   - 签名证书应该相同

2. **检查 Release：**
   - 进入仓库的 **Releases** 页面
   - 应该能看到最新的 Release
   - 包含 APK 和 AAB 文件

## 📋 常见问题

### Q1: 为什么每次签名都不同？
**A:** 因为没有配置 `RELEASE_KEY_STORE` Secret，导致每次使用临时的 debug 签名。

### Q2: Release 为什么没有生成？
**A:** 可能原因：
- 没有推送 Git 标签
- 构建失败
- Secret 配置错误

### Q3: 如何验证签名是否正确？
**A:** 
```bash
# 查看 APK 签名信息
unzip -p app.apk META-INF/*.RSA | keytool -printcert

# 或使用 apksigner
apksigner verify --verbose app.apk
```

### Q4: 可以继续使用 ANDROID_KEYSTORE_BASE64 吗？
**A:** 不建议。已统一使用 `RELEASE_KEY_STORE`，请迁移到这个名称。

## 🔗 相关文档

- [详细配置指南](SIGNING_AND_RELEASE_SETUP.md)
- [签名设置说明](SIGNING_SETUP.md)
- [云端构建指南](CLOUD_BUILD_GUIDE.md)

## 💡 提示

- 配置助手脚本：`.github/setup_signing.ps1`
- 查看详细日志：Actions → 选择工作流运行 → 查看日志
- 遇到问题：检查构建日志中的错误信息
