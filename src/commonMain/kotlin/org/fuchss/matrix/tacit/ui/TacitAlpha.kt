package org.fuchss.matrix.tacit.ui

/** Named alpha values that repeat across Tacit's composables or carry a specific meaning. */
internal object TacitAlpha {
    /** Soft accent fill (e.g. highlighted list entries, secondary container). */
    const val accentSoft = 0.22f

    /** Accent fill for selected / primary surfaces. */
    const val accentFill = 0.25f

    /** Stronger accent fill (selected row, primary container). */
    const val accentSoftStrong = 0.35f

    /** Quoted / replied-to text is dimmed slightly against regular message text. */
    const val quotedText = 0.82f

    /** Accent ring drawn around a keyboard-focused custom control. */
    const val focusRing = 0.8f

    /** Emphasised border: selected cards, warning borders, scrims. */
    const val borderEmphasis = 0.65f

    /** Faint outline around quiet cards, dividers and indicators. */
    const val borderSubtle = 0.45f

    /** Content of a disabled control. */
    const val disabled = 0.45f
}
