package eu.kanade.tachiyomi.extension.en.porncomic
import org.junit.Test
import org.junit.Assert.assertEquals
import org.jsoup.Jsoup
class SearchMergeTest {
 @Test fun includesIndexedTitlesWhenWebsiteHasOtherResults() {
  assertEquals(listOf("native", "indexed"), SearchMerge.unique(listOf("native"),listOf("indexed"),mutableSetOf()) { it })
 }
 @Test fun deduplicatesSourcesAndLaterPages() {
  val seen=mutableSetOf<String>()
  assertEquals(listOf("a","b"),SearchMerge.unique(listOf("a"),listOf("a","b"),seen){it})
  assertEquals(listOf("c"),SearchMerge.unique(listOf("b","c"),emptyList(),seen){it})
 }
 @Test fun videoWordDoesNotExcludeAMangaGallery() {
  val doc=Jsoup.parse("<ul id=list><li><a class=thumb href=/h/123.html><img alt='Video Game Gallery'></a><span class=category>Manga</span></li></ul>","https://porn-comic.com")
  assertEquals(1,ComicHtml.cards(doc).size)
 }
}
