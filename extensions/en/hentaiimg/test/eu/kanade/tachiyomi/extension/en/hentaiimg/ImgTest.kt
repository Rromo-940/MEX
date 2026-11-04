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
 @Test fun excludesSecondaryLists() { val d=Jsoup.parse("<ul id=image-list><li class=image-list-item><p class=image-list-item-title><a href=/image/one/>One</a></p></li></ul><ul id=image-list><li class=image-list-item><p class=image-list-item-title><a href=/image/two/>Two</a></p></li></ul>");assertEquals(1,ImgHtml.cards(d).size) }
 @Test(expected=IllegalArgumentException::class) fun rejectsOffsiteTags() { ImgHtml.tagPath("https://hentai-img.com","https://example.com/search/tag/example/") }
}
