package eu.kanade.tachiyomi.extension.en.hentaidad

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class GalleryHtmlTest {
    private fun document(html: String) = Jsoup.parse(html, "https://example.com/gallery")

    @Test
    fun filtersVideosAndUnlabelledCards() {
        val doc = document(
            """
            <article class="archive-card"><span class="archive-card-type">Video</span></article>
            <article class="archive-card"><span class="archive-card-type">Gallery</span></article>
            <article class="archive-card"><span class="archive-card-type">Gallery</span></article>
            <article class="archive-card"></article>
            """.trimIndent(),
        )
        assertEquals(2, GalleryHtml.cards(doc).size)
    }

    @Test
    fun extractsOnlyFullSizeGalleryImagesInDocumentOrder() {
        val doc = document(
            """
            <img src="/ad.webp">
            <div id="premium-gallery">
              <a class="gallery-item" href="/images/2.webp"><img src="/thumb2.webp"></a>
              <a class="gallery-item" href="/images/1.JPG?token=abc"><img src="/thumb1.webp"></a>
              <a class="gallery-item" href="/images/2.webp"></a>
              <a class="gallery-item" href="/videos/clip.mp4"></a>
              <a class="gallery-item" href="javascript:alert(1)"></a>
              <a class="ad" href="/ad.webp"></a>
            </div>
            <a class="gallery-item" href="/related.webp"></a>
            """.trimIndent(),
        )
        assertEquals(
            listOf("https://example.com/images/2.webp", "https://example.com/images/1.JPG?token=abc"),
            GalleryHtml.imageUrls(doc),
        )
    }

    @Test
    fun recognizesGalleryPostAndRejectsVideoPost() {
        assertTrue(GalleryHtml.isGallery(document("<header class='post-header'><span class='post-type'>Gallery</span></header>")))
        assertFalse(GalleryHtml.isGallery(document("<header class='post-header'><span class='post-type'>Video</span></header>")))
        assertFalse(GalleryHtml.isGallery(document("<main>Not a post</main>")))
    }

    @Test
    fun resolvesPaginationWithoutUsingRelatedLinks() {
        val doc = document("<a rel='next' href='/unrelated'></a><nav class='archive-pagination'><a rel='next' href='/search/2?q=a%26b'>Next</a></nav>")
        assertEquals("https://example.com/search/2?q=a%26b", GalleryHtml.nextUrl(doc))
        assertNull(GalleryHtml.nextUrl(document("<nav class='archive-pagination'><a href='/1'>1</a></nav>")))
    }

    @Test
    fun emptyOrLockedGalleryHasNoImages() {
        assertTrue(GalleryHtml.imageUrls(document("<div id='premium-gallery'>Sign in</div>")).isEmpty())
    }

    @Test
    fun validatesCapturedPublicWebsiteHtml() {
        val fixturePath = System.getenv("HENTAIDAD_FIXTURES")
        assumeNotNull(fixturePath)
        fun read(name: String) = Jsoup.parse(File(fixturePath!!, "$name.html"), "UTF-8", "https://hentaidad.com/")
        assertEquals(24, GalleryHtml.cards(read("galleries")).size)
        assertEquals("https://hentaidad.com/images/2", GalleryHtml.nextUrl(read("galleries")))
        assertEquals(24, GalleryHtml.cards(read("galleries-page2")).size)
        val mixed = read("trending")
        assertEquals(24, mixed.select("article.archive-card").size)
        assertEquals(21, GalleryHtml.cards(mixed).size)
        assertEquals(1, GalleryHtml.cards(read("search")).size)
        assertEquals("https://hentaidad.com/search/2?q=animation", GalleryHtml.nextUrl(read("search-mixed")))
        val gallery = read("gallery")
        assertTrue(GalleryHtml.isGallery(gallery))
        assertEquals(71, GalleryHtml.imageUrls(gallery).size)
        assertTrue(GalleryHtml.imageUrls(gallery).first().endsWith("-1.webp"))
        assertTrue(GalleryHtml.imageUrls(gallery).last().endsWith("-71.webp"))
        assertEquals(71, gallery.select("#premium-gallery a.gallery-item img").size)
    }
}
