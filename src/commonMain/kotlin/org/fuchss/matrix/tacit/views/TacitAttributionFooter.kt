package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.fuchss.matrix.tacit.tacitTextMuted

@Composable
internal fun TacitAttributionFooter(
    modifier: Modifier = Modifier.fillMaxWidth(),
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
    ) {
        Text(
            text = "Built by ",
            color = tacitTextMuted,
            style = textStyle,
            maxLines = maxLines,
            overflow = overflow,
        )
        Text(
            text = "@dfuchss",
            color = MaterialTheme.colorScheme.primary,
            style = textStyle,
            textDecoration = TextDecoration.Underline,
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/dfuchss")
            },
        )
        Text(
            text = " · Powered by ",
            color = tacitTextMuted,
            style = textStyle,
            maxLines = maxLines,
            overflow = overflow,
        )
        Text(
            text = "trixnity messenger",
            color = MaterialTheme.colorScheme.primary,
            style = textStyle,
            textDecoration = TextDecoration.Underline,
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger")
            },
        )
        Text(
            text = " · Alpha stage: do not use production accounts.",
            color = tacitTextMuted,
            style = textStyle,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
