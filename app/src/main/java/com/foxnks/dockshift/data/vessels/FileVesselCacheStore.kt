package com.foxnks.dockshift.data.vessels

import android.content.Context
import com.foxnks.dockshift.domain.vessels.ScheduledVessel
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import com.foxnks.dockshift.domain.vessels.WorkingVessel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [VesselCacheStore] in a JSON file in the app's private storage.
 *
 * The lists are temporary (replaced at every refresh), so they live in a file rather
 * than in the database: no table, no migration, and they are not part of backups.
 * An unreadable file (e.g. from a future version) is simply treated as empty.
 *
 * @param context the application context, for the private files folder.
 */
class FileVesselCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : VesselCacheStore {

    private val json = Json { ignoreUnknownKeys = true }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun read(): VesselCache? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(CacheJson.serializer(), file.readText()).toDomain() }.getOrNull()
    }

    override suspend fun write(cache: VesselCache) = withContext(Dispatchers.IO) {
        // Written beside, then renamed: a crash mid-write never leaves half a file
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(CacheJson.serializer(), cache.toJson()))
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Cannot replace $FILE_NAME")
        }
    }

    private companion object {
        const val FILE_NAME = "vessels.json"
    }
}

/** The file. Times are ISO texts ("2026-10-14T01:30"). */
@Serializable
private data class CacheJson(
    val fetchedAt: String? = null,
    val working: List<WorkingJson> = emptyList(),
    val scheduled: List<ScheduledJson> = emptyList(),
    val lastManualRefresh: String? = null,
)

/** One vessel being worked. */
@Serializable
private data class WorkingJson(
    val name: String,
    val callSign: String = "",
    val arrived: String? = null,
    val departs: String? = null,
    val completion: Int = 0,
)

/** One expected vessel. */
@Serializable
private data class ScheduledJson(
    val name: String,
    val operator: String = "",
    val callSign: String = "",
    val inVoyage: String = "",
    val outVoyage: String = "",
    val lane: String = "",
    val berths: String? = null,
    val departs: String? = null,
)

/** Domain → file. */
private fun VesselCache.toJson() = CacheJson(
    fetchedAt = snapshot?.fetchedAt?.toString(),
    working = snapshot?.working.orEmpty().map {
        WorkingJson(it.name, it.callSign, it.arrived?.toString(), it.departs?.toString(), it.completion)
    },
    scheduled = snapshot?.scheduled.orEmpty().map {
        ScheduledJson(it.name, it.operator, it.callSign, it.inVoyage, it.outVoyage, it.lane, it.berths?.toString(), it.departs?.toString())
    },
    lastManualRefresh = lastManualRefresh?.toString(),
)

/** File → domain. */
private fun CacheJson.toDomain() = VesselCache(
    snapshot = fetchedAt?.let { fetched ->
        VesselSnapshot(
            fetchedAt = LocalDateTime.parse(fetched),
            working = working.map {
                WorkingVessel(it.name, it.callSign, it.arrived?.let { time -> LocalDateTime.parse(time) }, it.departs?.let { time -> LocalDateTime.parse(time) }, it.completion)
            },
            scheduled = scheduled.map {
                ScheduledVessel(
                    it.name, it.operator, it.callSign, it.inVoyage, it.outVoyage, it.lane,
                    it.berths?.let { time -> LocalDateTime.parse(time) }, it.departs?.let { time -> LocalDateTime.parse(time) },
                )
            },
        )
    },
    lastManualRefresh = lastManualRefresh?.let { time -> LocalDateTime.parse(time) },
)
