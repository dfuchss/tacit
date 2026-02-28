package org.fuchss.matrix.tacit

import de.connect2x.trixnity.messenger.util.RootPath
import okio.Path.Companion.toPath

internal actual fun getDevRootPath(): RootPath? =
    if (System.getenv("TAMMY_ROOT_PATH") == null)
        RootPath("./app-data".toPath())
    else null

internal actual val platformDatabaseEncryptionEnabled: Boolean = true