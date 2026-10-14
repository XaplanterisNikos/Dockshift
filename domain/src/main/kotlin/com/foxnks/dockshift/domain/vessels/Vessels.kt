package com.foxnks.dockshift.domain.vessels

import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

/**
 * A vessel being worked at the terminal now.
 *
 * @property name the vessel's name, e.g. "UAFL RACER".
 * @property callSign its radio call sign, e.g. "D5UH6"; empty when not given.
 * @property arrived actual time of arrival (ATA), or null when not given.
 * @property departs estimated time of departure (ETD), or null when not given.
 * @property completion how much of the work is done, 0–100.
 */
data class WorkingVessel(
    val name: String,
    val callSign: String,
    val arrived: LocalDateTime?,
    val departs: LocalDateTime?,
    val completion: Int,
)

/**
 * A vessel expected at the terminal.
 *
 * @property name the vessel's name.
 * @property operator the shipping line, e.g. "COSCO"; empty when not given.
 * @property callSign its radio call sign; empty when not given.
 * @property inVoyage the inbound voyage number; empty when not given.
 * @property outVoyage the outbound voyage number; empty when not given.
 * @property lane the service (line) it sails on; empty when not given.
 * @property berths estimated time of berthing (ETB), or null when not given.
 * @property departs estimated time of departure (ETD), or null when not given.
 */
data class ScheduledVessel(
    val name: String,
    val operator: String,
    val callSign: String,
    val inVoyage: String,
    val outVoyage: String,
    val lane: String,
    val berths: LocalDateTime?,
    val departs: LocalDateTime?,
)

/**
 * The vessel lists as last read from the terminal's site.
 *
 * @property fetchedAt when they were read.
 * @property working the vessels being worked, by name.
 * @property scheduled the expected vessels, by berthing time.
 */
data class VesselSnapshot(
    val fetchedAt: LocalDateTime,
    val working: List<WorkingVessel>,
    val scheduled: List<ScheduledVessel>,
)

/** How a refresh ended. */
enum class RefreshResult {
    /** New lists were read and stored. */
    UPDATED,

    /** Nothing was read: the lists are fresh enough (automatic refresh only). */
    NOT_NEEDED,

    /** The manual refresh is still locked (see [VesselRefresh.MANUAL_COOLDOWN]). */
    COOLING_DOWN,

    /** The site's addresses are not set in this build. */
    NOT_CONFIGURED,

    /** The phone has no internet connection. The old lists stay. */
    NO_CONNECTION,

    /**
     * The phone is online but this app may not use the connection: a phone setting
     * limits its data (e.g. mobile data or Wi-Fi turned off for the app). The user can
     * fix it in the app's settings. The old lists stay.
     */
    APP_BLOCKED,

    /** The phone and the app are online, but the site did not answer (down or slow). The old lists stay. */
    SITE_UNAVAILABLE,

    /** The site answered, but its pages could not be read: their layout changed. The old lists stay. */
    FORMAT_ERROR,
    ;

    /** True for the results that mean the lists could not be read. */
    val isFailure: Boolean get() = this in setOf(NO_CONNECTION, APP_BLOCKED, SITE_UNAVAILABLE, FORMAT_ERROR)
}

/**
 * When the vessel lists are read again.
 *
 * Automatically when a screen showing them opens and they are older than
 * [STALE_AFTER]; by hand at most once per [MANUAL_COOLDOWN], to go easy on the site.
 */
object VesselRefresh {

    /** Lists older than this are read again when a screen opens. */
    val STALE_AFTER: Duration = Duration.ofHours(4)

    /** After a manual refresh, the button stays locked this long. */
    val MANUAL_COOLDOWN: Duration = Duration.ofHours(1)

    /**
     * @param snapshot the stored lists, or null when never read.
     * @param now the current time.
     * @return true when the lists should be read again.
     */
    fun isStale(snapshot: VesselSnapshot?, now: LocalDateTime): Boolean =
        snapshot == null || !snapshot.fetchedAt.plus(STALE_AFTER).isAfter(now)

    /**
     * @param lastManual the time of the last manual refresh, or null.
     * @param now the current time.
     * @return when the button unlocks, or null when it can be used now.
     */
    fun manualAvailableAt(lastManual: LocalDateTime?, now: LocalDateTime): LocalDateTime? {
        val unlock = lastManual?.plus(MANUAL_COOLDOWN) ?: return null
        return if (unlock.isAfter(now)) unlock else null
    }
}

/**
 * Reads the vessel lists from the terminal's site and keeps the last ones.
 * Defined here, implemented by the :app module.
 */
interface VesselRepository {

    /** True when the site's addresses are set in this build. */
    val isConfigured: Boolean

    /** The stored lists, emitted again after every refresh; null until first read. */
    val snapshot: Flow<VesselSnapshot?>

    /** The time of the last manual refresh, for the button's lock; null if never. */
    val lastManualRefresh: Flow<LocalDateTime?>

    /**
     * Reads the lists again, unless that is not needed or allowed.
     *
     * @param manual true for the refresh button (locked for an hour afterwards),
     * false for the automatic refresh (only when the lists are stale).
     * @param retry for an automatic refresh: read even fresh lists, because the last
     * attempt failed (e.g. the user comes back after allowing the app's data).
     */
    suspend fun refresh(manual: Boolean, retry: Boolean = false): RefreshResult
}
