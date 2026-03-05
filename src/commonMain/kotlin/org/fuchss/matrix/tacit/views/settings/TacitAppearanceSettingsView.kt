package org.fuchss.matrix.tacit.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.VerticalScrollbar
import de.connect2x.trixnity.messenger.compose.view.common.Header
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.settings.AppearanceSettingsSize
import de.connect2x.trixnity.messenger.compose.view.settings.AppearanceSettingsView
import de.connect2x.trixnity.messenger.compose.view.settings.SettingsCard
import de.connect2x.trixnity.messenger.compose.view.theme.DefaultAccentColor
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedListItemRadioButton
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedListItemSwitch
import de.connect2x.trixnity.messenger.viewmodel.settings.AppearanceSettingsViewModel
import org.fuchss.matrix.tacit.viewmodel.settings.TacitAppearanceSettingsViewModel
import org.fuchss.matrix.tacit.viewmodel.settings.TacitLanguageSelection
import org.fuchss.matrix.tacit.views.common.TacitColorPicker
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

class TacitAppearanceSettingsView : AppearanceSettingsView {
    @Composable
    override fun create(appearanceSettingsViewModel: AppearanceSettingsViewModel) {
        val i18n = DI.get<TacitI18nView>()
        val scroll = rememberScrollState()

        val defaultAccentColor = DI.get<DefaultAccentColor>().value
        val packedAccentColor by appearanceSettingsViewModel.accentColor.collectAsState()
        val isFocusHighlighting by appearanceSettingsViewModel.isFocusHighlighting.collectAsState()
        val activeAccentColor = packedAccentColor?.let { Color(it.toULong()) } ?: defaultAccentColor
        val tacitAppearanceSettingsViewModel = appearanceSettingsViewModel as? TacitAppearanceSettingsViewModel
        val languageSelection by (tacitAppearanceSettingsViewModel?.languageSelection?.collectAsState()
            ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(TacitLanguageSelection.SYSTEM) })

        Box(Modifier.fillMaxSize()) {
            Column {
                Header(appearanceSettingsViewModel::back, i18n.appearanceTitle())
                Box {
                    Column(Modifier.padding(10.dp).verticalScroll(scroll)) {
                        SettingsCard(title = i18n.appearanceColorsTitle(), icon = Icons.Filled.Colorize) {
                            TacitColorPicker(
                                heading = i18n.appearanceAccentColorHeading(),
                                resetTooltip = i18n.appearanceAccentColorDefault(),
                                defaultColor = defaultAccentColor,
                                color = activeAccentColor,
                                onColorSelected = { appearanceSettingsViewModel.setAccentColor(it.value.toLong()) },
                            )
                        }
                        if (tacitAppearanceSettingsViewModel != null) {
                            SettingsCard(title = i18n.tacitLanguageTitle(), icon = Icons.Filled.Translate) {
                                ThemedListItemRadioButton(
                                    style = MaterialTheme.components.settingsItem,
                                    headlineContent = { Text(i18n.tacitLanguageSystemDefault()) },
                                    selected = languageSelection == TacitLanguageSelection.SYSTEM,
                                    onChange = {
                                        if (it) tacitAppearanceSettingsViewModel.setLanguageSelection(
                                            TacitLanguageSelection.SYSTEM
                                        )
                                    },
                                )
                                ThemedListItemRadioButton(
                                    style = MaterialTheme.components.settingsItem,
                                    headlineContent = { Text(i18n.tacitLanguageEnglish()) },
                                    selected = languageSelection == TacitLanguageSelection.ENGLISH,
                                    onChange = {
                                        if (it) tacitAppearanceSettingsViewModel.setLanguageSelection(
                                            TacitLanguageSelection.ENGLISH
                                        )
                                    },
                                )
                                ThemedListItemRadioButton(
                                    style = MaterialTheme.components.settingsItem,
                                    headlineContent = { Text(i18n.tacitLanguageGerman()) },
                                    selected = languageSelection == TacitLanguageSelection.GERMAN,
                                    onChange = {
                                        if (it) tacitAppearanceSettingsViewModel.setLanguageSelection(
                                            TacitLanguageSelection.GERMAN
                                        )
                                    },
                                )
                            }
                        }
                        SettingsCard(title = i18n.appearanceAccessibilityTitle(), icon = Icons.Filled.FormatSize) {
                            AppearanceSettingsSize(appearanceSettingsViewModel)
                            Spacer(Modifier.height(15.dp))
                            ThemedListItemSwitch(
                                style = MaterialTheme.components.settingsItem,
                                headlineContent = { Text(i18n.appearanceFocusHighlightingHeading()) },
                                supportingContent = { Text(i18n.appearanceFocusHighlightingExplanation()) },
                                selected = isFocusHighlighting,
                                onChange = { appearanceSettingsViewModel.toggleFocusHighlighting() },
                            )
                        }
                    }
                    VerticalScrollbar(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        scroll,
                    )
                }
            }
        }
    }
}
