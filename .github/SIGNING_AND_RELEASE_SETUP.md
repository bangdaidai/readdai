# GitHub Actions 签名和 Release 配置指南

## 🔧 问题说明

你遇到的两个主要问题：
1. **每次构建的 APK 签名不一致** - 导致无法覆盖安装
2. **Release 文件未生成** - GitHub Releases 中没有发布文件

## ✅ 解决方案

### 第一步：准备签名密钥

#### 1.1 如果你还没有签名密钥

在本地生成一个新的 keystore：

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias my-release-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=Your Name, OU=Your Org, O=Your Company, L=Your City, S=Your State, C=CN"
```

**重要提示：**
- 请替换 `YOUR_STORE_PASSWORD` 和 `YOUR_KEY_PASSWORD` 为你的实际密码
- 记住这些密码，后续需要配置到 GitHub Secrets
- `my-release-key` 是密钥别名，也需要记住

#### 1.2 将密钥转换为 Base64

**Linux/Mac:**
```bash
base64 -i release.keystore > release.keystore.base64
```

**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64
```

**验证转换结果：**
```bash
# 检查 base64 文件内容（应该是一行很长的字符串）
head -c 100 release.keystore.base64
echo ""
wc -l release.keystore.base64  # 应该是 1 行
```

### 第二步：配置 GitHub Secrets

1. **进入你的 GitHub 仓库**
2. **点击 Settings → Secrets and variables → Actions**
3. **点击 New repository secret**
4. **添加以下 4 个 Secrets（名称必须完全一致）：**

| Secret 名称 | 值 | 示例 |
|------------|-----|------|
| `RELEASE_KEY_STORE` | release.keystore.base64 文件的完整内容 | (很长的 base64 字符串) |
| `RELEASE_STORE_PASSWORD` | 你的密钥库密码 | your_store_password |
| `RELEASE_KEY_ALIAS` | 你的密钥别名 | my-release-key |
| `RELEASE_KEY_PASSWORD` | 你的密钥密码 | your_key_password |

**⚠️ 重要注意事项：**
- ✅ Secret 名称必须是 `RELEASE_KEY_STORE`（不是 `ANDROID_KEYSTORE_BASE64`）
- ✅ `RELEASE_KEY_STORE` 的值必须是 **base64 编码的内容**，不是文件名
- ✅ 确保 base64 内容是完整的，没有换行符或空格
- ✅ 所有密码区分大小写

### 第三步：验证配置

#### 3.1 手动触发构建测试

1. 进入 **Actions** 标签页
2. 选择 **Android CI/CD Pipeline**
3. 点击 **Run workflow**
4. 选择 `build_type: release`
5. 点击 **Run workflow**

#### 3.2 检查构建日志

在构建日志中应该看到：
```
✅ Using RELEASE_KEY_STORE secret for consistent signing
Setting up signing keys...
✅ Keystore loaded successfully at ./keystore/release.keystore
-rw-r--r-- 1 runner docker 2345 Jun 15 12:00 /home/runner/work/readdai/readdai/keystore/release.keystore
```

**如果看到警告信息：**
```
⚠️ No signing keys configured, build will use debug signature
⚠️ This will cause different signatures for each build!
```
说明 Secret 配置有问题，请检查：
- Secret 名称是否正确
- base64 内容是否完整
- 是否有额外的空格或换行符

#### 3.3 验证签名一致性

构建完成后，下载两个不同时间构建的 APK，验证签名是否一致：

```bash
# 解压 APK 查看签名信息
unzip -p app-release.apk META-INF/*.RSA | keytool -printcert

# 或者使用 apksigner 验证
apksigner verify --verbose app-release.apk
```

两次构建的签名证书指纹应该完全相同。

### 第四步：创建 Release

#### 4.1 自动创建 Release（推荐）

工作流配置为在以下情况自动创建 GitHub Release：
- 推送 Git 标签时（如 `git tag v3.2405241200 && git push origin v3.2405241200`）
- 手动触发 workflow_dispatch 时

#### 4.2 手动创建 Release

如果你想立即创建一个 Release：

```bash
# 1. 获取当前版本号
cat version.txt  # 或在 Actions 日志中查看

# 2. 创建并推送标签
git tag v3.2405241200
git push origin v3.2405241200
```

