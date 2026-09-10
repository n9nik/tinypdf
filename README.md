# TinyPDF - PDF Tools

Offline PDF utility for Android: **merge**, **split**, **compress**, and **watermark** PDFs.
100% on-device — no accounts, no cloud, no daily limits, no paywalls.

- Package: `com.n9nik.pdftools`
- Min SDK 24, target/compile SDK 36
- UI: Jetpack Compose (Material3)
- PDF engine: [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0).
  Never iText (AGPL).
- Files picked via Storage Access Framework; output saved to Downloads/TinyPDF.
- Banner ads via Google Mobile Ads with **sample IDs** in test builds.
  Real AdMob IDs are injected at release time via `-PADMOB_APP_ID` / `-PADMOB_BANNER_ID`.

## Build

Cloud builds via GitHub Actions (`.github/workflows/android-cloud-build.yml`):
- `assembleDebug` → debug APK (artifact `android-apk`)
- `bundleRelease` → signed AAB when `UPLOAD_KEYSTORE_*` secrets are present (artifact `android-aab`)

Release minification (R8) and resource shrinking stay **OFF** — a previous app
crashed on-device with minify enabled.
