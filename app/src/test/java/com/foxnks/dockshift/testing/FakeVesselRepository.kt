package com.foxnks.dockshift.testing

import com.foxnks.dockshift.domain.vessels.RefreshResult
import com.foxnks.dockshift.domain.vessels.VesselRepository
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [VesselRepository] for screen tests: each refresh returns [result] and, when that
 * is [RefreshResult.UPDATED], stores [next].
 *
 * @property isConfigured whether the pages are "set up".
 */
class FakeVesselRepository(override val isConfigured: Boolean = true) : VesselRepository {

    /** The stored lists, settable by tests. */
    val stored = MutableStateFlow<VesselSnapshot?>(null)

    /** The last manual refresh, settable by tests. */
    val lastManual = MutableStateFlow<LocalDateTime?>(null)

    /** What the next refreshes return. */
    var result = RefreshResult.UPDATED

    /** What an [RefreshResult.UPDATED] refresh stores. */
    var next: VesselSnapshot? = null

    /** Every refresh asked for: true for manual. */
    val calls = mutableListOf<Boolean>()

    override val snapshot: Flow<VesselSnapshot?> = stored

    override val lastManualRefresh: Flow<LocalDateTime?> = lastManual

    override suspend fun refresh(manual: Boolean): RefreshResult {
        calls += manual
        if (result == RefreshResult.UPDATED) stored.value = next
        return result
    }
}
