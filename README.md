# MEX â€” personal Mihon extension store

Prepared for https://github.com/Rromo-940/MEX and Mihon v0.21.3.

The store includes signed HentaiDad v1.6.2 and Truyen Hentai v1.6.4 gallery-only extensions. It preserves the signing certificate, package name, and source ID used by the installed APK.

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

Version 1.6.4 fixes preview-only gallery reading. The reader now follows the website's expanded-gallery flow: open /expand, read the media ID, fetch the primary image, then request every additional album image. Images are returned in the API's order, with duplicate URLs removed. Thumbnail-only responses no longer silently substitute for complete gallery pages. Videos and locked assets are excluded.

Existing library and chapter paths remain stable. After updating, restart Mihon and reopen the gallery. Previously cached pages or old downloads may still contain only the preview: clear the affected cached chapter or delete its old download and redownload it.

Tag search remains a lightweight text field using website gallery search. Categories remain selectable and alphabetized. Set Categories to All when using Tag search. The extension does not download the full tag catalogue.

Sixteen tests passed, including multiple-image API composition, order, duplicate first images, single-image posts, locked/video exclusions, thumbnail-only failure, and expanded URLs. Lint and signing verification passed. The expanded reader was inspected in a browser; no Android-device test was performed.


## The Hentai

The Hentai v1.6.1 adds the English site en.thehentai.net. Supports the latest feed, keyword search, pagination, separate category and text tag filters, artist metadata, descriptions, and full-size gallery reading/downloads. Gallery updates remain enabled because the site adds pages to existing galleries. Popular currently uses the site's default feed; the site exposes no popularity sort.

Categories: 3D Porn, Exclusive, Hentai, Images, Porn Comics. Tag filtering accepts a displayed name from the site's small tag directory, an exact site slug, or a same-site /tag/ URL. Artist, parody, and character URLs also work through this field. English names outside the site's directory need their actual site URL or slug (the underlying slugs are often Portuguese). One tag at a time; set Category to All when using it. Keywords can be combined with either category or tag.

The reader follows the gallery's thumbnail links and extracts #img_gallery_big from each native image page, preserving site order and avoiding thumbnail substitution. No account is required. Ads and off-site cards are excluded. No video player is implemented.

Four regression tests, release build, lint, and store signing verification passed. Public HTML checks confirmed a 25-page gallery, full-size reader markup, keyword results, and category pagination. No Android-device test was performed.


## Hentai Yoga

Hentai Yoga v1.6.1 supports image galleries, comics, manga and manhwa from hentai.yoga. No account required. Latest feed, most-viewed browsing, keyword search, paging, content type selection, Newest/Most viewed/Random sorts (Random unavailable for comics), lightweight text tag filtering, manga/manhwa genre slugs or same-site genre URLs, and nine language choices including All.

Gallery tags use website keyword search, while comics support one exact tag. Genre and language apply to manga/manhwa; use the main search field for series keywords. Manga language defaults to English. Video-only categories and character directories are outside the image catalogue and are not exposed as image filters. Account interactions such as comments and favorites remain on the website.

Gallery reading uses the native complete full-size noscript image set, preserving its order. Manga/manhwa use native chapter lists and chapter images, with update checks, chapter dates, descriptions, and available author/artist/status metadata. No video player is included. Native gallery pages were verified with 160 and 68 images, a manhwa chapter with 30 images, and manga with 15 chapters; keyword, exact comic tag, genre and language routes returned results.

Six regression tests, release build, lint, and store signing verification passed. No Android-device test was performed.


## Hentai IMG

Hentai IMG v1.6.2 adds hentai-img.com: complete full-size image galleries, keyword search, exact tags, Latest, Popular posts, Download/Bookmark/Like rankings, Recently viewed, Recent downloads/bookmarks/likes, catalogue pagination, and gallery update checks. No account is required for these public features.

Tags use a text field. Display names are resolved using the site's tag directory; exact slugs and same-site /search/tag/ URLs are also accepted. Keyword search and exact tag are separate; browse feeds apply when both fields are empty. The site does not expose a separate category taxonomy for galleries.

The reader extracts full-size links marked data-modal-gallery-image-item, never the p=700 display previews or p=160x200 thumbnails. Native Next links are followed when a gallery spans pages, with duplicate images removed and loops detected. Previous/Next Post links are excluded. Single-image ranking/archive walls, videos, and account actions such as posting comments/bookmarks remain website features.

Six regression tests, release build, lint, and store signing verification passed. Public HTML checks verified all nine browse feeds, keyword and tag pagination, and 76-image and 40-image galleries. No Android-device test was performed.


### Hentai IMG v1.6.2 phone layout fix

Fixes empty results on Mihon: the site serves a different layout to Android browsers. Supports the phone entry_list catalogue, mobile next-page links, detail_list full-size gallery images, and shared Open Graph title/cover metadata. Desktop support remains. The primary list is selected separately from repeated sidebar lists.

Eight regression tests and release lint passed. Captured Android-user-agent pages returned 10 entries each for Popular/Latest and 76 full-size gallery images. The original desktop-only verification missed this variant. No Android-device test was performed.




## Faster website search

