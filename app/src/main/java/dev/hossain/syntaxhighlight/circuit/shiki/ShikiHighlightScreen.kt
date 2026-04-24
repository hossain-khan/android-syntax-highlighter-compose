package dev.hossain.syntaxhighlight.circuit.shiki

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.slack.circuit.runtime.screen.Screen
import dev.hossain.shiki.model.HighlightDualResponse
import dev.hossain.shiki.model.Theme
import dev.hossain.syntaxhighlight.R
import dev.hossain.syntaxhighlight.data.samples.CodeSample
import dev.hossain.syntaxhighlight.data.samples.CodeSamples
import dev.hossain.syntaxhighlight.data.shiki.ShikiRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.time.measureTimedValue

@Parcelize
data object ShikiHighlightScreen : Screen {
    @Stable
    sealed interface State : CircuitUiState {
        val selectedSample: CodeSample
        val selectedThemePair: ThemePair
        val availableSamples: List<CodeSample>
        val availableThemePairs: List<ThemePair>
        val eventSink: (Event) -> Unit

        data class Loading(
            override val selectedSample: CodeSample,
            override val selectedThemePair: ThemePair,
            override val availableSamples: List<CodeSample>,
            override val availableThemePairs: List<ThemePair>,
            override val eventSink: (Event) -> Unit,
        ) : State

        data class Success(
            override val selectedSample: CodeSample,
            override val selectedThemePair: ThemePair,
            override val availableSamples: List<CodeSample>,
            override val availableThemePairs: List<ThemePair>,
            val response: HighlightDualResponse,
            val requestDurationMs: Long,
            override val eventSink: (Event) -> Unit,
        ) : State

        data class Error(
            override val selectedSample: CodeSample,
            override val selectedThemePair: ThemePair,
            override val availableSamples: List<CodeSample>,
            override val availableThemePairs: List<ThemePair>,
            val message: String,
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
            val themePair: ThemePair,
        ) : Event

        data object Retry : Event
    }

    /** A named pair of dark + light themes for the Shiki dual endpoint. */
    data class ThemePair(
        val label: String,
        val dark: String,
        val light: String,
    )
}

val defaultThemePairs =
    listOf(
        ShikiHighlightScreen.ThemePair("GitHub", Theme.GITHUB_DARK, Theme.GITHUB_LIGHT),
        ShikiHighlightScreen.ThemePair("One Dark Pro / Min Light", Theme.ONE_DARK_PRO, Theme.MIN_LIGHT),
        ShikiHighlightScreen.ThemePair("Dracula / GitHub Light", Theme.DRACULA, Theme.GITHUB_LIGHT),
    )

@AssistedInject
class ShikiHighlightPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        private val shikiRepository: ShikiRepository,
    ) : Presenter<ShikiHighlightScreen.State> {
        @Composable
        override fun present(): ShikiHighlightScreen.State {
            var selectedSample by rememberRetained { mutableStateOf(CodeSamples.all.first()) }
            var selectedThemePair by rememberRetained {
                mutableStateOf(defaultThemePairs.first { it.dark == Theme.ONE_DARK_PRO })
            }
            var response by rememberRetained { mutableStateOf<HighlightDualResponse?>(null) }
            var requestDurationMs by rememberRetained { mutableStateOf(0L) }
            var errorMessage by rememberRetained { mutableStateOf<String?>(null) }
            var retryTrigger by rememberRetained { mutableStateOf(0) }

            LaunchedEffect(selectedSample, selectedThemePair, retryTrigger) {
                response = null
                errorMessage = null
                // Measure end-to-end network request time: from sending the highlight request
                // to receiving the tokenized response. This includes HTTP round-trip latency
                // and server-side Shiki tokenization time.
                val startMs = System.currentTimeMillis()
                shikiRepository
                    .highlightDual(
                        code = selectedSample.code,
                        language = selectedSample.language,
                        darkTheme = selectedThemePair.dark,
                        lightTheme = selectedThemePair.light,
                    ).onSuccess {
                        requestDurationMs = System.currentTimeMillis() - startMs
                        response = it
                    }.onFailure { errorMessage = it.message ?: "Unknown error" }
            }

            val eventSink: (ShikiHighlightScreen.Event) -> Unit = { event ->
                when (event) {
                    ShikiHighlightScreen.Event.NavigateBack -> {
                        navigator.pop()
                    }

                    is ShikiHighlightScreen.Event.SampleSelected -> {
                        selectedSample = event.sample
                    }

                    is ShikiHighlightScreen.Event.ThemePairSelected -> {
                        selectedThemePair = event.themePair
                    }

                    ShikiHighlightScreen.Event.Retry -> {
                        retryTrigger++
                    }
                }
            }

            val common =
                Triple(
                    selectedSample,
                    selectedThemePair,
                    eventSink,
                )

            return when {
                errorMessage != null -> {
                    ShikiHighlightScreen.State.Error(
                        selectedSample = common.first,
                        selectedThemePair = common.second,
                        availableSamples = CodeSamples.all,
                        availableThemePairs = defaultThemePairs,
                        message = errorMessage!!,
                        eventSink = common.third,
                    )
                }

                response != null -> {
                    ShikiHighlightScreen.State.Success(
                        selectedSample = common.first,
                        selectedThemePair = common.second,
                        availableSamples = CodeSamples.all,
                        availableThemePairs = defaultThemePairs,
                        response = response!!,
                        requestDurationMs = requestDurationMs,
                        eventSink = common.third,
                    )
                }

                else -> {
                    ShikiHighlightScreen.State.Loading(
                        selectedSample = common.first,
                        selectedThemePair = common.second,
                        availableSamples = CodeSamples.all,
                        availableThemePairs = defaultThemePairs,
                        eventSink = common.third,
                    )
                }
            }
        }

        @CircuitInject(ShikiHighlightScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): ShikiHighlightPresenter
        }
    }

