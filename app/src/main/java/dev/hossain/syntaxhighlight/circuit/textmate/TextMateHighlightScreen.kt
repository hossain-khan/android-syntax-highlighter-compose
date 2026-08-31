package dev.hossain.syntaxhighlight.circuit.textmate

import android.content.ClipData
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.ParcelableScreen
import dev.hossain.syntaxhighlight.R
import dev.hossain.syntaxhighlight.data.textmate.TextMateRepository
import dev.hossain.syntaxhighlight.data.textmate.TextMateSample
import dev.hossain.syntaxhighlight.data.textmate.TextMateThemePair
import dev.hossain.syntaxhighlight.data.textmate.defaultTextMateThemePairs
import dev.hossain.syntaxhighlight.data.textmate.textMateSamples
import dev.textmate.compose.CodeHighlighter
import dev.textmate.grammar.Grammar
import dev.textmate.theme.Theme
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlin.time.measureTimedValue

/**
 * Screen that demonstrates fully on-device syntax highlighting using
 * [kotlin-textmate](https://github.com/ivan-magda/kotlin-textmate).
 *
 * Grammar files (`.tmLanguage.json`) and theme files are loaded from the app's `assets/`
 * directory on a background thread. Once loaded, [dev.textmate.compose.CodeHighlighter]
 * tokenizes the selected code snippet entirely on-device — no network connection is required.
 *
 * ## Available kotlin-textmate Compose APIs
 *
 * The library ships three levels of API for rendering highlighted code:
 *
 * - **[dev.textmate.compose.CodeBlock]** — highest-level drop-in composable. Handles
 *   background color, horizontal scroll, padding, and [androidx.compose.foundation.text.selection.SelectionContainer]
 *   automatically. Best for static, single-theme displays.
 * - **[dev.textmate.compose.rememberHighlightedCode]** — `@Composable` wrapper around
 *   [dev.textmate.compose.CodeHighlighter] that uses `remember(code, grammar, theme)` to cache
 *   the [androidx.compose.ui.text.AnnotatedString]. Useful when you need full control over
 *   the `Text` call but still want the caching handled for you.
 * - **[dev.textmate.compose.CodeHighlighter]** — lowest-level API. Tokenizes synchronously
 *   and returns an [androidx.compose.ui.text.AnnotatedString]. Use when you need to call
 *   highlighting outside of composition (e.g., in a `LaunchedEffect` on a background
 *   dispatcher) or when you need to produce multiple `AnnotatedString`s in one pass.
 *
 * This screen uses [dev.textmate.compose.CodeHighlighter] directly because:
 * 1. It pre-builds **both** dark and light [androidx.compose.ui.text.AnnotatedString]s in a
 *    single `LaunchedEffect` on [kotlinx.coroutines.Dispatchers.Default], so theme toggling
 *    is instantaneous with no re-tokenization.
 * 2. [dev.textmate.compose.rememberHighlightedCode] is `@Composable` and therefore cannot be
 *    called from a presenter; it also re-tokenizes on every theme change.
 * 3. The tokenization duration is captured via `measureTimedValue` for the metrics row.
 */
@Parcelize
data object TextMateHighlightScreen : ParcelableScreen {
    @Stable
    sealed interface State : CircuitUiState {
        val eventSink: (Event) -> Unit

        data class Loading(
            override val eventSink: (Event) -> Unit,
        ) : State

        data class Error(
            val message: String,
            override val eventSink: (Event) -> Unit,
        ) : State

        data class Ready(
            val samples: List<TextMateSample>,
            val selectedSample: TextMateSample,
            /** Pre-built on [kotlinx.coroutines.Dispatchers.Default]; null while tokenization is in progress. */
            val annotatedDark: AnnotatedString?,
            val annotatedLight: AnnotatedString?,
            val bgColorDark: Color,
            val bgColorLight: Color,
            val tokenizeDurationMs: Long,
            val isDark: Boolean,
            val availableThemePairs: List<TextMateThemePair>,
            val selectedThemePair: TextMateThemePair,
            override val eventSink: (Event) -> Unit,
        ) : State
    }

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data object NavigateBack : Event

        data class SampleSelected(
            val sample: TextMateSample,
        ) : Event

        data object ToggleTheme : Event

        data class ThemePairSelected(
            val pair: TextMateThemePair,
        ) : Event
    }
}

