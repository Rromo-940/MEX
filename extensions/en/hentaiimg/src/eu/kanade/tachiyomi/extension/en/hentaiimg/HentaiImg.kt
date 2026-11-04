package eu.kanade.tachiyomi.extension.en.hentaiimg

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class HentaiImg : KeiSource() {
    override val supportsLatest = true
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)
    override fun getHomeUrl() = "$baseUrl/search/"
    override suspend fun getPopularManga(page: Int) = listing(ImgHtml.listUrl(baseUrl, "/ranking/", page))
    override suspend fun getLatestUpdates(page: Int) = listing(ImgHtml.listUrl(baseUrl, "/search/", page))
    private var tagNames: Map<String, String>? = null
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tag = filters.firstInstanceOrNull<TagFilter>()?.state.orEmpty().trim()
        require(query.isBlank() || tag.isEmpty()) { "Use either keyword search or one exact tag." }
        val path = when {
            tag.isNotEmpty() -> {
                if (tagNames == null) {
                    val doc = client.get("$baseUrl/tag/", Fetch.DOCUMENT).asJsoup()
                    tagNames = ImgHtml.tags(doc)
                }
                tagNames?.get(tag.lowercase()) ?: ImgHtml.tagPath(baseUrl, tag)
            }
            query.isNotBlank() -> "/search/keyword/" + query.trim().toHttpUrlSegment() + "/"
            else -> filters.firstInstanceOrNull<FeedFilter>()?.path ?: "/search/"
        }
        return listing(ImgHtml.listUrl(baseUrl, path, page))
    }
    private fun String.toHttpUrlSegment() = "$baseUrl/".toHttpUrl().newBuilder().addPathSegment(this).build().encodedPath.removePrefix("/")
    private suspend fun listing(url: String): MangasPage {
        val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = ImgHtml.cards(doc).map { card ->
            SManga.create().apply {
                val link = ImgHtml.link(card)!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = card.selectFirst("img")?.attr("alt")?.takeIf { it.isNotBlank() } ?: link.text()
                val image = card.selectFirst("img")
                thumbnail_url = image?.absUrl("data-original")?.takeIf { it.isNotEmpty() } ?: image?.absUrl("src")
            }
        }
        return MangasPage(mangas, ImgHtml.hasNext(doc))
    }
    private fun details(doc: Document) = SManga.create().apply {
        setUrlWithoutDomain(doc.location())
        title = doc.selectFirst("meta[property=og:title]")!!.attr("content").removeSuffix(" - Hentai Image")
        thumbnail_url = doc.selectFirst("meta[property=og:image]")?.absUrl("content")
        genre = doc.select("#detail_tag a").map { it.text() }.distinct().joinToString()
        status = SManga.UNKNOWN
    }
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !Regex("/image/[^/]+/?").matches(url.encodedPath)) return null
        return details(client.get(url.toString(), Fetch.DOCUMENT).asJsoup())
    }
    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        check(ImgHtml.images(doc).isNotEmpty()) { "No gallery images found." }
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
        val urls = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var url: String? = getChapterUrl(chapter)
        while (url != null) {
            check(visited.add(url)) { "The gallery pagination repeats a page." }
            val doc = client.get(url, Fetch.DOCUMENT).asJsoup()
            val images = ImgHtml.images(doc)
            check(images.isNotEmpty()) { "A gallery page returned no images." }
            urls.addAll(images)
            url = ImgHtml.nextGallery(doc)
        }
        return urls.mapIndexed { index, image -> Page(index, imageUrl = image) }
    }
    override fun getFilterList(data: JsonElement?) = FilterList(
        FeedFilter(),
        Filter.Header("Feed applies when search and tag are empty. Keyword search and exact tag are separate."),
        TagFilter(),
        Filter.Header("Tag: site display name, exact slug, or /search/tag/ URL. No large tag dropdown."),
    )
}
private class TagFilter : Filter.Text("Exact tag")
private class FeedFilter : Filter.Select<String>("Browse", arrayOf("Latest", "Popular posts", "Download ranking", "Bookmark ranking", "Like ranking", "Recently viewed", "Recent downloads", "Recent bookmarks", "Recent likes")) {
    val path get() = arrayOf("/search/", "/ranking/", "/ranking-download/", "/ranking-bookmark/", "/ranking-like/", "/recently/", "/recently-download/", "/recently-bookmark/", "/recently-like/")[state]
}
