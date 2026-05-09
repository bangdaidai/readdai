# 🚀 GitHub Actions 云端构建 - 快速开始

## 📖 这是什么？

这是一套完整的 GitHub Actions CI/CD 配置，用于自动构建你的 Android 应用。无需本地环境，GitHub 服务器会自动完成：

- ✅ 代码检查和测试
- ✅ 构建 Debug/Release APK
- ✅ 自动签名（需配置密钥）
- ✅ 发布到 GitHub Releases
- ✅ 发布到 Google Play（可选）
- ✅ 自动版本管理

---

## ⚡ 3 分钟快速开始

### 方法 1: 使用快速设置脚本（推荐）

**Windows 用户:**
```bash
.github\scripts\quick-setup.bat
```

**Linux/Mac 用户:**
```bash
chmod +x .github/scripts/quick-setup.sh
.github/scripts/quick-setup.sh
```

脚本会自动：
1. 检查 Git 配置
2. 验证工作流文件
3. 提示配置签名密钥
4. 提交并推送代码
5. 提供下一步指引

### 方法 2: 手动推送

```bash
git add .
git commit -m "Add GitHub Actions CI/CD"
git push origin main
```

推送后，GitHub Actions 会自动运行！

---

## 📱 查看构建状态

1. 访问你的 GitHub 仓库
2. 点击 **Actions** 标签页
3. 你会看到工作流正在运行
4. 点击可查看详细信息和日志

---

## 🔑 配置签名密钥（Release 构建必需）

### 简单步骤

1. **生成签名密钥**（如果还没有）
   ```bash
   keytool -genkeypair -v -keystore release.keystore -alias my-key -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **转换为 Base64**
   
   Windows PowerShell:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | clip
   ```
   
   Linux/Mac:
   ```bash
   base64 -i release.keystore | pbcopy  # Mac
   base64 -i release.keystore | xclip -selection clipboard  # Linux
   ```

3. **添加到 GitHub Secrets**
   - 进入 **Settings** → **Secrets and variables** → **Actions**
   - 点击 **New repository secret**
   - 添加以下 4 个 Secrets：

   | Secret 名称 | 值 |
   |------------|-----|
   | `ANDROID_KEYSTORE_BASE64` | 刚才复制的 base64 字符串 |
   | `RELEASE_STORE_PASSWORD` | 你的密钥库密码 |
   | `RELEASE_KEY_ALIAS` | 你的密钥别名（如：my-key） |
   | `RELEASE_KEY_PASSWORD` | 你的密钥密码 |

📖 详细指南：[SIGNING_SETUP.md](.github/SIGNING_SETUP.md)

---

## 🎯 使用方法

### 1️⃣ 自动构建（推送到 main 分支）

每次推送代码都会自动触发构建：

```bash
git add .
git commit -m "Update code"
git push origin main
```

### 2️⃣ 手动触发构建

1. 进入 **Actions** → **Android CI/CD Pipeline**
2. 点击 **Run workflow**
3. 选择构建类型：
   - `debug` - 仅构建 Debug APK（无需签名）
   - `release` - 仅构建 Release APK（需要签名）
   - `both` - 同时构建两者
4. 点击 **Run workflow**

### 3️⃣ 自动发布版本

1. 进入 **Actions** → **Auto Version & Release**
2. 点击 **Run workflow**
3. 选择版本类型：
   - `patch` - 小修复 (1.0.0 → 1.0.1)
   - `minor` - 新功能 (1.0.0 → 1.1.0)
   - `major` - 重大更新 (1.0.0 → 2.0.0)
4. 选择是否发布到 GitHub Releases
5. 点击 **Run workflow**

系统会自动：
- 更新版本号
- 构建应用
- 创建 Git 标签
- 发布到 GitHub Releases

### 4️⃣ 创建正式 Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

推送标签后会自动创建 GitHub Release。

---

## 📦 下载构建产物

1. 进入 **Actions** → 选择某次运行
2. 滚动到底部的 **Artifacts** 部分
3. 点击下载链接：
   - `debug-apk` - Debug 版本（未签名）
   - `release-apk` - Release 版本（已签名）
   - `release-aab` - Google Play 格式

---

## 🛠️ 常见问题

### ❓ 构建失败怎么办？

1. 点击失败的步骤查看详细日志
2. 查看 [CLOUD_BUILD_GUIDE.md](.github/CLOUD_BUILD_GUIDE.md) 的故障排除部分
3. 常见原因：
   - 未配置签名密钥（只影响 Release 构建）
   - 代码有 Lint 错误
   - 依赖下载失败
   - 内存不足

### ❓ 如何只进行 Debug 构建？

不需要配置签名密钥，直接触发构建即可。Debug APK 可以用于测试。

### ❓ 构建需要多长时间？

- 首次构建：5-10 分钟（下载依赖）
- 后续构建：2-5 分钟（使用缓存）

### ❓ 如何加快构建速度？

- ✅ Gradle 缓存已自动配置
- ✅ 并行构建已启用
- 避免频繁修改依赖
- 定期清理旧的 Artifacts

### ❓ GitHub Actions 免费额度是多少？

公共仓库：无限制
私有仓库：每月 2000 分钟（免费账户）

对于小型项目完全够用！

---

## 📚 完整文档

- 📖 [云端构建完全指南](.github/CLOUD_BUILD_GUIDE.md) - 详细使用说明
- 🔐 [签名密钥配置指南](.github/SIGNING_SETUP.md) - 安全配置签名
- 🛠️ [故障排除](.github/CLOUD_BUILD_GUIDE.md#故障排除) - 解决常见问题

---

## 🎉 开始使用

现在你已经准备好了！只需：

1. **推送代码**到 GitHub
2. **查看 Actions** 标签页
3. **等待构建完成**
4. **下载 APK** 并安装测试

就是这么简单！🚀

---

## 💡 提示

- 首次使用前建议先阅读 [完整指南](.github/CLOUD_BUILD_GUIDE.md)
- Release 构建必须配置签名密钥
- Debug 构建无需任何配置
- 可以自定义工作流以满足特定需求
- 所有配置都在 `.github/workflows/` 目录中

---

## 🆘 需要帮助？

- 查看工作流日志
- 阅读完整文档
- 搜索 GitHub Issues
- 联系项目维护者

祝你构建愉快！✨