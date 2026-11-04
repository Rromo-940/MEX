package eu.kanade.tachiyomi.extension.en.truyenhentai

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryHtmlTest {
    private val base = "https://www.truyen-hentai.com"
    private fun xml(text: String) = Jsoup.parse(text, base, Parser.xmlParser())

    @Test
    fun readsAllSitemapPagesAndRejectsForeignOrigins() {
        val index = xml("""
            <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
            <sitemap><loc>$base/sitemaps/tags1.xml</loc></sitemap>
            <sitemap><loc>$base/sitemaps/tags2.xml</loc></sitemap>
            <sitemap><loc>$base/sitemaps/tags1.xml</loc></sitemap>
            <sitemap><loc>https://other.example/sitemaps/tags.xml</loc></sitemap>
            </sitemapindex>
        """.trimIndent())
        assertEquals(listOf("$base/sitemaps/tags1.xml", "$base/sitemaps/tags2.xml"), DirectoryHtml.sitemaps(index, base))
    }

    @Test
    fun decodesTagLabelsAndPreservesNativeEncodedPaths() {
        val document = xml("""
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
            <url><loc>$base/en/animegirls</loc></url>
            <url><loc>$base/en/animegirls/brigitte+%28overwatch%29</loc></url>
            <url><loc>$base/en/animegirls/tracer</loc></url>
            <url><loc>$base/en/xxx-category/overwatch</loc></url>
            <url><loc>https://other.example/en/animegirls/foreign</loc></url>
            </urlset>
        """.trimIndent())
        val tags = DirectoryHtml.tags(document, base)
        assertEquals(listOf("brigitte (overwatch)", "tracer"), tags.map { it.name })
        assertEquals("/en/animegirls/brigitte+%28overwatch%29", tags.first().path)
    }

    @Test
    fun sortsEveryEntryAndRemovesDuplicatePaths() {
        val entries = listOf(DirectoryEntry("Zulu", "/z"), DirectoryEntry("alpha", "/a"), DirectoryEntry("Bravo", "/b"), DirectoryEntry("alpha", "/a"))
        assertEquals(listOf("alpha", "Bravo", "Zulu"), DirectoryHtml.sorted(entries).map { it.name })
    }

    @Test
    fun keepsCategoryAndTagPathsSeparate() {
        assertEquals("/en/xxx-category/overwatch", CategoryDto("Overwatch", "overwatch").entry().path)
        assertEquals("$base/en/animegirls/brigitte+%28overwatch%29?media_type=galleries", DirectoryHtml.listUrl(base, "/en/animegirls/brigitte+%28overwatch%29"))
        assertEquals("$base/en/xxx-category/overwatch?media_type=galleries", DirectoryHtml.listUrl(base, "/en/xxx-category/overwatch"))
    }

    @Test
    fun directoryUrlCannotLeaveSourceHost() {
        assertTrue(runCatching { DirectoryHtml.listUrl(base, "https://other.example/path") }.exceptionOrNull() is IllegalArgumentException)
    }
}
