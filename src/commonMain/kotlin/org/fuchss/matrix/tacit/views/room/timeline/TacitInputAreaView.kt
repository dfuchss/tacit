package org.fuchss.matrix.tacit.views.room.timeline

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.collectAsTextFieldState
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.*
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSurface
import de.connect2x.trixnity.messenger.viewmodel.room.ImagePackViewModel
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
        val mentionsLoading = inputAreaViewModel.suggestedMentionsLoading.collectAsState().value
        val focusRequester = remember { FocusRequester() }
        val mentionFocusRequester = remember { FocusRequester() }
        // Upstream's sticker packs (null while the feature is off). The panel above the input row hosts upstream's
        // picker with its pack management; it closes after a sticker was sent, on Escape and with the button.
        val stickerPacks = inputAreaViewModel.stickerPacks
        val stickersOpen = remember(inputAreaViewModel) { mutableStateOf(false) }
        val showStickerButton = isStickerButtonVisible(stickerPacks != null, canSendMessages, isEdit)
        val stickerPanelOpen = stickersOpen.value && showStickerButton
        val textFieldState = inputAreaViewModel.textField.collectAsTextFieldState()
        // Read in composition so that the suggestions follow every edit and cursor move.
        val text = textFieldState.text.toString()
        val selection = textFieldState.selection
        // Like upstream: Escape hides the mention list until the text or the cursor changes.
        var mentionsDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(textFieldState) {
            snapshotFlow { textFieldState.text.toString() to textFieldState.selection }
                .collect { mentionsDismissed = false }
        }

        val shortcodeMatch = remember(text, selection) { findEmojiShortcodeMatch(text, selection.start, selection.end) }
        val emojiSuggestions = remember(shortcodeMatch) {
            shortcodeMatch?.let { emojiShortcodeSuggestions(it.query) }.orEmpty()
        }
        val slashCommandMatch = remember(text, selection) {
            findSlashCommandMatch(text = text, selectionStart = selection.start, selectionEnd = selection.end)
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
                !mentionsLoading &&
                mentionSuggestions.isNullOrEmpty() &&
                slashSuggestions.isNotEmpty() &&
                slashCommandMatch != null
        val showEmojiSuggestions = canSendMessages &&
                !suggestionsDismissed &&
                !mentionsLoading &&
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
                    val match = textFieldState.findSlashCommandMatch()
                    val suggestion = slashSuggestions.getOrNull(selectedSuggestionIndex)
                    if (match != null && suggestion != null) {
                        textFieldState.replaceSlashCommand(match, suggestion.insertion)
                        focusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }
            }

            showEmojiSuggestions -> {
                {
                    val match = textFieldState.findShortcodeMatch()
                    val suggestion = emojiSuggestions.getOrNull(selectedSuggestionIndex)
                    if (match != null && suggestion != null) {
                        textFieldState.replaceShortcode(match, suggestion.emoji)
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
            Column(
                Modifier.fillMaxWidth().onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape && stickerPanelOpen) {
                        stickersOpen.value = false
                        focusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    val mentionsAvailable = mentionsLoading || !mentionSuggestions.isNullOrEmpty()
                    if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Escape) return@onPreviewKeyEvent false
                    if (mentionsDismissed || !mentionsAvailable) return@onPreviewKeyEvent false
                    focusRequester.requestFocus()
                    mentionsDismissed = true
                    true
                }
            ) {
                HorizontalDivider(Modifier.fillMaxWidth())
                if (isReplyTo) {
                    ReplyToArea(inputAreaViewModel)
                }

                if (!mentionsDismissed) {
                    MentionSelector(inputAreaViewModel, focusRequester, mentionFocusRequester)
                }
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

                if (stickerPanelOpen && stickerPacks != null) {
                    TacitStickerPanel(
                        stickerPacks = stickerPacks,
                        onDismiss = {
                            stickersOpen.value = false
                            focusRequester.requestFocus()
                        },
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        // No IntrinsicSize here: the text field's intrinsic height ignores its line limit, so a
                        // long draft would stretch the row over the whole timeline. The field caps itself and scrolls.
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        // A parent of the text field, so this runs before upstream's handler. It only consumes keys
                        // while Tacit's own suggestions are visible, which never happens together with mention
                        // suggestions; Up/Down then still reach upstream's mention navigation.
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
                            textFieldState = textFieldState,
                            focusRequester = focusRequester,
                            canRecordAudio = false,
                            mentionFocusRequester = mentionFocusRequester.takeUnless { mentionsDismissed },
                        )

                        if (isEdit) {
                            EditButton(inputAreaViewModel)
                        }
                        // Bottom-aligned so the buttons stay next to the last line of a multi-line draft.
                        if (showStickerButton && stickerPacks != null) {
                            Box(Modifier.align(Alignment.Bottom), contentAlignment = Alignment.Center) {
                                StickerButton(stickerPacks, stickersOpen)
                            }
                        }
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
        if (stickerPacks != null) {
            StickerPackErrorDialog(stickerPacks)
        }
    }
}

/** Stickers need the feature, the permission to write and a new message: an edit cannot turn into a sticker. */
internal fun isStickerButtonVisible(hasStickerPacks: Boolean, canSendMessages: Boolean, isEdit: Boolean): Boolean =
    hasStickerPacks && canSendMessages && !isEdit

/** Upstream's [StickerPackPicker] in a Tacit card above the input row, like the autocomplete list. */
@Composable
private fun TacitStickerPanel(stickerPacks: ImagePackViewModel, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(StickerPanelHeight)
            .clip(TacitShapes.card)
            .background(MaterialTheme.tacit.surfaceAlt)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.card)
            .padding(vertical = 4.dp),
    ) {
        StickerPackPicker(model = stickerPacks, onDismiss = onDismiss)
    }
}

private val StickerPanelHeight = 260.dp

@Composable
private fun AutocompleteSuggestions(
    suggestions: List<ComposerSuggestion>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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

internal fun TextFieldState.findShortcodeMatch(): TacitEmojiShortcodeMatch? =
    findEmojiShortcodeMatch(text.toString(), selection.start, selection.end)

internal fun TextFieldState.findSlashCommandMatch(): TacitSlashCommandMatch? =
    findSlashCommandMatch(text = text.toString(), selectionStart = selection.start, selectionEnd = selection.end)

internal fun TextFieldState.replaceShortcode(match: TacitEmojiShortcodeMatch, emoji: String) =
    applyComposerEdit(replaceEmojiShortcode(text.toString(), match, emoji))

internal fun TextFieldState.replaceSlashCommand(match: TacitSlashCommandMatch, replacement: String) =
    applyComposerEdit(
        org.fuchss.matrix.tacit.viewmodel.room.timeline.replaceSlashCommand(text.toString(), match, replacement)
    )

/** Replaces the whole text with [change] and puts the cursor where [change] says, as one undoable edit. */
internal fun TextFieldState.applyComposerEdit(change: TacitComposerEdit) {
    edit {
        replace(0, length, change.text)
        selection = TextRange(change.cursor)
    }
}
