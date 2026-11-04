package eu.kanade.tachiyomi.extension.en.truyenhentai

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
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseZonedDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class TruyenHentai : KeiSource() {
    override val supportsLatest = false

    override val supportsFilterFetching = true

    override fun getHomeUrl() = "$baseUrl/en/search/latest-galleries"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override fun Headers.Builder.configureHeaders() = removeAll("Referer")

    override suspend fun getPopularManga(page: Int) = listing(GalleryHtml.listUrl(baseUrl, 1), page)

    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return rawSearch(page, query, filters)
        val titleFilters = FilterList()
        return rawSearch(page, query, titleFilters)
    }
    private suspend fun rawSearch(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.path
        val tagSearch = filters.firstInstanceOrNull<TagSearchFilter>()?.state.orEmpty().trim()
        require(category == null || tagSearch.isEmpty()) { "Set Categories to All when using Tag search." }
        val url = if (category == null) DirectoryHtml.searchUrl(baseUrl, query, tagSearch) else DirectoryHtml.listUrl(baseUrl, category)
        return listing(url, page)
    }

    private val starts = object : LinkedHashMap<Pair<String, Int>, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, String>) = size > 64
    }

    private suspend fun listing(firstUrl: String, page: Int): MangasPage {
        val cached = synchronized(starts) {
            starts.entries.filter { it.key.first == firstUrl && it.key.second <= page }
                .maxByOrNull { it.key.second }?.let { it.key.second to it.value }
        }
        var logicalPage = cached?.first ?: 1
        var url = cached?.second ?: firstUrl
        while (true) {
            val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
            val mangas = GalleryHtml.cards(doc).map { card ->
                SManga.create().apply {
                    setUrlWithoutDomain(card.absUrl("href"))
                    title = card.selectFirst(".grid-card__title")!!.text()
                    thumbnail_url = card.selectFirst("img")?.absUrl("src")
                }
            }
            val next = GalleryHtml.nextUrl(doc, url)
            if (mangas.isNotEmpty() || next == null) {
                if (logicalPage == page) {
                    if (next != null) synchronized(starts) { starts[firstUrl to page + 1] = next }
                    return MangasPage(mangas, next != null)
                }
                if (next == null) return MangasPage(emptyList(), false)
                logicalPage++
            }
            url = next
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        val doc = client.get(baseUrl + url.encodedPath.trimEnd('/').removeSuffix("/expand"), Fetch.DOCUMENT).asJsoup()
        return if (GalleryHtml.images(doc).isEmpty()) null else details(doc)
    }

    private fun details(doc: Document) = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst("h1.main-media-title")!!.text()
        thumbnail_url = doc.selectFirst("meta[property=og:image]")?.absUrl("content")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val updated = details(doc).apply { url = manga.url }
        val chapterList = if (GalleryHtml.images(doc).isEmpty()) {
            emptyList()
        } else {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    chapter_number = 1f
                    date_upload = DateTimeFormatter.ISO_OFFSET_DATE_TIME.tryParseZonedDateTime(doc.selectFirst("time[datetime]")?.attr("datetime"))
                },
            )
        }
        return SMangaUpdate(updated, chapterList)
    }

    override fun getChapterUrl(chapter: SChapter) = GalleryHtml.expandedUrl(baseUrl, chapter.url)

    private val ajaxHeaders get() = headers.newBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val expandedUrl = getChapterUrl(chapter)
        val doc = client.get(expandedUrl, Fetch.DOCUMENT).asJsoup()
        val id = doc.selectFirst("#component-node[data-media-id]")!!.attr("data-media-id").toLong()
        val main = client.get("$baseUrl/en/get-image/$id", ajaxHeaders, Fetch.XHR.from(expandedUrl)).parseAs<MediaResponse>().image
        if (main.isLocked || !main.isImage()) return emptyList()
        val additional = if (main.type == "img-gal") {
            client.get("$baseUrl/en/get-album-images/$id", ajaxHeaders, Fetch.XHR.from(expandedUrl)).parseAs<AlbumResponse>().images
        } else {
            emptyList()
        }
        return GalleryPages.images(main, additional).mapIndexed { index, image -> Page(index, imageUrl = image) }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val categories = client.get("$baseUrl/en/get_categories/public", Fetch.XHR)
            .parseAs<List<CategoryDto>>().map { it.entry() }.let(DirectoryHtml::sorted)
        check(categories.isNotEmpty()) { "The category directory is empty." }
        return Directories(categories).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        // Discard the old full-tag cache once; the next refresh fetches only categories.
        if (data is JsonObject && "tags" in data) {
            applicationContext.cacheDir.resolve("source_$id/filters.json.zst").delete()
        }
        val directories = data?.parseAs<Directories>()
        return FilterList(
            buildList {
                add(Filter.Header("Main search uses website results (titles and metadata) and takes priority. With search empty, use Categories or Tag search."))
                if (directories != null) {
                    add(CategoryFilter(directories.categories))
                }
                add(TagSearchFilter())
            },
        )
    }
}
