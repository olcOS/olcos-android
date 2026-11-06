package io.olcos.app.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.olcos.app.R
import io.olcos.app.model.Details
import io.olcos.app.model.SubscriptionList

/**
 * A link opened from another app, or a text shared to olcOS, is shown before
 * it is taken in: a web page could otherwise add a server of its own (which a
 * clean install makes the main connection), or a list the app then fetches
 * from a stranger's address every half hour. Text that adds nothing skips
 * the question and gets the usual message (importNeedsConfirm).
 */
@Composable
fun ImportConfirmDialog(raw: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val what = remember(raw) { importSummary(context, raw) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_confirm_title), style = Type.title) },
        text = { Text(stringResource(R.string.import_confirm_message_fmt, what), style = Type.body) },
        confirmButton = { OlcTextButton(onClick = onAdd) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { OlcTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Whether the text would add anything; a link the app cannot read goes straight to its error message. */
fun importNeedsConfirm(raw: String): Boolean = when (detect(raw)) {
    is SubscriptionList.Import.Unrecognized, is SubscriptionList.Import.Unsupported -> false
    else -> true
}

/** What the text would add, in a line: the connection's name, the list's or the invite's address. */
private fun importSummary(context: Context, raw: String): String = when (val input = detect(raw)) {
    is SubscriptionList.Import.Connection -> context.getString(
        R.string.import_what_connection_fmt,
        input.name.ifBlank {
            when (val d = input.details) {
                is Details.Olcrtc -> d.link.serviceName
                is Details.Xray -> d.link.fallbackName
            }
        },
    )
    is SubscriptionList.Import.Url -> context.getString(R.string.import_what_subscription_fmt, Uri.parse(input.https).host ?: input.https)
    is SubscriptionList.Import.Invite -> context.getString(R.string.import_what_invite_fmt, input.invite.host)
    is SubscriptionList.Import.Body -> context.getString(R.string.import_what_list)
    else -> context.getString(R.string.import_what_list)
}

/** The text as the import reads it, percent-decoded when it came through a browser that way. */
private fun detect(raw: String): SubscriptionList.Import {
    val text = raw.trim()
    val input = SubscriptionList.detectImport(text)
    if (input !is SubscriptionList.Import.Unrecognized) return input
    val decoded = Uri.decode(text)
    return if (decoded != text) SubscriptionList.detectImport(decoded) else input
}
