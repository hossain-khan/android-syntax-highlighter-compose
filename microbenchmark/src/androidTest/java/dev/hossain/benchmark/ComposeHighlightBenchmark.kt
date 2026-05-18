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

@RunWith(AndroidJUnit4::class)
class ComposeHighlightBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var scenario: ActivityScenario<BenchmarkActivity>
    private lateinit var engine: HighlightEngine
    private lateinit var lightTheme: HighlightTheme
    private lateinit var darkTheme: HighlightTheme

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(BenchmarkActivity::class.java)

        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            engine = HighlightEngine(activity as Context)
            lightTheme = HighlightTheme.tomorrow(activity)
            darkTheme = HighlightTheme.tomorrowNight(activity)
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)

        // Initialize the engine (warm up WebView)
        runBlocking {
            engine.initialize()
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
    fun highlightKotlin_large_bothThemes() {
        benchmarkRule.measureRepeated {
            runBlocking {
                engine.highlightBothThemes(
                    code = BenchmarkCodeSamples.KOTLIN_LARGE,
                    language = "kotlin",
                    lightTheme = lightTheme,
                    darkTheme = darkTheme,
                )
            }
        }
    }
}
