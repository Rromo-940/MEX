package eu.kanade.tachiyomi.extension.en.hentaiimg

import java.text.Normalizer
import java.util.Locale

internal object TitleSearch {

    private fun words(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    fun matches(title: String, query: String): Boolean {
        val wanted = words(query)
        val available = words(title).toSet()
        return wanted.isNotEmpty() && wanted.all { it in available }
    }
}
