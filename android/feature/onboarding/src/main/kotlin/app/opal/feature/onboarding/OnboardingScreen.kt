package app.opal.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.opal.core.designsystem.component.LocalToastState
import app.opal.core.designsystem.component.MorphIndicator
import app.opal.core.designsystem.component.Panel
import app.opal.core.designsystem.component.PanelButton
import app.opal.core.designsystem.glass.GlassButton
import app.opal.core.designsystem.glass.GlassStyle
import app.opal.core.designsystem.glass.GlassSurface
import app.opal.core.designsystem.icon.OpalIcons
import app.opal.core.designsystem.theme.OpalTheme
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

/** What the shell provides: system screens and the device's background-restriction state. */
interface OnboardingHost {
    fun openVpnSettings()

    fun requestBatteryExemption()

    fun isIgnoringBatteryOptimizations(): Boolean

    fun openAutostartHelp()
}

private const val PAGES = 3

@Composable
fun OnboardingRoute(
    host: OnboardingHost,
    onFinish: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toast = LocalToastState.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { PAGES }
    var vpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    var notificationsGranted by remember { mutableStateOf(notificationsAllowed(context)) }
    var batteryOk by remember { mutableStateOf(host.isIgnoringBatteryOptimizations()) }
    LifecycleResumeEffect(Unit) {
        vpnGranted = VpnService.prepare(context) == null
        notificationsGranted = notificationsAllowed(context)
        batteryOk = host.isIgnoringBatteryOptimizations()
        onPauseOrDispose {}
    }
    val deniedText = stringResource(R.string.onb_vpn_denied)
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            vpnGranted =
                result.resultCode == Activity.RESULT_OK || VpnService.prepare(context) == null
            if (!vpnGranted) toast.show(deniedText, OpalIcons.VpnLock)
        }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsGranted = it
        }

    Column(
        modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            PageColumn {
                when (page) {
                    0 -> IntroPage()
                    1 ->
                        VpnPage(
                            granted = vpnGranted,
                            onGrant = {
                                VpnService.prepare(context)?.let(consent::launch)
                                    ?: run { vpnGranted = true }
                            },
                            onOpenVpnSettings = host::openVpnSettings,
                        )
                    else ->
                        BackgroundPage(
                            notificationsGranted = notificationsGranted,
                            onAllowNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    notificationPermission.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                            },
                            batteryOk = batteryOk,
                            onBattery = host::requestBatteryExemption,
                            onAutostart = host::openAutostartHelp,
                        )
                }
            }
        }
        PageDots(pager.currentPage, Modifier.padding(vertical = 12.dp))
        Row(
            Modifier.widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pager.currentPage > 0) {
                PanelButton(
                    stringResource(R.string.onb_back),
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    icon = OpalIcons.ArrowBack,
                )
            } else {
                Spacer(Modifier.size(1.dp))
            }
            val last = pager.currentPage == PAGES - 1
            GlassButton(
                onClick = {
                    if (last) onFinish()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            ) {
                Text(
                    stringResource(if (last) R.string.onb_start else R.string.onb_next),
                    style = OpalTheme.type.bodyStrong,
                )
                if (!last)
                    Icon(
                        OpalIcons.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
            }
        }
    }
}

private fun notificationsAllowed(context: android.content.Context) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        style = OpalTheme.type.title,
        color = OpalTheme.colors.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp).semantics { heading() },
    )
}

