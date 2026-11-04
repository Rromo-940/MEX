package eu.kanade.tachiyomi.extension.en.hentaiyoga
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.jsoup.Jsoup
class YogaTest {
 @Test fun fullGalleryOrderExcludesHeroAndRelated() {
 val d=Jsoup.parse("<main><img id=mainImage src=preview.jpg><noscript><img src=/1.webp><img src=/2.webp></noscript><img src=related.jpg></main>", "https://hentai.yoga")
 assertEquals(listOf("https://hentai.yoga/1.webp", "https://hentai.yoga/2.webp"), YogaHtml.images(d))
 }
 @Test fun chapterImagesOnly() { assertEquals(listOf("https://hentai.yoga/p.jpg"),YogaHtml.images(Jsoup.parse("<img class=chapter-image src=/p.jpg><img src=ad.jpg>","https://hentai.yoga"))) }
 @Test fun seriesRoutesExcludeVideosAndChapters() { assertTrue(YogaHtml.isDetail("/manhwa/example")); assertFalse(YogaHtml.isDetail("/video/example")); assertFalse(YogaHtml.isDetail("/manhwa/example/chapter/1")) }
 @Test fun searchAndPagination() { assertTrue(YogaHtml.listUrl("https://hentai.yoga","gallery",2,"newest","office","art","","").contains("s=office%20art")) }
 @Test(expected=IllegalArgumentException::class) fun rejectsUnsupportedComicSort() { YogaHtml.listUrl("https://hentai.yoga","comics",1,"random","","","","") }
 @Test(expected=IllegalArgumentException::class) fun rejectsForeignGenre() { YogaHtml.listUrl("https://hentai.yoga","manhwa",1,"newest","","","https://example.com/manhwa/genre/drama","US") }
}
