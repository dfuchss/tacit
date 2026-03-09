package org.fuchss.matrix.tacit

import androidx.compose.ui.graphics.Color
import de.connect2x.trixnity.messenger.compose.view.common.contrastByLuminance
import de.connect2x.trixnity.messenger.compose.view.common.deriveFromHue
import de.connect2x.trixnity.messenger.compose.view.common.hue

private val tacitDefaultAccentColor = Color(0xFF3BB2FA)
private var tacitAccentHue = tacitDefaultAccentColor.hue

internal fun updateTacitAccentColor(color: Color) {
    tacitAccentHue = color.hue
}

private fun Color.tacitize(): Color = deriveFromHue(tacitAccentHue)
private fun tacit(color: Color): Color = color.tacitize()

internal val tacitDefaultAccent: Color
    get() = tacitDefaultAccentColor
internal val accentColor: Color
    get() = tacit(tacitDefaultAccentColor)

internal val tacitShell: Color
    get() = tacit(Color(0xFF080E0B))
internal val tacitLayer: Color
    get() = tacit(Color(0xFF101914))
internal val tacitPanel: Color
    get() = tacit(Color(0xFF14231B))
internal val tacitPanelHigh: Color
    get() = tacit(Color(0xFF1A2E23))
internal val tacitBorder: Color
    get() = tacit(Color(0xFF264235))
internal val tacitText: Color
    get() = tacit(Color(0xFFEAF7ED))
internal val tacitTextMuted: Color
    get() = tacit(Color(0xFFA2C1AE))
internal val tacitAccentSoft: Color
    get() = tacit(Color(0xFFC9F7B8))

internal val tacitCommonBannerBg: Color
    get() = tacit(Color(0xFF1A3125))
internal val tacitCommonBannerBorder: Color
    get() = tacit(Color(0xFF335F45))
internal val tacitCommonBannerText: Color
    get() = tacit(Color(0xFFEAF7ED))

internal val tacitWarningBannerBg = Color(0xFF3A2812)
internal val tacitWarningBannerBorder = Color(0xFF8B5E26)
internal val tacitWarningBannerText = Color(0xFFFFE3B5)
internal val tacitErrorBannerBg = Color(0xFF341616)
internal val tacitErrorBannerBorder = Color(0xFFC24A4A)
internal val tacitErrorBannerText = Color(0xFFFFD9D9)

internal val tacitLabelSubtle: Color
    get() = tacit(Color(0xFF8FB39A))
internal val tacitToolbarBackground: Color
    get() = tacitLayer
internal val tacitInputBackground: Color
    get() = tacitLayer
internal val tacitInputBackgroundHover: Color
    get() = tacitPanelHigh
internal val tacitInputPlaceholder: Color
    get() = tacitTextMuted

internal val tacitItemSelectedBackground: Color
    get() = tacit(Color(0xFF2D8D41))
internal val tacitItemHoverBackground: Color
    get() = tacitPanelHigh
internal val tacitItemUnreadBackground: Color
    get() = tacitPanelHigh
internal val tacitTextOnSelected: Color
    get() = tacitText
internal val tacitTextSubtle: Color
    get() = tacitTextMuted
internal val tacitBadgeSuccessBackground: Color
    get() = accentColor
internal val tacitBadgeSuccessContent: Color
    get() = tacitBadgeSuccessBackground.contrastByLuminance(
        brightColor = tacitText,
        darkColor = tacitShell,
    )

internal val tacitCardBackground: Color
    get() = tacitPanelHigh
internal val tacitCardBackgroundAlt: Color
    get() = tacitLayer

internal val tacitHeroGradientStart: Color
    get() = tacitPanel
internal val tacitHeroGradientMiddle: Color
    get() = tacitPanelHigh
internal val tacitHeroGradientEnd: Color
    get() = tacitLayer
internal val tacitDialogGradientMiddle: Color
    get() = tacitPanelHigh
internal val tacitDialogGradientEnd: Color
    get() = tacitLayer
