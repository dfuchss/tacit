package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.fuchss.matrix.tacit.tacit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** How long the target of a jump from a reply quote stays lit. */
internal val TacitJumpHighlightDuration = 1_600.milliseconds

/**
 * The jump-to-reply highlight: a scroll that follows a press on a reply quote ([replyJumpSignal]) is a
 * jump to the quoted message, and that row lights up for [duration] so the eye finds it (see
 * [LocalTacitJumpHighlightKey]). A new jump restarts the timer, even to the same row.
 */
@Stable
internal class JumpHighlightState(val duration: Duration = TacitJumpHighlightDuration) {
    val replyJumpSignal = TacitReplyJumpSignal()

    /** The key of the highlighted row, if any. */
    var key by mutableStateOf<String?>(null)
        private set

    private var token by mutableIntStateOf(0)

    fun highlight(key: String) {
        this.key = key
        token++
    }

    /** Clears the highlight [duration] after the latest [highlight]; runs for as long as the timeline is shown. */
    suspend fun expire() {
        snapshotFlow { token }.collectLatest {
            if (key != null) {
                delay(duration)
                key = null
            }
        }
    }
}

@Composable
internal fun rememberJumpHighlightState(): JumpHighlightState {
    val state = remember { JumpHighlightState() }
    LaunchedEffect(state) { state.expire() }
    return state
}

/**
 * The accent tint a row draws while it is the target of a jump ([isJumpTarget]): it fades in from
 * transparent (150 ms) and out again afterwards (700 ms). `null` while there is nothing to draw, so
 * only a row that is (or just was) the target pays for the animation and the extra layer.
 *
 * The animation state lives behind the early return: it is created -- at [Color.Transparent] -- when
 * a row becomes the target, and dropped together with the layer once the fade-out has finished.
 */
@Composable
internal fun rememberJumpHighlightTint(isJumpTarget: Boolean): Color? {
    var active by remember { mutableStateOf(false) }
    if (!isJumpTarget && !active) return null
    val accent = MaterialTheme.tacit.accent.copy(alpha = 0.18f)
    val tint = remember { Animatable(Color.Transparent) }
    LaunchedEffect(isJumpTarget, accent) {
        if (isJumpTarget) {
            active = true
            tint.animateTo(accent, tween(durationMillis = 150))
        } else {
            tint.animateTo(Color.Transparent, tween(durationMillis = 700))
            active = false
        }
    }
    return tint.value
}