/**
 * Presenter for [TextMateHighlightScreen].
 *
 * Delegates asset loading to [TextMateRepository], which keeps file I/O out of the presenter.
 * Uses two independent [LaunchedEffect]s:
 * - **Grammar loading** (runs once on first composition): loads all grammar files via
 *   [TextMateRepository.loadGrammars].
 * - **Theme loading** (re-runs on theme pair change): loads the selected theme pair via
 *   [TextMateRepository.loadThemePair], resetting to null first to show a brief loading state.
 *
 * State transitions:
 * - [TextMateHighlightScreen.State.Loading] until both grammars and themes are loaded
 * - [TextMateHighlightScreen.State.Ready] once all assets are available
 * - [TextMateHighlightScreen.State.Error] if any asset fails to load
 */
@AssistedInject
class TextMateHighlightPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        private val textMateRepository: TextMateRepository,
    ) : Presenter<TextMateHighlightScreen.State> {
        @Composable
        override fun present(): TextMateHighlightScreen.State {
            val systemDark = isSystemInDarkTheme()
            var grammarMap by rememberRetained { mutableStateOf<Map<String, Grammar>?>(null) }
            var darkTheme by rememberRetained { mutableStateOf<Theme?>(null) }
            var lightTheme by rememberRetained { mutableStateOf<Theme?>(null) }
            var errorMessage by rememberRetained { mutableStateOf<String?>(null) }
            var selectedSample by rememberRetained { mutableStateOf(textMateSamples.first()) }
            var isDark by rememberRetained { mutableStateOf(systemDark) }
            var selectedThemePair by rememberRetained {
                mutableStateOf(defaultTextMateThemePairs.first { it.darkOverlayAsset.contains("one_dark_pro") })
            }
            var annotatedDark by rememberRetained { mutableStateOf<AnnotatedString?>(null) }
            var annotatedLight by rememberRetained { mutableStateOf<AnnotatedString?>(null) }
            var bgColorDark by rememberRetained { mutableStateOf(Color.Unspecified) }
            var bgColorLight by rememberRetained { mutableStateOf(Color.Unspecified) }
            var tokenizeDurationMs by rememberRetained { mutableStateOf(0L) }

            // Load all grammars once on first composition.
            LaunchedEffect(Unit) {
                try {
                    grammarMap = textMateRepository.loadGrammars(textMateSamples)
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to load grammar files"
                }
            }

            // Reload dark and light themes whenever the selected theme pair changes. Reset to null
            // first so the UI shows a brief loading state rather than stale colors from the old theme.
            LaunchedEffect(selectedThemePair) {
                darkTheme = null
                lightTheme = null
                try {
                    val (dark, light) = textMateRepository.loadThemePair(selectedThemePair)
                    darkTheme = dark
                    lightTheme = light
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to load theme files"
                }
            }

            // Tokenize on Dispatchers.Default whenever the sample, grammars, or themes change.
            // [CodeHighlighter] is used directly here instead of the higher-level
            // [dev.textmate.compose.rememberHighlightedCode] or [dev.textmate.compose.CodeBlock]
            // for three reasons:
            //  1. Both dark and light AnnotatedStrings are pre-built in one pass so that theme
            //     switching is instantaneous — no re-tokenization on toggle.
            //  2. rememberHighlightedCode is @Composable and cannot be called from a presenter;
            //     it also re-runs whenever the theme key changes.
            //  3. measureTimedValue captures the tokenization duration for the metrics row.
            LaunchedEffect(selectedSample, grammarMap, darkTheme, lightTheme) {
                val map = grammarMap ?: return@LaunchedEffect
                val dark = darkTheme ?: return@LaunchedEffect
                val light = lightTheme ?: return@LaunchedEffect
                val grammar =
                    map[selectedSample.label] ?: run {
                        errorMessage = "No grammar loaded for ${selectedSample.label}"
                        return@LaunchedEffect
                    }
                // Clear previous results to show a brief in-content loading state.
                annotatedDark = null
                annotatedLight = null
                // Compute off the main thread; assign state after withContext returns so that
                // CancellationException prevents stale writes if this LaunchedEffect is cancelled.
                val computed =
                    withContext(Dispatchers.Default) {
                        val (darkResult, duration) =
                            measureTimedValue { CodeHighlighter(grammar, dark).highlight(selectedSample.code) }
                        val lightResult = CodeHighlighter(grammar, light).highlight(selectedSample.code)
                        object {
                            val annotatedDark = darkResult
                            val annotatedLight = lightResult
                            val bgColorDark = Color(dark.defaultStyle.background.toInt())
                            val bgColorLight = Color(light.defaultStyle.background.toInt())
                            val tokenizeDurationMs = duration.inWholeMilliseconds
                        }
                    }
                annotatedDark = computed.annotatedDark
                annotatedLight = computed.annotatedLight
                bgColorDark = computed.bgColorDark
                bgColorLight = computed.bgColorLight
                tokenizeDurationMs = computed.tokenizeDurationMs
            }

            // Remembered so its identity is stable across recompositions; prevents false
            // inequality in the State data classes that include eventSink.
            val eventSink: (TextMateHighlightScreen.Event) -> Unit =
                remember {
                    { event ->
                        when (event) {
                            TextMateHighlightScreen.Event.NavigateBack -> navigator.pop()
                            is TextMateHighlightScreen.Event.SampleSelected -> selectedSample = event.sample
                            TextMateHighlightScreen.Event.ToggleTheme -> isDark = !isDark
                            is TextMateHighlightScreen.Event.ThemePairSelected -> selectedThemePair = event.pair
                        }
                    }
                }

            return when {
                errorMessage != null -> {
                    TextMateHighlightScreen.State.Error(errorMessage!!, eventSink)
                }

                grammarMap != null && darkTheme != null && lightTheme != null -> {
                    TextMateHighlightScreen.State.Ready(
                        samples = textMateSamples,
                        selectedSample = selectedSample,
                        annotatedDark = annotatedDark,
                        annotatedLight = annotatedLight,
                        bgColorDark = bgColorDark,
                        bgColorLight = bgColorLight,
                        tokenizeDurationMs = tokenizeDurationMs,
                        isDark = isDark,
                        availableThemePairs = defaultTextMateThemePairs,
                        selectedThemePair = selectedThemePair,
                        eventSink = eventSink,
                    )
                }

                else -> {
                    TextMateHighlightScreen.State.Loading(eventSink)
                }
            }
        }

        @CircuitInject(TextMateHighlightScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): TextMateHighlightPresenter
        }
    }

