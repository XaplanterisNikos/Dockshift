package com.foxnks.dockshift.data.vessels

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [NetworkStatus] from Android's ConnectivityManager (needs ACCESS_NETWORK_STATE, a
 * permission granted at install like INTERNET).
 *
 * The phone counts as online when any of its networks reaches the internet (Android
 * "validated" it). For this app, Android hides the default network or marks it
 * BLOCKED when a phone setting keeps the app off it, e.g. Samsung's "Mobile data"
 * switch per app: online phone + blocked default network = [Connectivity.APP_BLOCKED].
 *
 * @param context the application context.
 */
class AndroidNetworkStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkStatus {

    @Suppress("DEPRECATION") // allNetworks and NetworkInfo: the only views that still show a blocked network
    override fun check(): Connectivity {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return Connectivity.ONLINE
        val phoneOnline = manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)?.let { caps ->
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } == true
        }
        if (!phoneOnline) return Connectivity.OFFLINE
        // getActiveNetwork() is null "when the default network is blocked" for this app
        val blocked = manager.activeNetwork == null ||
            manager.activeNetworkInfo?.detailedState == NetworkInfo.DetailedState.BLOCKED
        return if (blocked) Connectivity.APP_BLOCKED else Connectivity.ONLINE
    }
}
