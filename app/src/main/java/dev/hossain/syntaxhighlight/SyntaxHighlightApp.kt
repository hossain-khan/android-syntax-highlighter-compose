package dev.hossain.syntaxhighlight

import android.app.Application
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.hossain.syntaxhighlight.di.AppGraph
import dev.hossain.syntaxhighlight.work.SampleWorker
import dev.zacsweers.metro.createGraphFactory
import java.util.concurrent.Executors

private const val TAG = "SyntaxHighlightApp"

/**
 * Application class for the app with key initializations.
 *
 * This class demonstrates the following Metro features:
 * - Graph creation using [createGraphFactory]
 * - Lazy initialization of the dependency graph
 *
 * See https://zacsweers.github.io/metro/latest/dependency-graphs/ for more on creating graphs.
 */
class SyntaxHighlightApp :
    Application(),
    Configuration.Provider {
    /**
     * Lazily creates the Metro app graph using the factory pattern.
     *
     * [createGraphFactory] is a Metro intrinsic function that generates a factory
     * for creating the dependency graph. The graph is created with the Application
     * context as a runtime dependency.
     *
     * See https://zacsweers.github.io/metro/latest/dependency-graphs/#creating-factories
     */
    val appGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

    fun appGraph(): AppGraph = appGraph

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(appGraph.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        preWarmWebView()
        scheduleBackgroundWork()
    }

    /**
     * Pre-warms the WebView renderer process to reduce first-call latency for the
     * compose-highlight library (which uses a hidden WebView to run Highlight.js).
     *
     * See: https://github.com/hossain-khan/android-compose-highlight#optional-webview-pre-warming
     */
    private fun preWarmWebView() {
        Log.d(TAG, "WebView pre-warming: started")
        val startMs = System.currentTimeMillis()
        runCatching {
            WebViewCompat.startUpWebView(
                applicationContext,
                WebViewStartUpConfig.Builder(Executors.newSingleThreadExecutor()).build(),
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        val elapsed = System.currentTimeMillis() - startMs
                        Log.d(TAG, "WebView pre-warming: completed in ${elapsed}ms")
                        Log.d(TAG, "  totalTimeInUiThread=${result.totalTimeInUiThreadMillis}ms")
                        Log.d(TAG, "  maxTimePerTaskInUiThread=${result.maxTimePerTaskInUiThreadMillis}ms")
                        Log.d(TAG, "  uiThreadBlockingLocations=${result.uiThreadBlockingStartUpLocations}")
                        Log.d(TAG, "  nonUiThreadBlockingLocations=${result.nonUiThreadBlockingStartUpLocations}")
                    }

                    override fun onError(error: WebViewStartupException) {
                        val elapsed = System.currentTimeMillis() - startMs
                        Log.e(TAG, "WebView pre-warming: failed after ${elapsed}ms", error)
                    }
                },
            )
        }.onFailure { e ->
            Log.e(TAG, "WebView pre-warming: failed to start", e)
        }
    }

    /**
     * Schedules a background work request using the [WorkManager].
     * This is just an example to demonstrate how to use WorkManager with Metro DI.
     */
    private fun scheduleBackgroundWork() {
        val workRequest =
            OneTimeWorkRequestBuilder<SampleWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(SampleWorker.KEY_WORK_NAME to "Circuit App ${System.currentTimeMillis()}"))
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()

        appGraph.workManager.enqueue(workRequest)
    }
}
