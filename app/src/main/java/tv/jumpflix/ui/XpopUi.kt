// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import tv.jumpflix.R
import tv.jumpflix.data.CatalogItem
import tv.jumpflix.xpop.*

@Composable
internal fun XpopMark(text: String, large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.xpop_kernel), null, Modifier.size(if (large) 46.dp else 26.dp))
            Text("XP", color = Color(0xFF5C2B00), fontWeight = FontWeight.Black, fontSize = if (large) 13.sp else 8.sp)
        }
        Text(text, color = Ink, fontWeight = FontWeight.Bold, style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun XpopPanel(state: XpopState) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val summary = state.summary
        XpopMark(if (summary == null) "Your XPOP" else "${NumberFormat.getIntegerInstance().format(summary.total)} XPOP", true)
        if (summary != null) {
            listOf(Triple("Watching", summary.watching, summary.watchingPoints), Triple("Ratings", summary.ratings, summary.ratingPoints),
                Triple("Reviews", summary.reviews, summary.reviewPoints), Triple("Archive changes", summary.contributions, summary.contributionPoints)).forEach { (label, count, points) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$label · $count", color = Muted, modifier = Modifier.weight(1f))
                    Text("$points XPOP", color = Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        HorizontalDivider(color = Outline.copy(alpha = .5f))
        Text("5 per watch · 15 per rating · 25 per review\nApproved archive changes: 10 per unit", color = Muted, style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun XpopNotice(reward: XpopReward, modifier: Modifier = Modifier) {
    Surface(modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Surface, shape = RoundedCornerShape(18.dp), shadowElevation = 12.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            XpopMark("+${reward.amount} XPOP", true)
            Text(reward.label, color = Muted)
        }
    }
}

@Composable
internal fun XpopAction(text: String, tv: Boolean, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit) {
    AppAction(text, tv, primary = primary, enabled = enabled, action = action)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaAccountControls(item: CatalogItem, state: MediaAccountState, signedIn: Boolean, tv: Boolean,
    retry: () -> Unit, signIn: () -> Unit, rate: (Int) -> Unit, review: (String) -> Unit, markWatched: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = Surface)
        Text("Your activity", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (!signedIn) {
            Text("Sign in to earn XPOP, rate films, and share a review.", color = Muted)
            XpopAction("Sign in", tv, primary = true, action = signIn)
            return@Column
        }
        if (!state.loaded) {
            if (state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            else { Text(state.error, color = MaterialTheme.colorScheme.error); XpopAction("Retry account activity", tv, action = retry) }
            return@Column
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); XpopAction("Reload saved activity", tv, !state.busy, action = retry) }
        if (item.type == "movie") {
            val watched = watchKey(item.id, item.playback) in state.watched || item.id in state.watched
            XpopAction(if (watched) "Watched" else "Mark watched · +5 XPOP", tv, !watched && !state.busy, action = markWatched)
        }
        var expanded by remember(item.id, state.ownerId) { mutableStateOf(false) }
        XpopAction(if (expanded) "Hide rating & review" else "Rate & review", tv, !state.busy) { expanded = !expanded }
        if (!expanded) return@Column
        var rating by remember(item.id, state.ownerId, state.rating) { mutableIntStateOf(state.rating ?: 0) }
        Text(if (state.rating == null) "Rate this title · first rating +15 XPOP" else "Your rating: ${state.rating}/10", color = Ink, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..10).forEach { value ->
                if (tv) androidx.tv.material3.Button({ rating = value }, enabled = !state.busy,
                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (rating == value) ActionAccent else SurfaceRaised), contentPadding = PaddingValues(12.dp)) { androidx.tv.material3.Text(value.toString(), style = MaterialTheme.typography.labelLarge) }
                else OutlinedButton({ rating = value }, enabled = !state.busy, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentPadding = PaddingValues(8.dp),
                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (rating == value) ActionAccent else Color.Transparent, contentColor = Ink)) { Text(value.toString()) }
            }
        }
        XpopAction("Save rating", tv, !state.busy && rating in 1..10 && rating != state.rating, primary = true) { rate(rating) }
        var body by remember(item.id, state.ownerId, state.review) { mutableStateOf(state.review) }
        val keyboard = LocalSoftwareKeyboardController.current
        Text("Your review", color = Ink, fontWeight = FontWeight.Bold)
        Text("Reviews are public. Your first review earns 25 XPOP; editing keeps the same reward.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(body, { body = it.take(2000) }, enabled = !state.busy, label = { Text("Write a review") }, minLines = 3, maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            supportingText = { Text("${body.length}/2000") }, modifier = Modifier.fillMaxWidth())
        XpopAction(if (state.review.isBlank()) "Post review" else "Save review", tv, !state.busy && body.trim().isNotEmpty() && body.trim() != state.review, primary = true) { review(body.trim()) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
    }
}
