package dev.hossain.syntaxhighlight.circuit.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.hossain.syntaxhighlight.R

private data class AppLink(
    val label: String,
    val url: String,
    val iconRes: Int,
)

private val appLinks =
    listOf(
        AppLink(
            label = "GitHub Repository",
            url = "https://github.com/hossain-khan/android-syntax-highlighter-compose",
            iconRes = R.drawable.github_logo,
        ),
        AppLink(
            label = "Blog: Shiki — Server-Driven Syntax Highlighting on Android",
            url = "https://hossain.dev/posts/syntax-highlighting-on-android-bringing-shiki-engine-to-compose/",
            iconRes = R.drawable.open_in_new_24dp,
        ),
        AppLink(
            label = "Blog: Highlight.js — Native Compose Engine on Android",
            url = "https://hossain.dev/posts/syntax-highlighting-on-android-highlight-js-native-compose-engine/",
            iconRes = R.drawable.open_in_new_24dp,
        ),
    )

/**
 * Bottom sheet content for the app-info overlay.
 *
 * Displays the app name, version, a short description of what the app does,
 * and a set of external links (GitHub repository and blog posts).
 *
 * Intended to be used as the [com.slack.circuitx.overlays.BottomSheetOverlay] body,
 * triggered from the [dev.hossain.syntaxhighlight.circuit.home.HomeScreen] toolbar.
 */
@Composable
fun AppInfoBottomSheet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName =
        remember {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
        ) {
            Text(
                text = "Syntax Highlighter",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "v$versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    "A showcase of syntax highlighting approaches in Jetpack Compose: " +
                        "server-driven tokenization via Shiki, on-device TextMate grammar " +
                        "rendering, and WebView-based Highlight.js.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        HorizontalDivider()

        Text(
            text = "Links",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 4.dp),
        )

        appLinks.forEach { link ->
            ListItem(
                headlineContent = {
                    Text(
                        text = link.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(link.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { uriHandler.openUri(link.url) },
            )
        }
    }
}
