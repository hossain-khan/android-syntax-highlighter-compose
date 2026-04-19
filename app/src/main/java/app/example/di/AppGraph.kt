package app.example.di

import android.app.Activity
import android.content.Context
import androidx.work.WorkManager
import com.slack.circuit.foundation.Circuit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * Metro dependency graph for the application, which includes all necessary bindings and providers.
 *
 * This is the root dependency graph (component) for the application. It is scoped to [AppScope]
 * which makes all bindings in this graph singletons within the application lifecycle.
 *
 * Key Metro features used:
 * - [DependencyGraph]: Marks this as a Metro dependency graph (similar to Dagger's @Component)
 * - [SingleIn]: Declares the scope for this graph (AppScope in this case)
 * - [Provides]: Functions that provide dependencies to the graph
 * - [DependencyGraph.Factory]: Factory interface for creating the graph with runtime inputs
 * - Multibindings: `activityProviders` is a map multibinding for Activity injection
 *
 * See https://zacsweers.github.io/metro/latest/dependency-graphs/ for more information on dependency graphs.
 * See https://zacsweers.github.io/metro/latest/scopes/ for more information on scopes.
 * See https://zacsweers.github.io/metro/latest/bindings/ for more information on bindings and multibindings.
 */
@DependencyGraph(scope = AppScope::class)
@SingleIn(AppScope::class)
interface AppGraph {
    /**
     * Map of Activity classes to their providers. This is a multibinding that allows
     * activities to be injected via constructor using [ComposeAppComponentFactory].
     *
     * The [@Multibinds][Multibinds] annotation declares this as a multibinding collection
     * that Activity classes can contribute to via [@ContributesIntoMap][ContributesIntoMap].
     *
     * See https://zacsweers.github.io/metro/latest/bindings/#multibindings
     */
    @Multibinds
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    /**
     * Circuit instance for handling UI presentation and state management.
     *
     * See https://slackhq.github.io/circuit/
     */
    val circuit: Circuit

    val workManager: WorkManager
    val workerFactory: AppWorkerFactory

    @Provides
    fun providesWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    /**
     * Factory for creating the [AppGraph] with runtime inputs.
     *
     * The [ApplicationContext] parameter is marked with [@Provides][Provides] which makes it
     * available as a binding in the graph. This is analogous to Dagger's @BindsInstance.
     *
     * See https://zacsweers.github.io/metro/latest/dependency-graphs/#provides
     */
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @ApplicationContext @Provides context: Context,
        ): AppGraph
    }
}
