package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.collectAsTextFieldValueState
import de.connect2x.trixnity.messenger.compose.view.common.EmojiSelector
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.*
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSurface
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import org.fuchss.matrix.tacit.tacitSearchResultBackground
import org.fuchss.matrix.tacit.tacitSearchResultBorder
import org.fuchss.matrix.tacit.tacitText

private data class EmojiShortcodeMatch(
    val startIndex: Int,
    val cursorIndex: Int,
    val query: String,
)

class TacitInputAreaView : InputAreaView {
    @Composable
    override fun create(inputAreaViewModel: InputAreaViewModel) {
        val i18n = DI.get<I18nView>()
        val isReplyTo = inputAreaViewModel.isReply.collectAsState().value
        val canSendMessages = inputAreaViewModel.isAllowedToSendMessages.collectAsState().value
        val isEdit = inputAreaViewModel.isReplace.collectAsState().value
        val mentionSuggestions = inputAreaViewModel.listOfMentions.collectAsState().value
        val focusRequester = remember { FocusRequester() }
        val textField = inputAreaViewModel.textField.collectAsTextFieldValueState()

        val shortcodeMatch = remember(textField.value) { textField.value.findShortcodeMatch() }
        val emojiSuggestions = remember(shortcodeMatch) {
            shortcodeMatch?.let { emojiShortcodeSuggestions(it.query) }.orEmpty()
        }
        var selectedSuggestionIndex by remember(shortcodeMatch?.query, emojiSuggestions.size) { mutableStateOf(0) }
        val showEmojiSuggestions = canSendMessages &&
                mentionSuggestions.isNullOrEmpty() &&
                emojiSuggestions.isNotEmpty() &&
                shortcodeMatch != null

        ThemedSurface(
            style = MaterialTheme.components.inputAreaSurface,
        ) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(Modifier.fillMaxWidth())
                if (isReplyTo) {
                    ReplyToArea(inputAreaViewModel)
                }

                UserSelector(inputAreaViewModel, focusRequester)
                if (showEmojiSuggestions) {
                    EmojiShortcodeSuggestions(
                        suggestions = emojiSuggestions,
                        selectedIndex = selectedSuggestionIndex,
                        onSelect = { suggestion ->
                            val match = textField.value.findShortcodeMatch() ?: return@EmojiShortcodeSuggestions
                            textField.value = textField.value.replaceShortcode(match, suggestion.emoji)
                            focusRequester.requestFocus()
                        },
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .padding(8.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown || !showEmojiSuggestions) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    selectedSuggestionIndex =
                                        if (selectedSuggestionIndex >= emojiSuggestions.lastIndex) 0
                                        else selectedSuggestionIndex + 1
                                    true
                                }

                                Key.DirectionUp -> {
                                    selectedSuggestionIndex =
                                        if (selectedSuggestionIndex <= 0) emojiSuggestions.lastIndex
                                        else selectedSuggestionIndex - 1
                                    true
                                }

                                Key.Enter -> {
                                    if (keyEvent.isShiftPressed) return@onPreviewKeyEvent false
                                    val match = textField.value.findShortcodeMatch()
                                    val suggestion = emojiSuggestions.getOrNull(selectedSuggestionIndex)
                                    if (match != null && suggestion != null) {
                                        textField.value = textField.value.replaceShortcode(match, suggestion.emoji)
                                        focusRequester.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }

                                else -> false
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canSendMessages) {
                        InputAreaTextField(
                            inputAreaViewModel = inputAreaViewModel,
                            textField = textField,
                            focusRequester = focusRequester,
                        )

                        if (isEdit) {
                            EditButton(inputAreaViewModel)
                        }
                        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                            AttachmentButton(inputAreaViewModel)
                            SendButton(inputAreaViewModel)
                        }
                    } else {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                i18n.inputAreaCannotSendMessages(),
                                modifier = Modifier.padding(10.dp).align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiShortcodeSuggestions(
    suggestions: List<TacitEmojiShortcode>,
    selectedIndex: Int,
    onSelect: (TacitEmojiShortcode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(tacitSearchResultBackground, RoundedCornerShape(10.dp))
            .border(1.dp, tacitSearchResultBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index == selectedIndex) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(suggestion) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = suggestion.emoji,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = ":${suggestion.shortcode}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tacitText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun TextFieldValue.insert(insertion: String): TextFieldValue =
    TextFieldValue(
        text = text.substring(0, selection.start) + insertion + text.substring(selection.end),
        selection = TextRange(selection.start + insertion.length),
    )

private fun TextFieldValue.findShortcodeMatch(): EmojiShortcodeMatch? {
    if (!selection.collapsed || selection.start == 0) return null
    val cursorIndex = selection.start
    val textBeforeCursor = text.substring(0, cursorIndex)
    val colonIndex = textBeforeCursor.lastIndexOf(':')
    if (colonIndex < 0) return null

    if (colonIndex > 0) {
        val previous = textBeforeCursor[colonIndex - 1]
        if (previous.isLetterOrDigit() || previous == '_' || previous == '-') return null
    }

    val query = textBeforeCursor.substring(colonIndex + 1)
    if (query.isBlank()) return null
    if (query.contains(':')) return null
    if (!query.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return null

    return EmojiShortcodeMatch(
        startIndex = colonIndex,
        cursorIndex = cursorIndex,
        query = query.lowercase(),
    )
}

private fun TextFieldValue.replaceShortcode(
    match: EmojiShortcodeMatch,
    emoji: String,
): TextFieldValue {
    val appendSpace = match.cursorIndex >= text.length
    val replacement = if (appendSpace) "$emoji " else emoji
    val newText = buildString {
        append(text.substring(0, match.startIndex))
        append(replacement)
        append(text.substring(match.cursorIndex))
    }
    val newCursor = match.startIndex + replacement.length
    return TextFieldValue(
        text = newText,
        selection = TextRange(newCursor),
    )
}
