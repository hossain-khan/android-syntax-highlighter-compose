package dev.hossain.syntaxhighlight.circuit.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.hossain.shiki.model.HighlightDualResponse
import dev.hossain.shiki.model.Theme
import dev.hossain.syntaxhighlight.R
import dev.hossain.syntaxhighlight.circuit.shiki.buildAnnotatedStringFromDualResponse
import dev.hossain.syntaxhighlight.circuit.shiki.resolveShikiBackgroundColor
import dev.hossain.syntaxhighlight.data.samples.CodeSample
import dev.hossain.syntaxhighlight.data.samples.CodeSamples
import dev.hossain.syntaxhighlight.data.shiki.ShikiRepository
import dev.hossain.syntaxhighlight.data.textmate.TextMateRepository
import dev.hossain.syntaxhighlight.data.textmate.defaultTextMateThemePairs
import dev.hossain.syntaxhighlight.data.textmate.textMateSamples
import dev.textmate.compose.CodeHighlighter
import dev.textmate.grammar.Grammar
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlin.time.measureTimedValue

/**
 * Approximate bundled grammar file sizes in bytes, keyed by [CodeSample.label].
 * Used in the device-footprint section of the info card.
 */
private val grammarSizeByLabel: Map<String, Long> =
    mapOf(
        "Kotlin" to 18_000L,
        "Python" to 99_000L,
        "JSON" to 5_000L,
        "JavaScript" to 98_000L,
    )

/** Approximate size of bundled VS Dark+/VS Light+ theme assets in bytes (~25 KB). */
private const val THEME_ASSETS_BYTES = 25_000L

/** Approximate size of bundled kotlin-textmate library JARs in bytes (~172 KB). */
private const val LIBRARY_BYTES = 172_000L

/** Samples that have both a Shiki language ID and a bundled TextMate grammar. */
private val comparisonSamples: List<CodeSample> =
    CodeSamples.all.filter { sample -> textMateSamples.any { it.label == sample.label } }

/**
 * Screen that renders a side-by-side comparison of both syntax highlighting approaches:
 * the cloud-based [Shiki Token Service][dev.hossain.syntaxhighlight.data.shiki.ShikiRepository]
 * and the on-device [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate) library.
 *
 * The screen shows the same code snippet tokenized by both approaches, together with timing
 * metrics and a device-footprint breakdown so users can easily evaluate the trade-offs.
 */
@Parcelize
data object ComparisonScreen : Screen {
    @Stable
    data class State(
        val availableSamples: List<CodeSample>,
        val selectedSample: CodeSample,
        val isDark: Boolean,
        val shikiState: ShikiState,
        val textMateState: TextMateState,
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    /**
     * Per-highlight state for the cloud (Shiki) side.
     * [Success] stores the raw API response so the UI can derive the [AnnotatedString]
     * with `remember(response, isDark)` — avoiding stale colors on theme changes.
     */
    sealed interface ShikiState {
        data object Loading : ShikiState

        data class Success(
            val response: HighlightDualResponse,
            /** Time for the network request to the Shiki token service. */
            val networkMs: Long,
        ) : ShikiState

        data class Error(
            val message: String,
        ) : ShikiState
    }

    /**
     * Per-highlight state for the on-device (TextMate) side.
     * Stores loaded [Grammar] and both [darkTheme]/[lightTheme] so the UI can pick the
     * correct one with `remember(grammar, theme)` — avoiding stale colors on theme changes.
     */
    sealed interface TextMateState {
        data object Loading : TextMateState

        data class Success(
            val grammar: Grammar,
            val darkTheme: dev.textmate.theme.Theme,
            val lightTheme: dev.textmate.theme.Theme,
        ) : TextMateState

        data class Error(
            val message: String,
        ) : TextMateState
    }

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data object NavigateBack : Event

        data class SampleSelected(
            val sample: CodeSample,
        ) : Event

        data object RetryShiki : Event

        data object RetryTextMate : Event
    }
}

