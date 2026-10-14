package com.foxnks.dockshift.data.vessels

import com.foxnks.dockshift.domain.vessels.RefreshResult
import com.foxnks.dockshift.domain.vessels.ScheduledVessel
import com.foxnks.dockshift.domain.vessels.VesselPageException
import com.foxnks.dockshift.domain.vessels.VesselPages
import com.foxnks.dockshift.domain.vessels.VesselRefresh
import com.foxnks.dockshift.domain.vessels.VesselRepository
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [VesselRepository] that reads the terminal's public pages.
 *
 * One instance for the whole app, so "Today" and "Vessels" share one cache and one
 * lock: two screens opening together read the site once. Old lists are kept on any
 * failure, so a dropped connection never empties the screens.
 *
 * @param sources the pages' addresses.
 * @param fetcher downloads a page.
 * @param store keeps the lists between app starts.
 * @param clock the current time.
 */
@Singleton
class SiteVesselRepository @Inject constructor(
    private val sources: VesselSources,
    private val fetcher: PageFetcher,
    private val store: VesselCacheStore,
    private val clock: Clock,
) : VesselRepository {

    /** The cache in memory; null until read from the store. */
    private val cache = MutableStateFlow<VesselCache?>(null)

    /** One refresh (or first read) at a time. */
    private val mutex = Mutex()

    /** The last automatic attempt that failed, so a broken site is not asked on every screen open. */
    private var lastFailedAutoAttempt: LocalDateTime? = null

    override val isConfigured: Boolean get() = sources.isConfigured

    override val snapshot: Flow<VesselSnapshot?> = cacheFlow().map { it.snapshot }.distinctUntilChanged()

    override val lastManualRefresh: Flow<LocalDateTime?> = cacheFlow().map { it.lastManualRefresh }.distinctUntilChanged()

    override suspend fun refresh(manual: Boolean): RefreshResult {
        if (!sources.isConfigured) return RefreshResult.NOT_CONFIGURED
        return mutex.withLock {
            val current = loadLocked()
            val now = LocalDateTime.now(clock)
            when {
                manual && VesselRefresh.manualAvailableAt(current.lastManualRefresh, now) != null ->
                    return@withLock RefreshResult.COOLING_DOWN
                !manual && !VesselRefresh.isStale(current.snapshot, now) ->
                    return@withLock RefreshResult.NOT_NEEDED
                !manual && lastFailedAutoAttempt?.let { now.isBefore(it.plus(AUTO_RETRY_AFTER)) } == true ->
                    return@withLock RefreshResult.NOT_NEEDED
            }
            try {
                val working = VesselPages.parseWorking(fetcher.fetch(sources.workingUrl))
                val scheduled = VesselPages.upcoming(readScheduled(), now)
                val updated = VesselCache(
                    snapshot = VesselSnapshot(now, working, scheduled),
                    // Only a press that reached the site locks the button
                    lastManualRefresh = if (manual) now else current.lastManualRefresh,
                )
                save(updated)
                lastFailedAutoAttempt = null
                RefreshResult.UPDATED
            } catch (e: IOException) {
                android.util.Log.e("VesselDebug", "Network error", e)

                if (!manual) lastFailedAutoAttempt = now
                RefreshResult.NETWORK_ERROR
            } catch (e: VesselPageException) {
                android.util.Log.e("VesselDebug", "Page parsing error", e)

                if (manual) save(current.copy(lastManualRefresh = now))
                else lastFailedAutoAttempt = now

                RefreshResult.FORMAT_ERROR
            }
        }
    }

    /**
     * Reads the long-term schedule page by page, earliest berthing first, up to
     * [MAX_SCHEDULED_PAGES] pages: enough for the weeks ahead without many requests.
     * Stops early at an empty page, or when a page repeats the one before (the site
     * ignored the page number).
     */
    private suspend fun readScheduled(): List<ScheduledVessel> {
        val all = mutableListOf<ScheduledVessel>()
        var previous: List<ScheduledVessel>? = null
        for (page in 0 until MAX_SCHEDULED_PAGES) {
            val vessels = VesselPages.parseScheduled(fetcher.fetch(withQuery(sources.scheduledUrl, "order=etb&sort=asc&page=$page")))
            if (vessels.isEmpty() || vessels == previous) break
            all += vessels
            previous = vessels
        }
        return all
    }

    /** The cache, read from the store on first use, then every change. */
    private fun cacheFlow(): Flow<VesselCache> = flow {
        mutex.withLock { loadLocked() }
        emitAll(cache.filterNotNull())
    }

    /** The cache in memory, reading the store the first time. Call with [mutex] held. */
    private suspend fun loadLocked(): VesselCache =
        cache.value ?: (store.read() ?: VesselCache()).also { cache.value = it }

    /** Keeps [updated] in memory, and in the store if possible (a failed write only loses it at exit). */
    private suspend fun save(updated: VesselCache) {
        cache.value = updated
        try {
            store.write(updated)
        } catch (e: IOException) {
            Unit
        }
    }

    companion object {
        /** Pages of the long-term schedule read per refresh (50 vessels each). */
        const val MAX_SCHEDULED_PAGES = 4

        /** After a failed automatic refresh, the next automatic one waits this long. */
        val AUTO_RETRY_AFTER: Duration = Duration.ofMinutes(15)

        /** [url] with [query] added after "?" or "&", as the url needs. */
        fun withQuery(url: String, query: String): String = if ('?' in url) "$url&$query" else "$url?$query"
    }
}
