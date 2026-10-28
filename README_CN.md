# AI-RP Android

简体中文 | [English](README.md)

AI-RP Android 是 [AI-RP](https://rp.dys.asia/) 的原生 Android WebView 客户端。WebView 默认打开 `https://rp.dys.asia/`，并通过一个 JavaScript 桥接补上网页在 Android 上做不到的两件事：持久化的加密存储和异步文件选择。

## 存储

加密全部留在 Web 层完成。Vault 密钥在浏览器内用 PBKDF2 和 AES-GCM 加密，Android 只接触加密后的结果：

- 加密字符串写入应用私有目录的 `files/ai-rp/data` 与 `files/ai-rp/local`。
- 会话中的 JPEG/PNG data URL 随加密会话一起保存，重启 WebView 后图片仍可恢复。
- 密码、派生密钥和 API key 不会跨越桥接进入原生层。

## 文件选择

页面加载完成后，Android 注入一个异步接口：

```js
const file = await window.AndroidBridge.pickFile("application/json,image/png");
// { name, type, data }，data 为 Base64（不含 data: 前缀）
```

实现使用 `ACTION_OPEN_DOCUMENT`，支持 MIME 类型和常见扩展名，单文件上限 32 MiB。用户取消或读取失败时 Promise 会 reject，网页端因此不需要自己处理 Activity Result。

## 构建

需要 JDK 17 或更新版本和 Android SDK（API 35）。可以直接用 Android Studio 打开本目录构建，也可以用 Gradle wrapper：

```bash
./gradlew assembleDebug       # Linux、macOS
gradlew.bat assembleDebug     # Windows
```

Debug APK 输出到 `app/build/outputs/apk/debug/`。项目 targetSdk 35、minSdk 26，使用 AGP 8.6.1 构建。

## 发布签名构建

签名密钥不在仓库里。构建 release APK 时，把构建指向现有的 keystore：

```bat
set AI_RP_KEYSTORE=E:\path\to\release.jks
set AI_RP_STORE_PASSWORD=...
set AI_RP_KEY_ALIAS=...
set AI_RP_KEY_PASSWORD=...
gradlew.bat assembleRelease
```

同样的值也可以用 Gradle 属性传入：`-PandroidKeystore`、`-PandroidStorePassword`、`-PandroidKeyAlias` 和 `-PandroidKeyPassword`。不要把密码或 keystore 文件提交进仓库；`.gitignore` 已排除 `*.jks`、`*.keystore` 和常见签名配置文件。

没有配置 keystore 时，`assembleDebug` 等非 release 构建不需要任何签名配置；`assembleRelease` 会产出未签名的 APK。

## 持续集成

[.github/workflows/android.yml](.github/workflows/android.yml) 在每次 push 到 `master` 或 `main`、提交 Pull Request 以及手动触发时运行：

- 始终构建 Debug APK，并作为 `app-debug` artifact 上传。
- 配置了签名 secrets 后，还会构建签名 Release APK，并作为 `app-release` artifact 上传。

在仓库的 Settings → Secrets and variables → Actions 中添加以下 secrets：

| Secret | 内容 |
| --- | --- |
| `AI_RP_KEYSTORE_BASE64` | keystore 文件的 Base64 编码 |
| `AI_RP_STORE_PASSWORD` | keystore 密码 |
| `AI_RP_KEY_ALIAS` | 密钥别名 |
| `AI_RP_KEY_PASSWORD` | 密钥密码 |

keystore 用 `base64 -w0 release.jks` 编码，也可以用 GitHub CLI 设置 secrets：

```bash
gh secret set AI_RP_KEYSTORE_BASE64 < keystore.b64
gh secret set AI_RP_STORE_PASSWORD
gh secret set AI_RP_KEY_ALIAS
gh secret set AI_RP_KEY_PASSWORD
```
