package eu.kanade.tachiyomi.extension.en.hentaidad

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

internal object GalleryHtml {
    fun tags(document: Document): List<Tag> = document.select("#tags-container a.page-tag[href]")
        .mapNotNull { element ->
            val url = element.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val segments = url.pathSegments.filter(String::isNotEmpty)
            val name = element.selectFirst("h2")?.text()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (segments.size != 2 || segments.first() != "hentai") return@mapNotNull null
            Tag(name, segments.last())
        }
        .distinctBy { it.slug }
        .sortedBy { it.name.lowercase() }

    fun cards(document: Document) = document.select("article.archive-card").filter {
        it.selectFirst(".archive-card-type")?.text() == "Gallery"
    }

    fun nextUrl(document: Document) = document.selectFirst("nav.archive-pagination a[rel=next]")?.absUrl("href")

    fun isGallery(document: Document) = document.selectFirst(".post-header .post-type")?.text() == "Gallery"

    fun imageUrls(document: Document): List<String> = document.select("#premium-gallery a.gallery-item[href]")
        .map { it.absUrl("href") }
        .filter { url ->
            val parsed = url.toHttpUrlOrNull()
            parsed != null && parsed.pathSegments.last().substringAfterLast('.').lowercase() in imageExtensions
        }
        .distinct()

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl")
}
