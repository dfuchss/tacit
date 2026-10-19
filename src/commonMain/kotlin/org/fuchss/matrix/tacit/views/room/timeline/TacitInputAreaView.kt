package org.fuchss.matrix.tacit.views.room.timeline

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.collectAsTextFieldValueState
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.*
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSurface
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import org.fuchss.matrix.tacit.tacit
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitComposerEdit
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitEmojiShortcodeMatch
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitSlashCommandMatch
import org.fuchss.matrix.tacit.viewmodel.room.timeline.findEmojiShortcodeMatch
import org.fuchss.matrix.tacit.viewmodel.room.timeline.findSlashCommandMatch
import org.fuchss.matrix.tacit.viewmodel.room.timeline.replaceEmojiShortcode
import org.fuchss.matrix.tacit.viewmodel.room.timeline.slashCommandSuggestions
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView


private data class ComposerSuggestion(
    val title: String,
    val subtitle: String? = null,
    val leading: String? = null,
)

class TacitInputAreaView : InputAreaView {
    @Composable
    override fun create(inputAreaViewModel: InputAreaViewModel) {
        val i18n = DI.get<TacitI18nView>()
        val isReplyTo = inputAreaViewModel.isReply.collectAsState().value
        val canSendMessages = inputAreaViewModel.isAllowedToSendMessages.collectAsState().value
        val isEdit = inputAreaViewModel.isReplace.collectAsState().value
        val mentionSuggestions = inputAreaViewModel.suggestedMentions.collectAsState().value
        val focusRequester = remember { FocusRequester() }
        val textField = inputAreaViewModel.textField.collectAsTextFieldValueState()

        val shortcodeMatch = remember(textField.value) { textField.value.findShortcodeMatch() }
        val emojiSuggestions = remember(shortcodeMatch) {
            shortcodeMatch?.let { emojiShortcodeSuggestions(it.query) }.orEmpty()
        }
        val slashCommandMatch = remember(textField.value) {
            findSlashCommandMatch(
                text = textField.value.text,
                selectionStart = textField.value.selection.start,
                selectionEnd = textField.value.selection.end,
            )
        }
        val slashSuggestions = remember(slashCommandMatch) {
            slashCommandMatch?.let { slashCommandSuggestions(it.query) }.orEmpty()
        }
        // Identifies the match span the suggestions belong to. Escape marks this key as dismissed;
        // as soon as the user types on (or starts a new token) the key changes and suggestions return.
        val suggestionKey = when {
            slashCommandMatch != null && slashSuggestions.isNotEmpty() ->
                "slash@${slashCommandMatch.startIndex}:${slashCommandMatch.query}"

            shortcodeMatch != null && emojiSuggestions.isNotEmpty() ->
                "emoji@${shortcodeMatch.startIndex}:${shortcodeMatch.query}"

            else -> null
        }
        var dismissedSuggestionKey by remember { mutableStateOf<String?>(null) }
        val suggestionsDismissed = suggestionKey != null && suggestionKey == dismissedSuggestionKey

        val showSlashSuggestions = canSendMessages &&
                !suggestionsDismissed &&
                mentionSuggestions.isNullOrEmpty() &&
                slashSuggestions.isNotEmpty() &&
                slashCommandMatch != null
        val showEmojiSuggestions = canSendMessages &&
                !suggestionsDismissed &&
                mentionSuggestions.isNullOrEmpty() &&
                !showSlashSuggestions &&
                emojiSuggestions.isNotEmpty() &&
                shortcodeMatch != null
        val suggestionItems = when {
            showSlashSuggestions -> slashSuggestions.map {
                ComposerSuggestion(
                    title = "/${it.command}",
                    subtitle = DI.get<TacitI18n>().tacitSlashCommandDescription(it.id),
                )
            }

            showEmojiSuggestions -> emojiSuggestions.map {
                ComposerSuggestion(
                    title = ":${it.shortcode}:",
                    leading = it.emoji,
                )
            }

            else -> emptyList()
        }
        val suggestionsCount = when {
            showSlashSuggestions -> slashSuggestions.size
            showEmojiSuggestions -> emojiSuggestions.size
            else -> 0
        }
        val suggestionQuery = slashCommandMatch?.query ?: shortcodeMatch?.query
        var selectedSuggestionIndex by remember(suggestionQuery, suggestionsCount) { mutableStateOf(0) }
        val applySelectedSuggestion: (() -> Boolean)? = when {
            showSlashSuggestions -> {
                {
                    val match = findSlashCommandMatch(
                        text = textField.value.text,
                        selectionStart = textField.value.selection.start,
                        selectionEnd = textField.value.selection.end,
                    )
                    val suggestion = slashSuggestions.getOrNull(selectedSuggestionIndex)
                    if (match != null && suggestion != null) {
                        textField.value = textField.value.replaceSlashCommand(match, suggestion.insertion)
                        focusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }
            }

            showEmojiSuggestions -> {
                {
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
            }

            else -> null
        }

        ThemedSurface(
            style = MaterialTheme.components.inputAreaSurface,
        ) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(Modifier.fillMaxWidth())
                if (isReplyTo) {
                    ReplyToArea(inputAreaViewModel)
                }

                MentionSelector(inputAreaViewModel, focusRequester)
                if (suggestionItems.isNotEmpty()) {
                    AutocompleteSuggestions(
                        suggestions = suggestionItems,
                        selectedIndex = selectedSuggestionIndex,
                        onSelect = { index ->
                            selectedSuggestionIndex = index
                            applySelectedSuggestion?.invoke()
                        },
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        // No IntrinsicSize here: the text field's intrinsic height ignores its line limit, so a
                        // long draft would stretch the row over the whole timeline. The field caps itself and scrolls.
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            val autocompleteVisible = showSlashSuggestions || showEmojiSuggestions
                            if (keyEvent.type != KeyEventType.KeyDown || !autocompleteVisible) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    selectedSuggestionIndex =
                                        if (selectedSuggestionIndex >= suggestionsCount - 1) 0
                                        else selectedSuggestionIndex + 1
                                    true
                                }

                                Key.DirectionUp -> {
                                    selectedSuggestionIndex =
                                        if (selectedSuggestionIndex <= 0) suggestionsCount - 1
                                        else selectedSuggestionIndex - 1
                                    true
                                }

                                Key.Enter -> {
                                    if (keyEvent.isShiftPressed) return@onPreviewKeyEvent false
                                    applySelectedSuggestion?.invoke() == true
                                }

                                Key.Escape -> {
                                    // Only consume Escape when a popup is actually dismissed; otherwise the
                                    // early return above lets it bubble up to the dialog / room close handling.
                                    val key = suggestionKey ?: return@onPreviewKeyEvent false
                                    dismissedSuggestionKey = key
                                    true
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
                            canRecordAudio = false,
                        )

                        if (isEdit) {
                            EditButton(inputAreaViewModel)
                        }
                        // Bottom-aligned so the buttons stay next to the last line of a multi-line draft.
                        Box(Modifier.align(Alignment.Bottom), contentAlignment = Alignment.Center) {
                            AttachmentButton(
                                inputAreaViewModel = inputAreaViewModel,
                                insideTextInputField = false,
                            )
                            SendButton(inputAreaViewModel)
                        }
                    } else {
                        Box(Modifier.fillMaxWidth()) {
                            Text(
                                i18n.inputAreaCannotSendMessages(),
                                modifier = Modifier.padding(10.dp).align(Alignment.Center),
                                color = MaterialTheme.tacit.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutocompleteSuggestions(
    suggestions: List<ComposerSuggestion>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(TacitShapes.card)
            .background(MaterialTheme.tacit.surfaceAlt)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.card)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index == selectedIndex) MaterialTheme.tacit.accent(TacitAlpha.accentSoft)
                        else Color.Transparent,
                        TacitShapes.compact,
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestion.leading?.let { leading ->
                    Text(
                        text = leading,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = suggestion.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.tacit.text,
                    )
                    suggestion.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.tacit.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun TextFieldValue.findShortcodeMatch(): TacitEmojiShortcodeMatch? =
    findEmojiShortcodeMatch(text, selection.start, selection.end)

private fun TextFieldValue.replaceShortcode(match: TacitEmojiShortcodeMatch, emoji: String): TextFieldValue =
    replaceEmojiShortcode(text, match, emoji).toTextFieldValue()

private fun TextFieldValue.replaceSlashCommand(match: TacitSlashCommandMatch, replacement: String): TextFieldValue =
    org.fuchss.matrix.tacit.viewmodel.room.timeline.replaceSlashCommand(text, match, replacement).toTextFieldValue()

private fun TacitComposerEdit.toTextFieldValue() = TextFieldValue(text = text, selection = TextRange(cursor))
