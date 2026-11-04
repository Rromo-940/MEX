package eu.kanade.tachiyomi.extension.en.thehentai

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class TheHentai : KeiSource() {
    override val supportsLatest = true
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)
    override suspend fun getPopularManga(page: Int) = listing(TheHentaiHtml.listUrl(baseUrl, "/", page, ""))
    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)
    private val titleSearch = TitleSearch()
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return rawSearch(page, query, filters)
        val titleFilters = FilterList()
        return titleSearch.find(page, query, query) { remotePage -> rawSearch(remotePage, query, titleFilters) }
    }
    private suspend fun rawSearch(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.path.orEmpty()
        val tag = filters.firstInstanceOrNull<TagFilter>()?.state.orEmpty().trim()
        require(tag.isEmpty() || category.isEmpty()) { "Choose All categories when filtering by a tag." }
        var path = category.ifEmpty { "/" }
        if (tag.isNotEmpty()) {
            val directory = client.get("$baseUrl/tags/", Fetch.DOCUMENT).asJsoup()
            val match = directory.select(".tagsGrid a[href^=/tag/]").firstOrNull {
                it.select(".media_info").text().equals(tag, true)
            }
            path = match?.attr("href") ?: TheHentaiHtml.tagPath(baseUrl, tag)
        }
        return listing(TheHentaiHtml.listUrl(baseUrl, path, page, query))
    }
    private suspend fun listing(url: String): MangasPage {
        val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
        val items = doc.select(".gridPosts").mapNotNull { card ->
            val link = card.selectFirst("h3 a[href^=/]") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst(".img_cage img")?.absUrl("src")
            }
        }
        return MangasPage(items, TheHentaiHtml.hasNext(doc))
    }
    private fun details(doc: Document) = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst(".post_title")!!.text()
        thumbnail_url = doc.selectFirst("#img_cover")?.absUrl("src")
        author = doc.select(".tdArtist a").joinToString { it.text().removePrefix("@") }
        artist = author
        genre = doc.select(".tdCategory a, .tdTag a, .tdParody a, .tdCharacter a").map { it.text().removePrefix("#") }.distinct().joinToString()
        description = doc.select(".description").text()
        status = SManga.UNKNOWN
    }
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val doc = client.get(url.toString(), Fetch.DOCUMENT).asJsoup()
        return if (doc.selectFirst(".post_title") == null) null else details(doc)
    }
    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            details(doc).apply { url = manga.url },
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Gallery"
                    chapter_number = 1f
                },
            ),
        )
    }
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val links = TheHentaiHtml.pages(doc)
        check(links.isNotEmpty()) { "No gallery image pages found." }
        return links.mapIndexed { index, url -> Page(index, url) }
    }
    override suspend fun getImageUrl(page: Page): String {
        val doc = client.get(page.url, Fetch.DOCUMENT).asJsoup()
        return checkNotNull(doc.selectFirst("#img_gallery_big")?.absUrl("src")?.takeIf { it.isNotEmpty() }) { "Full-size gallery image missing." }
    }
    override fun getFilterList(data: kotlinx.serialization.json.JsonElement?) = FilterList(
        Filter.Header("Categories and tags are separate. One tag at a time; keyword search can be combined."),
        CategoryFilter(),
        Filter.Header("Tag: directory name, site slug, or /tag/ URL. Artist/parody/character URLs also work."),
        TagFilter(),
    )
}

private class TagFilter : Filter.Text("Tag / artist / parody / character")
private class CategoryFilter : Filter.Select<String>("Category", arrayOf("All", "3D Porn", "Exclusive", "Hentai", "Images", "Porn Comics")) {
    val path get() = arrayOf("", "/category/3d/", "/category/exclusivo/", "/category/hentai/", "/category/imagens/", "/category/comics-hq/")[state]
}
