package com.foxnks.dockshift.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** The author's public links and his other app, in one place. */
object AboutLinks {

    /** The author's info page on GitHub (his GitHub Pages repository). */
    const val GITHUB = "https://github.com/XaplanterisNikos/xaplanterisnikos.github.io"

    /** This app's public source code (MIT). */
    const val SOURCE = "https://github.com/XaplanterisNikos/Dockshift"

    /** The author's LinkedIn profile. */
    const val LINKEDIN = "https://www.linkedin.com/in/nick-xaplanteris"

    /** The author's email. */
    const val EMAIL = "foxnks@gmail.com"

    /** Package name of the author's other app on Google Play. */
    const val OTHER_APP_PACKAGE = "com.foxnks.xeiristisexamquiz"

    /** Its Google Play page in a browser; the campaign tag tells the store where visitors came from. */
    const val OTHER_APP_WEB = "https://play.google.com/store/apps/details?id=$OTHER_APP_PACKAGE&pcampaignid=web_share"
}

/**
 * Opens [url] in whatever app handles it (browser, LinkedIn, GitHub...). A phone with
 * no such app is rare; then nothing happens.
 */
fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Unit
    }
}

/** Opens a new email to [address] in the phone's mail app. */
fun Context.sendEmail(address: String) {
    try {
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Unit
    }
}

/**
 * Opens an app's page in the Google Play app, or in the browser when the phone has no
 * Play Store.
 *
 * @param packageName the app.
 * @param webUrl its page on the web, the fallback.
 */
fun Context.openStorePage(packageName: String, webUrl: String) {
    val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        .setPackage("com.android.vending")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(store)
    } catch (e: ActivityNotFoundException) {
        openLink(webUrl)
    }
}
