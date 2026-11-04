package eu.kanade.tachiyomi.extension.en.truyenhentai

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
internal class DirectoryEntry(val name: String, val path: String)

@Serializable
internal class Directories(val categories: List<DirectoryEntry>)

@Serializable
internal class CategoryDto(private val name: String, private val slug: String) {
    fun entry() = DirectoryEntry(name, "/en/xxx-category/$slug")
}

internal open class DirectoryFilter(label: String, private val entries: List<DirectoryEntry>) : Filter.Select<String>(label, (listOf("All") + entries.map { it.name }).toTypedArray()) {
    val path: String? get() = entries.getOrNull(state - 1)?.path
}

internal class CategoryFilter(entries: List<DirectoryEntry>) : DirectoryFilter("Categories (A–Z)", entries)
internal class TagSearchFilter : Filter.Text("Tag search")

internal object DirectoryHtml {
    fun sorted(entries: List<DirectoryEntry>) = entries.distinctBy { it.path }.sortedBy { it.name.lowercase() }

    fun searchUrl(base: String, query: String, tagSearch: String): String = GalleryHtml.listUrl(base, 1, tagSearch.trim().ifEmpty { query })

    fun listUrl(base: String, path: String): String {
        val url = base.toHttpUrl().resolve(path) ?: error("Invalid directory URL")
        require(url.host == base.toHttpUrl().host)
        return url.newBuilder().addQueryParameter("media_type", "galleries").build().toString()
    }
}
