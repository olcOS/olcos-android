package io.olcos.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.olcos.app.AppGraph
import io.olcos.app.R
import io.olcos.app.core.KillSignal

/**
 * Shown once, when the system cut a session that was wanted (KillSignal): what keeps the
 * app alive in the background is in the app's battery settings and in the system's VPN
 * settings (Always-on VPN), one tap from here each.
 */
@Composable
fun KillGuideDialog() {
    val due by AppGraph.settings.killGuide.collectAsStateWithLifecycle()
    if (!due) return
    val context = LocalContext.current
    val close = { AppGraph.settings.dismissKillGuide() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.kill_guide_title), style = Type.title) },
        text = {
            Column {
                Text(stringResource(R.string.kill_guide_text), style = Type.body)
                OlcTextButton(onClick = { runCatching { context.startActivity(KillSignal.batterySettings(context)) } }) {
                    Text(stringResource(R.string.kill_guide_battery))
                }
                OlcTextButton(onClick = { runCatching { context.startActivity(KillSignal.vpnSettings()) } }) {
                    Text(stringResource(R.string.kill_guide_vpn))
                }
            }
        },
        confirmButton = { OlcTextButton(onClick = close) { Text(stringResource(R.string.kill_guide_ok)) } },
    )
}
