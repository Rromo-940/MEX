package eu.kanade.tachiyomi.extension.en.hentaiimg
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
class GalleryIndexTest {
 @Test fun creatorLookupUsesOneShard() { assertEquals("mim",GalleryIndex.prefix("[Mimiya] Cow Girl X Monster [Full Set]")) }
 @Test fun creatorNameFindsSlug() { assertTrue(GalleryIndex.matches("mimiya-cow-girl-x-monster-full-set", "Mimiya")) }
 @Test fun excludesUnrelatedGallery() { assertFalse(GalleryIndex.matches("another-cow-girl", "mimiya")) }
 @Test fun allWordsMatchWithoutTitleCrawl() { assertTrue(GalleryIndex.matches("mimiya-cow-girl-full-set", "[Mimiya] Cow Girl [Full Set]")) }

 @Test fun partialCreatorNames() { assertTrue(GalleryIndex.matches("mimiyama-kiichi-gallery", "mimiya")) }
}
