package eu.kanade.tachiyomi.extension.en.porncomic
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.jsoup.Jsoup
class ComicTest {
 @Test fun nativePageLinksArePreserved() { val d=Jsoup.parse("<p class=manga-picture><img src=full.jpg></p><div id=pages><span>1</span><a href=/h/123-22.html>22</a></div>","https://porn-comic.com/h/123.html");val p=ComicHtml.pageUrls(d);assertEquals(22,p.size);assertEquals("https://porn-comic.com/h/123-22.html",p.last()) }
 @Test fun noImagesForVideoPlayer() { assertTrue(ComicHtml.pageUrls(Jsoup.parse("<video src=movie.mp4>","https://porn-comic.com/cos/123.html")).isEmpty()) }
 @Test fun filtersVideoCards() { val d=Jsoup.parse("<ul id=list><li><a class=thumb href=/h/1.html><img alt=Gallery></a></li><li><a class=thumb href=/gif/2.html><img alt=Movie></a></li><li><a class=thumb href=/cos/3.html><img alt='Video MP4'></a></li></ul>","https://porn-comic.com");assertEquals(1,ComicHtml.cards(d).size) }
 @Test fun westernHasNativeSeriesAndChapterRoutes() { assertTrue(ComicHtml.isGallery("/western/comic-5608.html"));assertTrue(ComicHtml.isGallery("/western/500620.html"));assertFalse(ComicHtml.isGallery("/western/index-2.html")) }
 @Test fun partialNameMatching() { assertTrue(GalleryIndex.matches("Mimiyama gallery", "mimiya")) }
}
