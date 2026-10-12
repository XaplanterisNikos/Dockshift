package com.foxnks.dockshift.ui.home

/**
 * Everything the home screen needs to draw itself, and nothing else.
 *
 * A sealed interface makes every possible state explicit, so the screen cannot
 * forget to handle one (the `when` over it must be exhaustive).
 */
sealed interface HomeUiState {

    /** Settings are still being read from storage (the first instant after launch). */
    data object Loading : HomeUiState

    /**
     * Settings are loaded.
     *
     * @property rotationEnabled whether the rotating schedule setting is on.
     */
    data class Ready(val rotationEnabled: Boolean) : HomeUiState
}
