package app.example.circuit

// -------------------------------------------------------------------------------------
//
// THIS IS AN EXAMPLE FILE WITH CIRCUIT SCREENS AND PRESENTERS
// Example content is taken from https://slackhq.github.io/circuit/tutorial/
//
// You should delete this file and create your own screens with presenters.
//
//  -------------------------------------------------------------------------------------

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import app.example.circuit.overlay.AppInfoOverlay
import app.example.data.ExampleAppVersionService
import app.example.data.model.Email
import app.example.data.repository.EmailRepository
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.overlay.OverlayEffect
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

// See https://slackhq.github.io/circuit/screen/
@Parcelize
data object InboxScreen : Screen {
    // See https://slackhq.github.io/circuit/states-and-events/
    @Stable
    sealed interface State : CircuitUiState {
        data object Loading : State

        data class Success(
            val emails: List<Email>,
            val showAppInfo: Boolean = false,
            val eventSink: (Event) -> Unit,
        ) : State

        data class Error(
            val message: String,
            val eventSink: (Event) -> Unit,
        ) : State
    }

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data class EmailClicked(
            val emailId: String,
        ) : Event

        data object InfoClicked : Event

        data object InfoDismissed : Event

        data object Retry : Event

        data object OnNewEmail : Event

        data object OnViewDrafts : Event

        data object OnViewSent : Event
    }
}

// See https://slackhq.github.io/circuit/presenter/
@AssistedInject
class InboxPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        private val emailRepository: EmailRepository,
        private val appVersionService: ExampleAppVersionService,
    ) : Presenter<InboxScreen.State> {
        @Composable
        override fun present(): InboxScreen.State {
            // rememberRetained persists state across configuration changes AND back-stack navigation,
            // so emails are not re-fetched on screen rotation or when returning from a detail screen.
            // See https://slackhq.github.io/circuit/presenter/#retention
            var emails by rememberRetained { mutableStateOf<List<Email>?>(null) }
            var errorMessage by rememberRetained { mutableStateOf<String?>(null) }
            var showAppInfo by rememberRetained { mutableStateOf(false) }
            var retryTrigger by rememberRetained { mutableStateOf(0) }

            LaunchedEffect(retryTrigger) {
                emails = null
                errorMessage = null
                try {
                    emails = emailRepository.getInboxEmails()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Unknown error"
                }
            }

            // This is just example of how the DI injected service is used in this presenter
            Log.d("InboxPresenter", "Application version: ${appVersionService.getApplicationVersion()}")

            val eventSink: (InboxScreen.Event) -> Unit = { event ->
                when (event) {
                    // Navigate to the detail screen when an email is clicked
                    is InboxScreen.Event.EmailClicked -> navigator.goTo(DetailScreen(event.emailId))

                    // Show app info overlay when info button is clicked
                    InboxScreen.Event.InfoClicked -> showAppInfo = true

                    // Dismiss app info overlay
                    InboxScreen.Event.InfoDismissed -> showAppInfo = false

                    // Retry loading emails after an error
                    InboxScreen.Event.Retry -> retryTrigger++

                    // Navigate to compose new email screen
                    InboxScreen.Event.OnNewEmail -> navigator.goTo(ComposeEmailScreen())

                    // Navigate to drafts screen
                    InboxScreen.Event.OnViewDrafts -> navigator.goTo(DraftsScreen)

                    // Navigate to sent screen
                    InboxScreen.Event.OnViewSent -> navigator.goTo(SentScreen)
                }
            }

            return when {
                errorMessage != null -> InboxScreen.State.Error(errorMessage!!, eventSink)
                emails != null -> InboxScreen.State.Success(emails!!, showAppInfo, eventSink)
                else -> InboxScreen.State.Loading
            }
        }

        @CircuitInject(InboxScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(navigator: Navigator): InboxPresenter
        }
    }

@CircuitInject(screen = InboxScreen::class, scope = AppScope::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Inbox(
    state: InboxScreen.State,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is InboxScreen.State.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is InboxScreen.State.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    Button(onClick = { state.eventSink(InboxScreen.Event.Retry) }) {
                        Text("Retry")
                    }
                }
            }
        }

        is InboxScreen.State.Success -> {
            // Use OverlayEffect for a state-driven, UDF-compliant overlay pattern.
            // show() is a suspend function that blocks until the overlay is dismissed.
            // InfoDismissed is only emitted after the user closes the overlay.
            // See https://slackhq.github.io/circuit/overlays/
            if (state.showAppInfo) {
                OverlayEffect {
                    // Suspends here until the user dismisses the overlay
                    show(AppInfoOverlay())
                    state.eventSink(InboxScreen.Event.InfoDismissed)
                }
            }

            Scaffold(
                modifier = modifier,
                topBar = {
                    TopAppBar(
                        title = { Text("Inbox") },
                        actions = {
                            IconButton(
                                onClick = { state.eventSink(InboxScreen.Event.InfoClicked) },
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_info_24),
                                    contentDescription = "App Info",
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.email_24dp),
                                    contentDescription = "Inbox",
                                )
                            },
                            label = { Text("Inbox") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { state.eventSink(InboxScreen.Event.OnViewDrafts) },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.edit_24dp),
                                    contentDescription = "Drafts",
                                )
                            },
                            label = { Text("Drafts") },
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { state.eventSink(InboxScreen.Event.OnViewSent) },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.send_24dp),
                                    contentDescription = "Sent",
                                )
                            },
                            label = { Text("Sent") },
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { state.eventSink(InboxScreen.Event.OnNewEmail) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.add_24dp),
                            contentDescription = "New Email",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                },
            ) { innerPadding ->
                LazyColumn(modifier = Modifier.padding(innerPadding)) {
                    items(state.emails) { email ->
                        EmailItem(
                            email = email,
                            onClick = { state.eventSink(InboxScreen.Event.EmailClicked(email.id)) },
                        )
                    }
                }
            }
        }
    }
}
