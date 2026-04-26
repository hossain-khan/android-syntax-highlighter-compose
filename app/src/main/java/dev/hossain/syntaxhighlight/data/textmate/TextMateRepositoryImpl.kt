package dev.hossain.syntaxhighlight.data.textmate

import android.content.Context
import dev.hossain.syntaxhighlight.di.ApplicationContext
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production implementation of [TextMateRepository] that reads grammar and theme
 * files from the app's `assets/` directory.
 *
 * All I/O is performed on [Dispatchers.IO] so callers can invoke these functions
 * from any coroutine context without blocking the main thread.
 *
 * Note: [@Inject][dev.zacsweers.metro.Inject] is implicit on
 * [@ContributesBinding][ContributesBinding] as of Metro 0.10.0.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class TextMateRepositoryImpl
    constructor(
        @ApplicationContext private val context: Context,
    ) : TextMateRepository {
        override suspend fun loadGrammars(samples: List<TextMateSample>): Map<String, Grammar> =
            withContext(Dispatchers.IO) {
                val onigLib = JoniOnigLib()
                samples.associate { sample ->
                    val raw =
                        context.assets
                            .open(sample.grammarAsset)
                            .use { GrammarReader.readGrammar(it) }
                    sample.label to Grammar(raw.scopeName, raw, onigLib)
                }
            }

        override suspend fun loadThemePair(pair: TextMateThemePair): Pair<Theme, Theme> =
            withContext(Dispatchers.IO) {
                val dark =
                    context.assets.open(pair.darkBaseAsset).use { base ->
                        context.assets.open(pair.darkOverlayAsset).use { overlay ->
                            ThemeReader.readTheme(base, overlay)
                        }
                    }
                val light =
                    context.assets.open(pair.lightBaseAsset).use { base ->
                        context.assets.open(pair.lightOverlayAsset).use { overlay ->
                            ThemeReader.readTheme(base, overlay)
                        }
                    }
                dark to light
            }
    }
