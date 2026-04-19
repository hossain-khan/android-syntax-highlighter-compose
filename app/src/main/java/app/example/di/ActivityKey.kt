package app.example.di

import android.app.Activity
import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

/**
 * A Metro map key annotation used for registering an [Activity] into the dependency graph.
 *
 * This is used with [@ContributesIntoMap][dev.zacsweers.metro.ContributesIntoMap] to add
 * Activities to the multibinding map that [ComposeAppComponentFactory] uses for
 * constructor injection of Activities.
 *
 * Example usage:
 * ```kotlin
 * @ActivityKey(MainActivity::class)
 * @ContributesIntoMap(AppScope::class, binding = binding<Activity>())
 * @Inject
 * class MainActivity(
 *     private val circuit: Circuit,
 * ) : ComponentActivity() {
 *     // ...
 * }
 * ```
 *
 * See https://zacsweers.github.io/metro/latest/bindings/#multibindings for more on map multibindings.
 * See [MapKey] for more on map key annotations.
 */
@MapKey
annotation class ActivityKey(
    val value: KClass<out Activity>,
)
