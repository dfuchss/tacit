package org.fuchss.matrix.tacit.readmeshots

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.update.TacitUpdateController
import org.fuchss.matrix.tacit.update.TacitUpdateState

/** A scripted updater for the screenshots: the generator sets [state]; nothing is ever checked or downloaded. */
internal class ScreenshotUpdateController : TacitUpdateController {
    override val state = MutableStateFlow<TacitUpdateState>(TacitUpdateState.UpToDate)
    override val autoCheck: StateFlow<Boolean> = MutableStateFlow(true)

    override val lastError: StateFlow<String?> = MutableStateFlow(null)
    override val releaseUrl = "https://github.com/dfuchss/tacit/releases/latest"

    override fun setAutoCheck(enabled: Boolean) = Unit
    override fun checkNow() = Unit
    override fun restartToUpdate() = Unit
}
