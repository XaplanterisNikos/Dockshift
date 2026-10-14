package com.foxnks.dockshift.domain.vessels

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the vessel pages. The pages here are made up in the shape of the real ones
 * (a server-made table with sortable column titles), with invented vessels and no
 * site names: nothing of the real site is committed.
 */
class VesselPagesTest {

    /** A table wrapped in page clutter, like the real site's. */
    private fun page(table: String) = """
        <!DOCTYPE html><html><head><title>Vessels</title></head><body>
        <nav><ul><li><a href="/">Home</a></li></ul></nav>
        <form><input type="text" name="title"><button>Search</button></form>
        $table
        <footer><p>Footer text &copy; 2026</p></footer>
        </body></html>
    """.trimIndent()

    private val workingPage = page(
        """
        <table class="views-table cols-5">
          <thead>
            <tr>
              <th id="view-title" class="views-field"><a href="?sort=asc&amp;order=Vessel%20Name" title="sort by Vessel Name">Vessel Name</a></th>
              <th class="views-field"><a href="?order=Call%20Sign">Call Sign</a></th>
              <th class="views-field"><a href="?order=Ata">Ata</a></th>
              <th class="views-field"><a href="?order=Etd">Etd</a></th>
              <th class="views-field"><a href="?order=Completion">Completion(%)</a></th>
            </tr>
          </thead>
          <tbody>
            <tr class="odd">
              <td class="views-field views-field-title">  SEA&nbsp;BREEZE  </td>
              <td class="views-field">ABC1</td>
              <td class="views-field"><time datetime="2026-10-12T17:10:00Z">12/10/2026 17:10</time></td>
              <td class="views-field">14/10/2026 14:40</td>
              <td class="views-field">12</td>
            </tr>
            <tr class="even">
              <td>ALPHA STAR</td><td>XYZ9</td><td>09/10/2026 22:30</td><td></td><td>96.6</td>
            </tr>
          </tbody>
        </table>
        """,
    )

    @Test
    fun `working vessels are read by column title, sorted by name`() {
        val vessels = VesselPages.parseWorking(workingPage)

        assertEquals(2, vessels.size)
        assertEquals(
            WorkingVessel("ALPHA STAR", "XYZ9", LocalDateTime.of(2026, 10, 9, 22, 30), null, 97),
            vessels[0],
        )
        assertEquals(
            WorkingVessel("SEA BREEZE", "ABC1", LocalDateTime.of(2026, 10, 12, 17, 10), LocalDateTime.of(2026, 10, 14, 14, 40), 12),
            vessels[1],
        )
    }

    @Test
    fun `a reordered table still reads correctly`() {
        val html = page(
            """
            <table><tr><th>Completion(%)</th><th>Etd</th><th>Vessel Name</th><th>Ata</th></tr>
            <tr><td>50%</td><td>14/10/2026 10:00</td><td>GAMMA</td><td>13/10/2026 08:00</td></tr></table>
            """,
        )
        val vessel = VesselPages.parseWorking(html).single()
        assertEquals("GAMMA", vessel.name)
        assertEquals(50, vessel.completion)
        assertEquals("", vessel.callSign)
    }

    @Test
    fun `no table means no vessels`() {
        assertTrue(VesselPages.parseWorking(page("<div class=\"view-empty\">No vessels.</div>")).isEmpty())
    }

    @Test
    fun `a message row is not a vessel`() {
        val html = page(
            """
            <table><thead><tr><th>Vessel Name</th><th>Ata</th><th>Etd</th><th>Completion(%)</th></tr></thead>
            <tbody><tr><td colspan="4">No results</td></tr></tbody></table>
            """,
        )
        assertTrue(VesselPages.parseWorking(html).isEmpty())
    }

    @Test(expected = VesselPageException::class)
    fun `a changed layout is reported, never misread`() {
        VesselPages.parseWorking(page("<table><tr><th>Ship</th><th>Progress</th></tr><tr><td>A</td><td>1</td></tr></table>"))
    }

