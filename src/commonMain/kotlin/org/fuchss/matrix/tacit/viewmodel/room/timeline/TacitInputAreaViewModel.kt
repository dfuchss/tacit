package org.fuchss.matrix.tacit.viewmodel.room.timeline

import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.message.MessageBuilder
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.util.FileDescriptor
import de.connect2x.trixnity.messenger.viewmodel.MatrixClientViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModelImpl
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.OpenMentionCallback
import kotlinx.coroutines.launch

internal object TacitInputAreaViewModelFactory : InputAreaViewModelFactory {
    override fun create(
        viewModelContext: MatrixClientViewModelContext,
        selectedRoomId: RoomId,
        onMessageReplaceFinished: (RoomId, EventId) -> Unit,
        onMessageReplyFinished: (RoomId, EventId) -> Unit,
        onShowAttachmentSendView: (file: FileDescriptor) -> Unit,
        onOpenMention: OpenMentionCallback,
    ): InputAreaViewModel = TacitInputAreaViewModel(
        viewModelContext = viewModelContext,
        roomId = selectedRoomId,
        onMessageReplaceFinished = onMessageReplaceFinished,
        onMessageReplyFinished = onMessageReplyFinished,
        onShowAttachmentSendView = onShowAttachmentSendView,
        onOpenMention = onOpenMention,
    )
}

private class TacitInputAreaViewModel(
    viewModelContext: MatrixClientViewModelContext,
    private val roomId: RoomId,
    onMessageReplaceFinished: (RoomId, EventId) -> Unit,
    onMessageReplyFinished: (RoomId, EventId) -> Unit,
    onShowAttachmentSendView: (file: FileDescriptor) -> Unit,
    onOpenMention: OpenMentionCallback,
) : InputAreaViewModelImpl(
    viewModelContext = viewModelContext,
    roomId = roomId,
    onMessageReplaceFinished = onMessageReplaceFinished,
    onMessageReplyFinished = onMessageReplyFinished,
    onShowAttachmentSendView = onShowAttachmentSendView,
    onOpenMention = onOpenMention,
), TacitSlashCommandContext {
    override fun sendMessage() {
        val composerText = textField.value.text
        val parsedCommand = parseSlashCommand(composerText)
        if (parsedCommand is TacitSlashCommandParseResult.Incomplete) return
        if (parsedCommand !is TacitSlashCommandParseResult.Parsed || isReply.value || isReplace.value) {
            super.sendMessage()
            return
        }
        parsedCommand.command.execute(this, parsedCommand.rawArguments)
    }

    override fun send(builder: suspend MessageBuilder.() -> Unit) {
        textField.update("")
        coroutineScope.launch {
            matrixClient.room.deleteDraftMessage(roomId)
            matrixClient.room.sendMessage(roomId = roomId, builder = builder)
        }
    }
}
