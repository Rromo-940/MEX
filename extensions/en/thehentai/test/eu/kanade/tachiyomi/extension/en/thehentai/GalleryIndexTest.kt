package eu.kanade.tachiyomi.extension.en.thehentai
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
class GalleryIndexTest {
 @Test fun partialCreatorNames() { assertTrue(GalleryIndex.matches("Mimiyama gallery", "mimiya")) }
 @Test fun unrelatedNamesExcluded() { assertFalse(GalleryIndex.matches("Another gallery", "mimiya")) }
}
