package app.opal.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal const val PACKAGE = "app.opal"
private const val TIMEOUT_MS = 5_000L

/**
 * The paths a user takes most, driven through stable test tags (exposed as resource ids), so they
 * work in any locale. The VPN is never started: consent dialogs cannot be automated reliably and
 * the startup path must not depend on the network.
 */
internal fun MacrobenchmarkScope.skipOnboardingIfShown() {
    repeat(3) {
        val next = device.wait(Until.findObject(By.res("onboarding_next")), 1_500) ?: return
        next.click()
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.visitTabs() {
    for (tab in listOf("tab_apps", "tab_connection", "tab_settings", "tab_home")) {
        device.wait(Until.findObject(By.res(tab)), TIMEOUT_MS)?.click()
        device.waitForIdle()
        if (tab == "tab_settings" || tab == "tab_apps") scrollContent()
    }
}

private fun MacrobenchmarkScope.scrollContent() {
    val scrollable = device.findObject(By.scrollable(true)) ?: return
    scrollable.setGestureMargin(device.displayWidth / 5)
    scrollable.fling(Direction.DOWN)
    device.waitForIdle()
    scrollable.fling(Direction.UP)
    device.waitForIdle()
}