    @Test
    fun `percentages stay within 0 to 100`() {
        val html = page(
            """
            <table><tr><th>Vessel Name</th><th>Ata</th><th>Etd</th><th>Completion(%)</th></tr>
            <tr><td>A</td><td></td><td></td><td>140</td></tr>
            <tr><td>B</td><td></td><td></td><td>n/a</td></tr></table>
            """,
        )
        assertEquals(listOf(100, 0), VesselPages.parseWorking(html).map { it.completion })
    }

    private val scheduledPage = page(
        """
        <table class="views-table">
          <thead><tr>
            <th>Vessel Name</th><th>Vessel Operator</th><th>Call Sign</th><th>In Voyage</th>
            <th>Out Voyage</th><th>Out Lane</th><th><a href="?order=etb&amp;sort=asc">etb</a></th><th>etd</th>
          </tr></thead>
          <tbody>
            <tr><td>DELTA ONE</td><td>LINE A &amp; B</td><td>DD11</td><td>624W</td><td>625E</td><td>LN1</td><td>19/10/2026 22:45</td><td>21/10/2026 22:30</td></tr>
            <tr><td>EPSILON</td><td>LINE C</td><td>EE22</td><td>100EB</td><td>100EB</td><td>LN2</td><td>15/10/2026 23:24</td><td>17/10/2026 07:00</td></tr>
          </tbody>
        </table>
        """,
    )

    @Test
    fun `scheduled vessels keep every field`() {
        val vessels = VesselPages.parseScheduled(scheduledPage)
        assertEquals(
            ScheduledVessel(
                name = "DELTA ONE",
                operator = "LINE A & B",
                callSign = "DD11",
                inVoyage = "624W",
                outVoyage = "625E",
                lane = "LN1",
                berths = LocalDateTime.of(2026, 10, 19, 22, 45),
                departs = LocalDateTime.of(2026, 10, 21, 22, 30),
            ),
            vessels[0],
        )
        assertEquals(2, vessels.size)
    }

    @Test
    fun `upcoming drops gone and repeated vessels and sorts by berthing`() {
        val now = LocalDateTime.of(2026, 10, 16, 12, 0)
        val read = VesselPages.parseScheduled(scheduledPage)
        val gone = read[1].copy(name = "GONE", departs = LocalDateTime.of(2026, 10, 16, 11, 59))
        val noTimes = read[0].copy(name = "NO TIMES", berths = null, departs = null)

        val upcoming = VesselPages.upcoming(read + read + gone + noTimes, now)

        assertEquals(listOf("EPSILON", "DELTA ONE", "NO TIMES"), upcoming.map { it.name })
    }

    @Test
    fun `entities and inner tags become plain text`() {
        val html = page(
            """
            <table><tr><th>Vessel Name</th><th>Ata</th><th>Etd</th><th>Completion(%)</th></tr>
            <tr><td><a href="/v/1"><span>MSC&#32;ZETA</span></a> &#x26; CO</td><td></td><td></td><td>1</td></tr></table>
            """,
        )
        assertEquals("MSC ZETA & CO", VesselPages.parseWorking(html).single().name)
    }

    @Test
    fun `refresh timing`() {
        val now = LocalDateTime.of(2026, 10, 14, 12, 0)
        assertTrue(VesselRefresh.isStale(null, now))
        assertFalse(VesselRefresh.isStale(VesselSnapshot(now.minusHours(3), emptyList(), emptyList()), now))
        assertTrue(VesselRefresh.isStale(VesselSnapshot(now.minusHours(4), emptyList(), emptyList()), now))

        assertNull(VesselRefresh.manualAvailableAt(null, now))
        assertEquals(now.plusMinutes(42), VesselRefresh.manualAvailableAt(now.minusMinutes(18), now))
        assertNull(VesselRefresh.manualAvailableAt(now.minusHours(1), now))
    }
}
