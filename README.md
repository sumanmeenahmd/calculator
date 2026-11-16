# Calendar Android App (`calendar-5ce.pages.dev`)

Full-screen Jetpack Compose + WebView Android app wrapping `https://calendar-5ce.pages.dev`, configured with automated GitHub Actions Release APK builds on every push.

## Features
- **Full-Screen Edge-to-Edge WebView**: Displays `https://calendar-5ce.pages.dev` full-screen with notch & system navigation bar safety (`WindowInsets.safeDrawing`), HTML5 fullscreen view support, and smooth splash/progress overlays.
- **Offline & Error Recovery**: Detects connectivity automatically via `ConnectivityManager` and auto-reloads when internet returns, with manual **Try Again** and **Open in Browser** fallbacks.
- **Web Capabilities**: Persistent cookies/localStorage/IndexedDB, hardware back-button history navigation, zero-permission file upload (`<input type="file">`), and `http`/`https`/`data:`/`blob:` file download support.
- **Minimal, Play-Compliant Permissions**:
  - `android.permission.INTERNET` (load the web app)
  - `android.permission.ACCESS_NETWORK_STATE` (detect offline/online state)
  - No intrusive storage, camera, or location permissions requested.

## Automated Release APK & Seamless Updates (GitHub Actions)

For Android to **install new APKs as updates** over older versions without uninstalling:
1. Every build must use the **same signing keystore** (`my-upload-key.jks` with alias `upload`).
2. Every build must have a **higher `versionCode`** (automatically set via `VERSION_CODE=${{ github.run_number }}` in CI).

### One-Time Signing Setup (2 minutes)

1. Generate your permanent release keystore locally (keep this file safe and never commit it to Git):
   ```bash
   keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Convert `my-upload-key.jks` to base64:
   - **Linux / macOS:**
     ```bash
     base64 -w 0 my-upload-key.jks
     ```
     *(on macOS use `base64 -i my-upload-key.jks | tr -d '\n'`)*
   - **Windows PowerShell:**
     ```powershell
     [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))
     ```
3. In your GitHub repository, go to **Settings → Secrets and variables → Actions → New repository secret** and add these 3 secrets:
   - `SIGNING_KEYSTORE_BASE64`: the base64 output from step 2
   - `STORE_PASSWORD`: the keystore password you entered in `keytool`
   - `KEY_PASSWORD`: the key password for alias `upload`

### Every Push Builds & Releases Automatically
- On every `git push` to `main` or `master`, `.github/workflows/android-release.yml` runs unit tests, increments `versionCode`, builds the signed `app-release.apk`, uploads it as an Actions artifact, and creates a new **GitHub Release** (`v1.0.<run_number>`) with `Calendar-v1.0.<run_number>.apk` ready to download and update directly on Android.
