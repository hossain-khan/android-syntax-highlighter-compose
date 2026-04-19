package app.example.di

import javax.inject.Qualifier

/**
 * Qualifier annotation to denote a `Context` that is specifically an Application context.
 *
 * This is a custom [Qualifier] annotation used in Metro DI to distinguish between different
 * types of Context (e.g., Application context vs Activity context).
 *
 * Example usage:
 * ```kotlin
 * @Inject
 * class SomeService(
 *     @ApplicationContext private val context: Context,
 * )
 * ```
 *
 * See https://zacsweers.github.io/metro/latest/bindings/#qualifiers for more on qualifiers.
 */
@Qualifier annotation class ApplicationContext