@Composable
private fun Block(title: String?, body: String, icon: ImageVector) {
    Panel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = OpalTheme.colors.onBackground,
                modifier = Modifier.size(24.dp),
            )
            Column {
                if (title != null)
                    Text(
                        title,
                        style = OpalTheme.type.subhead,
                        color = OpalTheme.colors.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                Text(
                    body,
                    style = OpalTheme.type.callout,
                    color = OpalTheme.colors.onBackgroundMuted,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.IntroPage() {
    GlassSurface(
        shape = Capsule(),
        style = GlassStyle.Sphere,
        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp).size(120.dp),
    ) {
        MorphIndicator(
            progress = 0.35f,
            color = OpalTheme.colors.accent,
            modifier = Modifier.size(56.dp),
        )
    }
    PageTitle(stringResource(R.string.onb_intro_title))
    Block(null, stringResource(R.string.onb_intro_does), OpalIcons.Shield)
    Block(
        stringResource(R.string.onb_intro_cannot_title),
        stringResource(R.string.onb_intro_cannot),
        OpalIcons.Info,
    )
    Block(
        stringResource(R.string.onb_intro_api_title),
        stringResource(R.string.onb_intro_api),
        OpalIcons.VisibilityOff,
    )
}

@Composable
private fun ColumnScope.VpnPage(
    granted: Boolean,
    onGrant: () -> Unit,
    onOpenVpnSettings: () -> Unit,
) {
    PageTitle(stringResource(R.string.onb_vpn_title))
    Block(null, stringResource(R.string.onb_vpn_body), OpalIcons.VpnLock)
    StatusAction(
        granted,
        stringResource(R.string.onb_vpn_granted),
        stringResource(R.string.onb_vpn_grant),
        onGrant,
    )
    Spacer(Modifier.height(20.dp))
    Block(
        stringResource(R.string.onb_always_on_title),
        stringResource(R.string.onb_always_on_body),
        OpalIcons.ShieldLock,
    )
    PanelButton(
        stringResource(R.string.onb_always_on_open),
        onClick = onOpenVpnSettings,
        icon = OpalIcons.OpenInNew,
    )
}

@Composable
private fun ColumnScope.BackgroundPage(
    notificationsGranted: Boolean,
    onAllowNotifications: () -> Unit,
    batteryOk: Boolean,
    onBattery: () -> Unit,
    onAutostart: () -> Unit,
) {
    PageTitle(stringResource(R.string.onb_bg_title))
    Block(null, stringResource(R.string.onb_notif_body), OpalIcons.Notifications)
    StatusAction(
        notificationsGranted,
        stringResource(R.string.onb_notif_done),
        stringResource(R.string.onb_notif_allow),
        onAllowNotifications,
    )
    Spacer(Modifier.height(20.dp))
    Block(null, stringResource(R.string.onb_battery_body), OpalIcons.BatterySaver)
    StatusAction(
        batteryOk,
        stringResource(R.string.onb_battery_done),
        stringResource(R.string.onb_battery_allow),
        onBattery,
    )
    Spacer(Modifier.height(20.dp))
    Text(
        stringResource(R.string.onb_autostart_hint),
        style = OpalTheme.type.callout,
        color = OpalTheme.colors.onBackgroundMuted,
        textAlign = TextAlign.Center,
    )
    PanelButton(
        stringResource(R.string.onb_autostart_open),
        onClick = onAutostart,
        icon = OpalIcons.Help,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** A done-state line, or the action that gets there. */
@Composable
private fun StatusAction(
    done: Boolean,
    doneText: String,
    actionText: String,
    onAction: () -> Unit,
) {
    if (done) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                OpalIcons.CheckCircleFilled,
                contentDescription = null,
                tint = OpalTheme.colors.connected,
                modifier = Modifier.size(22.dp),
            )
            Text(doneText, style = OpalTheme.type.bodyStrong, color = OpalTheme.colors.connected)
        }
    } else {
        PanelButton(actionText, onClick = onAction, icon = OpalIcons.ChevronRight)
    }
}

@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.onb_page, current + 1, PAGES)
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(PAGES) { index ->
            val color by
                animateColorAsState(
                    if (index == current) OpalTheme.colors.onBackground
                    else OpalTheme.colors.onBackgroundMuted.copy(alpha = 0.35f),
                    label = "dot",
                )
            Box(
                Modifier.size(if (index == current) 22.dp else 8.dp, 8.dp)
                    .clip(Capsule())
                    .background(color)
            )
        }
    }
}
