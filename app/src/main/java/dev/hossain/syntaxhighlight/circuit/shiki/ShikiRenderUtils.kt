package dev.hossain.syntaxhighlight.circuit.shiki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.hossain.shiki.model.HighlightDualResponse

/**
 * Builds a Compose [AnnotatedString] from a Shiki dual-theme token response.
 *
 * Each token's [SpanStyle] color is chosen from [HighlightDualResponse.DualToken.darkColor]
 * or [HighlightDualResponse.DualToken.lightColor] based on [isDark].
 */
internal fun buildAnnotatedStringFromDualResponse(
    response: HighlightDualResponse,
    isDark: Boolean,
): AnnotatedString =
    buildAnnotatedString {
        response.tokens.forEachIndexed { lineIndex, line ->
            line.forEach { token ->
                val hex = if (isDark) token.darkColor else token.lightColor
                val color = parseHexColor(hex)
                withStyle(SpanStyle(color = color)) {
                    append(token.text)
                }
            }
            if (lineIndex < response.tokens.lastIndex) {
                append("\n")
            }
        }
    }

/**
 * Returns a code-block background color appropriate for the Shiki response.
 *
 * The Shiki service does not return an explicit background color; we approximate with
 * well-known GitHub dark/light surface values.
 */
internal fun resolveShikiBackgroundColor(isDark: Boolean): Color = if (isDark) Color(0xFF0D1117) else Color(0xFFF6F8FA)

/** Parses a 6- or 8-digit hex color string (with or without a leading `#`). */
internal fun parseHexColor(hex: String): Color {
    val clean = hex.trimStart('#')
    return when (clean.length) {
        6 -> Color(android.graphics.Color.parseColor("#$clean"))
        8 -> Color(android.graphics.Color.parseColor("#$clean"))
        else -> Color.Unspecified
    }
}
