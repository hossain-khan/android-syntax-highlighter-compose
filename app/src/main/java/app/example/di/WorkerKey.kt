package app.example.di

import androidx.work.ListenableWorker
import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

/**
 * A Metro [MapKey] annotation for binding Workers in a multibinding map.
 *
 * This is used with [@ContributesIntoMap][dev.zacsweers.metro.ContributesIntoMap] to add
 * Workers to the multibinding map that [AppWorkerFactory] uses for dependency injection.
 *
 * Example usage:
 * ```kotlin
 * @AssistedInject
 * class SampleWorker(
 *     context: Context,
 *     @Assisted params: WorkerParameters,
 * ) : CoroutineWorker(context, params) {
 *
 *     @WorkerKey(SampleWorker::class)
 *     @ContributesIntoMap(AppScope::class, binding = binding<WorkerInstanceFactory<*>>())
 *     @AssistedFactory
 *     abstract class Factory : WorkerInstanceFactory<SampleWorker>
 * }
 * ```
 *
 * See https://zacsweers.github.io/metro/latest/bindings/#multibindings for more on map multibindings.
 * See [MapKey] for more on map key annotations.
 */
@MapKey
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkerKey(
    val value: KClass<out ListenableWorker>,
)
