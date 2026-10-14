package com.foxnks.dockshift.data.vessels

import com.foxnks.dockshift.domain.vessels.RefreshResult
import com.foxnks.dockshift.domain.vessels.ScheduledVessel
import com.foxnks.dockshift.domain.vessels.VesselPageException
import com.foxnks.dockshift.domain.vessels.VesselPages
import com.foxnks.dockshift.domain.vessels.VesselRefresh
import com.foxnks.dockshift.domain.vessels.VesselRepository
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import java.io.IOException
import java.net.UnknownHostException
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
 * @param network tells, after a failed download, whether the phone or the app is offline.
 */
@Singleton
class SiteVesselRepository @Inject constructor(
    private val sources: VesselSources,
    private val fetcher: PageFetcher,
    private val store: VesselCacheStore,
    private val clock: Clock,
    private val network: NetworkStatus,
) : VesselRepository {

    /** The cache in memory; null until read from the store. */
    private val cache = MutableStateFlow<VesselCache?>(null)

    /** One refresh (or first read) at a time. */
    private val mutex = Mutex()

    /** The last automatic attempt the site failed, so a broken site is not asked on every screen open. */
    private var lastFailedAutoAttempt: LocalDateTime? = null

    override val isConfigured: Boolean get() = sources.isConfigured

    override val snapshot: Flow<VesselSnapshot?> = cacheFlow().map { it.snapshot }.distinctUntilChanged()

    override val lastManualRefresh: Flow<LocalDateTime?> = cacheFlow().map { it.lastManualRefresh }.distinctUntilChanged()

    override suspend fun refresh(manual: Boolean, retry: Boolean): RefreshResult {
        if (!sources.isConfigured) return RefreshResult.NOT_CONFIGURED
        return mutex.withLock {
            val current = loadLocked()
            val now = LocalDateTime.now(clock)
            when {
                manual && VesselRefresh.manualAvailableAt(current.lastManualRefresh, now) != null ->
                    return@withLock RefreshResult.COOLING_DOWN
                !manual && !retry && !VesselRefresh.isStale(current.snapshot, now) ->
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
                // No lock: the user may try again once fixed
                val failure = failureOf(e)
                // Only a site that failed to answer is left alone for a while; offline or
                // blocked never reached it, so coming back after fixing a setting retries at once
                if (!manual && failure == RefreshResult.SITE_UNAVAILABLE) lastFailedAutoAttempt = now
                failure
            } catch (e: SecurityException) {
                // Android refused the connection to this app (a permission is missing or revoked)
                RefreshResult.APP_BLOCKED
            } catch (e: VesselPageException) {
                // The site answered but changed: a press still counts, so it is not asked again and again
                if (manual) save(current.copy(lastManualRefresh = now)) else lastFailedAutoAttempt = now
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

    /**
     * Why a download failed, in words the user can act on: the phone is offline; the
     * phone is online but this app is kept off the connection (a phone setting); or
     * both are online and the site did not answer.
     *
     * @param error the download's error.
     */
    private fun failureOf(error: IOException): RefreshResult = when (network.check()) {
        Connectivity.OFFLINE -> RefreshResult.NO_CONNECTION
        Connectivity.APP_BLOCKED -> RefreshResult.APP_BLOCKED
        // Online by Android's account, yet the name could not be looked up or the socket
        // was refused: that is a block on this app, not the site being down
        Connectivity.ONLINE -> if (error.looksBlocked()) RefreshResult.APP_BLOCKED else RefreshResult.SITE_UNAVAILABLE
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

        /**
         * True for the errors a blocked app gets: the site's name cannot be looked up, or
         * the system refuses the socket ("EPERM", "EACCES", "Permission denied").
         */
        fun IOException.looksBlocked(): Boolean {
            if (this is UnknownHostException) return true
            return generateSequence<Throwable>(this) { it.cause }.take(MAX_CAUSES).any { error ->
                val message = error.message.orEmpty()
                BLOCKED_MARKS.any { message.contains(it, ignoreCase = true) }
            }
        }

        /** Words in the system's errors when it refuses a connection to this app. */
        private val BLOCKED_MARKS = listOf("EPERM", "EACCES", "Permission denied", "Operation not permitted")

        /** How deep to look into an error's causes. */
        private const val MAX_CAUSES = 5

        /** [url] with [query] added after "?" or "&", as the url needs. */
        fun withQuery(url: String, query: String): String = if ('?' in url) "$url&$query" else "$url?$query"
    }
}
