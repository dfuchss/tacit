package org.fuchss.matrix.tacit.views.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FormatSize
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
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.settings.AppearanceSettingsColor
import de.connect2x.trixnity.messenger.compose.view.settings.AppearanceSettingsSize
import de.connect2x.trixnity.messenger.compose.view.settings.AppearanceSettingsView
import de.connect2x.trixnity.messenger.compose.view.settings.SettingsCard
import de.connect2x.trixnity.messenger.compose.view.theme.DefaultAccentColor
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedListItemSwitch
import de.connect2x.trixnity.messenger.viewmodel.settings.AppearanceSettingsViewModel

class TacitAppearanceSettingsView : AppearanceSettingsView {
    @Composable
    override fun create(appearanceSettingsViewModel: AppearanceSettingsViewModel) {
        val i18n = DI.get<I18nView>()
        val scroll = rememberScrollState()

        val defaultAccentColor = DI.get<DefaultAccentColor>().value
        val packedAccentColor by appearanceSettingsViewModel.accentColor.collectAsState()
        val isFocusHighlighting by appearanceSettingsViewModel.isFocusHighlighting.collectAsState()
        val activeAccentColor = packedAccentColor?.let { Color(it.toULong()) } ?: defaultAccentColor

        Box(Modifier.fillMaxSize()) {
            Column {
                Header(appearanceSettingsViewModel::back, i18n.appearanceTitle())
                Box {
                    Column(Modifier.padding(10.dp).verticalScroll(scroll)) {
                        SettingsCard(title = i18n.appearanceColorsTitle(), icon = Icons.Filled.Colorize) {
                            AppearanceSettingsColor(
                                text = i18n.appearanceAccentColorHeading(),
                                defaultColor = defaultAccentColor,
                                color = activeAccentColor,
                            ) {
                                appearanceSettingsViewModel.setAccentColor(it.value.toLong())
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
