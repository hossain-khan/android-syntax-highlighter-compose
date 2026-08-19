package dev.hossain.benchmark

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hossain.highlight.engine.HighlightEngine
import dev.hossain.highlight.engine.HighlightTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Benchmarks the compose-highlight library's WebView-backed JS highlighting engine.
 *
 * The [HighlightEngine] is initialized once in [setup] (including WebView warm-up) so only
 * the [HighlightEngine.highlightBothThemes] call is measured per iteration.
 * Tests cover multiple languages and input sizes:
 * - Small: short inline snippets from [BenchmarkCodeSamples]
 * - Medium: ~100-line Kotlin snippet (5x repeat of the small sample)
 * - Large: real-world 1051-line JavaScript file loaded from assets
 *   ([BenchmarkCodeSamples.ASSET_JAVASCRIPT_LARGE])
 *
 * A [BenchmarkActivity] is launched to provide a valid [Context] for WebView creation.
 */
@RunWith(AndroidJUnit4::class)
class ComposeHighlightBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scenario: ActivityScenario<BenchmarkActivity>
    private lateinit var engine: HighlightEngine
    private lateinit var lightTheme: HighlightTheme
    private lateinit var darkTheme: HighlightTheme

    /** Real-world JavaScript source loaded from assets - used for large-input benchmarks. */
    private lateinit var javascriptLarge: String

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(BenchmarkActivity::class.java)

        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            engine = HighlightEngine(activity as Context)
            lightTheme = HighlightTheme.tomorrow()
            darkTheme = HighlightTheme.tomorrowNight()
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)

        // Initialize the engine (warm up WebView)
        runBlocking {
            engine.initialize()
        }

        // Load real-world large JavaScript source from assets - excluded from benchmarks
        scenario.onActivity { activity ->
            javascriptLarge = BenchmarkCodeSamples.loadFromAssets(activity, BenchmarkCodeSamples.ASSET_JAVASCRIPT_LARGE)
        }
    }

    @After
    fun teardown() {
        engine.destroy()
        scenario.close()
    }

    @Test
    fun highlightKotlin_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = BenchmarkCodeSamples.KOTLIN,
                    language = "kotlin",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }

    @Test
    fun highlightPython_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = BenchmarkCodeSamples.PYTHON,
                    language = "python",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }

    @Test
    fun highlightJson_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = BenchmarkCodeSamples.JSON,
                    language = "json",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }

    @Test
    fun highlightJavaScript_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = BenchmarkCodeSamples.JAVASCRIPT,
                    language = "javascript",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }

    @Test
    fun highlightKotlin_medium_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = BenchmarkCodeSamples.KOTLIN_MEDIUM,
                    language = "kotlin",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }

    @Test
    fun highlightJavaScript_large_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = javascriptLarge,
                    language = "javascript",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }
}
