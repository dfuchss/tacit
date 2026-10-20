package org.fuchss.matrix.tacit.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

/**
 * The detached helper that swaps the install once Tacit has exited. Replacing the app while it was running
 * crashed it on macOS, so the helper first waits for [pid] to disappear and never touches [installRoot] before.
 */
data class TacitUpdateHelperParams(
    val os: TacitOs,
    val pid: Long,
    val installRoot: Path,
    val stagedRoot: Path,
    val stagingDir: Path,
    val resultFile: Path,
    val version: String,
) {
    val backup: Path get() = installRoot.resolveSibling("${installRoot.fileName}.old-update")
}

object TacitUpdateHelper {
    const val WAIT_SECONDS = 300
    private const val UPDATE_ENV_PREFIX = "TACIT_UPDATE_"

    fun launch(params: TacitUpdateHelperParams): Process = prepare(params).start()

    /**
     * Writes and validates the helper script without starting it, so a restart can be refused before Tacit
     * exits. The helper runs outside the staging folder, which the next start of the app deletes.
     */
    fun prepare(params: TacitUpdateHelperParams): ProcessBuilder {
        val staging = params.stagingDir.toAbsolutePath().normalize()
        val install = params.installRoot.toAbsolutePath().normalize()
        if (staging.startsWith(install) || install.startsWith(staging)) {
            throw IOException("The update staging folder must not overlap the install")
        }
        if (!params.stagedRoot.isDirectory()) throw IOException("The staged update ${params.stagedRoot} is missing")
        if (!install.isDirectory()) throw IOException("The install $install is not a folder")
        Files.createDirectories(staging)
        val command = when (params.os) {
            TacitOs.WINDOWS -> {
                val script = staging.resolve("helper.ps1")
                // Windows PowerShell 5.1 reads BOM-less scripts in the ANSI code page, which breaks non-ASCII paths.
                Files.write(script, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + powerShellScript(params).toByteArray())
                listOf(
                    windowsPowerShell(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden", "-File", script.toString(), "-AppPid", params.pid.toString(),
                )
            }

            TacitOs.MACOS, TacitOs.LINUX -> {
                val script = staging.resolve("helper.sh")
                script.writeText(shScript(params))
                if (!File("/bin/sh").canExecute()) throw IOException("/bin/sh is not available")
                val setsid = listOf("/usr/bin/setsid", "/bin/setsid").firstOrNull { File(it).canExecute() }
                listOfNotNull(setsid, "nohup", "/bin/sh", script.toString())
            }
        }
        return ProcessBuilder(command)
            .apply { removeUpdateEnvironment(environment()) }
            .directory(neutralDirectory(staging))
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .redirectOutput(ProcessBuilder.Redirect.appendTo(staging.resolve("helper.log").toFile()))
            .redirectErrorStream(true)
    }

    /** A working directory that is neither the staging folder nor the install, both of which get replaced. */
    private fun neutralDirectory(avoid: Path): File {
        val home = System.getProperty("user.home")?.let(::File)?.takeIf { it.isDirectory }
        return home ?: File(System.getProperty("java.io.tmpdir")).takeIf { it.isDirectory } ?: avoid.parent.toFile()
    }

    /**
     * The updater's test overrides must not leak into the helper process.
     */
    fun removeUpdateEnvironment(environment: MutableMap<String, String>) {
        environment.keys.removeIf { it.startsWith(UPDATE_ENV_PREFIX) }
    }

    private fun nullDevice(): File =
        File(if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "NUL" else "/dev/null")

    private fun windowsPowerShell(): String {
        val systemRoot = System.getenv("SystemRoot") ?: return "powershell.exe"
        val path = File(systemRoot, "System32\\WindowsPowerShell\\v1.0\\powershell.exe")
        return if (path.exists()) path.path else "powershell.exe"
    }

    private fun shQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    fun shScript(p: TacitUpdateHelperParams): String {
        return """
            |#!/bin/sh
            |# Tacit update helper (generated). It runs from the staging folder and only touches the install after
            |# the Tacit process has exited.
            |APP_PID=${p.pid}
            |INSTALL=${shQuote(p.installRoot.toString())}
            |STAGED=${shQuote(p.stagedRoot.toString())}
            |BACKUP=${shQuote(p.backup.toString())}
            |RESULT=${shQuote(p.resultFile.toString())}
            |VERSION=${shQuote(p.version)}
            |
            |log() { echo "${'$'}(date '+%Y-%m-%dT%H:%M:%S') ${'$'}*"; }
            |
            |# ${'$'}1: true|false, ${'$'}2: a fixed message without quotes
            |write_result() {
            |  printf '{"success":%s,"version":"%s","message":"%s"}\n' "${'$'}1" "${'$'}VERSION" "${'$'}2" > "${'$'}RESULT.tmp" &&
            |    mv -f "${'$'}RESULT.tmp" "${'$'}RESULT"
            |}
            |
            |log "waiting for Tacit (pid ${'$'}APP_PID) to exit"
            |waited=0
            |while kill -0 "${'$'}APP_PID" 2>/dev/null; do
            |  if [ "${'$'}waited" -ge $WAIT_SECONDS ]; then
            |    log "Tacit did not exit in time, leaving the install untouched"
            |    write_result false "Tacit did not exit in time"
            |    exit 1
            |  fi
            |  sleep 1
            |  waited=${'$'}((waited + 1))
            |done
            |
            |if [ ! -d "${'$'}STAGED" ]; then
            |  log "staged update ${'$'}STAGED is missing"
            |  write_result false "the staged update is missing"
            |  exit 1
            |fi
            |
            |[ -e "${'$'}BACKUP" ] && rm -rf "${'$'}BACKUP"
            |log "moving ${'$'}INSTALL to ${'$'}BACKUP"
            |if ! mv "${'$'}INSTALL" "${'$'}BACKUP"; then
            |  write_result false "could not move the current version aside"
            |  exit 1
            |fi
            |
            |log "moving ${'$'}STAGED to ${'$'}INSTALL"
            |if mv "${'$'}STAGED" "${'$'}INSTALL"; then
            |  rm -rf "${'$'}BACKUP"
            |  log "update to ${'$'}VERSION installed"
            |  write_result true "updated"
            |else
            |  log "moving the update failed, restoring the previous version"
            |  [ -e "${'$'}INSTALL" ] && rm -rf "${'$'}INSTALL"
            |  if mv "${'$'}BACKUP" "${'$'}INSTALL"; then
            |    write_result false "could not move the update in place, the previous version was restored"
            |  else
            |    log "restoring failed, the previous version is at ${'$'}BACKUP"
            |    write_result false "could not restore the previous version"
            |  fi
            |fi
            |
        """.trimMargin()
    }

    /** PowerShell treats the typographic single quotes like `'`, so all of them are doubled. */
    private fun psQuote(value: String) = "'" + value.replace(Regex("['‘’‚‛]")) { it.value + it.value } + "'"

    fun powerShellScript(p: TacitUpdateHelperParams): String = """
        |# Tacit update helper (generated). It runs from the staging folder and only touches the install after
        |# the Tacit process has exited.
        |param([Parameter(Mandatory = ${'$'}true)][int]${'$'}AppPid)
        |${'$'}ErrorActionPreference = 'Stop'
        |${'$'}Install = ${psQuote(p.installRoot.toString())}
        |${'$'}Staged = ${psQuote(p.stagedRoot.toString())}
        |${'$'}Backup = ${psQuote(p.backup.toString())}
        |${'$'}Result = ${psQuote(p.resultFile.toString())}
        |${'$'}Version = ${psQuote(p.version)}
        |# Logs go straight to stdout (helper.log) so they never end up in a function's return value.
        |function Log([string]${'$'}Message) { [Console]::Out.WriteLine(('{0:s} {1}' -f (Get-Date), ${'$'}Message)) }
        |
        |function Write-Result([bool]${'$'}Success, [string]${'$'}Message) {
        |    ${'$'}json = @{ success = ${'$'}Success; version = ${'$'}Version; message = ${'$'}Message } | ConvertTo-Json -Compress
        |    [System.IO.File]::WriteAllText(${'$'}Result, ${'$'}json)
        |}
        |
        |# Antivirus and the search indexer can hold files for a moment after Tacit exited.
        |function Retry([scriptblock]${'$'}Action, [int]${'$'}Attempts = 30) {
        |    for (${'$'}i = 1; ${'$'}i -le ${'$'}Attempts; ${'$'}i++) {
        |        try {
        |            & ${'$'}Action | Out-Null
        |            return ${'$'}true
        |        } catch {
        |            Log "attempt ${'$'}i failed: ${'$'}(${'$'}_.Exception.Message)"
        |            if (${'$'}i -lt ${'$'}Attempts) { Start-Sleep -Seconds 1 }
        |        }
        |    }
        |    return ${'$'}false
        |}
        |
        |Log "waiting for Tacit (pid ${'$'}AppPid) to exit"
        |# An already exited pid and the timeout are both plain errors here; Get-Process below decides.
        |try { Wait-Process -Id ${'$'}AppPid -Timeout $WAIT_SECONDS -ErrorAction SilentlyContinue } catch { }
        |if (Get-Process -Id ${'$'}AppPid -ErrorAction SilentlyContinue) {
        |    Log 'Tacit did not exit in time, leaving the install untouched'
        |    Write-Result ${'$'}false 'Tacit did not exit in time'
        |    exit 1
        |}
        |
        |if (-not (Test-Path -LiteralPath ${'$'}Staged -PathType Container)) {
        |    Log "staged update ${'$'}Staged is missing"
        |    Write-Result ${'$'}false 'the staged update is missing'
        |    exit 1
        |}
        |
        |if (Test-Path -LiteralPath ${'$'}Backup) { Remove-Item -LiteralPath ${'$'}Backup -Recurse -Force -ErrorAction SilentlyContinue }
        |Log "moving ${'$'}Install to ${'$'}Backup"
        |if (-not (Retry { Rename-Item -LiteralPath ${'$'}Install -NewName (Split-Path -Leaf ${'$'}Backup) })) {
        |    Write-Result ${'$'}false 'could not move the current version aside'
        |    exit 1
        |}
        |
        |Log "moving ${'$'}Staged to ${'$'}Install"
        |# Move-Item cannot move folders across volumes, so a staging folder on another drive is copied instead.
        |${'$'}sameVolume = [System.IO.Path]::GetPathRoot(${'$'}Staged) -eq [System.IO.Path]::GetPathRoot(${'$'}Install)
        |${'$'}moved = Retry {
        |    if (Test-Path -LiteralPath ${'$'}Install) { Remove-Item -LiteralPath ${'$'}Install -Recurse -Force }
        |    if (${'$'}sameVolume) {
        |        Move-Item -LiteralPath ${'$'}Staged -Destination ${'$'}Install
        |    } else {
        |        Copy-Item -LiteralPath ${'$'}Staged -Destination ${'$'}Install -Recurse -Force
        |    }
        |} 5
        |
        |if (${'$'}moved) {
        |    Remove-Item -LiteralPath ${'$'}Backup -Recurse -Force -ErrorAction SilentlyContinue
        |    Log "update to ${'$'}Version installed"
        |    Write-Result ${'$'}true 'updated'
        |} else {
        |    Log 'moving the update failed, restoring the previous version'
        |    if (Test-Path -LiteralPath ${'$'}Install) { Remove-Item -LiteralPath ${'$'}Install -Recurse -Force -ErrorAction SilentlyContinue }
        |    if (Retry { Rename-Item -LiteralPath ${'$'}Backup -NewName (Split-Path -Leaf ${'$'}Install) }) {
        |        Write-Result ${'$'}false 'could not move the update in place, the previous version was restored'
        |    } else {
        |        Log "restoring failed, the previous version is at ${'$'}Backup"
        |        Write-Result ${'$'}false 'could not restore the previous version'
        |    }
        |}
        |
    """.trimMargin()
}
