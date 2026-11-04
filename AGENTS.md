# MEX source requirements

These are the user's standing requirements for every existing and new MEX source:

- Provide fast gallery-name index lookups alongside native website search. Do not crawl result pages on the phone to simulate title search.
- Support partial-name matching: a query such as `mimiya` must include titles containing `mimiyama`.
- Add each new source to the index builder/export adapter, publish its shards and coverage manifest, and test the reported name searches before release.
- Prefer public catalogue feeds/sitemaps. Respect access restrictions. If the full catalogue cannot be fetched, mark the index as partial and explicitly report missing coverage; never claim a full index from a small seed.
- Preserve native search, source IDs, gallery/chapter URLs, and signing identity. Index results must open through the source's normal detail/reader flow.
- Keep lookup downloads small and pagination in batches. Never load a huge tag/index list into filter widgets.
- Use `templates/NameIndex.kt` and the source's GalleryIndex matcher as the starting point for new adapters. Declare source-specific content/language filtering where needed.
- Run module tests, release lint, signing checks, and public index/download checks before delivery.
