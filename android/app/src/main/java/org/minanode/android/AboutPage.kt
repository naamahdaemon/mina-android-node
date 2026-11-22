package org.minanode.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.json.JSONObject
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Contact and project links only; no network requests until a link is opened. */
@Composable
fun AboutPage() {
    val context = LocalContext.current
    val engine = remember(context) {
        runCatching {
            val info = context.assets.open("node/BUILD-INFO.json").bufferedReader().use { JSONObject(it.readText()) }
            info.optString("version").ifBlank { info.getString("revision").take(12) }
        }.getOrNull()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineLarge)
        Text("MinAndroid", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium)
        engine?.let { Text(stringResource(R.string.about_engine, it), style = MaterialTheme.typography.labelMedium) }
        Text(stringResource(R.string.about_intro))
        AboutLink(stringResource(R.string.about_support), "https://github.com/naamahdaemon/mina-android-node/issues")
        AboutCard(stringResource(R.string.about_contact)) {
            Text("Naamah Daemon · @naamahdaemon", style = MaterialTheme.typography.titleMedium)
            AboutLink(stringResource(R.string.about_email), "mailto:naamahdaemon@gmail.com", "naamahdaemon@gmail.com")
            SelectionContainer {
                Text(stringResource(R.string.about_discord_handle, "naamah8064"), style = MaterialTheme.typography.bodyMedium)
            }
            AboutLink("Telegram", "https://t.me/naamahdaemon", "@naamahdaemon")
            AboutLink(stringResource(R.string.about_discord_server), "https://discord.com/invite/ZkqQw8dK4E")
            AboutLink("X", "https://x.com/naamahdaemon", "@naamahdaemon")
            AboutLink(stringResource(R.string.about_website), "https://mina.naamahdaemon.eu", "mina.naamahdaemon.eu")
            AboutLink("GitHub", "https://github.com/naamahdaemon", "naamahdaemon")
        }
        Text(stringResource(R.string.about_projects), style = MaterialTheme.typography.titleLarge)
        AboutProject("Mina Graph", R.string.about_minagraph, "https://github.com/naamahdaemon/minagraph", "https://webapp.minagraph.com/")
        AboutProject("Mina Telegram Bot Wallet", R.string.about_bot, service = "https://t.me/mina_monitor_bot", documentation = "https://mina.naamahdaemon.eu/docs/Node%20operator%20guide/Telegram_bot/mina_alert_bot")
        AboutProject("zkRoll", R.string.about_zkroll, "https://github.com/naamahdaemon/zkroll", "https://zkroll.naamahdaemon.eu/")
        AboutProject("Mina Validator Performance", R.string.about_validators, "https://github.com/naamahdaemon/validator-performance-dashboard", "https://mina-validators.naamahdaemon.eu/")
        AboutProject("Mina Auto Payouts", R.string.about_payouts, "https://github.com/naamahdaemon/mina-auto-payouts")
        AboutProject("Block2GCal", R.string.about_block2gca, "https://github.com/naamahdaemon/block2gca")
        AboutProject("Zeko Bridge UI", R.string.about_bridge, "https://github.com/naamahdaemon/zeko-bridge-ui")
        AboutProject("Mina Windows Node / Command Center", R.string.about_windows, "https://github.com/naamahdaemon/mina-win-node", privateSource = true)
        Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.titleLarge)
        AboutCard(stringResource(R.string.about_gareth)) {
            Text(stringResource(R.string.about_gareth_body))
            AboutLink("Mina Rust · GitHub", "https://github.com/minanode/mina-rust")
            AboutLink("Gareth Davies · X", "https://x.com/garethtdavies")
            AboutLink("minanode.org", "https://minanode.org")
        }
        AboutCard(stringResource(R.string.about_original)) {
            Text(stringResource(R.string.about_original_body))
            AboutLink("o1Labs", "https://www.o1labs.org")
            AboutLink("OpenMina · GitHub", "https://github.com/o1-labs/mina-rust")
        }
        AboutCard(stringResource(R.string.about_mina_community)) {
            Text(stringResource(R.string.about_mina_community_body))
            AboutLink(stringResource(R.string.about_mina_discord), "https://discord.gg/minaprotocol")
            AboutLink("Mina Protocol", "https://minaprotocol.com")
            AboutLink("MinAndroid · GitHub", "https://github.com/naamahdaemon/mina-android-node")
        }
        AboutCard("JR Washburn · mina-pool-payout") {
            Text(stringResource(R.string.about_jrwashburn))
            AboutLink("JR Washburn · GitHub", "https://github.com/jrwashburn")
            AboutLink("mina-pool-payout · GitHub", "https://github.com/jrwashburn/mina-pool-payout")
        }
        AboutCard(stringResource(R.string.about_licenses)) {
            Text(stringResource(R.string.about_licenses_body))
            AboutLink(stringResource(R.string.about_licenses), "https://github.com/naamahdaemon/mina-android-node/blob/main/docs/licenses.md")
            AboutLink(stringResource(R.string.privacy_policy), "https://github.com/naamahdaemon/mina-android-node/blob/main/PRIVACY.md")
        }
        Text(stringResource(R.string.about_not_official), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AboutProject(title: String, description: Int, source: String? = null, service: String? = null,
    documentation: String? = null, privateSource: Boolean = false) {
    AboutCard(title) {
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        service?.let { AboutLink(stringResource(R.string.about_open), it) }
        documentation?.let { AboutLink(stringResource(R.string.about_documentation), it) }
        if (privateSource) Text(stringResource(R.string.about_private_notice), style = MaterialTheme.typography.bodySmall)
        source?.let { AboutLink(stringResource(if (privateSource) R.string.about_private_source else R.string.about_source), it) }
    }
}

@Composable
private fun AboutLink(label: String, uri: String, detail: String? = null) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.about_link_unavailable)
    TextButton(onClick = {
        try {
            context.startActivity(Intent(if (uri.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
        }
    }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$label  \u2197")
            detail?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}