@CircuitInject(screen = TextMateHighlightScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMateHighlight(
    state: TextMateHighlightScreen.State,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("TextMate Highlighting") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(TextMateHighlightScreen.Event.NavigateBack) }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24dp),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state is TextMateHighlightScreen.State.Ready) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("code", state.selectedSample.code)),
                                )
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.content_copy_24dp),
                                contentDescription = "Copy code",
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.light_mode_24dp),
                                contentDescription = "Light mode",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = state.isDark,
                                onCheckedChange = { state.eventSink(TextMateHighlightScreen.Event.ToggleTheme) },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Icon(
                                painter = painterResource(R.drawable.dark_mode_24dp),
                                contentDescription = "Dark mode",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (state) {
            is TextMateHighlightScreen.State.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading grammars and themes…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is TextMateHighlightScreen.State.Error -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is TextMateHighlightScreen.State.Ready -> {
                ReadyContent(state, innerPadding)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    state: TextMateHighlightScreen.State.Ready,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val annotated = if (state.isDark) state.annotatedDark else state.annotatedLight
    val bgColor = if (state.isDark) state.bgColorDark else state.bgColorLight

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageDropdown(
                samples = state.samples,
                selectedSample = state.selectedSample,
                onSampleSelected = { state.eventSink(TextMateHighlightScreen.Event.SampleSelected(it)) },
                modifier = Modifier.weight(1f),
            )
            TextMateThemePairDropdown(
                pairs = state.availableThemePairs,
                selected = state.selectedThemePair,
                onSelect = { state.eventSink(TextMateHighlightScreen.Event.ThemePairSelected(it)) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (annotated == null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotated,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(bgColor, shape = MaterialTheme.shapes.small)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                )
            }

            HorizontalDivider()
            TextMateMetricsRow(
                durationMs = state.tokenizeDurationMs,
                code = state.selectedSample.code,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    samples: List<TextMateSample>,
    selectedSample: TextMateSample,
    onSampleSelected: (TextMateSample) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedSample.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            samples.forEach { sample ->
                DropdownMenuItem(
                    text = { Text(sample.label) },
                    onClick = {
                        onSampleSelected(sample)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextMateThemePairDropdown(
    pairs: List<TextMateThemePair>,
    selected: TextMateThemePair,
    onSelect: (TextMateThemePair) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pairs.forEach { pair ->
                DropdownMenuItem(
                    text = { Text(pair.label) },
                    onClick = {
                        onSelect(pair)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun TextMateMetricsRow(
    durationMs: Long,
    code: String,
    modifier: Modifier = Modifier,
) {
    val lines = code.lines().size
    val chars = code.length
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "⏱ ${durationMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "↕ $lines lines",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "∑ $chars chars",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
