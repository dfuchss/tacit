package org.fuchss.matrix.tacit.views.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.connect2x.trixnity.messenger.compose.view.common.ErrorView
import de.connect2x.trixnity.messenger.compose.view.common.Header
import de.connect2x.trixnity.messenger.compose.view.common.HeaderBackButtonType
import de.connect2x.trixnity.messenger.compose.view.theme.components
import org.fuchss.matrix.tacit.views.room.timeline.WithTooltips
import org.fuchss.matrix.tacit.views.room.timeline.WithoutTooltips

/**
 * Upstream's [Header] without the "Back"/"Close" tooltip on its arrow, which only repeats the obvious.
 *
 * The tooltip is hard-wired in upstream's header, so the whole header runs without tooltips and the
 * [additionalButtons] get the regular ones back.
 */
@Composable
internal fun TacitHeader(
    onBack: () -> Unit,
    title: String,
    backButtonType: HeaderBackButtonType = HeaderBackButtonType.BACK,
    additionalButtons: @Composable (() -> Unit)? = null,
) {
    val styles = MaterialTheme.components
    WithoutTooltips {
        Header(
            onBack = onBack,
            title = title,
            backButtonType = backButtonType,
            additionalButtons = additionalButtons?.let { buttons -> { WithTooltips(styles, buttons) } },
        )
    }
}

/** Upstream's `ExtrasPaneHeader` built on [TacitHeader]. */
@Composable
internal fun TacitExtrasPaneHeader(
    title: String,
    error: String?,
    onBack: () -> Unit,
    backButtonType: HeaderBackButtonType,
    additionalButtons: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column {
            TacitHeader(onBack, title, backButtonType, additionalButtons)
            if (error != null) ErrorView(error)
            content()
        }
    }
}
