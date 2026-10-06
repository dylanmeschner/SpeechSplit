package no.srrlsm.speechsplit.ui

import androidx.compose.runtime.Composable

/**
 * Desktop version of the system back button: a PC has none, and every screen
 * already has its own back/close button, so this does nothing.
 */
@Composable
fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
}
