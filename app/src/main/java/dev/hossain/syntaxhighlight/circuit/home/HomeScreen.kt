package dev.hossain.syntaxhighlight.circuit.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.hossain.syntaxhighlight.R
import dev.hossain.syntaxhighlight.circuit.comparison.ComparisonScreen
import dev.hossain.syntaxhighlight.circuit.shiki.ShikiHighlightScreen
import dev.hossain.syntaxhighlight.circuit.textmate.TextMateHighlightScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.parcelize.Parcelize

@Parcelize
data object HomeScreen : Screen {
    @Stable
    data class State(
        val eventSink: (Event) -> Unit,
    ) : CircuitUiState

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data object OpenShikiHighlight : Event

        data object OpenTextMateHighlight : Event

        data object OpenComparison : Event
    }
}

@AssistedInject
class HomePresenter
    constructor(
        @Assisted private val navigator: Navigator,
    ) : Presenter<HomeScreen.State> {
        @Composable
        override fun present(): HomeScreen.State =
            HomeScreen.State { event ->
                when (event) {
                    HomeScreen.Event.OpenShikiHighlight -> navigator.goTo(ShikiHighlightScreen)
                    HomeScreen.Event.OpenTextMateHighlight -> navigator.goTo(TextMateHighlightScreen)
                    HomeScreen.Event.OpenComparison -> navigator.goTo(ComparisonScreen)
                }
            }

        @CircuitInject(HomeScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): HomePresenter
        }
    }

private data class HighlightApproach(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val event: HomeScreen.Event,
)

@CircuitInject(screen = HomeScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    state: HomeScreen.State,
    modifier: Modifier = Modifier,
) {
    val approaches =
        listOf(
            HighlightApproach(
                title = "Shiki Server",
                subtitle =
                    "Server-driven tokenization via Shiki Token Service. " +
                        "Code is sent to the backend which returns colored tokens; " +
                        "the app builds an AnnotatedString and renders it natively.",
                iconRes = R.drawable.cloud_24dp,
                event = HomeScreen.Event.OpenShikiHighlight,
            ),
            HighlightApproach(
                title = "KotlinTextMate",
                subtitle =
                    "On-device tokenization using TextMate grammars and VS Code themes. " +
                        "No network required — grammars and themes are bundled in the app assets.",
                iconRes = R.drawable.cloud_off_24dp,
                event = HomeScreen.Event.OpenTextMateHighlight,
            ),
            HighlightApproach(
                title = "Compare Both",
                subtitle =
                    "Side-by-side comparison of Shiki (cloud) vs TextMate (on-device). " +
                        "Includes performance metrics and device footprint analysis for each approach.",
                iconRes = R.drawable.code_blocks_24dp,
                event = HomeScreen.Event.OpenComparison,
            ),
        )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Syntax Highlight") })
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
            item { Spacer(modifier = Modifier.height(4.dp)) }
            items(
                count = approaches.size,
                key = { approaches[it].title },
            ) { index ->
                ApproachCard(
                    approach = approaches[index],
                    onClick = { state.eventSink(approaches[index].event) },
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ApproachCard(
    approach: HighlightApproach,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(approach.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = approach.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = approach.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
