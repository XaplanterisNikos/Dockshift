/**
 * Top-level build file.
 *
 * Plugins are only declared here (`apply false`) so that every module uses
 * the same versions from the version catalog; each module applies what it needs.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
