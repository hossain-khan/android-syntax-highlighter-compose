package app.example.circuit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.example.R
import app.example.data.model.Email
import app.example.data.repository.EmailRepository
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.parcelize.Parcelize

/**
 * Screen that lists all sent emails (read-only).
 *
 * This screen fetches emails via [EmailRepository.getSentEmails], which in the
 * current implementation filters the inbox for emails with "sent" status.
 */
@Parcelize
data object SentScreen : Screen {
    // See https://slackhq.github.io/circuit/states-and-events/
    @Stable
    sealed interface State : CircuitUiState {
        data object Loading : State

        data class Success(
            val emails: List<Email>,
            val eventSink: (Event) -> Unit,
        ) : State

        data class Error(
            val message: String,
            val eventSink: (Event) -> Unit,
        ) : State
    }

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data object OnRetry : Event

        data object OnBack : Event
    }
}

@AssistedInject
class SentPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        private val emailRepository: EmailRepository,
    ) : Presenter<SentScreen.State> {
        @Composable
        override fun present(): SentScreen.State {
            var emails by rememberRetained { mutableStateOf<List<Email>?>(null) }
            var errorMessage by rememberRetained { mutableStateOf<String?>(null) }
            var retryTrigger by rememberRetained { mutableStateOf(0) }

            LaunchedEffect(retryTrigger) {
                emails = null
                errorMessage = null
                try {
                    emails = emailRepository.getSentEmails()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Unknown error"
                }
            }

            val eventSink: (SentScreen.Event) -> Unit = { event ->
                when (event) {
                    SentScreen.Event.OnRetry -> retryTrigger++
                    SentScreen.Event.OnBack -> navigator.pop()
                }
            }

            return when {
                errorMessage != null -> SentScreen.State.Error(errorMessage!!, eventSink)
                emails != null -> SentScreen.State.Success(emails!!, eventSink)
                else -> SentScreen.State.Loading
            }
        }

        @CircuitInject(SentScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): SentPresenter
        }
    }

@CircuitInject(screen = SentScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentContent(
    state: SentScreen.State,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SentScreen.State.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SentScreen.State.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    Button(onClick = { state.eventSink(SentScreen.Event.OnRetry) }) {
                        Text("Retry")
                    }
                }
            }
        }

        is SentScreen.State.Success -> {
            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        title = { Text("Sent") },
                        navigationIcon = {
                            IconButton(onClick = { state.eventSink(SentScreen.Event.OnBack) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.arrow_back_24dp),
                                    contentDescription = "Back",
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                if (state.emails.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No sent emails",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(innerPadding)) {
                        items(state.emails) { email ->
                            EmailItem(email = email)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