@CircuitInject(screen = ShikiHighlightScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShikiHighlight(
    state: ShikiHighlightScreen.State,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Shiki Server") },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ShikiHighlightScreen.Event.NavigateBack) }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_back_24dp),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (state is ShikiHighlightScreen.State.Success) {
                        IconButton(onClick = { state.eventSink(ShikiHighlightScreen.Event.Retry) }) {
                            Icon(
                                painter = painterResource(R.drawable.refresh_24dp),
                                contentDescription = "Refresh",
                            )
                        }
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
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SampleDropdown(
                    samples = state.availableSamples,
                    selected = state.selectedSample,
                    onSelect = { state.eventSink(ShikiHighlightScreen.Event.SampleSelected(it)) },
                    modifier = Modifier.weight(1f),
                )
                ThemePairDropdown(
                    pairs = state.availableThemePairs,
                    selected = state.selectedThemePair,
                    onSelect = { state.eventSink(ShikiHighlightScreen.Event.ThemePairSelected(it)) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            when (state) {
                is ShikiHighlightScreen.State.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is ShikiHighlightScreen.State.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { state.eventSink(ShikiHighlightScreen.Event.Retry) }) {
                            Icon(
                                painter = painterResource(R.drawable.refresh_24dp),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Plain text (highlighting unavailable)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.selectedSample.code,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                    ),
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .horizontalScroll(rememberScrollState())
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                            )
                        }
                    }
                }

                is ShikiHighlightScreen.State.Success -> {
                    val isDark = isSystemInDarkTheme()
                    // Measure local annotation-build time separately from the network request.
                    // total = network (requestDurationMs) + client-side AnnotatedString construction.
                    val (annotated, annotationDuration) =
                        remember(state.response, isDark) {
                            measureTimedValue { buildAnnotatedStringFromDualResponse(state.response, isDark) }
                        }
                    val totalDurationMs = state.requestDurationMs + annotationDuration.inWholeMilliseconds
                    val bgColor =
                        remember(isDark) {
                            resolveShikiBackgroundColor(isDark)
                        }
                    Column(modifier = Modifier.fillMaxSize()) {
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
                                        .background(bgColor)
                                        .horizontalScroll(rememberScrollState())
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                            )
                        }
                        HorizontalDivider()
                        MetricsRow(
                            networkDurationMs = state.requestDurationMs,
                            totalDurationMs = totalDurationMs,
                            code = state.selectedSample.code,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun MetricsRow(
    networkDurationMs: Long,
    totalDurationMs: Long,
    code: String,
    modifier: Modifier = Modifier,
) {
    val lines = code.lines().size
    val chars = code.length
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cloud icon = server-side network request time (HTTP round-trip + Shiki tokenization)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.cloud_24dp),
                contentDescription = "Network request time",
                modifier = Modifier.height(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${networkDurationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Total = network time + client-side AnnotatedString construction
        Text(
            text = "⏱ ${totalDurationMs}ms",
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

// ---------------------------------------------------------------------------
// Dropdown composables
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
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            singleLine = true,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePairDropdown(
    pairs: List<ShikiHighlightScreen.ThemePair>,
    selected: ShikiHighlightScreen.ThemePair,
    onSelect: (ShikiHighlightScreen.ThemePair) -> Unit,
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
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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
