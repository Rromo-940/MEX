package eu.kanade.tachiyomi.extension.en.truyenhentai

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryHtmlTest {
    private val base = "https://www.truyen-hentai.com"
    private fun doc(html: String) = Jsoup.parse(html, "$base/en/example/gallery")

    @Test
    fun filtersVideoCardsAndUnrelatedRecommendations() {
        val document = doc(
            """
            <div id="wall">
              <article class="grid-item"><a class="grid-card" href="/en/example/gallery"><span class="grid-card__format grid-card__format--gallery">Gallery</span><h2 class="grid-card__title">Example</h2><img src="/thumb.webp"></a></article>
              <article class="grid-item"><a class="grid-card" href="/en/example/video"><span class="grid-card__format grid-card__format--video">Video</span></a></article>
            </div>
            <a class="grid-card"><span class="grid-card__format--gallery">Gallery</span></a>
            """.trimIndent(),
        )
        assertEquals(1, GalleryHtml.cards(document).size)
        assertEquals("$base/en/example/gallery", GalleryHtml.cards(document).single().absUrl("href"))
    }

    @Test
    fun readsOnlyMainImageElementsAndPreservesOrder() {
        val document = doc(
            """
            <img class="media-error__card-thumb" src="/ad.webp">
            <img class="main-media-element media_backdrop_target" src="https://cdn.example/first?url=original&amp;sign=keep">
            <img class="main-media-element" src="/second.webp">
            <img class="main-media-element" src="/second.webp">
            <video class="main-media-element" src="/clip.mp4"></video>
            <img src="/recommendation.webp">
            """.trimIndent(),
        )
        assertEquals(listOf("https://cdn.example/first?url=original&sign=keep", "$base/second.webp"), GalleryHtml.images(document))
        assertTrue(GalleryHtml.images(doc("<video class='main-media-element' src='/clip.mp4'></video>")).isEmpty())
    }

    @Test
    fun handlesNextPageAndEndOfFeed() {
        assertTrue(GalleryHtml.hasNext(doc("<button class='show-more' data-next-page='2'>Show more</button>")))
        assertFalse(GalleryHtml.hasNext(doc("<button class='show-more disabled' data-next-page='2'>Show more</button>")))
        assertFalse(GalleryHtml.hasNext(doc("<main>No results</main>")))
        val next = doc("<button class='show-more' data-next-page='3'>Show more</button>")
        assertEquals("$base/en/search/overwatch/page-3?media_type=galleries", GalleryHtml.nextUrl(next, "$base/en/search/overwatch/page-2?media_type=galleries"))
    }

    @Test
    fun buildsVerifiedGallerySearchAndPageTwoRoutes() {
        assertEquals("$base/en/search/latest-galleries?media_type=galleries", GalleryHtml.listUrl(base, 1))
        assertEquals("$base/en/search/overwatch/page-2?media_type=galleries", GalleryHtml.listUrl(base, 2, "overwatch"))
        val encoded = GalleryHtml.listUrl(base, 1, "a&b").substringBefore('?')
        assertTrue(encoded.endsWith("/a&b"))
    }

    @Test
    fun categoryTakesPriorityOverTextAndKeepsGalleryRestriction() {
        assertEquals("$base/en/xxx-category/overwatch/page-2?media_type=galleries", GalleryHtml.listUrl(base, 2, "ignored", "$base/en/xxx-category/overwatch"))
        assertEquals("$base/en/xxx-category/overwatch?media_type=galleries", GalleryHtml.listUrl(base, 1, category = "Overwatch"))
    }

    @Test
    fun rejectsForeignCategoriesAndInvalidSlugs() {
        for (input in listOf("https://other.example/en/xxx-category/overwatch", "$base/en/example/gallery", "a/b", "..")) {
            assertTrue(runCatching { GalleryHtml.listUrl(base, 1, category = input) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