/**
 * Presenter for [ComparisonScreen].
 *
 * Concurrently fetches Shiki tokens via [ShikiRepository] and loads TextMate grammars/themes
 * via [TextMateRepository], then exposes both results through a single [ComparisonScreen.State].
 *
 * Key responsibilities:
 * - Calls [ShikiRepository.highlightDual] whenever the selected code sample changes
 * - Loads all bundled [Grammar] files once on first composition via [TextMateRepository.loadGrammars]
 * - Loads the One Dark Pro / Quiet Light theme pair via [TextMateRepository.loadThemePair]
 * - Tracks independent retry counters for Shiki and TextMate to allow per-side retries
 * - Measures both Shiki network latency and TextMate tokenization time for comparison metrics
 */
@AssistedInject
class ComparisonPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        private val shikiRepository: ShikiRepository,
        private val textMateRepository: TextMateRepository,
    ) : Presenter<ComparisonScreen.State> {
        @Composable
        override fun present(): ComparisonScreen.State {
            val isDark = isSystemInDarkTheme()
            var selectedSample by rememberRetained { mutableStateOf(comparisonSamples.first()) }
            var shikiState by rememberRetained { mutableStateOf<ComparisonScreen.ShikiState>(ComparisonScreen.ShikiState.Loading) }
            var grammarMap by rememberRetained { mutableStateOf<Map<String, Grammar>?>(null) }
            var textMateThemes by rememberRetained { mutableStateOf<Pair<dev.textmate.theme.Theme, dev.textmate.theme.Theme>?>(null) }
            var textMateState by rememberRetained { mutableStateOf<ComparisonScreen.TextMateState>(ComparisonScreen.TextMateState.Loading) }
            var shikiRetry by rememberRetained { mutableIntStateOf(0) }
            var textMateRetry by rememberRetained { mutableIntStateOf(0) }

            // Load all grammars + One Dark Pro / Quiet Light theme pair once on first composition.
            LaunchedEffect(textMateRetry) {
                try {
                    grammarMap = textMateRepository.loadGrammars(textMateSamples)
                    val pair = defaultTextMateThemePairs.first { it.darkOverlayAsset.contains("one_dark_pro") }
                    textMateThemes = textMateRepository.loadThemePair(pair)
                } catch (e: Exception) {
                    textMateState = ComparisonScreen.TextMateState.Error(e.message ?: "Failed to load grammars or themes")
                }
            }

            // Tokenize with TextMate whenever the sample or grammars/themes change.
            LaunchedEffect(selectedSample, grammarMap, textMateThemes) {
                val map = grammarMap ?: return@LaunchedEffect
                val themes = textMateThemes ?: return@LaunchedEffect
                textMateState = ComparisonScreen.TextMateState.Loading
                withContext(Dispatchers.Default) {
                    try {
                        val grammar =
                            map[selectedSample.label]
                                ?: throw IllegalStateException("No grammar for ${selectedSample.label}")
                        textMateState =
                            ComparisonScreen.TextMateState.Success(
                                grammar = grammar,
                                darkTheme = themes.first,
                                lightTheme = themes.second,
                            )
                    } catch (e: Exception) {
                        textMateState = ComparisonScreen.TextMateState.Error(e.message ?: "Tokenization failed")
                    }
                }
            }

            // Call the Shiki API whenever the sample changes or a retry is triggered.
            LaunchedEffect(selectedSample, shikiRetry) {
                shikiState = ComparisonScreen.ShikiState.Loading
                val (result, elapsed) =
                    measureTimedValue {
                        shikiRepository.highlightDual(
                            code = selectedSample.code,
                            language = selectedSample.language,
                            darkTheme = Theme.ONE_DARK_PRO,
                            lightTheme = Theme.MIN_LIGHT,
                        )
                    }
                shikiState =
                    result.fold(
                        onSuccess = { response ->
                            ComparisonScreen.ShikiState.Success(
                                response = response,
                                networkMs = elapsed.inWholeMilliseconds,
                            )
                        },
                        onFailure = { e ->
                            ComparisonScreen.ShikiState.Error(e.message ?: "Network request failed")
                        },
                    )
            }

            val eventSink: (ComparisonScreen.Event) -> Unit = { event ->
                when (event) {
                    ComparisonScreen.Event.NavigateBack -> navigator.pop()
                    is ComparisonScreen.Event.SampleSelected -> selectedSample = event.sample
                    ComparisonScreen.Event.RetryShiki -> shikiRetry++
                    ComparisonScreen.Event.RetryTextMate -> textMateRetry++
                }
            }

            return ComparisonScreen.State(
                availableSamples = comparisonSamples,
                selectedSample = selectedSample,
                isDark = isDark,
                shikiState = shikiState,
                textMateState = textMateState,
                eventSink = eventSink,
            )
        }

        @CircuitInject(ComparisonScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): ComparisonPresenter
        }
    }

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

