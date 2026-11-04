package eu.kanade.tachiyomi.extension.en.hentaiimg

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

internal object ImgHtml {
    fun listUrl(base: String, path: String, page: Int) = base + path.trimEnd('/') + if (page == 1) "/" else "/page/$page/"
    fun cards(doc: Document) = (
        doc.selectFirst("#display_area_image")?.selectFirst("#image-list")?.select(".image-list-item")
            ?: doc.selectFirst("#right_sidebar")?.selectFirst("#entry_list")?.select("li")
        ).orEmpty().filter { link(it) != null }
    fun link(card: org.jsoup.nodes.Element) = card.selectFirst(".image-list-item-title a[href^=/image/], a[href^=/image/]")
    fun hasNext(doc: Document) = doc.selectFirst("a.nextpostslink[href], #page_nation a[rel=next][href]") != null
    fun images(doc: Document) = doc.select("#display_image_detail a[data-modal-gallery-image-item][href], #detail_list a[data-modal-gallery-image-item][href]").map { it.absUrl("href") }.distinct()
    fun nextGallery(doc: Document): String? = doc.select("#paginator a[href], #page_nation a[href]").firstOrNull { it.text().replace(" ", "").equals("Next>", true) }?.absUrl("href")
    fun tags(doc: Document) = doc.select("a[href^=/search/tag/]").associate {
        it.text().replace(Regex("\\s*\\(\\d+\\)\\s*$"), "").trim().lowercase() to it.attr("href")
    }
    fun titleSlug(query: String) = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFKD).lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
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
