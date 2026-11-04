package eu.kanade.tachiyomi.extension.en.hentaiimg
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.jsoup.Jsoup
class ImgTest {
 @Test fun fullImagesExcludePreviewsAndSidebar() { assertEquals(listOf("https://hentai-img.com/1.jpg"),ImgHtml.images(Jsoup.parse("<div id=display_image_detail><a data-modal-gallery-image-item href=/1.jpg><img src=preview.jpg></a></div><a href=ad.jpg>Ad</a>","https://hentai-img.com"))) }
 @Test fun ignoresNextPost() { assertNull(ImgHtml.nextGallery(Jsoup.parse("<div id=paginator><a href=/image/other/>Next Post</a></div>","https://hentai-img.com"))) }
 @Test fun followsNextGalleryPage() { assertEquals("https://hentai-img.com/image/sample/2/",ImgHtml.nextGallery(Jsoup.parse("<div id=paginator><a href=/image/sample/2/>Next&gt;</a></div>","https://hentai-img.com"))) }
 @Test fun stripsTagCounts() { assertEquals("/search/tag/example/", ImgHtml.tags(Jsoup.parse("<a href=/search/tag/example/>Example (123)</a>"))["example"]) }
 @Test fun excludesSecondaryLists() { val d=Jsoup.parse("<div id=display_area_image><ul id=image-list><li class=image-list-item><p class=image-list-item-title><a href=/image/one/>One</a></p></li></ul></div><div id=display_area_image><ul id=image-list><li class=image-list-item><p class=image-list-item-title><a href=/image/two/>Two</a></p></li></ul></div>");assertEquals(1,ImgHtml.cards(d).size) }
 @Test(expected=IllegalArgumentException::class) fun rejectsOffsiteTags() { ImgHtml.tagPath("https://hentai-img.com","https://example.com/search/tag/example/") }

    @Test fun mobileCatalogueAndPagination() {
        val doc = Jsoup.parse("<div id=right_sidebar><ul id=entry_list><li><a href=/image/sample/><div class=content_img><img alt='Sample gallery' src=thumb.jpg></div><span class=posted>2026/11/04</span><span>Sample gallery</span></a></li></ul></div><div id=page_nation><a rel=next href=/search/page/2/>Next &gt;</a></div></div><ul id=entry_list><li><a href=/image/sidebar/>Sidebar</a></li></ul>", "https://hentai-img.com")
        assertEquals(1, ImgHtml.cards(doc).size)
        assertEquals("/image/sample/", ImgHtml.link(ImgHtml.cards(doc).single())!!.attr("href"))
        org.junit.Assert.assertTrue(ImgHtml.hasNext(doc))
    }
    @Test fun mobileFullImagesAndNextPage() {
        val doc = Jsoup.parse("<ul id=detail_list><li><a data-modal-gallery-image-item href=/full.jpg><img src=preview.jpg></a></li></ul></div><div id=page_nation><a href=/image/sample/2/>Next &gt;</a></div>", "https://hentai-img.com")
        assertEquals(listOf("https://hentai-img.com/full.jpg"), ImgHtml.images(doc))
        assertEquals("https://hentai-img.com/image/sample/2/", ImgHtml.nextGallery(doc))
    }

    @Test fun emptyPrimaryListExcludesSidebar() {
        val doc = Jsoup.parse("<div id=right_sidebar>No results</div><div id=right_sidebar><ul id=entry_list><li><a href=/image/related/>Related</a></li></ul></div>")
        assertEquals(0, ImgHtml.cards(doc).size)
    }
    @Test fun pastedTitleProducesNativeGallerySlug() {
        assertEquals("studio-cow-girl-x-monster-full-set", ImgHtml.titleSlug("[Studio] Cow Girl \u725b\u98fc\u5a18 X Monster [Full Set]"))
    }
}
