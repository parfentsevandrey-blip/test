package app.veil.vpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.veil.vpn.model.Localised

/**
 * Turns text the lower layers decided on into words in the reader's language.
 *
 * The network and tunnel code hands up a resource and its arguments rather than
 * a finished sentence, so this is where that becomes something to print.
 */
@Composable
fun Localised.resolve(): String = stringResource(id, *args.toTypedArray())
