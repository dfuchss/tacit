package org.fuchss.matrix.tacit.viewmodel.room.timeline

import de.connect2x.trixnity.client.room.message.MessageBuilder
import de.connect2x.trixnity.client.room.message.emote
import de.connect2x.trixnity.client.room.message.notice
import de.connect2x.trixnity.client.room.message.text

internal enum class TacitSlashCommandId {
    SPOILER,
    ME,
    NOTICE,
    SHRUG,
    TABLEFLIP,
    UNFLIP,
}

internal interface TacitSlashCommandContext {
    fun send(builder: suspend MessageBuilder.() -> Unit)
}

internal sealed class TacitSlashCommand(
    val id: TacitSlashCommandId,
    val command: String,
    val description: String,
    val usage: String,
    val insertion: String = "/$command ",
) {
    abstract fun execute(context: TacitSlashCommandContext, rawArguments: String)
}

internal data class TacitSlashCommandMatch(
    val startIndex: Int,
    val cursorIndex: Int,
    val query: String,
)

internal sealed interface TacitSlashCommandParseResult {
    data object NotACommand : TacitSlashCommandParseResult
    data class Unknown(val command: String) : TacitSlashCommandParseResult
    data class Incomplete(val command: TacitSlashCommand) : TacitSlashCommandParseResult
    data class Parsed(
        val command: TacitSlashCommand,
        val rawArguments: String,
    ) : TacitSlashCommandParseResult
}

internal data class TacitSpoilerContent(
    val reason: String?,
    val message: String,
)

private data object SpoilerSlashCommand : TacitSlashCommand(
    id = TacitSlashCommandId.SPOILER,
    command = "spoiler",
    description = "Hide text behind a spoiler. Use `reason | message` for a label.",
    usage = "/spoiler reason | message",
) {
    override fun execute(context: TacitSlashCommandContext, rawArguments: String) {
        val spoiler = parseSpoilerContent(rawArguments.trim())
        if (spoiler.message.isNotBlank()) {
            context.send {
                text(
                    body = spoiler.message,
                    format = "org.matrix.custom.html",
                    formattedBody = spoiler.toFormattedBody(),
                )
            }
        }
    }
}

private data object MeSlashCommand : TacitSlashCommand(
    id = TacitSlashCommandId.ME,
    command = "me",
    description = "Send an emote message.",
    usage = "/me waves",
) {
    override fun execute(context: TacitSlashCommandContext, rawArguments: String) {
        val message = rawArguments.trim()
        if (message.isNotBlank()) {
            context.send { emote(message) }
        }
    }
}

private data object NoticeSlashCommand : TacitSlashCommand(
    id = TacitSlashCommandId.NOTICE,
    command = "notice",
    description = "Send a notice message instead of regular chat text.",
    usage = "/notice Maintenance starts in 10 minutes",
) {
    override fun execute(context: TacitSlashCommandContext, rawArguments: String) {
        val message = rawArguments.trim()
        if (message.isNotBlank()) {
            context.send { notice(message) }
        }
    }
}

private data object ShrugSlashCommand : TacitSlashCommand(
    id = TacitSlashCommandId.SHRUG,
    command = "shrug",
    description = "Append a shrug to your message.",
    usage = "/shrug shipping it",
) {
    override fun execute(context: TacitSlashCommandContext, rawArguments: String) {
        val message = rawArguments.trim()
        context.send {
            text(if (message.isBlank()) "¯\\_(ツ)_/¯" else "$message ¯\\_(ツ)_/¯")
        }
    }
}

private data object TableflipSlashCommand : TacitSlashCommand(
    id = TacitSlashCommandId.TABLEFLIP,
    command = "tableflip",
    description = "Append a table flip.",
    usage = "/tableflip tests are red",
) {
    override fun execute(context: TacitSlashCommandContext, rawArguments: String) {
        val message = rawArguments.trim()
        context.send {
            text(if (message.isBlank()) "(╯°□°)╯︵ ┻━┻" else "$message (╯°□°)╯︵ ┻━┻")
        }
    }
}