internal val tacitChipBackground: Color
    get() = tacitLayer
internal val tacitSearchResultBackground: Color
    get() = tacitLayer
internal val tacitSearchResultBorder: Color
    get() = tacitBorder
internal val tacitDialogChannelText: Color
    get() = tacitText

internal val tacitRailGradientStart: Color
    get() = tacitShell
internal val tacitRailGradientMiddle: Color
    get() = tacitLayer
internal val tacitRailGradientEnd: Color
    get() = tacitShell
internal val tacitRailButtonBackground: Color
    get() = tacitLayer
internal val tacitRailButtonHoverBackground: Color
    get() = tacitPanelHigh
internal val tacitRailIconTint: Color
    get() = tacitText

internal val tacitActionDisabledBackground: Color
    get() = tacitLayer
internal val tacitActionPrimaryHoverBackground: Color
    get() = accentColor
internal val tacitActionPrimaryBackground: Color
    get() = tacit(Color(0xFF2D8D41))
internal val tacitActionHoverBackground: Color
    get() = tacitPanelHigh
internal val tacitActionBackground: Color
    get() = tacitLayer
internal val tacitActionDisabledBorder: Color
    get() = tacitBorder
internal val tacitActionPrimaryBorder: Color
    get() = accentColor
internal val tacitActionBorder: Color
    get() = tacitBorder
internal val tacitActionDisabledIconTint: Color
    get() = tacitTextMuted
internal val tacitActionPrimaryIconTint: Color
    get() = tacitText
internal val tacitActionIconTint: Color
    get() = tacitText

internal val tacitMessageHoverBackground: Color
    get() = tacit(Color(0x22233A2A))
internal val tacitMessageOwnHoverBackground: Color
    get() = tacit(Color(0x1E2B8B3E))
internal val tacitMessageQuickActionsBackground: Color
    get() = tacit(Color(0xFF12241B))
internal val tacitMessageQuickActionsBorder: Color
    get() = tacit(Color(0xFF2A5842))
internal val tacitMessageReactionsRowOwnBackground: Color
    get() = tacit(Color(0xFF162B21))
internal val tacitMessageReactionsRowBackground: Color
    get() = tacit(Color(0xFF102218))
internal val tacitMessageReactionsRowBorder: Color
    get() = tacit(Color(0xFF2A4C3A))
internal val tacitReactionPickerButtonBackground: Color
    get() = tacit(Color(0xFF1A3126))
internal val tacitReactionPickerButtonBorder: Color
    get() = tacit(Color(0xFF2B6044))
internal val tacitReactionChipSelectedBackground: Color
    get() = tacit(Color(0xFF28513D))
internal val tacitReactionChipBackground: Color
    get() = tacit(Color(0xFF182D22))
internal val tacitReactionChipSelectedBorder: Color
    get() = tacit(Color(0xFF5FD78B))
internal val tacitReactionChipBorder: Color
    get() = tacit(Color(0xFF2F5742))
internal val tacitReactionCountSelectedBackground: Color
    get() = tacit(Color(0x3346D47E))
internal val tacitReactionCountBackground: Color
    get() = tacit(Color(0xFF203528))

internal val tacitMembersButtonActiveBackground: Color
    get() = tacit(Color(0xFF2B5A43))
internal val tacitMembersButtonHoverBackground: Color
    get() = tacit(Color(0xFF203127))
internal val tacitMembersButtonBackground: Color
    get() = tacit(Color(0xFF18261F))
internal val tacitMembersButtonActiveBorder: Color
    get() = tacit(Color(0xFF62C890))
internal val tacitMembersButtonHoverBorder: Color
    get() = tacit(Color(0xFF3D644F))
internal val tacitMembersButtonBorder: Color
    get() = tacit(Color(0xFF2E4E3C))
internal val tacitMembersButtonActiveIconTint: Color
    get() = tacit(Color(0xFFE8FFF1))
internal val tacitMembersButtonIconTint: Color
    get() = tacit(Color(0xFFCFE8D8))
