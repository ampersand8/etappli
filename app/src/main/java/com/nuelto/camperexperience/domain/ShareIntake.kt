package com.nuelto.camperexperience.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a shared place waits between arriving as an Intent and reaching the NavHost. It
 * lives on the container rather than the Activity because in Firebase mode with nobody
 * signed in there is no NavHost yet — the share has to outlive the sign-in screen. One
 * slot: once the chooser is on the back stack the place rides its route arguments.
 */
class ShareIntake {
    private val _pending = MutableStateFlow<SharedPlace?>(null)
    val pending: StateFlow<SharedPlace?> = _pending

    fun offer(place: SharedPlace?) {
        _pending.value = place
    }

    fun consume() {
        _pending.value = null
    }
}
