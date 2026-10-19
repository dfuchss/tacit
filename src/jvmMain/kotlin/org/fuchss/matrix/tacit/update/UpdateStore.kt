package org.fuchss.matrix.tacit.update

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val storeLog = Logger("org.fuchss.matrix.tacit.update.UpdateStore")

/** The updater's settings and cache, global per install (not per profile): `<app data>/tacit-updater.json`. */
internal data class UpdateSettings(
    val autoCheck: Boolean = true,
    val lastCheckEpochMs: Long = 0,
    val etag: String? = null,
    val cachedRelease: TacitRelease? = null,
    val stagedTag: String? = null,
    val notifiedTag: String? = null,
) {
    /** Absent values are left out. */
    fun toJson(): JsonObject = buildJsonObject {
        put("autoCheck", autoCheck)
        put("lastCheckEpochMs", lastCheckEpochMs)
        etag?.let { put("etag", it) }
        cachedRelease?.let { put("cachedRelease", it.toJson()) }
        stagedTag?.let { put("stagedTag", it) }
        notifiedTag?.let { put("notifiedTag", it) }
    }

    companion object {
        /** A missing or malformed field falls back to its default; the other fields are kept. */
        fun parse(json: JsonObject) = UpdateSettings(
            autoCheck = json.boolean("autoCheck") ?: true,
            lastCheckEpochMs = json.long("lastCheckEpochMs") ?: 0,
            etag = json.string("etag"),
            cachedRelease = (json["cachedRelease"] as? JsonObject)?.let(TacitRelease::parse),
            stagedTag = json.string("stagedTag"),
            notifiedTag = json.string("notifiedTag"),
        )
    }
}

/**
 * `<app data>/tacit-updater.json` and `<app data>/updates/update-result.json`. Every access to the settings goes
 * through one lock, which is never held during network I/O, so persisting never waits for a running check.
 */
internal class UpdateStore(private val appDataDir: Path) {
    val updatesDir: Path = appDataDir.resolve("updates")
    private val settingsFile: Path = appDataDir.resolve("tacit-updater.json")
    val resultFile: Path = updatesDir.resolve("update-result.json")

    private val lock = Any()
    private var settings: UpdateSettings = synchronized(lock) { load() }

    val current: UpdateSettings get() = synchronized(lock) { settings }

    /** Applies [change] and writes the file; a failed write is logged and the in-memory value still changes. */
    fun update(change: (UpdateSettings) -> UpdateSettings): UpdateSettings = synchronized(lock) {
        val value = change(settings)
        settings = value
        runCatching {
            Files.createDirectories(appDataDir)
            val tmp = settingsFile.resolveSibling("${settingsFile.fileName}.tmp")
            tmp.writeText(value.toJson().toString())
            Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { storeLog.warn(it) { "could not write $settingsFile" } }
        value
    }

    /** The helper's result of the previous swap, if any; remove it with [clearResult] once handled. */
    fun readResult(): String? = if (resultFile.exists()) resultFile.readText() else null

    fun clearResult() {
        Files.deleteIfExists(resultFile)
    }

    fun writeFailure(version: String, message: String) {
        Files.createDirectories(updatesDir)
        resultFile.writeText(failureResult(version, message))
    }

    private fun load(): UpdateSettings = runCatching {
        if (!settingsFile.exists()) UpdateSettings()
        else UpdateSettings.parse(Json.parseToJsonElement(settingsFile.readText()).jsonObject)
    }.getOrElse {
        storeLog.warn(it) { "could not read $settingsFile" }
        UpdateSettings()
    }

    companion object {
        /** The result file format the helper scripts write too (see [TacitUpdateHelper]). */
        fun failureResult(version: String, message: String): String =
            buildJsonObject { put("success", false); put("version", version); put("message", message) }.toString()

        /** The message of a failed swap from the helper's result file, or `null` when it succeeded. */
        fun failureFromResult(text: String): String? {
            val result = Json.parseToJsonElement(text.trimStart('﻿')).jsonObject
            if (result.boolean("success") == true) return null
            return "${result.string("version").orEmpty()}: ${result.string("message").orEmpty()}"
        }
    }
}
