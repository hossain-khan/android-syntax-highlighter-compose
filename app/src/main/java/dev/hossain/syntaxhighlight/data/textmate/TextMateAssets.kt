package dev.hossain.syntaxhighlight.data.textmate

import dev.hossain.syntaxhighlight.data.samples.CodeSamples

/** A code sample paired with its TextMate grammar asset path. */
data class TextMateSample(
    val label: String,
    val grammarAsset: String,
    val code: String,
)

/**
 * A named pair of dark + light TextMate themes. Each theme is loaded as an overlay on top of
 * the VS Code base theme (`dark_vs` / `light_vs`), which provides default token color fallbacks
 * — exactly how VS Code's own theme inheritance works.
 */
data class TextMateThemePair(
    val label: String,
    /** Base asset path for the dark variant (provides fallback defaults). */
    val darkBaseAsset: String,
    /** Overlay asset path for the dark variant (specific theme colors take precedence). */
    val darkOverlayAsset: String,
    /** Base asset path for the light variant. */
    val lightBaseAsset: String,
    /** Overlay asset path for the light variant. */
    val lightOverlayAsset: String,
)

/** Code samples that have corresponding TextMate grammar files in `assets/grammars/`. */
val textMateSamples: List<TextMateSample> =
    buildList {
        CodeSamples.all.forEach { sample ->
            val grammarAsset =
                when (sample.label) {
                    "Kotlin" -> "grammars/kotlin.tmLanguage.json"
                    "Python" -> "grammars/python.tmLanguage.json"
                    "JSON" -> "grammars/JSON.tmLanguage.json"
                    "JavaScript" -> "grammars/JavaScript.tmLanguage.json"
                    else -> null
                }
            if (grammarAsset != null) {
                add(TextMateSample(sample.label, grammarAsset, sample.code))
            }
        }
    }

/** Available theme pairs, mirroring the choice count in the Shiki screen. */
val defaultTextMateThemePairs: List<TextMateThemePair> =
    listOf(
        TextMateThemePair(
            label = "VS Dark+ / VS Light+",
            darkBaseAsset = "themes/dark_vs.json",
            darkOverlayAsset = "themes/dark_plus.json",
            lightBaseAsset = "themes/light_vs.json",
            lightOverlayAsset = "themes/light_plus.json",
        ),
        TextMateThemePair(
            label = "One Dark Pro / Quiet Light",
            darkBaseAsset = "themes/dark_vs.json",
            darkOverlayAsset = "themes/one_dark_pro.json",
            lightBaseAsset = "themes/light_vs.json",
            lightOverlayAsset = "themes/quiet_light.json",
        ),
        TextMateThemePair(
            label = "Monokai / Solarized Light",
            darkBaseAsset = "themes/dark_vs.json",
            darkOverlayAsset = "themes/monokai.json",
            lightBaseAsset = "themes/light_vs.json",
            lightOverlayAsset = "themes/solarized_light.json",
        ),
    )
