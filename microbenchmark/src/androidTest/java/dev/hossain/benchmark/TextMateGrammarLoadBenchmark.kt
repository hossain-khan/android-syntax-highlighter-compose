package dev.hossain.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.ThemeReader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks the cost of loading TextMate grammar and theme files from assets.
 *
 * Grammar and theme loading is a one-time startup cost that happens before any highlighting
 * can occur. These benchmarks measure how long each file takes to parse so we can track
 * regressions and compare grammar complexity (e.g., Python vs Kotlin vs JavaScript).
 *
 * [JoniOnigLib] is intentionally created outside [BenchmarkRule.measureRepeated] so that
 * only the grammar parse/construction time is included in the measurement.
 */
@RunWith(AndroidJUnit4::class)
class TextMateGrammarLoadBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun loadKotlinGrammar() {
        val onigLib = JoniOnigLib()
        benchmarkRule.measureRepeated {
            context.assets.open("grammars/kotlin.tmLanguage.json").use { stream ->
                val raw = GrammarReader.readGrammar(stream)
                Grammar(raw.scopeName, raw, onigLib)
            }
        }
    }

    @Test
    fun loadPythonGrammar() {
        val onigLib = JoniOnigLib()
        benchmarkRule.measureRepeated {
            context.assets.open("grammars/python.tmLanguage.json").use { stream ->
                val raw = GrammarReader.readGrammar(stream)
                Grammar(raw.scopeName, raw, onigLib)
            }
        }
    }

    @Test
    fun loadJsonGrammar() {
        val onigLib = JoniOnigLib()
        benchmarkRule.measureRepeated {
            context.assets.open("grammars/JSON.tmLanguage.json").use { stream ->
                val raw = GrammarReader.readGrammar(stream)
                Grammar(raw.scopeName, raw, onigLib)
            }
        }
    }

    @Test
    fun loadJavaScriptGrammar() {
        val onigLib = JoniOnigLib()
        benchmarkRule.measureRepeated {
            context.assets.open("grammars/JavaScript.tmLanguage.json").use { stream ->
                val raw = GrammarReader.readGrammar(stream)
                Grammar(raw.scopeName, raw, onigLib)
            }
        }
    }

    @Test
    fun loadDarkPlusTheme() {
        benchmarkRule.measureRepeated {
            context.assets.open("themes/dark_vs.json").use { base ->
                context.assets.open("themes/dark_plus.json").use { overlay ->
                    ThemeReader.readTheme(base, overlay)
                }
            }
        }
    }

    @Test
    fun loadOneDarkProTheme() {
        benchmarkRule.measureRepeated {
            context.assets.open("themes/dark_vs.json").use { base ->
                context.assets.open("themes/one_dark_pro.json").use { overlay ->
                    ThemeReader.readTheme(base, overlay)
                }
            }
        }
    }

    @Test
    fun loadMonokaiTheme() {
        benchmarkRule.measureRepeated {
            context.assets.open("themes/dark_vs.json").use { base ->
                context.assets.open("themes/monokai.json").use { overlay ->
                    ThemeReader.readTheme(base, overlay)
                }
            }
        }
    }
}
