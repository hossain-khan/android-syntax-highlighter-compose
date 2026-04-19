package app.example.data

import android.content.Context
import app.example.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Example service class that demonstrates Metro constructor injection with scoping.
 *
 * The [@Inject][Inject] annotation marks this class for constructor injection, and
 * [@SingleIn][SingleIn] ensures only one instance exists per [AppScope].
 *
 * Note: [@Inject][Inject] is implicit when using [@ContributesBinding][dev.zacsweers.metro.ContributesBinding]
 * or [@ContributesIntoMap][dev.zacsweers.metro.ContributesIntoMap] as of Metro 0.10.0,
 * but is explicitly used here for standard classes.
 *
 * This service retrieves the application version at initialization time and caches it.
 *
 * See https://zacsweers.github.io/metro/latest/injection-types/#constructor-injection
 * See https://zacsweers.github.io/metro/latest/scopes/
 */
@SingleIn(AppScope::class)
@Inject
class ExampleAppVersionService
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val versionName: String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"

        fun getApplicationVersion(): String = versionName
    }
