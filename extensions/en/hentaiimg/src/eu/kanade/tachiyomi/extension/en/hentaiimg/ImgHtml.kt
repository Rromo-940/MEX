package eu.kanade.tachiyomi.extension.en.hentaiimg

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

internal object ImgHtml {
    fun listUrl(base: String, path: String, page: Int) = base + path.trimEnd('/') + if (page == 1) "/" else "/page/$page/"
    fun cards(doc: Document) = doc.selectFirst("#image-list")?.select(".image-list-item")?.filter { it.selectFirst(".image-list-item-title a[href^=/image/]") != null }.orEmpty()
    fun images(doc: Document) = doc.select("#display_image_detail a[data-modal-gallery-image-item][href]").map { it.absUrl("href") }.distinct()
    fun nextGallery(doc: Document): String? = doc.select("#paginator a[href]").firstOrNull { it.text().replace(" ", "").equals("Next>", true) }?.absUrl("href")
    fun tags(doc: Document) = doc.select("a[href^=/search/tag/]").associate {
        it.text().replace(Regex("\\s*\\(\\d+\\)\\s*$"), "").trim().lowercase() to it.attr("href")
    }
    fun tagPath(base: String, input: String): String {
        val raw = input.trim()
        if (raw.startsWith("https://") || raw.startsWith("/search/tag/")) {
            val url = (if (raw.startsWith("/")) base + raw else raw).toHttpUrl()
            require(url.host == base.toHttpUrl().host && Regex("/search/tag/[^/]+/?").matches(url.encodedPath)) { "Use a tag URL from this website." }
            return url.encodedPath.trimEnd('/') + "/"
        }
        require(!raw.contains('/') && !raw.contains('?') && !raw.contains('#')) { "Enter a tag name, slug, or site tag URL." }
        val slug = raw.lowercase().replace(' ', '-')
        return "/search/tag/" + "$base/".toHttpUrl().newBuilder().addPathSegment(slug).build().encodedPath.removePrefix("/") + "/"
    }
}