推送标签后，GitHub Actions 会自动：
1. 触发构建
2. 创建签名的 APK/AAB
3. 上传到 GitHub Releases
4. 创建 Release 页面

#### 4.3 检查 Release

1. 进入仓库的 **Releases** 页面
2. 应该能看到最新的 Release
3. 包含 APK 和 AAB 文件供下载

## 🛠️ 故障排除

### 问题 1：签名仍然不一致

**可能原因：**
1. Secret 名称错误（使用了 `ANDROID_KEYSTORE_BASE64` 而不是 `RELEASE_KEY_STORE`）
2. base64 内容不完整或有换行符
3. 多个 Secret 冲突

**解决方案：**
```bash
# 1. 删除旧的 Secret
# 在 GitHub Settings → Secrets 中删除 ANDROID_KEYSTORE_BASE64（如果存在）

# 2. 重新生成 base64（确保无换行）
base64 -w 0 -i release.keystore > release.keystore.base64

# 3. 复制内容到 RELEASE_KEY_STORE Secret
cat release.keystore.base64 | pbcopy  # Mac
cat release.keystore.base64 | clip   # Windows
```

### 问题 2：Release 未生成

**可能原因：**
1. 没有推送 Git 标签
2. `create-release` job 依赖失败
3. 权限问题

**解决方案：**
```bash
# 方法 1：手动推送标签
git tag v$(date -u +3.%y%m%d%H%M)
git push origin --tags

# 方法 2：手动触发 workflow
# 在 Actions → Android CI/CD Pipeline → Run workflow

# 方法 3：检查 workflow 日志
# 查看 create-release job 是否有错误
```

### 问题 3：构建失败 - Keystore 解码错误

**错误信息：**
```
base64: invalid input
```

**解决方案：**
1. 确保 base64 内容没有换行符
2. 重新生成 base64：
   ```bash
   base64 -w 0 -i release.keystore
   ```
3. 复制完整内容到 Secret

### 问题 4：APK 文件找不到

**检查路径：**
```bash
# 在构建日志中查找 APK 输出路径
find app/build/outputs -name "*.apk" -type f
```

**常见路径：**
- Debug: `app/build/outputs/apk/app/debug/`
- Release: `app/build/outputs/apk/app/release/`

## 📋 快速检查清单

在提交代码前，确认：

- [ ] 已生成 keystore 文件
- [ ] 已将 keystore 转换为 base64（无换行）
- [ ] 已在 GitHub Secrets 中配置 `RELEASE_KEY_STORE`
- [ ] 已在 GitHub Secrets 中配置 `RELEASE_STORE_PASSWORD`
- [ ] 已在 GitHub Secrets 中配置 `RELEASE_KEY_ALIAS`
- [ ] 已在 GitHub Secrets 中配置 `RELEASE_KEY_PASSWORD`
- [ ] 已删除旧的 `ANDROID_KEYSTORE_BASE64` Secret（如果存在）
- [ ] 已测试手动触发构建
- [ ] 已验证签名一致性
- [ ] 已成功创建 Release

## 🔒 安全建议

1. **永远不要**将 keystore 文件提交到 Git
2. **永远不要**在代码中硬编码密码
3. 定期轮换签名密钥（建议每年一次）
4. 限制有权限访问 Secrets 的人员
5. 备份 keystore 文件到安全位置（如密码管理器）

## 📞 需要帮助？

如果仍有问题：
1. 查看 GitHub Actions 构建日志
2. 检查 Secret 配置截图
3. 提供具体的错误信息

## ✨ 优化后的改进

本次修复包括：

1. ✅ **统一 Secret 名称**：只使用 `RELEASE_KEY_STORE`，避免混淆
2. ✅ **固定密钥路径**：使用 `./keystore/release.keystore` 确保一致性
3. ✅ **增强错误提示**：明确指示如何配置 Secret
4. ✅ **改进版本管理**：基于日期的版本号更清晰
5. ✅ **完善 Release 创建**：自动创建标签和 Release
6. ✅ **详细构建信息**：Release 描述包含更多有用信息

现在你的 GitHub Actions 应该能够：
- ✅ 每次构建使用相同的签名
- ✅ 支持覆盖安装旧版本
- ✅ 自动创建 GitHub Releases
- ✅ 提供清晰的构建和发布信息
