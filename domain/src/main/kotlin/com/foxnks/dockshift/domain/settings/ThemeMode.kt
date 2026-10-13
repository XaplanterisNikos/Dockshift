package com.foxnks.dockshift.domain.settings

/** Light/dark appearance chosen by the user in Settings. */
enum class ThemeMode {
    /** Follow the phone's own light/dark setting. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark. */
    DARK,
}
