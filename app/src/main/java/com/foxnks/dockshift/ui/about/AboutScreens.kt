package com.foxnks.dockshift.ui.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.BuildConfig
import com.foxnks.dockshift.R
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Menu → ⓘ: about the app and its author. Each row opens its link: GitHub info page, email,
 * LinkedIn, and the app's source code.
 *
 * @param onBack back arrow tapped.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InfoScaffold(title = stringResource(R.string.about_title), onBack = onBack) {
        Header(
            icon = R.drawable.ic_launcher_monochrome,
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        )
        Text(
            text = stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.about_author),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LinkRow(R.drawable.ic_code, stringResource(R.string.about_github), "xaplanterisnikos.github.io") { context.openLink(AboutLinks.GITHUB) }
        LinkRow(R.drawable.ic_mail, stringResource(R.string.about_email), AboutLinks.EMAIL) { context.sendEmail(AboutLinks.EMAIL) }
        LinkRow(R.drawable.ic_link, stringResource(R.string.about_linkedin), "nick-xaplanteris") { context.openLink(AboutLinks.LINKEDIN) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LinkRow(R.drawable.ic_code, stringResource(R.string.about_source), stringResource(R.string.about_license)) {
            context.openLink(AboutLinks.SOURCE)
        }
    }
}

/**
 * Today → ⓘ: the author's other app, with a button to its Google Play page.
 *
 * @param onBack back arrow tapped.
 */
@Composable
fun OtherAppScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InfoScaffold(title = stringResource(R.string.other_app_title), onBack = onBack) {
        Header(
            icon = R.drawable.ic_shop,
            title = stringResource(R.string.other_app_name),
            subtitle = stringResource(R.string.other_app_subtitle),
        )
        Text(
            text = stringResource(R.string.other_app_text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
        Button(
            onClick = { context.openStorePage(AboutLinks.OTHER_APP_PACKAGE, AboutLinks.OTHER_APP_WEB) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_shop), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.other_app_button), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A screen with a title, a back arrow and scrolling content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

/** A large icon with a title and a line under it, centered. */
@Composable
private fun Header(@DrawableRes icon: Int, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A tappable row with an icon, a label and the value it opens. */
@Composable
private fun LinkRow(@DrawableRes icon: Int, label: String, value: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Android Studio preview of [AboutScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun AboutScreenPreview() {
    DockshiftTheme { AboutScreen(onBack = {}) }
}

/** Android Studio preview of [OtherAppScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun OtherAppScreenPreview() {
    DockshiftTheme { OtherAppScreen(onBack = {}) }
}
