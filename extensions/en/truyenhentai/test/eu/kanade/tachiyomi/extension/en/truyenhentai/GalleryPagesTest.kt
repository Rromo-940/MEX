package eu.kanade.tachiyomi.extension.en.truyenhentai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPagesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun combinesHeroWithEveryAdditionalImageInApiOrder() {
        val main = json.decodeFromString<MediaResponse>("""{"image":{"type":"img-gal","isLocked":false,"processedImgSrc":"https://cdn.example/first?sign=hero","thumb_url":"https://example.com/thumbnail.jpg"}}""").image
        val rest = json.decodeFromString<AlbumResponse>("""{"images":[{"processedImgSrc":"https://cdn.example/second?sign=two"},{"url":"https://example.com/third.jpg"}]}""").images
        assertEquals(listOf("https://cdn.example/first?sign=hero", "https://cdn.example/second?sign=two", "https://example.com/third.jpg"), GalleryPages.images(main, rest))
    }

    @Test
    fun removesDuplicateHeroButKeepsTheOtherAlbumImage() {
        val main = GalleryAsset(type = "img-gal", url = "https://example.com/first.jpg")
        val images = listOf(main, GalleryAsset(url = "https://example.com/second.jpg"))
        assertEquals(listOf("https://example.com/first.jpg", "https://example.com/second.jpg"), GalleryPages.images(main, images))
    }

    @Test
    fun retainsSingleImagePostsAndExcludesVideosAndLockedAssets() {
        val main = GalleryAsset(type = "img", url = "https://example.com/image.jpg")
        assertEquals(listOf("https://example.com/image.jpg"), GalleryPages.images(main, emptyList()))
        assertEquals(1, GalleryPages.images(main, listOf(GalleryAsset(type = "video", url = "https://example.com/movie.mp4"), GalleryAsset(isLocked = true, url = "https://example.com/locked.jpg"))).size)
        assertTrue(GalleryPages.images(GalleryAsset(type = "img-gal", isLocked = true), emptyList()).isEmpty())
    }

    @Test
    fun usesNativeExpandedReaderUrlWithoutChangingStoredChapterPaths() {
        val base = "https://www.truyen-hentai.com"
        val path = "/en/example/gallery"
        assertEquals("$base$path/expand", GalleryHtml.expandedUrl(base, path))
        assertEquals("$base$path/expand", GalleryHtml.expandedUrl(base, "$path/expand/"))
    }

    @Test
    fun thumbnailOnlyResponseCannotMasqueradeAsFullGallery() {
        val main = json.decodeFromString<MediaResponse>("""{"image":{"type":"img-gal","thumb_url":"https://example.com/thumbnail.jpg"}}""").image
        assertTrue(runCatching { GalleryPages.images(main, emptyList()) }.exceptionOrNull() is IllegalStateException)
    }
}