private data object UnflipSlashCommand : TacitSlashCommand(
    id = TacitSlashCommandId.UNFLIP,
    command = "unflip",
    description = "Append a table unflip.",
    usage = "/unflip all good now",
) {
    override fun execute(context: TacitSlashCommandContext, rawArguments: String) {
        val message = rawArguments.trim()
        context.send {
            text(if (message.isBlank()) "┬─┬ノ( º _ ºノ)" else "$message ┬─┬ノ( º _ ºノ)")
        }
    }
}

internal val tacitSlashCommands = listOf(
    SpoilerSlashCommand,
    MeSlashCommand,
    NoticeSlashCommand,
    ShrugSlashCommand,
    TableflipSlashCommand,
    UnflipSlashCommand,
)

internal fun slashCommandSuggestions(
    query: String,
    limit: Int = 8,
): List<TacitSlashCommand> {
    if (query.isBlank()) return tacitSlashCommands.take(limit)
    val normalizedQuery = query.lowercase()
    val startsWith = tacitSlashCommands.asSequence()
        .filter { it.command.startsWith(normalizedQuery) }
        .take(limit)
        .toList()
    val contains = tacitSlashCommands.asSequence()
        .filter { it.command.contains(normalizedQuery) }
        .take(limit)
        .toList()
    return (startsWith + contains)
        .distinctBy { it.command }
        .take(limit)
}

internal fun findSlashCommandMatch(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): TacitSlashCommandMatch? {
    if (selectionStart != selectionEnd || selectionStart == 0) return null
    val cursorIndex = selectionStart
    val textBeforeCursor = text.substring(0, cursorIndex)
    if (!textBeforeCursor.startsWith("/")) return null
    if (textBeforeCursor.contains('\n')) return null
    val afterSlash = textBeforeCursor.drop(1)
    if (afterSlash.contains(' ') || afterSlash.contains('\t')) return null
    if (!afterSlash.all { it.isLetter() }) return null
    return TacitSlashCommandMatch(
        startIndex = 0,
        cursorIndex = cursorIndex,
        query = afterSlash.lowercase(),
    )
}

internal fun parseSlashCommand(text: String): TacitSlashCommandParseResult {
    if (!text.startsWith("/")) return TacitSlashCommandParseResult.NotACommand
    val firstLine = text.substringBefore('\n')
    val firstSpace = firstLine.indexOf(' ')
    val commandText = if (firstSpace >= 0) firstLine.substring(1, firstSpace) else firstLine.drop(1)
    if (commandText.isBlank()) return TacitSlashCommandParseResult.NotACommand
    val command = tacitSlashCommands.firstOrNull { it.command == commandText.lowercase() }
        ?: return TacitSlashCommandParseResult.Unknown(commandText)
    val rawArguments = if (text.length > commandText.length + 1) {
        text.substring(commandText.length + 2)
    } else {
        ""
    }
    return when {
        rawArguments.isBlank() && command.id in setOf(
            TacitSlashCommandId.SPOILER,
            TacitSlashCommandId.ME,
            TacitSlashCommandId.NOTICE,
        ) -> TacitSlashCommandParseResult.Incomplete(command)

        else -> TacitSlashCommandParseResult.Parsed(command, rawArguments)
    }
}

internal fun parseSpoilerContent(arguments: String): TacitSpoilerContent {
    val separatorIndex = arguments.indexOf('|')
    return if (separatorIndex >= 0) {
        val reason = arguments.substring(0, separatorIndex).trim().ifBlank { null }
        val message = arguments.substring(separatorIndex + 1).trim()
        TacitSpoilerContent(reason = reason, message = message)
    } else {
        TacitSpoilerContent(reason = null, message = arguments.trim())
    }
}

private fun TacitSpoilerContent.toFormattedBody(): String = buildString {
    append("<span data-mx-spoiler")
    reason?.let { spoilerReason ->
        append("=\"")
        append(escapeHtml(spoilerReason))
        append("\"")
    }
    append(">")
    append(escapeHtml(message).replace("\n", "<br />"))
    append("</span>")
}

private fun escapeHtml(value: String): String = buildString(value.length) {
    value.forEach { char ->
        append(
            when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                else -> char
            }
        )
    }
}
