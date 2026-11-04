package eu.kanade.tachiyomi.extension.en.truyenhentai

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryHtmlTest {
    private val base = "https://www.truyen-hentai.com"

    @Test
    fun tagSearchUsesWebsiteSearchAndKeepsGalleryFilter() {
        assertEquals("$base/en/search/tracer?media_type=galleries", DirectoryHtml.searchUrl(base, "ignored", " tracer "))
        assertEquals("$base/en/search/brigitte%20(overwatch)?media_type=galleries", DirectoryHtml.searchUrl(base, "", "brigitte (overwatch)"))
    }

    @Test
    fun emptyTagSearchKeepsNormalQueryAndDefaultBrowse() {
        assertEquals("$base/en/search/overwatch?media_type=galleries", DirectoryHtml.searchUrl(base, "overwatch", "   "))
        assertEquals("$base/en/search/latest-galleries?media_type=galleries", DirectoryHtml.searchUrl(base, "", ""))
    }

    @Test
    fun categoriesStaySortedAndUseTheirNativeRoute() {
        val entries = listOf(DirectoryEntry("Zulu", "/z"), DirectoryEntry("alpha", "/a"), DirectoryEntry("Bravo", "/b"), DirectoryEntry("alpha", "/a"))
        assertEquals(listOf("alpha", "Bravo", "Zulu"), DirectoryHtml.sorted(entries).map { it.name })
        assertEquals("/en/xxx-category/overwatch", CategoryDto("Overwatch", "overwatch").entry().path)
        assertEquals("$base/en/xxx-category/overwatch?media_type=galleries", DirectoryHtml.listUrl(base, "/en/xxx-category/overwatch"))
    }

    @Test
    fun oldCacheStillLoadsCategoriesWithoutRetainingTagEntries() {
        val json = Json { ignoreUnknownKeys = true }
        val cached = json.decodeFromString<Directories>("""{"categories":[{"name":"Overwatch","path":"/en/xxx-category/overwatch"}],"tags":[{"name":"legacy","path":"/en/animegirls/legacy"}]}""")
        assertEquals("Overwatch", cached.categories.single().name)
        assertFalse(json.encodeToString(cached).contains("tags"))
    }

    @Test
    fun directoryUrlCannotLeaveSourceHost() {
        assertTrue(runCatching { DirectoryHtml.listUrl(base, "https://other.example/path") }.exceptionOrNull() is IllegalArgumentException)
    }
}
