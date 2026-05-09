# GitHub 云端构建完全指南

## 📚 目录

1. [快速开始](#快速开始)
2. [工作流说明](#工作流说明)
3. [配置步骤](#配置步骤)
4. [使用方法](#使用方法)
5. [故障排除](#故障排除)

---

## 🚀 快速开始

### 前置要求

- ✅ GitHub 账号
- ✅ Android 项目（已完成）
- ✅ JDK 17 环境
- ✅ Gradle 构建工具

### 一键启动

1. **推送代码到 GitHub**
   ```bash
   git add .
   git commit -m "Add GitHub Actions CI/CD"
   git push origin main
   ```

2. **进入 GitHub Actions**
   - 访问你的仓库
   - 点击 **Actions** 标签页
   - 你会看到新的工作流自动运行

---

## 📋 工作流说明

本项目包含以下 GitHub Actions 工作流：

### 1. **Android CI/CD Pipeline** (`android-ci-cd.yml`)

**触发条件：**
- 推送到 `main` 或 `master` 分支
- Pull Request
- 手动触发（可选择构建类型）

**功能：**
- ✅ 代码检查（Lint）
- ✅ 单元测试
- ✅ Debug APK 构建
- ✅ Release APK/AAB 构建（需要签名密钥）
- ✅ 自动上传构建产物
- ✅ 创建 GitHub Release（当有标签时）

**输出：**
- Debug APK: 保留 30 天
- Release APK: 保留 90 天
- Release AAB: 保留 90 天

### 2. **Auto Version & Release** (`auto-version-release.yml`)

**触发条件：**
- 手动触发

**功能：**
- ✅ 自动计算版本号（支持 major/minor/patch）
- ✅ 更新 build.gradle 中的版本号
- ✅ 构建签名的 APK 和 AAB
- ✅ 创建 Git 标签
- ✅ 发布到 GitHub Releases
- ✅ 发布到 Google Play（可选）

**版本类型：**
- `patch`: 1.0.0 → 1.0.1
- `minor`: 1.0.0 → 1.1.0
- `major`: 1.0.0 → 2.0.0

### 3. **现有工作流**

项目中还包含其他工作流文件，可以根据需要启用或禁用：
- `build.yml` - 基础构建
- `release.yml` - 发布构建
- `ci_build.yml` - CI 构建
- `test.yml` - 测试工作流

---

## ⚙️ 配置步骤

### 步骤 1: 配置签名密钥（Release 构建必需）

#### 1.1 生成签名密钥（如果没有）

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias my-key-alias \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

按照提示输入：
- 密钥库密码
- 姓名、组织、城市等信息
- 密钥密码

#### 1.2 转换为 Base64

**Linux/Mac:**
```bash
base64 -i release.keystore -o release.keystore.base64
```

**Windows PowerShell:**
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64
```

#### 1.3 添加到 GitHub Secrets

1. 进入仓库 **Settings** → **Secrets and variables** → **Actions**
2. 点击 **New repository secret**
3. 添加以下 Secrets：

| Secret 名称 | 值 | 示例 |
|------------|-----|------|
| `ANDROID_KEYSTORE_BASE64` | base64 编码的 keystore 文件 | (长字符串) |
| `RELEASE_STORE_PASSWORD` | 密钥库密码 | `myStorePassword123` |
| `RELEASE_KEY_ALIAS` | 密钥别名 | `my-key-alias` |
| `RELEASE_KEY_PASSWORD` | 密钥密码 | `myKeyPassword123` |

> 🔒 **安全提示**: 所有密码都会加密存储，只有 GitHub Actions 可以访问。

### 步骤 2: 配置 Google Play（可选）

如果需要发布到 Google Play：

1. 在 [Google Cloud Console](https://console.cloud.google.com/) 创建服务账号
2. 下载 JSON 密钥文件
3. 将内容添加到 GitHub Secret `SERVICE_ACCOUNT_JSON`

详细步骤参考：[Google Play Developer API](https://developers.google.com/android-publisher/getting_started)

### 步骤 3: 验证配置

1. 进入 **Actions** 标签页
2. 选择 **Android CI/CD Pipeline**
3. 点击 **Run workflow**
4. 选择构建类型（建议先测试 `debug`）
5. 等待构建完成
6. 下载并测试生成的 APK

---

## 🎯 使用方法

### 方法 1: 自动构建（推荐）

每次推送到 `main` 分支时自动触发：

```bash
git add .
git commit -m "Update feature"
git push origin main
```

GitHub Actions 会自动：
1. 运行代码检查
2. 执行单元测试
3. 构建 Debug APK
4. 上传构建产物

### 方法 2: 手动触发 Debug 构建

1. 进入 **Actions** → **Android CI/CD Pipeline**
2. 点击 **Run workflow**
3. 选择 `build_type: debug`
4. 点击 **Run workflow**
5. 等待完成后下载 APK

### 方法 3: 手动触发 Release 构建

1. 确保已配置签名密钥（见步骤 1）
2. 进入 **Actions** → **Android CI/CD Pipeline**
3. 点击 **Run workflow**
4. 选择 `build_type: release` 或 `both`
5. 点击 **Run workflow**
6. 下载签名的 APK/AAB

### 方法 4: 自动版本发布

1. 进入 **Actions** → **Auto Version & Release**
2. 点击 **Run workflow**
3. 选择版本类型：
   - `patch`: 小修复（1.0.0 → 1.0.1）
   - `minor`: 新功能（1.0.0 → 1.1.0）
   - `major`: 重大更新（1.0.0 → 2.0.0）
4. 选择是否发布到 GitHub Releases
5. 选择是否发布到 Google Play
6. 点击 **Run workflow**

系统会自动：
- 更新版本号
- 构建应用
- 创建 Git 标签
- 发布到 GitHub Releases
- （可选）发布到 Google Play

### 方法 5: 创建正式 Release

```bash
# 创建标签
git tag v1.0.0
git push origin v1.0.0
```

推送标签后，CI/CD 工作流会自动创建 GitHub Release。

---

## 📦 构建产物

### Debug APK
- **位置**: `app/build/outputs/apk/app/debug/`
- **特点**: 未签名，用于测试
- **保留时间**: 30 天

### Release APK
- **位置**: `app/build/outputs/apk/app/release/`
- **特点**: 已签名，可发布
- **保留时间**: 90 天

### Release AAB
- **位置**: `app/build/outputs/bundle/appRelease/`
- **特点**: Google Play 格式
- **保留时间**: 90 天

### 下载方式

1. 进入 **Actions** → 选择工作流运行
2. 滚动到底部的 **Artifacts** 部分
3. 点击下载链接

---

## 🛠️ 故障排除

### 问题 1: 构建失败 - "Keystore not found"

**原因**: 未配置签名密钥

**解决方案**:
1. 按照[步骤 1](#步骤-1-配置签名密钥release-构建必需)配置 Secrets
2. 或者只构建 Debug 版本（不需要签名）

### 问题 2: 构建超时

**原因**: 依赖下载慢或内存不足

**解决方案**:
1. 检查工作流中的缓存配置
2. 增加 `GRADLE_OPTS` 内存限制
3. 使用国内镜像（修改 `build.gradle`）

### 问题 3: Lint 错误导致构建失败

**原因**: 代码存在警告或错误

**解决方案**:
1. 查看 Lint 报告（在 Artifacts 中）
2. 修复代码问题
3. 或者在 `build.gradle` 中禁用 Lint 检查：
   ```gradle
   android {
       lintOptions {
           abortOnError false
       }
   }
   ```

### 问题 4: 找不到 APK 文件

**原因**: 构建路径变化或 flavor 配置问题

**解决方案**:
1. 检查工作流中的路径配置
2. 确认 `productFlavors` 配置正确
3. 查看构建日志确认输出位置

### 问题 5: 权限错误

**原因**: gradlew 没有执行权限

**解决方案**:
```bash
chmod +x gradlew
git add gradlew
git commit -m "Fix gradlew permissions"
git push
```

### 问题 6: Java 版本不匹配

**原因**: 项目需要 JDK 17，但使用了其他版本

**解决方案**:
检查工作流中的 Java 版本配置：
```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
```

---

## 📊 优化建议

### 1. 加快构建速度

- ✅ 启用 Gradle 缓存（已配置）
- ✅ 使用并行构建（已配置）
- ✅ 配置构建守护进程
- ✅ 减少不必要的依赖

### 2. 减少工作流运行时间

- 只在必要时运行完整构建
- 使用路径过滤避免无关触发
- 分离 lint/test 和 build 任务

### 3. 节省 GitHub Actions 分钟数

- 合理使用缓存
- 避免重复构建
- 设置合适的保留策略

---

## 🔗 相关资源

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Android CI/CD 最佳实践](https://developer.android.com/studio/build/optimize-your-build)
- [Gradle 性能优化](https://docs.gradle.org/current/userguide/performance.html)
- [Android 应用签名](https://developer.android.com/studio/publish/app-signing)

---

## 💬 获取帮助

如果遇到问题：

1. 查看工作流运行日志
2. 检查 [SIGNING_SETUP.md](.github/SIGNING_SETUP.md)
3. 搜索 GitHub Issues
4. 联系项目维护者

---

## 📝 更新日志

### v1.0.0 (2026-05-09)
- ✅ 创建完整的 CI/CD 工作流
- ✅ 支持 Debug 和 Release 构建
- ✅ 自动版本管理
- ✅ GitHub Releases 集成
- ✅ Google Play 发布支持（可选）
- ✅ 完善的文档和故障排除指南