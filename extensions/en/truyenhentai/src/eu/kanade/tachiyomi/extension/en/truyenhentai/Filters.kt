package eu.kanade.tachiyomi.extension.en.truyenhentai

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.net.URLDecoder

@Serializable
internal class DirectoryEntry(val name: String, val path: String)

@Serializable
internal class Directories(val categories: List<DirectoryEntry>, val tags: List<DirectoryEntry>)

@Serializable
internal class CategoryDto(private val name: String, private val slug: String) {
    fun entry() = DirectoryEntry(name, "/en/xxx-category/$slug")
}

internal open class DirectoryFilter(label: String, private val entries: List<DirectoryEntry>) : Filter.Select<String>(label, (listOf("All") + entries.map { it.name }).toTypedArray()) {
    val path: String? get() = entries.getOrNull(state - 1)?.path
}

internal class CategoryFilter(entries: List<DirectoryEntry>) : DirectoryFilter("Categories (A–Z)", entries)
internal class TagFilter(entries: List<DirectoryEntry>) : DirectoryFilter("Tags (A–Z)", entries)

internal object DirectoryHtml {
    fun sorted(entries: List<DirectoryEntry>) = entries.distinctBy { it.path }.sortedBy { it.name.lowercase() }

    fun sitemaps(doc: Document, base: String): List<String> = doc.select("sitemap > loc").mapNotNull {
        val url = it.text().toHttpUrlOrNull() ?: return@mapNotNull null
        url.toString().takeIf { url.host == base.toHttpUrl().host && url.encodedPath.startsWith("/sitemaps/") }
    }.distinct()

    fun tags(doc: Document, base: String): List<DirectoryEntry> = doc.select("url > loc").mapNotNull {
        val url = it.text().toHttpUrlOrNull() ?: return@mapNotNull null
        val segments = url.pathSegments
        if (url.host != base.toHttpUrl().host || segments.size != 3 || segments[0] != "en" || segments[1] != "animegirls" || segments[2].isEmpty()) return@mapNotNull null
        DirectoryEntry(URLDecoder.decode(url.encodedPath.substringAfterLast('/'), "UTF-8"), url.encodedPath)
    }

    fun listUrl(base: String, path: String): String {
        val url = base.toHttpUrl().resolve(path) ?: error("Invalid directory URL")
        require(url.host == base.toHttpUrl().host)
        return url.newBuilder().addQueryParameter("media_type", "galleries").build().toString()
    }
}
