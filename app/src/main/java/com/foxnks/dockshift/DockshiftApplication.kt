package com.foxnks.dockshift

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class of Dockshift.
 *
 * [HiltAndroidApp] makes Hilt generate the app-wide dependency container
 * (the SingletonComponent) and attach it to this process. It must be declared
 * in the manifest (`android:name`).
 */
@HiltAndroidApp
class DockshiftApplication : Application()
