package no.srrlsm.speechsplit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.graphics.vector.ImageVector
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.core.FeedbackKind
import no.srrlsm.speechsplit.core.NotesState
import no.srrlsm.speechsplit.core.PRIVACY_URL
import no.srrlsm.speechsplit.core.RELEASES_URL
import no.srrlsm.speechsplit.core.ReleaseInfo
import no.srrlsm.speechsplit.core.UpdateState
import no.srrlsm.speechsplit.core.WEBSITE_URL
import no.srrlsm.speechsplit.core.plainReleaseNotes
import no.srrlsm.speechsplit.ui.theme.AppTheme

/** Slim banner at the top of the library when a newer version exists. Tap it for details. */
@Composable
fun UpdateBanner(app: AppController, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val c = AppTheme.colors
    val release = (app.updateState as? UpdateState.Available)?.release
    var showDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = release != null && !app.updateBannerHidden,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            onClick = { showDialog = true },
            color = c.adjusted.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, c.adjusted.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = c.adjusted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    s.updateAvailable(release?.version ?: ""),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDialog = true }) { Text(s.updateNow, color = c.adjusted, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showDialog && release != null) {
        UpdateDialog(app, release, onDismiss = { showDialog = false })
    }
}

/** What's new + "Update now". */
@Composable
fun UpdateDialog(app: AppController, release: ReleaseInfo, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val notes = remember(release) { plainReleaseNotes(release.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text(s.updateAvailable(release.version)) },
        text = {
            Column {
                Text(s.versionLabel(app.appVersion) + "  →  " + release.version, color = AppTheme.colors.textSub, fontSize = 14.sp)
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(s.whatsNew.uppercase())
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        Text(notes, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                ui.installUpdate(release)
            }) { Text(s.updateNow) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                app.hideUpdateBanner()
            }) { Text(s.later, color = AppTheme.colors.textMain) }
        },
    )
}

/** Version number, update check, patch notes, feedback and links, at the bottom of Settings. */
@Composable
fun AboutContent(app: AppController) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    val state = app.updateState
    var showDialog by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }

    Column {
        Text("Speech Split", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(s.versionLabel(app.appVersion), fontSize = 14.sp, color = c.textSub)
    }
    Spacer(Modifier.height(12.dp))

    // Status line
    val (statusText, statusColor) = when (state) {
        UpdateState.Checking -> s.checking to c.textSub
        UpdateState.UpToDate -> s.upToDate to c.green
        UpdateState.Failed -> s.updateFailed to c.textSub
        is UpdateState.Available -> s.updateAvailable(state.release.version) to c.adjusted
        UpdateState.Idle -> null to c.textSub
    }
    if (statusText != null) {
        Text(statusText, fontSize = 14.sp, color = statusColor, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
    }

    if (state is UpdateState.Available) {
        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.updateNow)
        }
    } else {
        OutlinedButton(
            onClick = { app.checkForUpdates() },
            enabled = state != UpdateState.Checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state == UpdateState.Checking) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = c.textMain)
            }
            Spacer(Modifier.width(8.dp))
            Text(s.checkUpdates, color = c.textMain)
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 14.dp))
    SwitchSetting(s.autoCheckTitle, s.autoCheckDesc, app.settings.autoUpdateCheck) { v ->
        app.updateSettings { it.copy(autoUpdateCheck = v) }
    }

    HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 4.dp))
    LinkRow(Icons.Default.NewReleases, s.patchNotes) { showNotes = true }
    LinkRow(Icons.Default.Feedback, s.feedback) { showFeedback = true }
    LinkRow(Icons.Default.Language, s.website, external = true) { ui.openUrl(WEBSITE_URL) }
    LinkRow(Icons.Default.Code, s.viewOnGitHub, external = true) { ui.openUrl(RELEASES_URL) }
    LinkRow(Icons.Default.Shield, s.privacy, external = true) { ui.openUrl(PRIVACY_URL) }
    Spacer(Modifier.height(10.dp))
    Text(s.madeBy, fontSize = 13.sp, color = c.textSub)

    if (showDialog && state is UpdateState.Available) {
        UpdateDialog(app, state.release, onDismiss = { showDialog = false })
    }
    if (showNotes) PatchNotesDialog(app, onDismiss = { showNotes = false })
    if (showFeedback) FeedbackDialog(app, onDismiss = { showFeedback = false })
}

@Composable
private fun LinkRow(icon: ImageVector, text: String, external: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.textSub, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(
                if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = c.textSub,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The notes of the latest releases, straight from GitHub, so they never need updating in the app. */
@Composable
fun PatchNotesDialog(app: AppController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    LaunchedEffect(Unit) { app.loadReleaseNotes() }
    val state = app.notesState

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NewReleases, contentDescription = null) },
        title = { Text(s.patchNotes) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                when (state) {
                    NotesState.Idle, NotesState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.notesLoading, color = c.textSub)
                    }
                    NotesState.Failed -> Text(s.notesFailed, color = c.textSub)
                    is NotesState.Loaded -> state.releases.forEachIndexed { i, release ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.versionLabel(release.version), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (release.version == app.appVersion) {
                                Spacer(Modifier.width(8.dp))
                                Surface(color = c.surfaceHigh, shape = RoundedCornerShape(50)) {
                                    Text(s.yourVersion, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                        }
                        if (release.date.isNotBlank()) Text(release.date, fontSize = 12.sp, color = c.textSub)
                        Spacer(Modifier.height(6.dp))
                        Text(plainReleaseNotes(release.notes).ifBlank { s.noNotes }, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ui.openUrl(RELEASES_URL) }) { Text(s.viewOnGitHub, color = c.textMain) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.close, color = c.textMain) }
        },
    )
}

/** Bug, idea or something else: opens the email app with a short template (incl. version). */
@Composable
fun FeedbackDialog(app: AppController, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val ui = LocalUiActions.current
    val c = AppTheme.colors
    fun send(kind: FeedbackKind) {
        val (to, subject, body) = app.feedbackMail(kind)
        onDismiss()
        ui.sendEmail(to, subject, body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Feedback, contentDescription = null) },
        title = { Text(s.feedback) },
        text = {
            Column {
                Text(s.feedbackText, fontSize = 14.sp, color = c.textSub)
                Spacer(Modifier.height(12.dp))
                listOf(
                    Triple(FeedbackKind.BUG, Icons.Default.BugReport, s.feedbackBug),
                    Triple(FeedbackKind.IDEA, Icons.Default.Lightbulb, s.feedbackIdea),
                    Triple(FeedbackKind.OTHER, Icons.Default.ChatBubbleOutline, s.feedbackOther),
                ).forEach { (kind, icon, label) ->
                    OutlinedButton(onClick = { send(kind) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Icon(icon, contentDescription = null, tint = c.textMain, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = c.textMain, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = c.textMain) } },
    )
}
