package eu.kanade.tachiyomi.extension.en.hentaidad

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal class Tag(val name: String, val slug: String)

internal class TagFilter(private val tags: List<Tag>) : Filter.Select<String>("Tag / series", (listOf("All") + tags.map { it.name }).toTypedArray()) {
    val slug: String? get() = tags.getOrNull(state - 1)?.slug
}

internal class CustomTagFilter : Filter.Text("Custom tag slug or URL")

internal object TagUrls {
    fun fromInput(baseUrl: String, input: String): String {
        val value = input.trim()
        val parsed = value.toHttpUrlOrNull()
        val slug = if (parsed != null) {
            val segments = parsed.pathSegments.filter(String::isNotEmpty)
            require(parsed.host == baseUrl.toHttpUrl().host && segments.size == 2 && segments.first() == "hentai") {
                "Enter a tag URL from this website, such as /hentai/video-game."
            }
            segments.last()
        } else {
            value.removePrefix("/hentai/").trim('/').lowercase().replace(whitespace, "-")
        }
        require(slug.isNotBlank() && slug.none { it in "/?#" } && slug != "." && slug != "..") {
            "Enter a single tag slug or a tag page URL."
        }
        return baseUrl.toHttpUrl().newBuilder().addPathSegment("hentai").addPathSegment(slug).build().toString()
    }

    private val whitespace = Regex("\\s+")
}
