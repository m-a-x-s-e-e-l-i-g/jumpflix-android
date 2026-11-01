// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tv.jumpflix.data.*

@Composable
internal fun ReviewsSection(itemId: String, tv: Boolean, ownReview: String, focus: FocusRequester) {
    key(itemId) {
        val context = LocalContext.current
        val repository = remember { ReviewRepository(context.applicationContext) }
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf(ReviewFeed(loading = true)) }
        var generation by remember { mutableIntStateOf(0) }
        var job by remember { mutableStateOf<Job?>(null) }
        var lastReset by remember { mutableStateOf(true) }
        fun load(reset: Boolean) {
            job?.cancel()
            val request = ++generation
            val cursor = if (reset) null else state.next
            lastReset = reset
            state = state.copy(loading = true, error = null)
            job = scope.launch {
                try {
                    val page = repository.page(itemId, cursor)
                    if (request == generation) state = state.accept(page, reset)
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) {
                    if (request == generation) state = state.copy(loading = false, error = "Reviews could not be loaded. Check your connection and try again.")
                }
            }
        }
        LaunchedEffect(ownReview) { load(true) }
        ReviewsContent(state, tv, focus, { load(lastReset) }) { load(false) }
    }
}

@Composable
internal fun ReviewsContent(state: ReviewFeed, tv: Boolean, focus: FocusRequester, retry: () -> Unit, more: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        var headingFocused by remember { mutableStateOf(false) }
        Text("Reviews", color = Ink, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.focusRequester(focus).onFocusChanged { headingFocused = it.isFocused }
                .then(if (headingFocused) Modifier.border(2.dp, Ink, RoundedCornerShape(8.dp)) else Modifier)
                .padding(8.dp).semantics { heading() }.focusable(tv))
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        state.error?.let {
            Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium)
            if (!state.loading) AppAction("Retry reviews", tv, action = retry)
        }
        if (state.loaded && !state.loading && state.error == null && state.reviews.isEmpty()) Text("No reviews yet. Be the first to share one.", color = Muted)
        state.reviews.forEach { review -> key(review.id, review.updated) { PublicReview(review, tv) } }
        if (state.next != null && state.error == null) AppAction(if (state.loading) "Loading reviews…" else "More reviews", tv, enabled = !state.loading, action = more)
    }
}

@Composable
private fun PublicReview(review: MediaReview, tv: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val date = remember(review.created) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(review.created) }
    Column(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, Ink, RoundedCornerShape(8.dp)) else Modifier)
        .padding(8.dp).focusable(tv), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(review.author, color = Ink, style = MaterialTheme.typography.titleMedium)
        Text(date + if (review.updated.isAfter(review.created.plusSeconds(1))) " · Edited" else "", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(review.body, color = Ink, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow })
        if (overflow || expanded) AppAction(if (expanded) "Show less" else "Read full review", tv) { expanded = !expanded }
    }
    HorizontalDivider(color = Outline.copy(alpha = .5f))
}
