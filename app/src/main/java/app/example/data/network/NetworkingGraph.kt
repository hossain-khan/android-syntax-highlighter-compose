package app.example.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Metro bindings for the networking layer contributed to [AppScope].
 *
 * Provides the [OkHttpClient], [Retrofit], and [EmailApiService] as singletons
 * using `@ContributesTo` so they are automatically aggregated into the app's
 * dependency graph without requiring manual wiring.
 *
 * See https://zacsweers.github.io/metro/latest/aggregation/ for more on aggregation.
 */
@ContributesTo(AppScope::class)
interface NetworkingGraph {
    /**
     * Provides a configured [OkHttpClient] with:
     * - HTTP request/response logging (body level)
     * - 30-second connect and read timeouts
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            ).connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * Provides a [Json] instance configured to be lenient with unknown keys,
     * ensuring forward-compatibility as the API evolves.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Provides a [Retrofit] instance configured with the email demo service base URL,
     * the shared [OkHttpClient], and the kotlinx-serialization converter.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl("https://email-demo.gohk.xyz/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /**
     * Provides the [EmailApiService] Retrofit implementation as a singleton.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun provideEmailApiService(retrofit: Retrofit): EmailApiService = retrofit.create(EmailApiService::class.java)
}
