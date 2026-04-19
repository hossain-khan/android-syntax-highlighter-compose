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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.example.R
import app.example.data.ExampleEmailValidator
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

// See https://slackhq.github.io/circuit/screen/
@Parcelize
data class DetailScreen(
    val emailId: String,
) : Screen {
    // See https://slackhq.github.io/circuit/states-and-events/
    @Stable
    sealed interface State : CircuitUiState {
        data object Loading : State

        data class Success(
            val email: Email,
            val eventSink: (Event) -> Unit,
        ) : State

        data class Error(
            val message: String,
            val eventSink: (Event) -> Unit,
        ) : State
    }

    @Immutable
    sealed interface Event : CircuitUiEvent {
        data object BackClicked : Event

        data object Retry : Event
    }
}

// See https://slackhq.github.io/circuit/presenter/
@AssistedInject
class DetailPresenter
    constructor(
        @Assisted private val navigator: Navigator,
        @Assisted private val screen: DetailScreen,
        private val emailRepository: EmailRepository,
        private val exampleEmailValidator: ExampleEmailValidator,
    ) : Presenter<DetailScreen.State> {
        @Composable
        override fun present(): DetailScreen.State {
            var email by rememberRetained { mutableStateOf<Email?>(null) }
            var errorMessage by rememberRetained { mutableStateOf<String?>(null) }
            var retryTrigger by rememberRetained { mutableStateOf(0) }

            LaunchedEffect(retryTrigger) {
                email = null
                errorMessage = null
                try {
                    email = emailRepository.getEmail(screen.emailId)
                    if (email == null) {
                        errorMessage = "Email not found"
                    }
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Unknown error"
                }
            }

            val eventSink: (DetailScreen.Event) -> Unit = { event ->
                when (event) {
                    DetailScreen.Event.BackClicked -> navigator.pop()
                    DetailScreen.Event.Retry -> retryTrigger++
                }
            }

            // Example usage of the validator that is injected in this presenter
            email?.let {
                val allValidEmail = it.recipients.all { r -> exampleEmailValidator.isValidEmail(r) }
                Log.d("DetailPresenter", "Is ${it.recipients} valid: $allValidEmail")
            }

            return when {
                errorMessage != null -> DetailScreen.State.Error(errorMessage!!, eventSink)
                email != null -> DetailScreen.State.Success(email!!, eventSink)
                else -> DetailScreen.State.Loading
            }
        }

        @CircuitInject(DetailScreen::class, AppScope::class)
        @AssistedFactory
        interface Factory {
            fun create(
                navigator: Navigator,
                screen: DetailScreen,
            ): DetailPresenter
        }
    }

@CircuitInject(DetailScreen::class, AppScope::class)
@Composable
fun EmailDetailContent(
    state: DetailScreen.State,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is DetailScreen.State.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is DetailScreen.State.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                    Button(onClick = { state.eventSink(DetailScreen.Event.Retry) }) {
                        Text("Retry")
                    }
                }
            }
        }

        is DetailScreen.State.Success -> {
            val email = state.email
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Column(modifier.padding(innerPadding).padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.baseline_person_24),
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .padding(4.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onTertiary),
                            contentDescription = null,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text(
                                    text = email.sender,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = email.timestamp,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.alpha(0.5f),
                                )
                            }
                            Text(text = email.subject, style = MaterialTheme.typography.labelMedium)
                            Row {
                                Text(
                                    "To: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = email.recipients.joinToString(","),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.alpha(0.5f),
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(text = email.body, style = MaterialTheme.typography.bodyMedium)

                    Button(
                        onClick = { state.eventSink(DetailScreen.Event.BackClicked) },
                        modifier = Modifier.padding(top = 16.dp).align(Alignment.End),
                    ) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

/** A simple email item to show in a list. */
@Composable
fun EmailItem(
    email: Email,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier.clickable(onClick = onClick).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.baseline_person_24),
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(4.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onTertiary),
            contentDescription = null,
        )
        Column {
            Row {
                Text(
                    text = email.sender,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = email.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.5f),
                )
            }

            Text(text = email.subject, style = MaterialTheme.typography.labelLarge)
            Text(
                text = email.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alpha(0.5f),
            )
        }
    }
}
