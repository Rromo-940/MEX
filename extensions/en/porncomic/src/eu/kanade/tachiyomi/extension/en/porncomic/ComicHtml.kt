package eu.kanade.tachiyomi.extension.en.porncomic

import org.jsoup.nodes.Document

internal object ComicHtml {
    fun isGallery(path: String) = Regex("/(h|hentai|cos|western)/(comic-)?[0-9]+\\.html").matches(path)
    fun cards(doc: Document) = doc.select("#list > li > a.thumb, #doujin_album > li > a.thumb").filter {
        isGallery(java.net.URI(it.absUrl("href")).path) && it.selectFirst("img") != null &&
            !it.parent()!!.select(".category").text().contains("video", true) &&
            !Regex("(?i)\\b(video|mp4)\\b").containsMatchIn(it.selectFirst("img")!!.attr("alt"))
    }.distinctBy { it.absUrl("href") }
    fun next(doc: Document) = doc.select("#pages a, .page a").firstOrNull { it.text().trim().equals("Next", true) }?.absUrl("href")
    fun pageUrls(doc: Document): List<String> {
        if (doc.selectFirst(".manga-picture img") == null) return emptyList()
        val first = doc.location().substringBefore('?').replace(Regex("-[0-9]+\\.html$"), ".html")
        val count = doc.select("#pages a, #pages span").mapNotNull { it.text().trim().toIntOrNull() }.maxOrNull() ?: 1
        return (1..count).map { if (it == 1) first else first.removeSuffix(".html") + "-$it.html" }
    }
    fun directory(kind: String, value: String): String {
        val slug = value.trim().lowercase().replace(Regex("\\s+"), "-")
        require(!slug.contains('/') && !slug.contains('?') && !slug.contains('#')) { "Enter a directory name or slug." }
        return "/$kind/$slug.html"
    }
}
