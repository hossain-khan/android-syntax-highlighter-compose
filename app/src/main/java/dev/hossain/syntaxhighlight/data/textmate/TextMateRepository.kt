package dev.hossain.syntaxhighlight.data.textmate

import dev.textmate.grammar.Grammar
import dev.textmate.theme.Theme

/**
 * Repository for loading TextMate grammar and theme assets from the device's asset directory.
 *
 * Abstracts file I/O away from presenters, following clean architecture principles:
 * presenters should not perform direct I/O but delegate data access to repositories.
 *
 * See https://slackhq.github.io/circuit/presenter/
 */
interface TextMateRepository {
    /**
     * Loads TextMate grammar files for the given [samples] from `assets/grammars/`.
     *
     * @param samples the list of samples whose grammar asset paths should be loaded.
     * @return a map of [TextMateSample.label] to the parsed [Grammar].
     * @throws Exception if any grammar file cannot be found or parsed.
     */
    suspend fun loadGrammars(samples: List<TextMateSample>): Map<String, Grammar>

    /**
     * Loads a dark/light [Theme] pair from the overlay asset paths in [pair].
     *
     * Each theme is built by reading a VS Code base theme first (for fallback defaults)
     * and then overlaying the named theme on top — mirroring VS Code's theme inheritance.
     *
     * @param pair the theme pair describing which base and overlay asset files to load.
     * @return a [Pair] of `(dark Theme, light Theme)`.
     * @throws Exception if any theme file cannot be found or parsed.
     */
    suspend fun loadThemePair(pair: TextMateThemePair): Pair<Theme, Theme>
}
