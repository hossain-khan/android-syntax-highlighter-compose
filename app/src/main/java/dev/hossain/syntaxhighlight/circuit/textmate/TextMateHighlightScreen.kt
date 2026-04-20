package dev.hossain.syntaxhighlight.circuit.textmate

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import dev.hossain.syntaxhighlight.R
import dev.hossain.syntaxhighlight.data.samples.CodeSamples
import dev.hossain.syntaxhighlight.di.ApplicationContext
import dev.textmate.compose.CodeHighlighter
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.regex.JoniOnigLib
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlin.time.measureTimedValue

/** A code sample paired with its TextMate grammar asset path. */
data class TextMateSample(
    val label: String,
    val grammarAsset: String,
    val code: String,
)

/** Code samples that have corresponding TextMate grammar files in `assets/grammars/`. */
private val textMateSamples: List<TextMateSample> =
    buildList {
        CodeSamples.all.forEach { sample ->
            val grammarAsset =
                when (sample.label) {
                    "Kotlin" -> "grammars/kotlin.tmLanguage.json"
                    "JSON" -> "grammars/JSON.tmLanguage.json"
                    "JavaScript" -> "grammars/JavaScript.tmLanguage.json"
                    else -> null
                }
            if (grammarAsset != null) {
                add(TextMateSample(sample.label, grammarAsset, sample.code))
            }
        }
    }

@Parcelize
data object TextMateHighlightScreen : Screen {
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
            val grammar: Grammar,
            val darkTheme: Theme,
            val lightTheme: Theme,
            val isDark: Boolean,
            override val eventSink: (Event) -> Unit,
        ) : State
    }

    sealed interface Event : CircuitUiEvent {
        data object NavigateBack : Event

        data class SampleSelected(
            val sample: TextMateSample,
        ) : Event

        data object ToggleTheme : Event
    }
}

@AssistedInject
class TextMateHighlightPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        @param:ApplicationContext private val context: Context,
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

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    try {
                        val onigLib = JoniOnigLib()
                        val loaded =
                            textMateSamples.associate { sample ->
                                val raw =
                                    context.assets
                                        .open(sample.grammarAsset)
                                        .use { GrammarReader.readGrammar(it) }
                                sample.label to Grammar(raw.scopeName, raw, onigLib)
                            }
                        val dark =
                            context.assets.open("themes/dark_vs.json").use { base ->
                                context.assets.open("themes/dark_plus.json").use { overlay ->
                                    ThemeReader.readTheme(base, overlay)
                                }
                            }
                        val light =
                            context.assets.open("themes/light_vs.json").use { base ->
                                context.assets.open("themes/light_plus.json").use { overlay ->
                                    ThemeReader.readTheme(base, overlay)
                                }
                            }
                        grammarMap = loaded
                        darkTheme = dark
                        lightTheme = light
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to load grammar or theme files"
                    }
                }
            }

            val eventSink: (TextMateHighlightScreen.Event) -> Unit = { event ->
                when (event) {
                    TextMateHighlightScreen.Event.NavigateBack -> navigator.pop()
                    is TextMateHighlightScreen.Event.SampleSelected -> selectedSample = event.sample
                    TextMateHighlightScreen.Event.ToggleTheme -> isDark = !isDark
                }
            }

            return when {
                errorMessage != null -> {
                    TextMateHighlightScreen.State.Error(errorMessage!!, eventSink)
                }

                grammarMap != null && darkTheme != null && lightTheme != null -> {
                    val grammar =
                        grammarMap!![selectedSample.label]
                            ?: return TextMateHighlightScreen.State.Error(
                                "No grammar loaded for ${selectedSample.label}",
                                eventSink,
                            )
                    TextMateHighlightScreen.State.Ready(
                        samples = textMateSamples,
                        selectedSample = selectedSample,
                        grammar = grammar,
                        darkTheme = darkTheme!!,
                        lightTheme = lightTheme!!,
                        isDark = isDark,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                text = if (state.isDark) "Dark" else "Light",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Switch(
                                checked = state.isDark,
                                onCheckedChange = { state.eventSink(TextMateHighlightScreen.Event.ToggleTheme) },
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
    val theme = if (state.isDark) state.darkTheme else state.lightTheme
    val (annotated, duration) =
        remember(state.selectedSample.code, state.grammar, theme) {
            measureTimedValue { CodeHighlighter(state.grammar, theme).highlight(state.selectedSample.code) }
        }
    val durationMs = duration.inWholeMilliseconds
    val bgColor = remember(theme) { Color(theme.defaultStyle.background.toInt()) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        LanguageDropdown(
            samples = state.samples,
            selectedSample = state.selectedSample,
            onSampleSelected = { state.eventSink(TextMateHighlightScreen.Event.SampleSelected(it)) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            SelectionContainer {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(bgColor, shape = MaterialTheme.shapes.small)
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = annotated,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        softWrap = false,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            TextMateMetricsRow(
                durationMs = durationMs,
                code = state.selectedSample.code,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
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
        modifier = modifier.fillMaxWidth(),
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
