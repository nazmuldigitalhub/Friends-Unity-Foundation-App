# Friends Unity Foundation

Android WebView app for [friendsunityfoundation.org](https://friendsunityfoundation.org), written in Kotlin with Gradle Kotlin DSL.

## Requirements

- JDK 17
- Android SDK Platform 34 (compileSdk / targetSdk 34, minSdk 24)

## Build

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

On Windows:

```bat
gradlew.bat assembleDebug
```

## Features

- Full-screen WebView with JavaScript, DOM storage, cookies, and third-party cookies enabled
- Pull-to-refresh via `SwipeRefreshLayout`
- File upload support for `<input type="file">`
- Downloads handled by Android `DownloadManager` (cookies and user agent forwarded)
- Back navigation through WebView history using `OnBackPressedCallback`
- `tel:`, `mailto:`, `whatsapp:`, `intent:`, and other custom schemes open in external apps; `http(s)` stays in the app
- Custom offline fallback page (`app/src/main/assets/offline.html`) with a Retry button bridged through `@JavascriptInterface` (`Android.retry()`)
- Cleartext traffic allowed in the manifest as a fallback

## CI

`.github/workflows/build-apk.yml` builds the debug APK on GitHub Actions with JDK 17 and uploads it as an artifact.
