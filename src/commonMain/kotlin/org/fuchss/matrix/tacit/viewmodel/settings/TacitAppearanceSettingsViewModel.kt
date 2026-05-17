package org.fuchss.matrix.tacit.viewmodel.settings

import de.connect2x.trixnity.messenger.MatrixMessengerSettingsBase
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.update
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.settings.AppearanceSettingsViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.AppearanceSettingsViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import org.fuchss.matrix.tacit.settings.readTacitWindowCloseBehavior
import org.fuchss.matrix.tacit.settings.writeTacitWindowCloseBehavior
import org.koin.core.component.get

internal enum class TacitLanguageSelection {
    SYSTEM,
    ENGLISH,
    GERMAN,
}

internal interface TacitAppearanceSettingsViewModel : AppearanceSettingsViewModel {
    val languageSelection: StateFlow<TacitLanguageSelection>
    val windowCloseBehavior: StateFlow<TacitWindowCloseBehavior>
    fun setLanguageSelection(selection: TacitLanguageSelection)
    fun setWindowCloseBehavior(behavior: TacitWindowCloseBehavior)
}

internal object TacitAppearanceSettingsViewModelFactory : AppearanceSettingsViewModelFactory {
    override fun create(
        viewModelContext: ViewModelContext,
        onCloseAppearanceSettings: () -> Unit,
    ): AppearanceSettingsViewModel {
        val delegate = AppearanceSettingsViewModelFactory.create(
            viewModelContext = viewModelContext,
            onCloseAppearanceSettings = onCloseAppearanceSettings,
        )
        return TacitAppearanceSettingsViewModelImpl(
            delegate = delegate,
            viewModelContext = viewModelContext,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TacitAppearanceSettingsViewModelImpl(
    private val delegate: AppearanceSettingsViewModel,
    viewModelContext: ViewModelContext,
) : TacitAppearanceSettingsViewModel, AppearanceSettingsViewModel by delegate, ViewModelContext by viewModelContext {
    private val settings = get<MatrixMessengerSettingsHolder>()

    override val languageSelection: StateFlow<TacitLanguageSelection> =
        settings.mapLatest { it.base.preferredLang.toTacitLanguageSelection() }
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(),
                settings.value.base.preferredLang.toTacitLanguageSelection(),
            )

    override val windowCloseBehavior: StateFlow<TacitWindowCloseBehavior> =
        settings.mapLatest { readTacitWindowCloseBehavior(it) }
            .stateIn(
                coroutineScope,
                SharingStarted.WhileSubscribed(),
                readTacitWindowCloseBehavior(settings.value),
            )

    override fun setLanguageSelection(selection: TacitLanguageSelection) {
        coroutineScope.launch {
            settings.update<MatrixMessengerSettingsBase> {
                it.copy(preferredLang = selection.toPreferredLangCode())
            }
        }
    }

    override fun setWindowCloseBehavior(behavior: TacitWindowCloseBehavior) {
        coroutineScope.launch {
            settings.update {
                writeTacitWindowCloseBehavior(behavior)
            }
        }
    }
}

private fun String?.toTacitLanguageSelection(): TacitLanguageSelection = when (this?.lowercase()) {
    DefaultLanguages.EN.code -> TacitLanguageSelection.ENGLISH
    DefaultLanguages.DE.code -> TacitLanguageSelection.GERMAN
    else -> TacitLanguageSelection.SYSTEM
}

private fun TacitLanguageSelection.toPreferredLangCode(): String? = when (this) {
    TacitLanguageSelection.SYSTEM -> null
    TacitLanguageSelection.ENGLISH -> DefaultLanguages.EN.code
    TacitLanguageSelection.GERMAN -> DefaultLanguages.DE.code
}
