package dev.hossain.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.textmate.compose.CodeHighlighter
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks the TextMate on-device syntax highlighting pipeline.
 *
 * Grammars and themes are loaded once in [setup] so only the [CodeHighlighter.highlight] call
 * is measured. Tests cover multiple languages and input sizes:
 * - Small: short inline snippets from [BenchmarkCodeSamples]
 * - Medium: ~100-line Kotlin snippet (5x repeat of the small sample)
 * - Large: real-world 1051-line JavaScript file loaded from assets
 *   ([BenchmarkCodeSamples.ASSET_JAVASCRIPT_LARGE])
 *
 * Note: [CodeHighlighter] is constructed inside [measureRepeated] to reflect the actual app
 * usage pattern. If [CodeHighlighter] becomes reusable across calls, hoist it to a field and
 * update these benchmarks accordingly.
 */
@RunWith(AndroidJUnit4::class)
class TextMateHighlightBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var kotlinGrammar: Grammar
    private lateinit var pythonGrammar: Grammar
    private lateinit var jsonGrammar: Grammar
    private lateinit var jsGrammar: Grammar
    private lateinit var darkTheme: Theme
    private lateinit var lightTheme: Theme
    private lateinit var oneDarkProTheme: Theme

    /** Real-world JavaScript source loaded from assets - used for large-input benchmarks. */
    private lateinit var javascriptLarge: String

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val assets = context.assets
        val onigLib = JoniOnigLib()

        kotlinGrammar = assets.open("grammars/kotlin.tmLanguage.json").use { stream ->
            val raw = GrammarReader.readGrammar(stream)
            Grammar(raw.scopeName, raw, onigLib)
        }
        pythonGrammar = assets.open("grammars/python.tmLanguage.json").use { stream ->
            val raw = GrammarReader.readGrammar(stream)
            Grammar(raw.scopeName, raw, onigLib)
        }
        jsonGrammar = assets.open("grammars/JSON.tmLanguage.json").use { stream ->
            val raw = GrammarReader.readGrammar(stream)
            Grammar(raw.scopeName, raw, onigLib)
        }
        jsGrammar = assets.open("grammars/JavaScript.tmLanguage.json").use { stream ->
            val raw = GrammarReader.readGrammar(stream)
            Grammar(raw.scopeName, raw, onigLib)
        }

        darkTheme = assets.open("themes/dark_vs.json").use { base ->
            assets.open("themes/dark_plus.json").use { overlay ->
                ThemeReader.readTheme(base, overlay)
            }
        }
        lightTheme = assets.open("themes/light_vs.json").use { base ->
            assets.open("themes/light_plus.json").use { overlay ->
                ThemeReader.readTheme(base, overlay)
            }
        }
        oneDarkProTheme = assets.open("themes/dark_vs.json").use { base ->
            assets.open("themes/one_dark_pro.json").use { overlay ->
                ThemeReader.readTheme(base, overlay)
            }
        }

        // Load real-world large JavaScript source from assets - excluded from benchmarks
        javascriptLarge = BenchmarkCodeSamples.loadFromAssets(context, BenchmarkCodeSamples.ASSET_JAVASCRIPT_LARGE)
    }

    @Test
    fun highlightKotlin_small() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(kotlinGrammar, darkTheme).highlight(BenchmarkCodeSamples.KOTLIN)
        }
    }

    @Test
    fun highlightKotlin_medium() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(kotlinGrammar, darkTheme).highlight(BenchmarkCodeSamples.KOTLIN_MEDIUM)
        }
    }

    @Test
    fun highlightJavaScript_large() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(jsGrammar, darkTheme).highlight(javascriptLarge)
        }
    }

    @Test
    fun highlightPython_small() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(pythonGrammar, darkTheme).highlight(BenchmarkCodeSamples.PYTHON)
        }
    }

    @Test
    fun highlightJson_small() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(jsonGrammar, darkTheme).highlight(BenchmarkCodeSamples.JSON)
        }
    }

    @Test
    fun highlightJavaScript_small() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(jsGrammar, darkTheme).highlight(BenchmarkCodeSamples.JAVASCRIPT)
        }
    }

    @Test
    fun highlightKotlin_lightTheme() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(kotlinGrammar, lightTheme).highlight(BenchmarkCodeSamples.KOTLIN)
        }
    }

    @Test
    fun highlightKotlin_oneDarkPro() {
        benchmarkRule.measureRepeated {
            CodeHighlighter(kotlinGrammar, oneDarkProTheme).highlight(BenchmarkCodeSamples.KOTLIN)
        }
    }
}
