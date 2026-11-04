package eu.kanade.tachiyomi.extension.en.hentaiyoga

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

internal object YogaHtml {
    fun isDetail(path: String) = Regex("/(gallery|comics)/open/[^/]+/?|/(manga|manhwa)/[^/]+/?").matches(path)
    fun listUrl(base: String, type: String, page: Int, sort: String, query: String, tag: String, genre: String, country: String): String {
        val series = type == "manga" || type == "manhwa"
        require(sort != "random" || type != "comics") { "The comics catalogue supports Newest and Most viewed." }
        require(genre.isBlank() || series) { "Genre applies to manga and manhwa." }
        require(tag.isBlank() || !series) { "Use the main search field for manga/manhwa keywords." }
        var url = "$base/$type".toHttpUrl().newBuilder()
        if (genre.isNotBlank()) {
            val value = genre.trim()
            val slug = if (value.startsWith("https://")) {
                val parsed = value.toHttpUrl()
                require(parsed.host == base.toHttpUrl().host && parsed.encodedPath.startsWith("/$type/genre/")) { "Use a genre URL for the selected content type." }
                parsed.pathSegments.last { it.isNotBlank() }
            } else {
                value.lowercase().replace(' ', '-')
            }
            require(!slug.contains('/')) { "Enter one genre slug." }
            url.addPathSegment("genre").addPathSegment(slug)
        }
        if (type == "comics" && tag.isNotBlank()) url.addPathSegment("tag").addPathSegment(tag.trim())
        val keyword = listOf(query.trim(), if (type == "gallery") tag.trim() else "").filter { it.isNotEmpty() }.joinToString(" ")
        if (keyword.isNotEmpty()) url.addQueryParameter("s", keyword)
        if (series) {
            url.addQueryParameter("country", country)
            if (type == "manga") url.addQueryParameter("type", "manga")
        }
        return url.addQueryParameter("sort", sort).addQueryParameter("page", page.toString()).build().toString()
    }
    fun images(doc: Document): List<String> {
        val chapter = doc.select("img.chapter-image").map { it.absUrl("src") }
        if (chapter.isNotEmpty()) return chapter.distinct()
        // Native gallery noscript contains the complete full-size set, in reading order.
        return doc.select("main noscript img").map { it.absUrl("src") }.distinct()
    }
    fun hasNext(doc: Document, url: String): Boolean {
        val current = url.toHttpUrl().queryParameter("page")?.toIntOrNull() ?: 1
        return doc.select("main a[href]").any {
            val next = runCatching { it.absUrl("href").toHttpUrl() }.getOrNull()
            next?.encodedPath == url.toHttpUrl().encodedPath && (next.queryParameter("page")?.toIntOrNull() ?: 0) > current
        }
    }
}
