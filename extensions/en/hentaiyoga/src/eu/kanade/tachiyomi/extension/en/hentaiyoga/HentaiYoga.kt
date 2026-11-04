package eu.kanade.tachiyomi.extension.en.hentaiyoga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiYoga : KeiSource() {
    override val supportsLatest = true
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)
    override fun getHomeUrl() = "$baseUrl/gallery"
    override suspend fun getPopularManga(page: Int) = listing(YogaHtml.listUrl(baseUrl, "gallery", page, "most_viewed", "", "", "", ""))
    override suspend fun getLatestUpdates(page: Int) = listing(YogaHtml.listUrl(baseUrl, "gallery", page, "newest", "", "", "", ""))
    private val nameIndex by lazy { NameIndex(client, "hentaiyoga", headers) }
    private val indexQueries = mutableSetOf<String>()
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return rawSearch(page, query, filters)
        val titleFilters = FilterList(listOfNotNull(filters.firstInstanceOrNull<TypeFilter>(), filters.firstInstanceOrNull<LanguageFilter>()))
        val accept = { path: String, language: String ->
            val type = filters.firstInstanceOrNull<TypeFilter>()?.value ?: "gallery"
            val country = filters.firstInstanceOrNull<LanguageFilter>()?.value ?: "US"
            val contentMatches = when (type) {
                "comics" -> path.startsWith("/comics/")
                "manga" -> path.startsWith("/manga/")
                "manhwa" -> path.startsWith("/manhwa/")
                else -> path.startsWith("/gallery/") || path.startsWith("/comics/")
            }
            contentMatches && (type == "gallery" || type == "comics" || country.isEmpty() || language.substringBefore('-').lowercase() == mapOf("US" to "en", "JP" to "ja", "RU" to "ru", "BR" to "pt", "ES" to "es", "VN" to "vi", "ID" to "id", "SA" to "ar")[country])
        }
        if (page > 1 && synchronized(indexQueries) { query in indexQueries }) return nameIndex.search(page, query, accept)
        if (page == 1) synchronized(indexQueries) { indexQueries.remove(query) }
        val result = rawSearch(page, query, titleFilters)
        if (page != 1 || result.mangas.isNotEmpty()) return result
        val indexed = nameIndex.search(page, query, accept)
        if (indexed.mangas.isNotEmpty()) {
            synchronized(indexQueries) {
                if (indexQueries.size >= 32) indexQueries.remove(indexQueries.first())
                indexQueries.add(query)
            }
        }
        return if (indexed.mangas.isNotEmpty()) indexed else result
    }
    private suspend fun rawSearch(page: Int, query: String, filters: FilterList): MangasPage = listing(
        YogaHtml.listUrl(
            baseUrl,
            filters.firstInstanceOrNull<TypeFilter>()?.value ?: "gallery",
            page,
            filters.firstInstanceOrNull<SortFilter>()?.value ?: "newest",
            query,
            filters.firstInstanceOrNull<TagFilter>()?.state.orEmpty(),
            filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty(),
            filters.firstInstanceOrNull<LanguageFilter>()?.value ?: "US",
        ),
    )
    private suspend fun listing(url: String): MangasPage {
        val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
        val items = doc.select("article.gallery-card, article.mcard").mapNotNull { card ->
            val link = card.selectFirst(".gallery-title-link, .mcard-title-link") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(items, YogaHtml.hasNext(doc, url))
    }
    private fun details(doc: Document) = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst("main h1")!!.text()
        thumbnail_url = doc.selectFirst("#mainImage, .manga-thumbnail-large img")?.absUrl("src")
        description = doc.select(".gallery-description-content, .manga-description").text()
        genre = doc.select("a.gallery-tag, .manhwa-genres a, .manga-genres a").joinToString { it.text() }
        fun field(key: String) = doc.select("dt").firstOrNull { it.text().contains(key, true) }?.nextElementSibling()?.text()
        author = field("author")
        artist = field("artist")
        status = when (field("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !YogaHtml.isDetail(url.encodedPath)) return null
        return details(client.get(url.toString(), Fetch.DOCUMENT).asJsoup())
    }
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = if (manga.url.contains("/open/")) {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    chapter_number = 1f
                },
            )
        } else {
            doc.select("a.manga-chapter-list-link").map { link ->
                SChapter.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    name = link.select(".manga-chapter-list-title").text()
                    date_upload = dateFormat.tryParse(link.select(".manga-chapter-list-meta").text())
                }
            }.distinctBy { it.url }
        }
        check(chapterList.isNotEmpty()) { "No readable chapters found." }
        return SMangaUpdate(details(doc).apply { url = manga.url }, chapterList)
    }
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = YogaHtml.images(client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup())
        check(images.isNotEmpty()) { "The website returned no gallery or chapter images." }
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Name index has partial catalogue coverage."),
        TypeFilter(),
        SortFilter(),
        Filter.Header("Gallery tags use site keyword search. Comics support one exact tag."),
        TagFilter(),
        Filter.Header("Genre and language apply to manga/manhwa. Use a genre slug or site genre URL."),
        GenreFilter(),
        LanguageFilter(),
    )
}
private class TypeFilter : Filter.Select<String>("Content", arrayOf("Galleries (includes comics)", "Comics", "Manga", "Manhwa")) {
    val value get() = arrayOf("gallery", "comics", "manga", "manhwa")[state]
}
private class SortFilter : Filter.Select<String>("Sort", arrayOf("Newest", "Most viewed", "Random (except comics)")) {
    val value get() = arrayOf("newest", "most_viewed", "random")[state]
}
private class TagFilter : Filter.Text("Tag / keyword")
private class GenreFilter : Filter.Text("Genre")
private class LanguageFilter : Filter.Select<String>("Manga language", arrayOf("English", "All", "Japanese", "Russian", "Portuguese", "Spanish", "Vietnamese", "Indonesian", "Arabic")) {
    val value get() = arrayOf("US", "", "JP", "RU", "BR", "ES", "VN", "ID", "SA")[state]
}
