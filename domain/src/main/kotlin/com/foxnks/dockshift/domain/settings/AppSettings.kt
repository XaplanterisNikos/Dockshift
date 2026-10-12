package com.foxnks.dockshift.domain.settings

/**
 * The user's app-wide settings.
 *
 * Default values are the product decisions themselves, so they live here in the
 * domain and not in the storage code: a fresh install, or a setting that was never
 * saved, always gets exactly these values.
 *
 * @property rotationEnabled whether the rotating schedule ("Κυλιόμενο πρόγραμμα")
 * predicts shifts. Off by default: not every operator works the rotation (some work
 * only mornings, or mornings/afternoons), so the app computes nothing until the
 * user turns it on.
 */
data class AppSettings(
    val rotationEnabled: Boolean = false,
)
