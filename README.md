# AI-RP Android

独立的 Android 原生 WebView 客户端，默认打开 `https://rp.dys.asia/`。

## 持久化设计

- Web 层继续负责 Vault 的 PBKDF2 + AES-GCM 加密。
- Android 只保存已经加密的字符串到应用私有目录：`files/ai-rp/data` 与 `files/ai-rp/local`。
- 会话中的 JPEG/PNG data URL 会随加密会话一起持久化，因此重启 WebView 后图片仍可恢复。
- 不把密码、派生密钥或 API key 传入原生桥接层。

## 文件选择桥接

页面加载完成后，Android 注入统一的异步接口：

```js
const file = await window.AndroidBridge.pickFile("application/json,image/png");
// { name, type, data }，data 为 Base64（不含 data: 前缀）
```

实现使用 Android `ACTION_OPEN_DOCUMENT` 文件选择器，支持 MIME 类型和常见扩展名，单文件限制为 32 MiB。取消选择或读取失败时 Promise 会 reject。网页端不需要为每个页面单独实现 Activity Result。

## 构建

需要 Android Studio（推荐）或 Gradle 8.7+，并安装 Android SDK 35。

```bash
gradlew.bat assembleDebug
```

也可以直接用 Android Studio 打开本目录并执行 **Build > Make Project**。

## 使用发布签名构建

签名文件不放入仓库。使用现有签名文件构建 release APK 时，在命令行提供凭据：

```bat
set AI_RP_KEYSTORE=E:\pj2\de_ba\work\daiyosei.jks
set AI_RP_STORE_PASSWORD=...
set AI_RP_KEY_ALIAS=...
set AI_RP_KEY_PASSWORD=...
gradlew.bat assembleRelease
```

也可以用 `-PandroidKeystore`、`-PandroidStorePassword`、`-PandroidKeyAlias` 和
`-PandroidKeyPassword` 传入。不要把密码写进项目文件或提交到 Git。

当前仓库没有提交签名密钥。发布包应在本地或 CI 注入签名配置。

## GitHub Actions 自动构建

[`.github/workflows/android.yml`](.github/workflows/android.yml) 会在 push 到 `master`/`main`、提交 Pull Request 或手动触发时构建 APK：

- 每次都构建 Debug APK，并作为 `app-debug` artifact 上传。
- 如果配置了发布签名 secrets，还会构建签名 Release APK，并作为 `app-release` artifact 上传。

在仓库的 **Settings → Secrets and variables → Actions** 中添加以下 secrets：

| Secret | 内容 |
| --- | --- |
| `AI_RP_KEYSTORE_BASE64` | keystore 文件的 Base64 编码 |
| `AI_RP_STORE_PASSWORD` | keystore 密码 |
| `AI_RP_KEY_ALIAS` | 密钥别名 |
| `AI_RP_KEY_PASSWORD` | 密钥密码 |

生成 Base64（Git Bash）：

```bash
base64 -w0 daiyosei.jks
```

keystore 本身不应提交到仓库，`.gitignore` 已排除 `*.jks`、`*.keystore` 和常见签名配置文件。使用 GitHub CLI 也可以添加 secrets：

```bash
gh secret set AI_RP_KEYSTORE_BASE64 < keystore.b64
gh secret set AI_RP_STORE_PASSWORD
gh secret set AI_RP_KEY_ALIAS
gh secret set AI_RP_KEY_PASSWORD
```

