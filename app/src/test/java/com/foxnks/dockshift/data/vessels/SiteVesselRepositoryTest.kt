package com.foxnks.dockshift.data.vessels

import com.foxnks.dockshift.domain.vessels.RefreshResult
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SiteVesselRepository] against made-up pages, a fake network and a store in memory. */
class SiteVesselRepositoryTest {

    private val sources = VesselSources("https://example.com/working", "https://example.com/scheduled?lang=en")

    /** A clock the test moves forward. */
    private class MovableClock(var now: LocalDateTime) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now.toInstant(ZoneOffset.UTC)
    }

    /** Answers with [pages] by address; anything else fails like a lost connection. */
    private class FakeFetcher(val pages: MutableMap<String, String>) : PageFetcher {
        val requested = mutableListOf<String>()
        var offline = false

        override suspend fun fetch(url: String): String {
            requested += url
            if (offline) throw IOException("offline")
            return pages[url] ?: throw IOException("404 $url")
        }
    }

    /** A connection state the test sets. */
    private class FakeNetwork(var state: Connectivity = Connectivity.ONLINE) : NetworkStatus {
        override fun check(): Connectivity = state
    }

    private val network = FakeNetwork()

    /** Keeps the cache in memory. */
    private class MemoryStore(var stored: VesselCache? = null) : VesselCacheStore {
        override suspend fun read(): VesselCache? = stored
        override suspend fun write(cache: VesselCache) {
            stored = cache
        }
    }

    private val working = """
        <table><tr><th>Vessel Name</th><th>Call Sign</th><th>Ata</th><th>Etd</th><th>Completion(%)</th></tr>
        <tr><td>SEA BREEZE</td><td>ABC1</td><td>12/10/2026 17:10</td><td>14/10/2026 14:40</td><td>12</td></tr></table>
    """

    private fun scheduledPage(vararg names: String) = buildString {
        append("<table><tr><th>Vessel Name</th><th>Vessel Operator</th><th>etb</th><th>etd</th></tr>")
        names.forEach { append("<tr><td>$it</td><td>LINE</td><td>20/10/2026 10:00</td><td>21/10/2026 10:00</td></tr>") }
        append("</table>")
    }

    private fun scheduledUrl(page: Int) = "https://example.com/scheduled?lang=en&order=etb&sort=asc&page=$page"

    private val clock = MovableClock(LocalDateTime.of(2026, 10, 14, 10, 0))

    private fun fetcher() = FakeFetcher(
        mutableMapOf(
            sources.workingUrl to working,
            scheduledUrl(0) to scheduledPage("A", "B"),
            scheduledUrl(1) to scheduledPage("C"),
            scheduledUrl(2) to scheduledPage(),
        ),
    )

    @Test
    fun `first refresh reads both pages, page by page, and stores them`() = runBlocking {
        val fetcher = fetcher()
        val store = MemoryStore()
        val repository = SiteVesselRepository(sources, fetcher, store, clock, network)

        assertEquals(RefreshResult.UPDATED, repository.refresh(manual = false))

        val snapshot = repository.snapshot.first()!!
        assertEquals(listOf("SEA BREEZE"), snapshot.working.map { it.name })
        assertEquals(listOf("A", "B", "C"), snapshot.scheduled.map { it.name })
        assertEquals(clock.now, snapshot.fetchedAt)
        // Stops at the first empty page
        assertEquals(listOf(sources.workingUrl, scheduledUrl(0), scheduledUrl(1), scheduledUrl(2)), fetcher.requested)
        assertEquals(snapshot, store.stored?.snapshot)
        // Automatic refreshes never lock the button
        assertNull(repository.lastManualRefresh.first())
    }

    @Test
    fun `fresh lists are not read again until four hours pass`() = runBlocking {
        val fetcher = fetcher()
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)
        repository.refresh(manual = false)
        fetcher.requested.clear()

        clock.now = clock.now.plusHours(3)
        assertEquals(RefreshResult.NOT_NEEDED, repository.refresh(manual = false))
        assertTrue(fetcher.requested.isEmpty())

        clock.now = clock.now.plusHours(1)
        assertEquals(RefreshResult.UPDATED, repository.refresh(manual = false))
    }

    @Test
    fun `the button locks for an hour after use, also after an app restart`() = runBlocking {
        val store = MemoryStore()
        val repository = SiteVesselRepository(sources, fetcher(), store, clock, network)

        assertEquals(RefreshResult.UPDATED, repository.refresh(manual = true))
        assertEquals(clock.now, repository.lastManualRefresh.first())

        clock.now = clock.now.plusMinutes(59)
        assertEquals(RefreshResult.COOLING_DOWN, repository.refresh(manual = true))
        // A new start reads the lock from the store
        val restarted = SiteVesselRepository(sources, fetcher(), store, clock, network)
        assertEquals(RefreshResult.COOLING_DOWN, restarted.refresh(manual = true))

        clock.now = clock.now.plusMinutes(1)
        assertEquals(RefreshResult.UPDATED, restarted.refresh(manual = true))
    }

    @Test
    fun `offline keeps the old lists and does not lock the button`() = runBlocking {
        val fetcher = fetcher()
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)
        repository.refresh(manual = false)
        val before = repository.snapshot.first()

        fetcher.offline = true
        network.state = Connectivity.OFFLINE
        clock.now = clock.now.plusHours(5)
        assertEquals(RefreshResult.NO_CONNECTION, repository.refresh(manual = true))

        assertEquals(before, repository.snapshot.first())
        assertNull(repository.lastManualRefresh.first())
    }

    @Test
    fun `a site that did not answer is left alone for a while`() = runBlocking {
        // Phone online, yet the site fails: it is down or slow
        val fetcher = fetcher().apply { offline = true }
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)
        assertEquals(RefreshResult.SITE_UNAVAILABLE, repository.refresh(manual = false))

        clock.now = clock.now.plusMinutes(10)
        assertEquals(RefreshResult.NOT_NEEDED, repository.refresh(manual = false))

        fetcher.offline = false
        clock.now = clock.now.plusMinutes(5)
        assertEquals(RefreshResult.UPDATED, repository.refresh(manual = false))
    }

    @Test
    fun `a changed page is reported and the old lists stay`() = runBlocking {
        val fetcher = fetcher()
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)
        repository.refresh(manual = false)
        val before = repository.snapshot.first()

        fetcher.pages[sources.workingUrl] = "<table><tr><th>Ship</th></tr><tr><td>X</td></tr></table>"
        clock.now = clock.now.plusHours(5)
        assertEquals(RefreshResult.FORMAT_ERROR, repository.refresh(manual = true))

        assertEquals(before, repository.snapshot.first())
        // The site did answer: the press counts
        assertEquals(clock.now, repository.lastManualRefresh.first())
    }

    @Test
    fun `a site that ignores the page number is read once`() = runBlocking {
        val fetcher = fetcher()
        fetcher.pages[scheduledUrl(1)] = scheduledPage("A", "B")
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)

        repository.refresh(manual = false)

        assertEquals(listOf("A", "B"), repository.snapshot.first()!!.scheduled.map { it.name })
        assertEquals(3, fetcher.requested.size)
    }

    @Test
    fun `without addresses nothing is read`() = runBlocking {
        val fetcher = fetcher()
        val repository = SiteVesselRepository(VesselSources("", ""), fetcher, MemoryStore(), clock, network)

        assertEquals(RefreshResult.NOT_CONFIGURED, repository.refresh(manual = true))
        assertTrue(fetcher.requested.isEmpty())
        assertNull(repository.snapshot.first())
    }

    @Test
    fun `a phone setting that keeps the app offline is told apart`() = runBlocking {
        val fetcher = fetcher().apply { offline = true }
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)

        network.state = Connectivity.APP_BLOCKED
        assertEquals(RefreshResult.APP_BLOCKED, repository.refresh(manual = false))

        // Fixed in the settings: coming back retries at once, no waiting
        network.state = Connectivity.ONLINE
        fetcher.offline = false
        assertEquals(RefreshResult.UPDATED, repository.refresh(manual = false))
    }

    @Test
    fun `errors only a blocked app gets count as blocked`() = runBlocking {
        val blocked = object : PageFetcher {
            override suspend fun fetch(url: String): String =
                throw IOException("connect failed", Exception("socket failed: EPERM (Operation not permitted)"))
        }
        assertEquals(RefreshResult.APP_BLOCKED, SiteVesselRepository(sources, blocked, MemoryStore(), clock, network).refresh(manual = true))

        val noName = object : PageFetcher {
            override suspend fun fetch(url: String): String = throw java.net.UnknownHostException("Unable to resolve host")
        }
        assertEquals(RefreshResult.APP_BLOCKED, SiteVesselRepository(sources, noName, MemoryStore(), clock, network).refresh(manual = true))

        val refused = object : PageFetcher {
            override suspend fun fetch(url: String): String = throw SecurityException("Permission denied (missing INTERNET permission?)")
        }
        assertEquals(RefreshResult.APP_BLOCKED, SiteVesselRepository(sources, refused, MemoryStore(), clock, network).refresh(manual = true))
    }

    @Test
    fun `after a failure a retry reads even fresh lists`() = runBlocking {
        val fetcher = fetcher()
        val repository = SiteVesselRepository(sources, fetcher, MemoryStore(), clock, network)
        repository.refresh(manual = false)
        fetcher.requested.clear()

        assertEquals(RefreshResult.NOT_NEEDED, repository.refresh(manual = false))
        assertEquals(RefreshResult.UPDATED, repository.refresh(manual = false, retry = true))
    }
}
