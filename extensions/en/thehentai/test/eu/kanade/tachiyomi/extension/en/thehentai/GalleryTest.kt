package eu.kanade.tachiyomi.extension.en.thehentai
import org.junit.Test
import org.junit.Assert.assertEquals
import org.jsoup.Jsoup
class GalleryTest {
    @Test fun usesReaderLinksInSiteOrder() {
        val doc = Jsoup.parse("<div class=post_imgs><div class=thumbnails><a href=/g/01/><img src=thumb.jpg></a></div><div class=thumbnails><a href=/g/04/></div></div><a href=https://ad.example>Ad</a>", "https://en.thehentai.net")
        assertEquals(listOf("https://en.thehentai.net/g/01/", "https://en.thehentai.net/g/04/"), TheHentaiHtml.pages(doc))
    }
    @Test fun searchPaginationPreservesKeyword() { assertEquals("https://en.thehentai.net/category/hentai/page/2/?s=office", TheHentaiHtml.listUrl("https://en.thehentai.net", "/category/hentai/", 2, "office")) }
    @Test fun namespacedTags() { assertEquals("/tag/artista:laliberte/", TheHentaiHtml.tagPath("https://en.thehentai.net", "artista:laliberte")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsExternalTag() { TheHentaiHtml.tagPath("https://en.thehentai.net", "https://example.com/tag/a/") }
}
