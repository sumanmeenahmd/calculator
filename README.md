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

Every `git push` to `main` or `master` runs `.github/workflows/android-release.yml`, which:
1. Runs unit tests (`gradle :app:testDebugUnitTest`).
2. Increments `versionCode` (`github.run_number`) and `versionName` (`1.0.<run_number>`) automatically.
3. Signs `app-release.apk` with a **consistent signing key** so every new APK installs directly as an **update** over older builds without uninstalling:
   - **Zero-Config Default**: If custom secrets are not configured, the workflow automatically generates a keystore on the first run and stores it in a private **Draft Release** (`Signing Keystore Vault (Do Not Delete)`), reusing that exact same keystore on all future pushes.
   - **Optional Custom Keystore (Recommended for Play Store)**: If you prefer your own keystore, add `SIGNING_KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` under **Settings → Secrets and variables → Actions** and the workflow will use them instead.
4. Uploads `Calendar-v1.0.<run_number>.apk` to **Workflow Artifacts** and publishes a new **GitHub Release** (`v1.0.<run_number>`).
