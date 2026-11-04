package eu.kanade.tachiyomi.extension.en.hentaidad

import java.text.Normalizer
import java.util.Locale

internal object GalleryIndex {
    private fun words(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    fun prefix(query: String) = words(query).firstOrNull { it.length >= 3 && it !in setOf("girl", "girls", "images", "image", "erotic", "hentai", "generated", "sex", "porn", "part", "collection", "the", "and", "with", "from", "for", "full", "set", "patreon", "fanbox", "pixiv", "artist") }?.take(3)
    fun shard(prefix: String) = "%02x".format(Locale.ROOT, java.security.MessageDigest.getInstance("SHA-256").digest(prefix.toByteArray(Charsets.UTF_8))[0].toInt() and 63)
    fun matches(slug: String, query: String): Boolean {
        val terms = words(query)
        val available = words(slug).toSet()
        return terms.isNotEmpty() && terms.all { term -> available.any { term in it } }
    }
    fun thumbnail(url: String) = if ("/upload/" in url && "/p=" !in url) url.substringBeforeLast('/') + "/p=160x200/" + url.substringAfterLast('/') else url
}
