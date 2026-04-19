package dev.hossain.syntaxhighlight.data.shiki

import dev.hossain.shiki.model.HighlightDualResponse

/** Repository for server-driven syntax highlighting via the Shiki Token Service. */
interface ShikiRepository {
    /**
     * Tokenizes [code] for the given [language] and returns tokens with both
     * [darkTheme] and [lightTheme] colors in a single network call.
     */
    suspend fun highlightDual(
        code: String,
        language: String,
        darkTheme: String,
        lightTheme: String,
    ): Result<HighlightDualResponse>
}
