# GitHub Release 自动创建配置说明

## 🎯 问题原因

之前 Release 没有自动创建的原因是工作流中有条件限制：

```yaml
if: startsWith(github.ref, 'refs/tags/') || github.event_name == 'workflow_dispatch'
```

这意味着**只有在推送标签或手动触发时**才会创建 Release，普通的代码推送不会创建 Release。

## ✅ 已修复

### 修改内容

1. **移除触发条件限制**
   ```yaml
   # 修改前
   if: startsWith(github.ref, 'refs/tags/') || github.event_name == 'workflow_dispatch'
   
   # 修改后
   if: always() && needs.build-release.result == 'success'
   ```
   现在只要构建成功，就会自动创建 Release。

2. **智能标签管理**
   - 自动检查标签是否已存在
   - 如果存在则跳过创建，避免冲突
   - 如果不存在则创建新标签

3. **增强 Release 信息**
   - 添加分支信息
   - 更详细的构建说明

## 🚀 如何触发 Release 创建

### 方法 1: 推送代码到 master 分支（推荐）

```bash
git add .
git commit -m "Your changes"
git push origin master
```

GitHub Actions 会自动：
1. 触发构建
2. 创建签名的 APK/AAB
3. **自动创建 GitHub Release**
4. 上传文件到 Release

### 方法 2: 手动触发 workflow

1. 进入 **Actions → Android CI/CD Pipeline**
2. 点击 **Run workflow**
3. 选择 `build_type: release`
4. 点击 **Run workflow**

### 方法 3: 推送 Git 标签

```bash
git tag v3.2405241200
git push origin v3.2405241200
```

## 📋 Release 创建流程

```
推送代码/手动触发
    ↓
Lint & Test (代码检查和测试)
    ↓
Build Release (构建签名 APK/AAB)
    ↓
Create Release (创建 GitHub Release) ✨
    ↓
上传 APK/AAB 到 Release
```

## 🔍 验证 Release 是否创建成功

### 1. 检查 Actions 日志

进入 **Actions → Android CI/CD Pipeline**，查看最新的运行记录：

✅ **成功的标志：**
```
✅ Keystore loaded successfully at ./keystore/release.keystore
Release version: v3.2405241200
Creating new tag: v3.2405241200
Release created successfully
```

❌ **失败的标志：**
```
⚠️ No signing keys configured, build will use debug signature
Build failed
```

### 2. 检查 Releases 页面

进入仓库的 **Releases** 页面，应该能看到：
- 最新的 Release
- 包含 APK 和 AAB 文件
- 版本号如 `v3.2405241200`

### 3. 检查 Tags

进入仓库的 **Tags** 页面，应该能看到自动创建的标签。

## ⚠️ 常见问题

### Q1: 为什么还是没有 Release？

**可能原因：**
1. **Secret 未配置或配置错误**
   - 检查是否有 `RELEASE_KEY_STORE` Secret
   - 检查其他 3 个密码相关的 Secret
   
2. **构建失败**
   - 查看 Actions 日志中的错误信息
   - 常见错误：签名配置错误、编译错误

3. **workflow 未触发**
   - 确认推送到的是 `master` 分支
   - 或者手动触发 workflow

**解决方案：**
```bash
# 1. 检查 Secret 配置
# 进入 Settings → Secrets and variables → Actions
# 确认有这 4 个 Secret：
# - RELEASE_KEY_STORE
# - RELEASE_STORE_PASSWORD
# - RELEASE_KEY_ALIAS
# - RELEASE_KEY_PASSWORD

# 2. 手动触发测试
# 进入 Actions → Android CI/CD Pipeline → Run workflow

# 3. 查看日志
# 检查 "Setup Android Signing" 步骤的输出
```

### Q2: Release 创建了但没有文件？

**可能原因：**
- APK/AAB 文件路径不正确
- 构建产物未正确上传

**解决方案：**
检查 Actions 日志中的 "Upload Release APK" 和 "Upload Release AAB" 步骤是否有警告。

### Q3: 标签已存在错误？

现在工作流会自动检查标签是否存在，如果存在会跳过创建，不会报错。

## 📊 预期效果

每次推送到 master 分支后：

| 项目 | 结果 |
|------|------|
| 构建类型 | Release（签名） |
| 签名一致性 | ✅ 相同签名 |
| GitHub Release | ✅ 自动创建 |
| APK 文件 | ✅ 上传到 Release |
| AAB 文件 | ✅ 上传到 Release |
| Git Tag | ✅ 自动创建（如不存在） |

## 🔗 相关文档

- [快速修复指南](QUICK_FIX.md)
- [详细配置指南](SIGNING_AND_RELEASE_SETUP.md)
- [修复总结](FIX_SUMMARY.md)

## 💡 提示

- **首次配置后**，建议手动触发一次 workflow 进行测试
- **查看日志**是排查问题的最好方式
- **Release 创建需要时间**，通常在构建完成后 1-2 分钟内完成
- 如果遇到问题，检查 Actions 日志中的错误信息
