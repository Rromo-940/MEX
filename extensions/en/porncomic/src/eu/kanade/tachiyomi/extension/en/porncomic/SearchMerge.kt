package eu.kanade.tachiyomi.extension.en.porncomic

internal object SearchMerge {
    fun <T> unique(native: List<T>, indexed: List<T>, seen: MutableSet<String>, key: (T) -> String): List<T> = (native + indexed).filter { seen.add(key(it)) }
}
