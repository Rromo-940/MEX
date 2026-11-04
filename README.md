# MEX â€” personal Mihon extension store

Prepared for https://github.com/Rromo-940/MEX and Mihon v0.21.3.

The store includes signed HentaiDad v1.6.2 and Truyen Hentai v1.6.2 gallery-only extensions. It preserves the signing certificate, package name, and source ID used by the installed APK.

## Store URL

Add this URL in Mihon > More > Settings > Browse > Extension stores after the repository is public:

`https://raw.githubusercontent.com/Rromo-940/MEX/main/public/index.pb`

The generated store files are committed under public/. GitHub Pages is not needed. Enable 18+ content warnings to see HentaiDad.

## Publishing

Push package changes to main, or run Actions > Publish extension store with rebuild unchecked to regenerate the index. The workflow validates signing certificates before committing public download files. Repository visibility must be public for Mihon to access them.

## Add another website

Each website needs its own extension implementation; adding a website URL alone cannot make Mihon parse that site.

1. Add a complete extension module under `extensions/<language>/<source>/`, including its Kotlin implementation, build.gradle.kts, and icon resources. Existing source code here follows the pinned Keiyoushi API 1.6 format.
2. Keep names, package names, and source IDs stable across updates. Increment the module's `versionCode` whenever its code changes.
3. Configure the signing secrets below once, then manually run **Publish extension store** with **rebuild** checked. The workflow builds, runs unit tests, lints, validates signatures, commits the built packages, and publishes the updated index. Fix any failed check before publishing.
4. Refresh the extension store in Mihon. New websites appear as separate installable extensions.

To publish packages built locally, put each extension under `packages/<module>/`: `extension.apk`, `extension.jar`, `icon.png`, and `metadata.json` copied from the build's `keiyoushi-source-info.json`. Pushing package changes to main triggers publishing without rebuilding. Remove the corresponding package folder explicitly when retiring an extension.

## Signing secrets for future builds

The first deployment uses included binaries. To build additional extensions in GitHub Actions, create these repository Actions secrets:

- `SIGNING_KEY`: base64 of the original HentaiDad JKS.
- `KEY_STORE_PASSWORD`: its keystore password.
- `KEY_PASSWORD`: its key password.
- `ALIAS`: its key alias.

The original key and its configuration remain in `work/signing` in the Codex task workspace. They are excluded from this repository package. Store them only as private GitHub Actions secrets when you choose to enable builds; never commit them or paste them into chat. All store extensions must use this signing key. Do not replace the `signingKey` fingerprint in store.json unless you intend to migrate store identity and installed packages.

## Files

- `extensions/`: editable website source modules.
- `packages/`: signed extension binaries, icons, and build metadata.
- `scripts/generate_store.py`: generates index.pb, diagnostic index.json, and repo.json; validates APK signatures and duplicate package/source IDs.
- `scripts/build_extensions.py`: builds modules through pinned upstream build infrastructure.
- `.github/workflows/publish.yml`: build/publish automation.
- `public/`: locally generated deployment output, committed for direct GitHub hosting.

The public store contains extension programs and icons; it does not host gallery images or videos. No signing keys or account credentials are included.

## Local verification

Use Python 3.10+ and Java. Install requirements.txt, then run:

`python scripts/generate_store.py --base-url https://raw.githubusercontent.com/Rromo-940/MEX/main/public --apksigner <Android-SDK-build-tools-apksigner-path>`

The initial package and index were validated locally. GitHub Actions deployment and installation from the hosted store must be verified after account access is available.

## Upstream

Build engine: https://github.com/keiyoushi/extensions-source at c469cb1e161ef826373b5b87eb6174f872549108. Protobuf schema and generated bindings are from that repository. Upstream Apache-2.0 license is included in LICENSE.

## Truyen Hentai

English gallery-only source for https://www.truyen-hentai.com. Supports browsing, keyword search, pagination and pasted gallery URLs.

Version 1.6.2 replaces the combined text filter with separate **Categories (A–Z)** and **Tags (A–Z)** selectors. Categories come from the public category API; tags come from every page listed in the site's published tag sitemap index. Entries are deduplicated and alphabetized. The observed site directory contains 129 categories and 24,238 published tag entries across five sitemap pages.

Choose one category OR one tag; set the other selector to All. A directory selection takes priority over keyword search. Gallery-only filtering also applies to mixed category and tag pages. The selected native URL is preserved, including encoded tag names.

On first opening Filter, allow the directory to load, then close and reopen Filter or tap Reset. Directory data is cached by the extension framework and refreshed periodically. If Cloudflare prevents directory loading, open the source in Mihon WebView and resolve the website's normal challenge there before retrying.

Build, eleven automated tests, lint, and signing checks passed. Directory counts and tag URL structure were inspected in the browser. No Android-device UI test was performed. See validation/truyenhentai.json.
