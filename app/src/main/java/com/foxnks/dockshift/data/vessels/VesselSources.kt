package com.foxnks.dockshift.data.vessels

import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import java.io.IOException
import java.time.LocalDateTime

/**
 * The addresses of the terminal's two vessel pages.
 *
 * They come from `vessels.properties` at build time and are never committed (the
 * public repository must not name the terminal). A build without that file gets
 * empty addresses, and the vessel screens say they are not set up.
 *
 * @property workingUrl the "vessels being worked" page.
 * @property scheduledUrl the "long-term scheduled vessels" page.
 */
data class VesselSources(val workingUrl: String, val scheduledUrl: String) {

    /** True when both addresses are set. */
    val isConfigured: Boolean get() = workingUrl.isNotBlank() && scheduledUrl.isNotBlank()
}

/** Downloads a page. An interface, so the refresh logic is tested without a network. */
interface PageFetcher {

    /**
     * @param url the page's address.
     * @return the page's HTML.
     * @throws IOException without a connection, on a timeout, or when the site
     * answers with an error.
     */
    suspend fun fetch(url: String): String
}

/**
 * What is kept between app starts: the last lists, and when the button was last used
 * (so its one-hour lock survives closing the app).
 *
 * @property snapshot the last lists, or null when never read.
 * @property lastManualRefresh the time of the last manual refresh, or null.
 */
data class VesselCache(
    val snapshot: VesselSnapshot? = null,
    val lastManualRefresh: LocalDateTime? = null,
)

/** Keeps the [VesselCache] in a file. An interface, so tests use memory. */
interface VesselCacheStore {

    /** @return the stored cache, or null when there is none or it cannot be read. */
    suspend fun read(): VesselCache?

    /**
     * @param cache the cache to store, replacing the old one.
     * @throws IOException when it cannot be written (the lists then live until the app closes).
     */
    suspend fun write(cache: VesselCache)
}
