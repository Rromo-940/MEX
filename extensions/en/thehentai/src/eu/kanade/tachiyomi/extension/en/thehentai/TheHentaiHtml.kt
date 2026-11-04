package eu.kanade.tachiyomi.extension.en.thehentai

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

internal object TheHentaiHtml {
    fun listUrl(base: String, path: String, page: Int, query: String): String {
        val suffix = if (page > 1) "page/$page/" else ""
        return (base + path.trimEnd('/') + "/" + suffix).toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("s", query.trim())
        }.build().toString()
    }
    fun tagPath(base: String, input: String): String {
        val raw = input.trim()
        if (raw.startsWith("https://")) {
            val url = raw.toHttpUrl()
            require(url.host == base.toHttpUrl().host && url.encodedPath.startsWith("/tag/")) { "Use a tag URL from this website." }
            return url.encodedPath
        }
        if (raw.startsWith("/tag/")) return raw.trimEnd('/') + "/"
        require(!raw.contains('/') && !raw.contains('?') && !raw.contains('#')) { "Enter a site tag slug or tag URL." }
        return "/tag/" + raw.lowercase().replace(' ', '-') + "/"
    }
    fun pages(doc: Document) = doc.select(".post_imgs .thumbnails a[href]").map { it.absUrl("href") }.distinct()
    fun hasNext(doc: Document) = doc.select(".pagination a").any { it.text().trim() == ">" }
}
