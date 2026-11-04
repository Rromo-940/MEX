package eu.kanade.tachiyomi.extension.en.hentaidad
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
class TitleSearchTest {
 @Test fun punctuationCaseAndAccents() { assertTrue(TitleSearch.matches("[Studio] Caf\u00e9 Office \u2013 Part 2", "cafe office part 2")) }
 @Test fun excludesMetadataOnlyMatches() { assertFalse(TitleSearch.matches("A different gallery", "office")) }
 @Test fun allTitleWordsRequired() { assertFalse(TitleSearch.matches("Office Part 1", "office part 2")) }
 @Test fun wholeWords() { assertFalse(TitleSearch.matches("Officer", "office")) }
}
