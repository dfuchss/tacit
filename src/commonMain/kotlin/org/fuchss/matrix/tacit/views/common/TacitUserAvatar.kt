package org.fuchss.matrix.tacit.views.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.util.avatarSize

/**
 * [ThemedUserAvatar] with the generated initials centred in the circle at every size.
 *
 * Upstream draws the initials as a `Text` that only overrides the font size, so it keeps the line height of the
 * ambient body style (24.sp). In avatars smaller than that line (the 16.dp quote header, the 20.dp typing row) the
 * line is squeezed into the circle from the top and the letters sink towards the bottom edge. A one-em line,
 * centred and trimmed, puts the letters on the circle's centre instead. [overlay] keeps the ambient style.
 */
@Composable
internal fun TacitUserAvatar(
    initials: String,
    image: ByteArray? = null,
    size: Dp = avatarSize().dp,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val ambientStyle = LocalTextStyle.current
    CompositionLocalProvider(LocalTextStyle provides ambientStyle.merge(AvatarInitialsStyle)) {
        ThemedUserAvatar(
            initials = initials,
            image = image,
            size = size,
            modifier = modifier,
            overlay = { CompositionLocalProvider(LocalTextStyle provides ambientStyle) { overlay() } },
        )
    }
}

private val AvatarInitialsStyle = TextStyle(
    lineHeight = 1.em,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)
