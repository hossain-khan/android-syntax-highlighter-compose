package app.example.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.collections.get
import kotlin.reflect.KClass

/**
 * A custom [WorkerFactory] that uses Metro DI to create Workers with dependency injection.
 *
 * This factory receives a map of Worker classes to their factory instances through
 * multibinding, allowing Workers to be created with injected dependencies via assisted injection.
 *
 * Key Metro features used:
 * - [ContributesBinding]: Automatically binds this implementation to [WorkerFactory] in [AppScope]
 * - [Inject]: Constructor injection - Metro will automatically provide the workerProviders map
 * - Multibinding: The `workerProviders` map is a multibinding that Workers contribute to
 *
 * To register a Worker, annotate the Worker class with [@WorkerKey][WorkerKey] and
 * [@ContributesIntoMap][dev.zacsweers.metro.ContributesIntoMap] with the appropriate scope,
 * and create an [@AssistedFactory][dev.zacsweers.metro.AssistedFactory] for the Worker's parameters.
 *
 * Example:
 * ```kotlin
 * @AssistedInject
 * class YourWorker(
 *     context: Context,
 *     @Assisted params: WorkerParameters,
 *     // Your injected dependencies here
 * ) : CoroutineWorker(context, params) {
 *
 *     @WorkerKey(YourWorker::class)
 *     @ContributesIntoMap(
 *         AppScope::class,
 *         binding = binding<WorkerInstanceFactory<*>>(),
 *     )
 *     @AssistedFactory
 *     abstract class Factory : WorkerInstanceFactory<YourWorker>
 * }
 * ```
 *
 * See https://zacsweers.github.io/metro/latest/injection-types/#assisted-injection for assisted injection.
 * See https://zacsweers.github.io/metro/latest/bindings/#multibindings for multibindings.
 * See https://zacsweers.github.io/metro/latest/aggregation/ for contribution.
 *
 * Note: [@Inject][Inject] is now implicit when using [@ContributesBinding][ContributesBinding]
 * as of Metro 0.10.0 with `contributesAsInject` enabled by default.
 */
@ContributesBinding(AppScope::class)
class AppWorkerFactory(
    private val workerProviders: Map<KClass<out ListenableWorker>, WorkerInstanceFactory<*>>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = workerProviders[Class.forName(workerClassName).kotlin]?.create(workerParameters)

    /**
     * Factory interface for creating Workers with Metro DI.
     *
     * Implementations should use [@AssistedFactory][dev.zacsweers.metro.AssistedFactory]
     * to generate the factory implementation.
     */
    interface WorkerInstanceFactory<T : ListenableWorker> {
        fun create(params: WorkerParameters): T
    }
}
