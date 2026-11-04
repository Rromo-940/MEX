package eu.kanade.tachiyomi.extension.en.hentaidad

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient

internal class NameIndex(private val client: OkHttpClient, private val key: String, private val headers: okhttp3.Headers) {
    private var cached: Pair<String, List<List<String>>>? = null
    suspend fun search(page: Int, query: String, accept: (String, String) -> Boolean = { _, _ -> true }): MangasPage {
        val prefix = GalleryIndex.prefix(query) ?: return MangasPage(emptyList(), false)
        val bucket = GalleryIndex.shard(prefix)
        var rows = cached?.takeIf { it.first == bucket }?.second
        if (rows == null) {
            val response = client.get("https://raw.githubusercontent.com/Rromo-940/MEX/main/public/search/$key/v1/$bucket.json", headers, Fetch.XHR, ensureSuccess = false)
            if (response.code == 404) {
                response.close()
                return MangasPage(emptyList(), false)
            }
            check(response.isSuccessful) { "Name index returned HTTP ${response.code}" }
            val parsed = response.parseAs<List<List<String>>>()
            rows = parsed
            cached = bucket to parsed
        }
        val matches = checkNotNull(rows).filter { GalleryIndex.matches(it[1], query) && accept(it[0], it.getOrNull(3).orEmpty()) }
        val start = (page - 1) * 20
        val mangas = matches.drop(start).take(20).map { row ->
            SManga.create().apply {
                url = row[0]
                title = row[1]
                thumbnail_url = row.getOrNull(2)?.takeIf { it.isNotEmpty() }
            }
        }
        return MangasPage(mangas, start + 20 < matches.size)
    }
}
