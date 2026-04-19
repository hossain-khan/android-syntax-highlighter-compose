package dev.hossain.syntaxhighlight.data.shiki

import dev.hossain.shiki.ShikiClient
import dev.hossain.shiki.model.HighlightDualRequest
import dev.hossain.shiki.model.HighlightDualResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

private const val SHIKI_BASE_URL = "https://syntax-highlight.gohk.xyz"

/**
 * Production implementation of [ShikiRepository] that calls the Shiki Token Service.
 *
 * Uses [ShikiClient.highlightDual] to fetch tokens with both dark and light theme colors
 * in a single network request, avoiding double round-trips on theme changes.
 *
 * Note: [@Inject][dev.zacsweers.metro.Inject] is implicit on [@ContributesBinding][ContributesBinding]
 * as of Metro 0.10.0.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ShikiRepositoryImpl
    constructor() : ShikiRepository {
        private val client = ShikiClient(baseUrl = SHIKI_BASE_URL)

        override suspend fun highlightDual(
            code: String,
            language: String,
            darkTheme: String,
            lightTheme: String,
        ): Result<HighlightDualResponse> =
            client.highlightDual(
                HighlightDualRequest(
                    code = code,
                    language = language,
                    darkTheme = darkTheme,
                    lightTheme = lightTheme,
                ),
            )
    }
