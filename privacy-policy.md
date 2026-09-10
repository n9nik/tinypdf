# Privacy Policy for TinyPDF - PDF Tools

**Effective date:** September 10, 2026

**Developer:** n9nik (Nikhil Suresh)

TinyPDF - PDF Tools was built as an offline-first utility. No account required.

## Core principle: offline, no cloud

Your documents are processed **entirely on your device**. TinyPDF does not upload your PDFs to any server, does not require a login, has no daily limits and no paywalls.

- **File access:** We use Android's Storage Access Framework (the system file picker). We only read the PDF files you explicitly select, process them locally on-device (merge, split, compress, watermark), and write output to Downloads/TinyPDF via MediaStore. We do not access other files and request no broad storage permission.
- **No server-side storage:** We have no servers that receive your documents. Output stays on your device.
- **Encrypted PDFs:** If a PDF is password-protected, you may enter its password in the app; the password is used only in memory to open that document and is never stored or transmitted. We do not attempt to bypass document encryption.

## Ads and consent

This is an ad-supported app using Google Mobile Ads and Google User Messaging Platform (UMP) for consent where required.

Google and its partners may collect:
- Device identifiers (including advertising ID where available, subject to your device settings)
- IP address, coarse/approximate location
- App diagnostics, ad interactions
- This is governed by Google's policies, not ours: https://policies.google.com/privacy and https://support.google.com/admob/answer/6128543

**Consent:** Where required by law (GDPR, US state laws), the app shows Google's consent message before requesting ads. An "Ad privacy options" button is available in the app.

**No personal data collection by us:** We do not maintain a user account, profile, or server-side database of your documents or personal info.

## Permissions

- `INTERNET` / `ACCESS_NETWORK_STATE` — only to load ads and check consent. All PDF tools work in airplane mode.
- `AD_ID` — for ads personalization, respects your device's "Delete advertising ID" setting.

We intentionally avoid `READ_EXTERNAL_STORAGE`, `MANAGE_ALL_FILES`, `READ_CONTACTS`, `CAMERA`, and `LOCATION`.

## Data retention and deletion

We retain nothing: there is no account and no server copy. Deleting the app removes everything the app stored. Files you saved to Downloads/TinyPDF remain yours in the Downloads folder until you delete them.

## Contact

Questions about this policy: contact the developer via the Play Store listing.
