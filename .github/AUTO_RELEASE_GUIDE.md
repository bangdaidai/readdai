# 自动 Release 配置说明

## ✅ 已完成的配置

### 1. 触发条件
每次推送到以下分支都会自动创建 Release：
- ✅ `main` 分支
- ✅ `master` 分支
- ✅ 手动触发 workflow_dispatch

### 2. 工作流程

```
推送到 main/master 分支
    ↓
Lint & Test (代码检查和测试)
    ↓
Build Release (构建签名 APK/AAB)
    ↓
Create Release (自动创建 GitHub Release) ✨
    ↓
上传 APK/AAB 到 Release
```

### 3. 版本号生成

使用基于时间的版本号格式：`3.yyMMddHHmm`
- 例如：`3.2605241530` 表示 2026年5月24日 15:30（UTC时间）
- 每次构建都会生成唯一的版本号
- 自动创建对应的 Git 标签（如 `v3.2605241530`）

---

## 📋 使用前检查清单

### ✅ 必须配置的 GitHub Secrets

进入 **Settings → Secrets and variables → Actions**，确保配置了以下 4 个 Secret：

| Secret 名称 | 值 | 说明 |
|------------|-----|------|
| `RELEASE_KEY_STORE` | (base64 字符串) | 签名密钥的 base64 编码 |
| `RELEASE_STORE_PASSWORD` | `Readdai2024` | 密钥库密码 |
| `RELEASE_KEY_ALIAS` | `readdai-key` | 密钥别名 |
| `RELEASE_KEY_PASSWORD` | `Readdai2024` | 密钥密码 |

⚠️ **重要提示：**
- 如果有旧的 `ANDROID_KEYSTORE_BASE64` Secret，请删除它
- Secret 名称必须完全一致（区分大小写）

---

## 🚀 使用方法

### 方法 1: 推送代码（推荐）

```bash
git add .
git commit -m "你的修改说明"
git push origin main
```

GitHub Actions 会自动：
1. 运行代码检查和测试
2. 构建签名的 Release APK 和 AAB
3. **自动创建 GitHub Release**
4. 上传文件到 Release 页面

### 方法 2: 手动触发

1. 进入 **Actions → Android CI/CD Pipeline**
2. 点击 **Run workflow**
3. 选择 `build_type: release`
4. 点击 **Run workflow**

---

## 🔍 验证是否成功

### 1. 检查 Actions 日志

进入 **Actions** 页面，查看最新的运行记录：

✅ **成功的标志：**
```
✅ Using RELEASE_KEY_STORE secret for consistent signing
Setting up signing keys...
✅ Keystore setup completed at ./keystore/release.keystore
📦 Building version: 3.2605241530
Release version: v3.2605241530
Creating new tag: v3.2605241530
✅ Release created successfully
```

❌ **失败的标志：**
```
⚠️ No keystore configured, using debug signing
⚠️ This will cause different signatures for each build!
⚠️ Please configure RELEASE_KEY_STORE secret in GitHub Settings
```

### 2. 检查 Releases 页面

1. 进入仓库的 **Releases** 页面
2. 应该能看到最新的 Release，标题如：`Legado 3.2605241530`
3. 包含以下文件：
   - `legado_app_3.2605241530.apk` - 可直接安装的 APK
   - `app-release.aab` - Google Play 格式的 AAB

### 3. 检查 Tags 页面

进入 **Tags** 页面，应该能看到自动创建的标签（如 `v3.2605241530`）

---

## ⚠️ 常见问题

### Q1: 为什么没有创建 Release？

**可能原因：**
1. **Secret 未配置或配置错误**
   - 检查是否有 `RELEASE_KEY_STORE` Secret
   - 检查其他 3 个密码相关的 Secret
   
2. **构建失败**
   - 查看 Actions 日志中的错误信息
   - 常见错误：签名配置错误、编译错误、Lint 错误

3. **推送到错误的分支**
   - 确保推送到 `main` 或 `master` 分支

**解决方案：**
```bash
# 1. 检查当前分支
git branch

# 2. 如果是其他分支，切换到 main
git checkout main
git push origin main

# 3. 查看 Actions 日志排查错误
```

### Q2: Release 创建了但没有文件？

**可能原因：**
- APK/AAB 文件路径不正确
- 构建产物未正确上传

**解决方案：**
检查 Actions 日志中的 "Upload Release APK" 和 "Upload Release AAB" 步骤是否有警告。

### Q3: 标签已存在错误？

工作流会自动检查远程标签是否存在：
- 如果标签已存在，会使用现有标签
- 如果标签不存在，会创建新标签

由于版本号基于时间生成，通常不会冲突。

### Q4: 如何查看构建进度？

1. 进入 **Actions** 标签页
2. 点击最新的 workflow 运行记录
3. 查看各个 job 的执行状态：
   - Lint & Test
   - Build Release APK/AAB
   - Create GitHub Release

---

## 📊 预期效果

每次推送到 main/master 分支后：

| 项目 | 结果 |
|------|------|
| 触发条件 | ✅ 自动触发 |
| 构建类型 | ✅ Release（签名） |
| 签名一致性 | ✅ 始终相同 |
| GitHub Release | ✅ 自动创建 |
| APK 文件 | ✅ 上传到 Release |
| AAB 文件 | ✅ 上传到 Release |
| Git Tag | ✅ 自动创建 |
| 版本号 | ✅ 基于时间自动生成 |

---

## 💡 提示

1. **首次配置后**，建议立即推送一次代码测试
2. **查看详细日志**是排查问题的最好方式
3. **Release 创建需要时间**，通常在构建完成后 1-2 分钟内完成
4. 如果遇到问题，检查 Actions 日志中的错误信息
5. 可以禁用 Lint 检查以加快构建速度（在 `build.gradle` 中设置 `abortOnError false`）

---

## 🔗 相关文档

- [快速修复指南](QUICK_FIX.md)
- [详细签名配置指南](SIGNING_AND_RELEASE_SETUP.md)
- [修复总结](FIX_SUMMARY.md)