Main search now returns the website's normal results, including title, tag and description matches, as requested. The previous strict title-only scan and its pagination map have been removed from all five sources. Normal search no longer crawls extra result pages to collect title matches. The website's pagination determines the normal batch size.

Hentai IMG's verified full-title gallery-address lookup is retained only for an empty first result page and a multiword query at least 40 characters long. Short queries such as cow girl and mimiya do not trigger it. Existing primary-result parsing and mobile-reader fixes remain.

Versions: HentaiDad 1.6.4, Truyen Hentai 1.6.6, The Hentai 1.6.3, Hentai Yoga 1.6.3, Hentai IMG 1.6.4. Main search still takes priority over tag/category filters; Yoga retains content type and language. Gallery/video exclusion remains. Build, tests, lint and signing checks passed. Two optional HentaiDad capture tests were skipped because their external fixture paths were not supplied. No Android-device benchmark was performed.


## Hentai IMG gallery-name index (v1.6.5)

When the website's native search finds nothing, Hentai IMG now searches a compact gallery-name index hosted in MEX. Normal website results remain the first choice. The index contains 162,771 distinct public gallery URLs from 721 public image sitemaps, captured 2026-11-04. It finds seven exact Mimiya galleries, including the reported Cow Girl gallery, despite native search returning no results.

A fallback query downloads one compressed shard (at most 657 KB in this snapshot), filters it locally, and returns 20 galleries per page. The current shard is retained for pagination. There is no crawling of gallery pages or multiple search pages on the phone. Short queries require a meaningful word of at least three characters.

The index uses gallery URL slugs as approximate names and the first sitemap image as the cover. Opening a gallery fetches its exact title and native reader details. It is a snapshot, not a live or complete title database: new posts, titles missing words from their URLs, and non-Latin searches can be absent. Exact words are matched to avoid returning another creator named Mimiyama for Mimiya.

Refresh manually from a network where the site's public sitemaps are accessible: run python scripts/build_hentaiimg_index.py, then commit public/search/hentaiimg. The attempted cloud build was denied HTTP 403, so its failing scheduled workflow was removed. No automated refresh is enabled.

The generated index, reported regression gallery, compression format, release build, 18 unit tests, lint, and store signing were checked. No Android-device test was performed.


## Name indexes on all MEX sources

All five sources now have a fast name-index fallback when native search returns no results. Partial-name matching includes Mimiyama when searching mimiya. Hentai IMG's snapshot contains 12 such partial-name matches (seven exact Mimiya names plus five Mimiyama names). Normal website search remains first, without phone-side catalogue crawling.

Current coverage: Hentai IMG 162,771 public gallery URL snapshots; HentaiDad 143, Truyen Hentai 2, The Hentai 68, and Hentai Yoga 235 previously captured galleries/series. The latter four are explicitly PARTIAL. Fresh automated catalogue requests returned HTTP 403; complete indexes need permitted catalogue exports or accessible public feeds. A source having index support does not mean its whole catalogue has been indexed. Yoga preserves content type and language filtering.

Standing requirements for every new MEX source are in AGENTS.md. The NameIndex template, index builder, coverage manifests and regression tests are part of the repository. Index matching supports partial words; native search remains unchanged. Full coverage must be verified and declared rather than inferred from a small seed.

To expand partial indexes from permitted captured pages or catalogue exports: install scripts/index-requirements.txt, then run python scripts/build_name_indexes.py --input-dir EXPORT_DIRECTORY from the repository root. Explicit records can be supplied as SOURCE.records.json arrays of [path, title, thumbnail, language]. The builder merges them with existing records. New sources require a corresponding source/collector entry. Do not bypass access restrictions to populate an index.

Builds, tests, release lint, signing and public publication checks were performed. Two optional HentaiDad captured-fixture tests were skipped. No Android-device test was performed.




## Porn Comic v1.6.1

Adds porn-comic.com image galleries: native keyword search, Latest/Top, Hentai Manga, Doujin pictures, Full color, Western series/chapters, Cosplay images, and 16 language directories. Separate text filters for tags, artists, parodies, characters and groups/circles follow the site's native directory routes. Use one directory filter at a time; it replaces Browse. Main search takes priority. Videos/animation are not reader formats; identified video cards are excluded, and a video detail page reports that it cannot be read.

Manga/picture galleries return the native image-page list from the visible pagination, with image URLs resolved on demand. Western series expose their native chapter list. Artist, tags, parody, description, completed status when available, and upload dates are extracted. Native gallery and chapter paths are preserved. Video-only listing pages are skipped at most twice per request, without unbounded search crawling.

The name-index fallback contains 164 previously captured gallery/series entries and is explicitly partial. It supports partial-name matches and is registered in scripts/build_name_indexes.py for future exports. No complete sitemap/catalogue export was found. Native search remains primary. Language, tag, artist, parody, character and group filters use native links rather than invented combinations.

Five unit tests, release build, lint and signing checks passed. Public HTML checks covered a 22-page manga, its different second full-size image, a 314-page picture gallery, a six-page Western chapter, and the phone reader layout. One later keyword-search check hit Cloudflare; its live result remains unverified. No Android-device test was performed.
