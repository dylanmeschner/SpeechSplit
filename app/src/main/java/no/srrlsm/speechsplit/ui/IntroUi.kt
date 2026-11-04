package no.srrlsm.speechsplit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import no.srrlsm.speechsplit.core.AppController
import no.srrlsm.speechsplit.ui.theme.AppTheme

// ============================================================================
// INTRODUCTION (first start) and WHAT'S NEW (first start after an update)
// The introduction can be opened again from Settings → About.
// ============================================================================

/** A few cards to swipe through. Skipping or closing counts as seen. */
@Composable
fun TutorialDialog(onDone: () -> Unit) {
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    val cards: List<Triple<ImageVector, String, String>> = listOf(
        Triple(Icons.Default.Timer, s3.tut1Title, s3.tut1Text),
        Triple(Icons.Default.SkipNext, s3.tut2Title, s3.tut2Text),
        Triple(Icons.Default.School, s3.tut3Title, s3.tut3Text),
        Triple(Icons.Default.Groups, s3.tut4Title, s3.tut4Text),
        Triple(Icons.Default.Folder, s3.tut5Title, s3.tut5Text),
    )
    val pager = rememberPagerState(pageCount = { cards.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == cards.size - 1

    Dialog(onDismissRequest = onDone) {
        Surface(shape = RoundedCornerShape(28.dp), color = c.surface, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = LocalStrings.current.close, tint = c.textSub)
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    val (icon, title, text) = cards[page]
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(84.dp).clip(CircleShape).background(c.surfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(text, fontSize = 15.sp, color = c.textSub, textAlign = TextAlign.Center, modifier = Modifier.heightIn(min = 88.dp))
                    }
                }
                // Dots: where you are, and tap to jump
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(cards.size) { i ->
                        val on = i == pager.currentPage
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = if (on) 20.dp else 8.dp, height = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (on) c.accent else c.divider)
                                .clickable { scope.launch { pager.animateScrollToPage(i) } },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!last) TextButton(onClick = onDone) { Text(s3.tutSkip, color = c.textMain) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(if (last) s3.tutDone else s3.tutNext)
                    }
                }
            }
        }
    }
}

/** What's new in this version, with a way to all patch notes. */
@Composable
fun WhatsNewDialog(app: AppController, onDone: () -> Unit) {
    val s3 = LocalStrings3.current
    val c = AppTheme.colors
    var showNotes by remember { mutableStateOf(false) }
    if (showNotes) {
        PatchNotesDialog(app, onDismiss = { showNotes = false; onDone() })
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
        title = { ClosableTitle(s3.whatsNewTitle(app.appVersion), onDone) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                s3.whatsNewItems.split("\n").forEach { item ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text("•", color = c.textSub, modifier = Modifier.width(16.dp))
                        Text(item, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(s3.ok) } },
        dismissButton = { TextButton(onClick = { showNotes = true }) { Text(s3.allPatchNotes, color = c.textMain) } },
    )
}
