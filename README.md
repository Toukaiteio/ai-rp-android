# AI-RP Android

English | [简体中文](README_CN.md)

AI-RP Android is a native Android WebView client for [AI-RP](https://rp.dys.asia/), a browser-based roleplay app. The WebView opens `https://rp.dys.asia/` by default, and a small JavaScript bridge adds the two things a web page cannot do on its own on Android: durable encrypted storage and async file picking.

## Storage

All cryptography stays in the web layer. Vault secrets are encrypted in the browser with PBKDF2 and AES-GCM, and Android only ever sees the encrypted result:

- Encrypted strings are written to the app's private directory under `files/ai-rp/data` and `files/ai-rp/local`.
- JPEG and PNG data URLs from a session are stored with the encrypted session, so images survive a WebView restart.
- Passwords, derived keys, and API keys never cross the bridge into native code.

## File picking

After a page finishes loading, Android injects one asynchronous interface:

```js
const file = await window.AndroidBridge.pickFile("application/json,image/png");
// { name, type, data }, where data is Base64 without the data: prefix
```

The implementation uses `ACTION_OPEN_DOCUMENT`, accepts MIME types and common extensions, and caps a single file at 32 MiB. The promise rejects when the user cancels the picker or the read fails, so the web layer does not need its own Activity Result handling.

## Building

You need JDK 17 or newer and the Android SDK (API 35). Android Studio can open this directory and build it directly, or use the Gradle wrapper:

```bash
./gradlew assembleDebug       # Linux, macOS
gradlew.bat assembleDebug     # Windows
```

The debug APK lands in `app/build/outputs/apk/debug/`. The project targets SDK 35 with a minimum of 26 and builds with AGP 8.6.1.

## Signed release builds

The signing key is not in the repository. To produce a release APK, point the build at an existing keystore:

```bat
set AI_RP_KEYSTORE=E:\path\to\release.jks
set AI_RP_STORE_PASSWORD=...
set AI_RP_KEY_ALIAS=...
set AI_RP_KEY_PASSWORD=...
gradlew.bat assembleRelease
```

The same values work as Gradle properties: `-PandroidKeystore`, `-PandroidStorePassword`, `-PandroidKeyAlias`, and `-PandroidKeyPassword`. Do not commit passwords or keystore files; `.gitignore` already excludes `*.jks`, `*.keystore`, and common signing property files.

Without a configured keystore, `assembleDebug` and every other non-release build needs no signing setup, and `assembleRelease` produces an unsigned APK.

## Continuous integration

[.github/workflows/android.yml](.github/workflows/android.yml) runs on every push to `master` or `main`, on pull requests, and on manual dispatch:

- A debug APK is always built and uploaded as the `app-debug` artifact.
- When the signing secrets are configured, a signed release APK is also built and uploaded as the `app-release` artifact.

Add these secrets under Settings, then Secrets and variables, then Actions:

| Secret | Value |
| --- | --- |
| `AI_RP_KEYSTORE_BASE64` | The keystore file, Base64 encoded |
| `AI_RP_STORE_PASSWORD` | Keystore password |
| `AI_RP_KEY_ALIAS` | Key alias |
| `AI_RP_KEY_PASSWORD` | Key password |

Encode the keystore with `base64 -w0 release.jks`, or set the secrets with the GitHub CLI:

```bash
gh secret set AI_RP_KEYSTORE_BASE64 < keystore.b64
gh secret set AI_RP_STORE_PASSWORD
gh secret set AI_RP_KEY_ALIAS
gh secret set AI_RP_KEY_PASSWORD
```
