package app.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.remember
import app.example.circuit.InboxScreen
import app.example.di.ActivityKey
import app.example.ui.theme.CircuitAppTheme
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.navstack.rememberSaveableNavStack
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.sharedelements.SharedElementTransitionLayout
import com.slack.circuitx.gesturenavigation.GestureNavigationDecorationFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/**
 * Main activity for the application, demonstrating Metro constructor injection for Activities.
 *
 * This Activity is injected via constructor using Metro DI, enabled by [ComposeAppComponentFactory].
 *
 * Key Metro features demonstrated:
 * - [ActivityKey]: Map key annotation for multibinding
 * - [ContributesIntoMap]: Contributes this Activity to the multibinding map
 * - [Inject]: Marks this class for constructor injection
 * - [binding]: Type-safe binding helper for specifying the bound type
 *
 * The Activity receives its dependencies ([Circuit]) through constructor injection,
 * which is more testable and type-safe than field injection.
 *
 * See https://zacsweers.github.io/metro/latest/injection-types/#constructor-injection for constructor injection.
 * See https://zacsweers.github.io/metro/latest/bindings/#multibindings for multibindings.
 * See https://zacsweers.github.io/metro/latest/aggregation/ for contribution.
 *
 * Note: [@Inject][Inject] is now implicit when using [@ContributesIntoMap][ContributesIntoMap]
 * as of Metro 0.10.0 with `contributesAsInject` enabled by default.
 */
@ActivityKey(MainActivity::class)
@ContributesIntoMap(AppScope::class, binding = binding<Activity>())
class MainActivity
    constructor(
        private val circuit: Circuit,
    ) : ComponentActivity() {
        @OptIn(ExperimentalSharedTransitionApi::class)
        override fun onCreate(savedInstanceState: Bundle?) {
            enableEdgeToEdge()
            super.onCreate(savedInstanceState)

            setContent {
                CircuitAppTheme {
                    // See https://slackhq.github.io/circuit/navigation/
                    val navStack = rememberSaveableNavStack(root = InboxScreen)
                    val navigator = rememberCircuitNavigator(navStack)

                    // See https://slackhq.github.io/circuit/circuit-content/
                    CircuitCompositionLocals(circuit) {
                        // See https://slackhq.github.io/circuit/shared-elements/
                        SharedElementTransitionLayout {
                            // See https://slackhq.github.io/circuit/overlays/
                            ContentWithOverlays {
                                NavigableCircuitContent(
                                    navigator = navigator,
                                    navStack = navStack,
                                    decoratorFactory =
                                        remember(navigator) {
                                            GestureNavigationDecorationFactory(onBackInvoked = navigator::pop)
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
