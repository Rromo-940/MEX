package eu.kanade.tachiyomi.extension.en.hentaidad

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File

class TagFiltersTest {
    private val baseUrl = "https://example.com"

    @Test
    fun extractsSortedUniqueDirectoryTagsWithoutCountsOrNavigation() {
        val document = Jsoup.parse(
            """
            <a class="page-tag" href="/hentai/navigation"><h2>Navigation</h2></a>
            <div id="tags-container">
              <a class="page-tag" href="/hentai/video-game"><h2>Video Game</h2><span>1,234 posts</span></a>
              <a class="page-tag" href="/hentai/halo"><h2>Halo</h2><span>2 posts</span></a>
              <a class="page-tag" href="/hentai/halo"><h2>Halo</h2></a>
              <a class="page-tag" href="/not-a-tag"><h2>Invalid</h2></a>
              <a class="page-tag" href="/hentai/missing-name"></a>
            </div>
            """.trimIndent(),
            baseUrl,
        )
        val tags = GalleryHtml.tags(document)
        assertEquals(listOf("Halo", "Video Game"), tags.map { it.name })
        assertEquals(listOf("halo", "video-game"), tags.map { it.slug })
    }

    @Test
    fun acceptsSlugsNamesAndTagPageUrls() {
        for (input in listOf("video-game", " Video Game ", "/hentai/video-game/", "$baseUrl/hentai/video-game/?ref=test#top")) {
            assertEquals("$baseUrl/hentai/video-game", TagUrls.fromInput(baseUrl, input))
        }
    }

    @Test
    fun keepsTagTextInPathWithoutAddingQueryParameters() {
        assertEquals("$baseUrl/hentai/c++", TagUrls.fromInput(baseUrl, "c++"))
    }

    @Test
    fun rejectsForeignUrlsNonTagPagesAndMultipleSlugs() {
        for (input in listOf("https://other.example/hentai/halo", "$baseUrl/gallery", "halo/cortana", "halo?q=other", "", ".", "..")) {
            assertTrue("Expected rejection of $input", runCatching { TagUrls.fromInput(baseUrl, input) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun validatesCapturedTagDirectoryAndTagPagination() {
        val fixturePath = System.getenv("HENTAIDAD_FIXTURES")
        assumeNotNull(fixturePath)
        fun read(name: String) = Jsoup.parse(File(fixturePath!!, "$name.html"), "UTF-8", "https://hentaidad.com/")
        val tags = GalleryHtml.tags(read("categories"))
        assertEquals(271, tags.size)
        assertEquals(tags.map { it.name.lowercase() }.sorted(), tags.map { it.name.lowercase() })
        assertTrue(tags.any { it.slug == "video-game" })
        assertFalse(tags.any { it.name.contains("posts") })
        assertEquals(2, GalleryHtml.cards(read("tag-halo")).size)
        assertEquals("https://hentaidad.com/hentai/video-game/2", GalleryHtml.nextUrl(read("tag-video-game")))
    }
}
