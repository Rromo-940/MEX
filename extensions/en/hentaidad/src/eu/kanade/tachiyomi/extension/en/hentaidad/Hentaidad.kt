package eu.kanade.tachiyomi.extension.en.hentaidad

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseZonedDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class Hentaidad : KeiSource() {

    override val supportsLatest = false

    override val supportsFilterFetching = true

    override fun getHomeUrl() = "$baseUrl/images"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // Cache only pagination URLs, so skipping video-only pages preserves logical page numbers.
    private val searchStarts = object : LinkedHashMap<Pair<String, Int>, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, String>) = size > 64
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val path = if (page == 1) "/images" else "/images/$page"
        return parseListing(client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return rawSearch(page, query, filters)
        val titleFilters = FilterList()
        return rawSearch(page, query, titleFilters)
    }
    private suspend fun rawSearch(page: Int, query: String, filters: FilterList): MangasPage {
        val customTag = filters.firstInstanceOrNull<CustomTagFilter>()?.state.orEmpty()
        val selectedTag = filters.firstInstanceOrNull<TagFilter>()?.slug
        val tagUrl = when {
            customTag.isNotBlank() -> TagUrls.fromInput(baseUrl, customTag)
            selectedTag != null -> TagUrls.fromInput(baseUrl, selectedTag)
            else -> null
        }
        if (query.isBlank() && tagUrl == null) return getPopularManga(page)

        val firstUrl = tagUrl ?: "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build().toString()
        val cached = synchronized(searchStarts) {
            searchStarts.entries.filter { it.key.first == firstUrl && it.key.second <= page }
                .maxByOrNull { it.key.second }?.let { it.key.second to it.value }
        }
        var logicalPage = cached?.first ?: 1
        var url = cached?.second ?: firstUrl

        while (true) {
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            val result = parseListing(document)
            val next = GalleryHtml.nextUrl(document)
            if (result.mangas.isNotEmpty() || next == null) {
                if (logicalPage == page) {
                    if (next != null) synchronized(searchStarts) { searchStarts[firstUrl to page + 1] = next }
                    return result
                }
                if (next == null) return MangasPage(emptyList(), false)
                logicalPage++
            }
            url = next
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/categories", Fetch.DOCUMENT).asJsoup()
        val tags = GalleryHtml.tags(document)
        check(tags.isNotEmpty()) { "The website's tag directory could not be loaded." }
        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<Tag>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Main search uses website results (titles and metadata) and takes priority over tag filters."))
                if (tags.isNotEmpty()) add(TagFilter(tags))
                add(CustomTagFilter())
                add(Filter.Header("Custom tag overrides the picker when the main search is empty."))
            },
        )
    }

    private fun parseListing(document: Document): MangasPage {
        val mangas = GalleryHtml.cards(document).map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a.archive-card-link")!!.absUrl("href"))
                title = card.selectFirst(".archive-card-title")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, GalleryHtml.nextUrl(document) != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(baseUrl + url.encodedPath, Fetch.DOCUMENT).asJsoup()
        return if (GalleryHtml.isGallery(document)) parseDetails(document) else null
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst(".post-header h1")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
        description = document.selectFirst(".post-description > div")?.text()
        genre = document.select(".post-tags a").joinToString { it.text() }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        check(GalleryHtml.isGallery(document)) { "This post is not an image gallery." }
        val details = parseDetails(document).apply { url = manga.url }
        val chapterList = if (GalleryHtml.imageUrls(document).isEmpty()) {
            emptyList()
        } else {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    chapter_number = 1f
                    date_upload = DateTimeFormatter.ISO_OFFSET_DATE_TIME.tryParseZonedDateTime(
                        document.selectFirst(".post-meta time")?.attr("datetime"),
                    )
                },
            )
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        if (!GalleryHtml.isGallery(document)) return emptyList()
        return GalleryHtml.imageUrls(document).mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
}
