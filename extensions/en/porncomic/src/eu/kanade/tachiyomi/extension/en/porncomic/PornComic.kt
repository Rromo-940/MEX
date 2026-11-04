package eu.kanade.tachiyomi.extension.en.porncomic

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
abstract class PornComic : KeiSource() {
    override val supportsLatest = true
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)
    override suspend fun getPopularManga(page: Int) = listing("$baseUrl/hot/", page)
    override suspend fun getLatestUpdates(page: Int) = listing("$baseUrl/", page)
    private val nameIndex by lazy { NameIndex(client, "porncomic", headers) }
    private class SearchState {
        var nativeDone = false
        var indexDone = false
        val seen = mutableSetOf<String>()
    }
    private val searches = object : LinkedHashMap<String, SearchState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchState>) = size > 16
    }
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val first = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder().addPathSegment("q").addPathSegment(query.trim() + "-1.html").build().toString()
        } else {
            val language = filters.firstInstanceOrNull<LanguageFilter>()?.slug.orEmpty()
            val directories = listOfNotNull(
                language.takeIf { it.isNotEmpty() }?.let { "/language/$it.html" },
                filters.firstInstanceOrNull<TagFilter>()?.state?.takeIf { it.isNotBlank() }?.let { ComicHtml.directory("tags", it) },
                filters.firstInstanceOrNull<ArtistFilter>()?.state?.takeIf { it.isNotBlank() }?.let { ComicHtml.directory("artist", it) },
                filters.firstInstanceOrNull<ParodyFilter>()?.state?.takeIf { it.isNotBlank() }?.let { ComicHtml.directory("anime", it) },
                filters.firstInstanceOrNull<CharacterFilter>()?.state?.takeIf { it.isNotBlank() }?.let { ComicHtml.directory("characters", it) },
                filters.firstInstanceOrNull<GroupFilter>()?.state?.takeIf { it.isNotBlank() }?.let { ComicHtml.directory("circle", it) },
            )
            require(directories.size <= 1) { "Use one language, tag, artist, parody, character or group filter at a time." }
            baseUrl + (directories.firstOrNull() ?: filters.firstInstanceOrNull<BrowseFilter>()?.path ?: "/")
        }
        if (query.isBlank()) return listing(first, page)
        val state = synchronized(searches) {
            if (page == 1) searches[query] = SearchState()
            searches.getOrPut(query) { SearchState() }
        }
        val indexed = if (state.indexDone) MangasPage(emptyList(), false) else nameIndex.search(page, query)
        val native = try {
            if (state.nativeDone) MangasPage(emptyList(), false) else listing(first, page)
        } catch (e: java.io.IOException) {
            if (indexed.mangas.isEmpty()) throw e
            MangasPage(emptyList(), false)
        }
        state.nativeDone = !native.hasNextPage
        state.indexDone = !indexed.hasNextPage
        val mangas = SearchMerge.unique(native.mangas, indexed.mangas, state.seen) { it.url }
        return MangasPage(mangas, !state.nativeDone || !state.indexDone)
    }
    private val starts = object : LinkedHashMap<Pair<String, Int>, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, String>) = size > 64
    }
    private suspend fun listing(first: String, page: Int): MangasPage {
        val cached = synchronized(starts) { starts.entries.filter { it.key.first == first && it.key.second <= page }.maxByOrNull { it.key.second }?.let { it.key.second to it.value } }
        var logical = cached?.first ?: 1
        var url = cached?.second ?: first
        var skipped = 0
        while (true) {
            val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
            val mangas = ComicHtml.cards(doc).map { link ->
                SManga.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    title = link.selectFirst("img")!!.attr("alt").trim()
                    thumbnail_url = link.selectFirst("img")!!.absUrl("src")
                }
            }
            val next = ComicHtml.next(doc)
            if (mangas.isEmpty() && next != null && skipped++ < 2) {
                url = next
                continue
            }
            if (logical == page) {
                if (next != null) synchronized(starts) { starts[first to page + 1] = next }
                return MangasPage(mangas, next != null)
            }
            if (next == null) return MangasPage(emptyList(), false)
            logical++
            url = next
        }
    }
    private fun details(doc: Document) = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst("h1")!!.text()
        thumbnail_url = doc.selectFirst(".webtoon-thumb img, .manga-picture img")?.absUrl("src")
        author = doc.select(".summary a[href*=/artist/]").joinToString { it.text() }
        artist = author
        genre = doc.select(".summary a, .manga-tags a, .webtoon-tags a").map { it.text() }.distinct().joinToString()
        description = doc.select(".webtoon-content").text().ifEmpty { doc.select(".summary").firstOrNull()?.text().orEmpty() }
        status = if (doc.select(".summary").text().contains("Status: completed", true)) SManga.COMPLETED else SManga.UNKNOWN
    }
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || !ComicHtml.isGallery(url.encodedPath)) return null
        val doc = client.get(baseUrl + url.encodedPath, Fetch.DOCUMENT).asJsoup()
        return if (doc.selectFirst(".manga-picture img, .chapters a.chapter-name") == null) null else details(doc)
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        require(doc.selectFirst(".player video") == null) { "This entry is a video. Choose an image gallery." }
        val list = doc.select(".chapters a.chapter-name").map { link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.select("h3").text()
                date_upload = dateFormat.tryParse(link.select(".time").text().removePrefix("Date:").trim())
            }
        }.ifEmpty {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    chapter_number = 1f
                    date_upload = dateFormat.tryParse(Regex("Date:\\s*(\\d{4}-\\d{2}-\\d{2})").find(doc.select(".info").text())?.groupValues?.get(1))
                },
            )
        }
        return SMangaUpdate(details(doc).apply { url = manga.url }, list)
    }
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val urls = ComicHtml.pageUrls(doc)
        check(urls.isNotEmpty()) { "No image pages found. This entry may be a video." }
        return urls.mapIndexed { index, url -> Page(index, url, if (index == 0) doc.selectFirst(".manga-picture img")!!.absUrl("src") else null) }
    }
    override suspend fun getImageUrl(page: Page): String = checkNotNull(client.get(page.url, Fetch.DOCUMENT).asJsoup().selectFirst(".manga-picture img")?.absUrl("src")) { "Full-size image not found." }
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Native search comes first. Name-index coverage is partial."),
        BrowseFilter(),
        LanguageFilter(),
        TagFilter(),
        ArtistFilter(),
        ParodyFilter(),
        Filter.Header("Use one directory filter at a time. Main search takes priority."),
    )
}
private class BrowseFilter : Filter.Select<String>("Browse", arrayOf("Latest", "Top", "Hentai Manga", "Doujin pictures", "Full color", "Western", "Cosplay images")) {
    val path get() = arrayOf("/", "/hot/", "/h", "/hentai", "/tags/full-color.html", "/western", "/cos")[state]
}
private class LanguageFilter : Filter.Select<String>("Language", arrayOf("All", "English", "Chinese", "Japanese", "Korean", "Spanish", "Russian", "Italian", "French", "Indonesian", "Arabic", "Polish", "Portuguese", "Tagalog", "Vietnamese", "Ukrainian", "Thai")) {
    val slug get() = arrayOf("", "english", "chinese", "japanese", "korean", "spanish", "russian", "italian", "french", "indonesian", "arabic", "polish", "portuguese", "tagalog", "vietnamese", "ukrainian", "thai")[state]
}
private class TagFilter : Filter.Text("Tag")
private class ArtistFilter : Filter.Text("Artist")
private class ParodyFilter : Filter.Text("Parody / series")

private class CharacterFilter : Filter.Text("Character")
private class GroupFilter : Filter.Text("Group / circle")
