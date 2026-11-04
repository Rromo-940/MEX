package eu.kanade.tachiyomi.extension.en.truyenhentai

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal class MediaResponse(val image: GalleryAsset)

@Serializable
internal class AlbumResponse(val images: List<GalleryAsset>)

@Serializable
internal class GalleryAsset(
    val type: String? = null,
    val isLocked: Boolean = false,
    private val processedImgSrc: String? = null,
    private val url: String? = null,
) {
    fun isImage() = type != "video" && type != "embed"
    fun imageUrl(): String? = (processedImgSrc?.takeIf(String::isNotBlank) ?: url?.takeIf(String::isNotBlank))
        ?.takeIf { it.toHttpUrlOrNull() != null }
}

internal object GalleryPages {
    fun images(main: GalleryAsset, additional: List<GalleryAsset>): List<String> {
        if (main.isLocked || !main.isImage()) return emptyList()
        return (listOf(main) + additional).filter { !it.isLocked && it.isImage() }
            .map { checkNotNull(it.imageUrl()) { "The gallery response is missing an image URL." } }.distinct()
    }
}
