package eu.kanade.tachiyomi.extension.en.truyenhentai

import eu.kanade.tachiyomi.source.model.MangasPage
import java.text.Normalizer
import java.util.Locale

internal class TitleSearch {
    private val starts = object : LinkedHashMap<Pair<String, Int>, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, Int>) = size > 64
    }
    suspend fun find(page: Int, query: String, key: String, fetch: suspend (Int) -> MangasPage): MangasPage {
        val cached = synchronized(starts) { starts.entries.filter { it.key.first == key && it.key.second <= page }.maxByOrNull { it.key.second }?.let { it.key.second to it.value } }
        var logical = cached?.first ?: 1
        var remote = cached?.second ?: 1
        while (true) {
            val result = fetch(remote)
            val matches = result.mangas.filter { matches(it.title, query) }
            if (matches.isNotEmpty() || !result.hasNextPage) {
                if (logical == page || !result.hasNextPage) {
                    if (result.hasNextPage) synchronized(starts) { starts[key to page + 1] = remote + 1 }
                    return MangasPage(if (logical == page) matches else emptyList(), result.hasNextPage)
                }
                logical++
            }
            remote++
        }
    }
    companion object {
        private fun words(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        fun matches(title: String, query: String): Boolean {
            val wanted = words(query)
            val available = words(title).toSet()
            return wanted.isNotEmpty() && wanted.all { it in available }
        }
    }
}
