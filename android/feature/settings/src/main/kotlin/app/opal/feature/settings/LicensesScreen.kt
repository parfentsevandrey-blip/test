package app.opal.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.opal.core.designsystem.component.LocalSheetHost
import app.opal.core.designsystem.component.Note
import app.opal.core.designsystem.component.PanelSegment
import app.opal.core.designsystem.component.SettingRow
import app.opal.core.designsystem.component.SubScreenHeader
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class License(
    val component: String,
    val detail: String,
    val license: String,
    val asset: String,
)

/** Everything shipped in the APK, with the license text that ships with it (assets/licenses). */
val licenses: List<License> =
    listOf(
        License("Opal", "GNU GPL v3 or later", "GPL-3.0-or-later", "gpl-3.0.txt"),
        License("Tor", "0.4.9.x, The Tor Project", "BSD-3-Clause and others (see text)", "tor.txt"),
        License(
            "tor-android",
            "Guardian Project (libtor.so build)",
            "BSD-3-Clause",
            "tor-android.txt",
        ),
        License("OpenSSL", "in libtor.so", "Apache-2.0", "openssl.txt"),
        License("libevent", "in libtor.so", "BSD-3-Clause", "libevent.txt"),
        License("zlib", "in libtor.so", "Zlib", "zlib.txt"),
        License("zstd", "in libtor.so", "BSD-3-Clause", "zstd.txt"),
        License("IPtProxy", "5.5.1, Guardian Project", "MIT", "iptproxy.txt"),
        License(
            "Lyrebird",
            "obfs4, meek_lite, webtunnel",
            "BSD-2-Clause; parts GPL-3.0",
            "lyrebird.txt",
        ),
        License("Snowflake", "2.14.1, The Tor Project", "BSD-3-Clause", "snowflake.txt"),
        License("Pion WebRTC", "in Snowflake", "MIT", "pion.txt"),
        License("Go", "runtime and standard library", "BSD-3-Clause", "go.txt"),
        License(
            "IPtProxy Go modules",
            "full list with versions",
            "MIT / BSD / Apache-2.0 / CC0",
            "iptproxy-go-modules.txt",
        ),
        License("hev-socks5-tunnel", "heiher", "MIT", "hev-socks5-tunnel.txt"),
        License("hev-task-system", "heiher", "MIT", "hev-task-system.txt"),
        License("lwIP", "in hev-socks5-tunnel", "BSD-3-Clause", "lwip.txt"),
        License("yaml", "in hev-socks5-tunnel", "MIT", "yaml.txt"),
        License("GeoIP data", "IPFire Location Database", "CC BY-SA 4.0", "ipfire-location.txt"),
        License("Backdrop, Shapes", "Kyant0/AndroidLiquidGlass", "Apache-2.0", "apache-2.0.txt"),
        License("AndroidX, Jetpack Compose, CameraX", "Google", "Apache-2.0", "apache-2.0.txt"),
        License(
            "Kotlin, kotlinx.coroutines, serialization, collections",
            "JetBrains",
            "Apache-2.0",
            "apache-2.0.txt",
        ),
        License("zxing-cpp", "QR decoder", "Apache-2.0", "apache-2.0.txt"),
        License("Material Symbols Rounded", "Google", "Apache-2.0", "apache-2.0.txt"),
        License("Inter", "Rasmus Andersson", "SIL OFL 1.1", "inter-OFL.txt"),
    )

@Composable
fun LicensesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheets = LocalSheetHost.current
    val column = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            Box(column) {
                SubScreenHeader(
                    stringResource(R.string.licenses_title),
                    stringResource(R.string.settings_back),
                    onBack,
                )
            }
        }
        item(key = "intro") { Note(stringResource(R.string.licenses_intro), modifier = column) }
        itemsIndexed(licenses, key = { _, l -> l.component }) { index, license ->
            PanelSegment(
                first = index == 0,
                last = index == licenses.lastIndex,
                modifier = column,
            ) {
                SettingRow(
                    title = license.component,
                    subtitle = "${license.detail} · ${license.license}",
                    onClick = { sheets.show(license.component) { LicenseText(license.asset) } },
                ) {
                    Icon(
                        OpalIcons.ChevronRight,
                        contentDescription = null,
                        tint = OpalTheme.colors.onBackgroundMuted,
                    )
                }
            }
        }
        item(key = "end") { Box(Modifier.padding(bottom = 16.dp)) }
    }
}

@Composable
private fun LicenseText(asset: String) {
    val context = LocalContext.current
    val text by
        produceState("", asset) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            context.assets.open("licenses/$asset").use {
                                it.readBytes().decodeToString()
                            }
                        }
                        .getOrDefault("")
                }
        }
    Text(
        text,
        style = OpalTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
        color = OpalTheme.colors.onGlass,
    )
}
