package dev.hossain.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hossain.shiki.model.DualToken
import dev.hossain.shiki.model.HighlightDualResponse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks the [AnnotatedString] building step for Shiki server-side highlight responses.
 *
 * The Shiki pipeline has two phases: (1) network call to the Shiki token service, and
 * (2) converting the returned [HighlightDualResponse] tokens into a Compose [AnnotatedString].
 * This benchmark isolates phase 2 using synthetic mock responses.
 *
 * NOTE - future improvement: [buildAnnotatedStringFromDualResponse] and [parseHexColor] below
 * are local copies of the production functions in `ShikiRenderUtils.kt` (`:app` module). They
 * exist here because those functions are `internal` and `:microbenchmark` cannot depend on
 * `:app`. To ensure this benchmark always tests the real production logic, consider extracting
 * these utilities into a shared module (e.g., `:shiki-render`) that both `:app` and
 * `:microbenchmark` can depend on.
 */
@RunWith(AndroidJUnit4::class)
class ShikiAnnotationBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var smallResponse: HighlightDualResponse
    private lateinit var largeResponse: HighlightDualResponse

    @Before
    fun setup() {
        smallResponse = buildMockDualResponse(lineCount = 20, tokensPerLine = 5)
        largeResponse = buildMockDualResponse(lineCount = 200, tokensPerLine = 8)
    }

    @Test
    fun buildAnnotatedString_small_dark() {
        benchmarkRule.measureRepeated {
            buildAnnotatedStringFromDualResponse(smallResponse, isDark = true)
        }
    }

    @Test
    fun buildAnnotatedString_small_light() {
        benchmarkRule.measureRepeated {
            buildAnnotatedStringFromDualResponse(smallResponse, isDark = false)
        }
    }

    @Test
    fun buildAnnotatedString_large_dark() {
        benchmarkRule.measureRepeated {
            buildAnnotatedStringFromDualResponse(largeResponse, isDark = true)
        }
    }

    @Test
    fun buildAnnotatedString_large_light() {
        benchmarkRule.measureRepeated {
            buildAnnotatedStringFromDualResponse(largeResponse, isDark = false)
        }
    }

    private fun buildAnnotatedStringFromDualResponse(
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

    private fun parseHexColor(hex: String): Color {
        val clean = hex.trimStart('#')
        return when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> Color.Unspecified
        }
    }

    private fun buildMockDualResponse(
        lineCount: Int,
        tokensPerLine: Int,
    ): HighlightDualResponse {
        val keywords = listOf("val ", "fun ", "class ", "import ", "return ")
        val colors = listOf("#569CD6", "#DCDCAA", "#D4D4D4", "#CE9178", "#4EC9B0")
        val lightColors = listOf("#0000FF", "#795E26", "#000000", "#A31515", "#267F99")

        val tokens = (0 until lineCount).map { lineIdx ->
            (0 until tokensPerLine).map { tokenIdx ->
                DualToken(
                    text = keywords[tokenIdx % keywords.size] + "token${lineIdx}_$tokenIdx ",
                    darkColor = colors[tokenIdx % colors.size],
                    lightColor = lightColors[tokenIdx % lightColors.size],
                )
            }
        }
        return HighlightDualResponse(
            language = "kotlin",
            darkTheme = "one-dark-pro",
            lightTheme = "github-light",
            tokens = tokens,
        )
    }
}
