# AI-RP Android

独立的 Android 原生 WebView 客户端，默认打开 `https://rp.dys.asia/`。

## 持久化设计

- Web 层继续负责 Vault 的 PBKDF2 + AES-GCM 加密。
- Android 只保存已经加密的字符串到应用私有目录：`files/ai-rp/data` 与 `files/ai-rp/local`。
- 会话中的 JPEG/PNG data URL 会随加密会话一起持久化，因此重启 WebView 后图片仍可恢复。
- 不把密码、派生密钥或 API key 传入原生桥接层。

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
