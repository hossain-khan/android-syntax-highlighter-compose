package dev.hossain.syntaxhighlight.circuit.composehighlight

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
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
import com.slack.circuit.runtime.screen.Screen
import dev.hossain.highlight.engine.HighlightTheme
import dev.hossain.highlight.ui.rememberHighlightedCodeBothThemes
import dev.hossain.syntaxhighlight.R
import dev.hossain.syntaxhighlight.data.samples.CodeSample
import dev.hossain.syntaxhighlight.data.samples.CodeSamples
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** Named light/dark [HighlightTheme] pairs available on this screen. */
enum class ComposeHighlightThemePair(
    val label: String,
) {
    TOMORROW("Tomorrow"),
    ATOM_ONE("Atom One"),
}

/**
 * Screen that demonstrates on-device syntax highlighting using the
 * [compose-highlight](https://github.com/hossain-khan/android-compose-highlight) library,
 * which runs [Highlight.js](https://highlightjs.org/) inside a hidden WebView and converts
 * the tokenised HTML output to a native Compose [AnnotatedString].
 *
 * No network calls are made; all highlighting happens on-device via a single [dev.hossain.highlight.engine.HighlightEngine]
 * instance. Timing reflects the full WebView JS round-trip.
 */
@Parcelize
data object ComposeHighlightScreen : Screen {
    @Stable
    sealed interface State : CircuitUiState {
        val eventSink: (Event) -> Unit

        data class Ready(
            val samples: List<CodeSample>,
            val selectedSample: CodeSample,
            val selectedThemePair: ComposeHighlightThemePair,
            val isDark: Boolean,
            override val eventSink: (Event) -> Unit,
        ) : State
    }

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data object NavigateBack : Event

        data class SampleSelected(
            val sample: CodeSample,
        ) : Event

        data class ThemePairSelected(
            val pair: ComposeHighlightThemePair,
        ) : Event

        data object ToggleTheme : Event
    }
}

/**
 * Presenter for [ComposeHighlightScreen].
 *
 * Manages the selected code sample, theme pair, and light/dark toggle. No I/O — all
 * highlighting is done in the UI composable using [rememberHighlightEngine].
 */
@AssistedInject
class ComposeHighlightPresenter
    constructor(
        @Assisted private val navigator: Navigator,
    ) : Presenter<ComposeHighlightScreen.State> {
        @Composable
        override fun present(): ComposeHighlightScreen.State {
            val systemDark = isSystemInDarkTheme()
            var selectedSample by rememberRetained { mutableStateOf(CodeSamples.all.first()) }
            var selectedThemePair by rememberRetained { mutableStateOf(ComposeHighlightThemePair.TOMORROW) }
            var isDark by rememberRetained { mutableStateOf(systemDark) }

            return ComposeHighlightScreen.State.Ready(
                samples = CodeSamples.all,
                selectedSample = selectedSample,
                selectedThemePair = selectedThemePair,
                isDark = isDark,
                eventSink = { event ->
                    when (event) {
                        ComposeHighlightScreen.Event.NavigateBack -> navigator.pop()
                        is ComposeHighlightScreen.Event.SampleSelected -> selectedSample = event.sample
                        is ComposeHighlightScreen.Event.ThemePairSelected -> selectedThemePair = event.pair
                        ComposeHighlightScreen.Event.ToggleTheme -> isDark = !isDark
                    }
                },
            )
        }

        @CircuitInject(ComposeHighlightScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): ComposeHighlightPresenter
        }
    }

@CircuitInject(screen = ComposeHighlightScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeHighlight(
    state: ComposeHighlightScreen.State,
    modifier: Modifier = Modifier,
) {
    val readyState = state as? ComposeHighlightScreen.State.Ready
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Compose Highlight") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ComposeHighlightScreen.Event.NavigateBack) }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24dp),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (readyState != null) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("code", readyState.selectedSample.code)),
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
                                checked = readyState.isDark,
                                onCheckedChange = { state.eventSink(ComposeHighlightScreen.Event.ToggleTheme) },
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
        if (readyState != null) {
            ReadyContent(readyState, innerPadding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    state: ComposeHighlightScreen.State.Ready,
    innerPadding: PaddingValues,
) {
    val context = LocalContext.current

    val (lightTheme, darkTheme) =
        remember(state.selectedThemePair) {
            when (state.selectedThemePair) {
                ComposeHighlightThemePair.TOMORROW -> {
                    HighlightTheme.tomorrow(context) to HighlightTheme.tomorrowNight(context)
                }

                ComposeHighlightThemePair.ATOM_ONE -> {
                    HighlightTheme.atomOneLight(context) to HighlightTheme.atomOneDark(context)
                }
            }
        }
    val hlLanguage = state.selectedSample.toHighlightJsLanguage()

    var highlightMs by remember { mutableStateOf<Long?>(null) }

    // Single JS call produces both light + dark AnnotatedStrings; theme switching is instant.
    val themedResult by rememberHighlightedCodeBothThemes(
        code = state.selectedSample.code,
        language = hlLanguage,
        lightTheme = lightTheme,
        darkTheme = darkTheme,
        onHighlightComplete = { durationMs -> highlightMs = durationMs },
    )

    val annotatedCode = if (state.isDark) themedResult?.dark else themedResult?.light
    val activeTheme = if (state.isDark) darkTheme else lightTheme
    val bgColor = activeTheme.backgroundColor.takeIf { it != Color.Unspecified } ?: Color(0xFF1E1E1E)
    // No explicit textColor needed — since v0.5.0 HtmlToAnnotatedString embeds the .hljs base
    // text color as a full-range span in the AnnotatedString, so plain tokens no longer inherit
    // LocalContentColor from MaterialTheme.

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
            SampleDropdown(
                samples = state.samples,
                selectedSample = state.selectedSample,
                onSampleSelected = { state.eventSink(ComposeHighlightScreen.Event.SampleSelected(it)) },
                modifier = Modifier.weight(1f),
            )
            ThemePairDropdown(
                pairs = ComposeHighlightThemePair.entries,
                selected = state.selectedThemePair,
                onSelect = { state.eventSink(ComposeHighlightScreen.Event.ThemePairSelected(it)) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (annotatedCode == null) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(bgColor, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = annotatedCode,
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
        }

        HorizontalDivider()
        ComposeHighlightMetricsRow(
            highlightMs = highlightMs,
            code = state.selectedSample.code,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Maps a [CodeSample] label to the corresponding Highlight.js language identifier. */
private fun CodeSample.toHighlightJsLanguage(): String =
    when (label) {
        "Kotlin" -> "kotlin"
        "Python" -> "python"
        "JSON" -> "json"
        "JavaScript" -> "javascript"
        else -> label.lowercase()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleDropdown(
    samples: List<CodeSample>,
    selectedSample: CodeSample,
    onSampleSelected: (CodeSample) -> Unit,
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
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
private fun ThemePairDropdown(
    pairs: List<ComposeHighlightThemePair>,
    selected: ComposeHighlightThemePair,
    onSelect: (ComposeHighlightThemePair) -> Unit,
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
private fun ComposeHighlightMetricsRow(
    highlightMs: Long?,
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
            text = if (highlightMs != null) "⏱ ${highlightMs}ms" else "⏱ …",
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
