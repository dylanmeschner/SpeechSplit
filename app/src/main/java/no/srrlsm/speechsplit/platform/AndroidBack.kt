package no.srrlsm.speechsplit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** System back button/gesture (Android). The desktop app has its own version of this function. */
@Composable
fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)
