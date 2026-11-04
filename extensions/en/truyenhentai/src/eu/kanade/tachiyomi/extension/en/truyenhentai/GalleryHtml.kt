package eu.kanade.tachiyomi.extension.en.truyenhentai

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

internal object GalleryHtml {
    fun cards(doc: Document) = doc.select("#wall a.grid-card").filter {
        it.selectFirst(".grid-card__format--gallery") != null
    }

    fun hasNext(doc: Document) = doc.selectFirst(".show-more[data-next-page]:not(.disabled)") != null

    fun nextUrl(doc: Document, currentUrl: String): String? {
        val page = doc.selectFirst(".show-more[data-next-page]:not(.disabled)")?.attr("data-next-page")?.toIntOrNull()
            ?.takeIf { it > 0 } ?: return null
        val current = currentUrl.toHttpUrl()
        val builder = current.newBuilder()
        if (pageSegment.matches(current.pathSegments.last())) builder.removePathSegment(current.pathSegments.lastIndex)
        return builder.addPathSegment("page-$page").build().toString()
    }

    private val pageSegment = Regex("page-\\d+")

    fun images(doc: Document): List<String> = doc.select("img.main-media-element[src]")
        .map { it.absUrl("src") }
        .filter { it.toHttpUrlOrNull() != null }
        .distinct()

    fun listUrl(base: String, page: Int, query: String = "", category: String = ""): String {
        require(page > 0)
        val builder = base.toHttpUrl().newBuilder().addPathSegment("en")
        if (category.isNotBlank()) {
            val input = category.trim()
            val parsed = input.toHttpUrlOrNull()
            val slug = if (parsed != null) {
                val segments = parsed.pathSegments.filter(String::isNotEmpty)
                require(parsed.host.removePrefix("www.") == base.toHttpUrl().host.removePrefix("www.") && segments.size == 3 && segments[0] == "en" && segments[1] == "xxx-category") {
                    "Enter a category URL from this website."
                }
                segments.last()
            } else {
                input.trim('/').lowercase().replace(" ", "-")
            }
            require(slug.isNotEmpty() && slug != "." && slug != ".." && slug.none { it in "/?#" }) { "Enter one category slug." }
            builder.addPathSegment("xxx-category").addPathSegment(slug)
        } else {
            builder.addPathSegment("search").addPathSegment(query.trim().ifEmpty { "latest-galleries" })
        }
        if (page > 1) builder.addPathSegment("page-$page")
        return builder.addQueryParameter("media_type", "galleries").build().toString()
    }
}
