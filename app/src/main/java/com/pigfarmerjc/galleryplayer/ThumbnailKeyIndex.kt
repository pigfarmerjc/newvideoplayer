package com.pigfarmerjc.galleryplayer

internal class ThumbnailKeyIndex {
    private val latestKeyByUri = mutableMapOf<String, String>()
    private val uriByKey = mutableMapOf<String, String>()

    @Synchronized
    fun record(contentUri: String, key: String) {
        val previousUri = uriByKey.put(key, contentUri)
        if (previousUri != null && previousUri != contentUri && latestKeyByUri[previousUri] == key) {
            latestKeyByUri.remove(previousUri)
        }
        latestKeyByUri[contentUri] = key
    }

    @Synchronized
    fun remove(key: String) {
        val contentUri = uriByKey.remove(key) ?: return
        if (latestKeyByUri[contentUri] == key) {
            latestKeyByUri.remove(contentUri)
        }
    }

    @Synchronized
    fun latestKey(contentUri: String): String? = latestKeyByUri[contentUri]

    @Synchronized
    fun clear() {
        latestKeyByUri.clear()
        uriByKey.clear()
    }
}