@CircuitInject(screen = ComparisonScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Comparison(
    state: ComparisonScreen.State,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Compare Approaches") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ComparisonScreen.Event.NavigateBack) }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24dp),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SampleDropdown(
                    samples = state.availableSamples,
                    selected = state.selectedSample,
                    onSelect = { state.eventSink(ComparisonScreen.Event.SampleSelected(it)) },
                )
            }

            item {
                ApproachSectionHeader(
                    icon = R.drawable.cloud_24dp,
                    label = "Cloud — Shiki Token Service",
                )
            }

            item {
                ShikiApproachCard(
                    sample = state.selectedSample,
                    shikiState = state.shikiState,
                    isDark = state.isDark,
                    onRetry = { state.eventSink(ComparisonScreen.Event.RetryShiki) },
                )
            }

            item {
                ApproachSectionHeader(
                    icon = R.drawable.cloud_off_24dp,
                    label = "On-Device — TextMate (kotlin-textmate)",
                )
            }

            item {
                TextMateApproachCard(
                    sample = state.selectedSample,
                    textMateState = state.textMateState,
                    isDark = state.isDark,
                    onRetry = { state.eventSink(ComparisonScreen.Event.RetryTextMate) },
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ApproachSectionHeader(
    icon: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ---------------------------------------------------------------------------
// Shiki approach card
// ---------------------------------------------------------------------------

@Composable
private fun ShikiApproachCard(
    sample: CodeSample,
    shikiState: ComparisonScreen.ShikiState,
    isDark: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (shikiState) {
                is ComparisonScreen.ShikiState.Loading -> {
                    LoadingContent(label = "Requesting from Shiki server…")
                }

                is ComparisonScreen.ShikiState.Error -> {
                    ErrorContent(message = shikiState.message, onRetry = onRetry)
                }

                is ComparisonScreen.ShikiState.Success -> {
                    // Build AnnotatedString in the UI layer so theme changes auto-recompute.
                    val (annotated, renderDuration) =
                        remember(shikiState.response, isDark) {
                            measureTimedValue {
                                buildAnnotatedStringFromDualResponse(shikiState.response, isDark)
                            }
                        }
                    val renderMs = renderDuration.inWholeMilliseconds
                    val bgColor = remember(isDark) { resolveShikiBackgroundColor(isDark) }

                    CodePreview(annotated = annotated, bgColor = bgColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    ShikiInfoCard(
                        sample = sample,
                        networkMs = shikiState.networkMs,
                        renderMs = renderMs,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TextMate approach card
// ---------------------------------------------------------------------------

@Composable
private fun TextMateApproachCard(
    sample: CodeSample,
    textMateState: ComparisonScreen.TextMateState,
    isDark: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (textMateState) {
                is ComparisonScreen.TextMateState.Loading -> {
                    LoadingContent(label = "Loading grammars and themes…")
                }

                is ComparisonScreen.TextMateState.Error -> {
                    ErrorContent(message = textMateState.message, onRetry = onRetry)
                }

                is ComparisonScreen.TextMateState.Success -> {
                    val theme = if (isDark) textMateState.darkTheme else textMateState.lightTheme
                    // Tokenization measured here with remember so it re-runs only when inputs change,
                    // not on every recomposition.
                    val (annotated, tokenizeDuration) =
                        remember(sample.code, textMateState.grammar, theme) {
                            measureTimedValue {
                                CodeHighlighter(textMateState.grammar, theme).highlight(sample.code)
                            }
                        }
                    val tokenizeMs = tokenizeDuration.inWholeMilliseconds
                    val bgColor = remember(theme) { Color(theme.defaultStyle.background.toInt()) }

                    CodePreview(annotated = annotated, bgColor = bgColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    TextMateInfoCard(
                        sample = sample,
                        tokenizeMs = tokenizeMs,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Code preview
// ---------------------------------------------------------------------------

@Composable
private fun CodePreview(
    annotated: androidx.compose.ui.text.AnnotatedString,
    bgColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .background(bgColor, shape = MaterialTheme.shapes.small)
                .padding(10.dp)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = annotated,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            softWrap = false,
        )
    }
}

// ---------------------------------------------------------------------------
// Info cards
// ---------------------------------------------------------------------------

@Composable
private fun ShikiInfoCard(
    sample: CodeSample,
    networkMs: Long,
    renderMs: Long,
    modifier: Modifier = Modifier,
) {
    val totalMs = networkMs + renderMs
    InfoCard(modifier = modifier) {
        InfoSection(title = "Performance") {
            InfoRow(label = "☁️  Network request", value = "${networkMs}ms")
            InfoRow(label = "🔧  Build AnnotatedString", value = "${renderMs}ms")
            InfoRow(label = "⏱  Total", value = "${totalMs}ms", emphasized = true)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        InfoSection(title = "Device Footprint") {
            InfoRow(label = "📦  App storage", value = "0 KB")
            InfoSubtitle("Grammar & themes processed on server — nothing bundled in the APK.")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        InfoSection(title = "Capabilities") {
            InfoRow(label = "📡  Internet", value = "Required")
            InfoRow(label = "🌐  Languages", value = "200+ (server-side)")
            InfoRow(label = "🎨  Themes", value = "50+ (server-side)")
        }
    }
}

@Composable
private fun TextMateInfoCard(
    sample: CodeSample,
    tokenizeMs: Long,
    modifier: Modifier = Modifier,
) {
    val grammarKb = (grammarSizeByLabel[sample.label] ?: 0L) / 1000L
    val themeKb = THEME_ASSETS_BYTES / 1000L
    val libKb = LIBRARY_BYTES / 1000L
    val totalKb = grammarKb + themeKb + libKb
    InfoCard(modifier = modifier) {
        InfoSection(title = "Performance") {
            InfoRow(label = "⏱  Tokenization", value = "${tokenizeMs}ms", emphasized = true)
            InfoSubtitle("Pure CPU — no I/O after initial grammar load.")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        InfoSection(title = "Device Footprint (approx.)") {
            InfoRow(label = "📄  Grammar (${sample.label})", value = "~$grammarKb KB")
            InfoRow(label = "🎨  Themes (One Dark Pro / Quiet Light)", value = "~$themeKb KB")
            InfoRow(label = "📚  Library JARs", value = "~$libKb KB")
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            InfoRow(label = "📦  Total for this sample", value = "~$totalKb KB", emphasized = true)
            InfoSubtitle("Additional languages add ~5–99 KB each. All 8 theme JSONs add ~113 KB total.")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        InfoSection(title = "Capabilities") {
            InfoRow(label = "📡  Internet", value = "Not required")
            InfoRow(label = "🌐  Languages", value = "TextMate grammar compatible")
            InfoRow(label = "🎨  Themes", value = "VS Code JSON compatible")
        }
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    content()
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style =
                if (emphasized) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
        )
    }
}

@Composable
private fun InfoSubtitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(top = 2.dp, bottom = 2.dp),
    )
}

// ---------------------------------------------------------------------------
// Loading / Error states
// ---------------------------------------------------------------------------

@Composable
private fun LoadingContent(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Icon(painter = painterResource(R.drawable.refresh_24dp), contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Retry")
        }
    }
}

// ---------------------------------------------------------------------------
// Dropdown
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleDropdown(
    samples: List<CodeSample>,
    selected: CodeSample,
    onSelect: (CodeSample) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            samples.forEach { sample ->
                DropdownMenuItem(
                    text = { Text(sample.label) },
                    onClick = {
                        onSelect(sample)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
