# Sherlock Android — 0.16.2 port

A native Android front-end/port of Sherlock 0.16.2.

## Included
- Native Kotlin Android app.
- Sherlock 0.16.2 `data.json` bundled in the APK.
- Concurrent username checks using OkHttp.
- Manifest detection methods: `message`, `status_code`, `response_url`, `regexCheck`.
- Custom GET/HEAD/POST/PUT methods and JSON payload interpolation.
- Result states: CLAIMED, AVAILABLE, WAF, UNKNOWN, ILLEGAL, ERROR.
- Tap a CLAIMED result to open its profile in the browser.
- GitHub Actions workflow that builds a debug APK and uploads it as an artifact.
- `upstream/` contains the relevant original Python source for reference.

## GitHub build
1. Create a GitHub repository.
2. Upload the contents of this directory.
3. Push to GitHub.
4. Open **Actions → Build APK**.
5. Open the completed run and download **sherlock-debug-apk**.

## Local build
Requires JDK 17 and Gradle 8.13:
```bash
gradle :app:assembleDebug
```
APK:
`app/build/outputs/apk/debug/app-debug.apk`

## Notes
This is a native Android port, not the original Python runtime. Websites may block automated/mobile requests or change their anti-bot behavior, so results can differ from desktop Sherlock. The bundled manifest is the 0.16.2 manifest from the supplied archive.

Original project: https://github.com/sherlock-project/sherlock

MIT license; see `LICENSE`.
