package app.opal.feature.apps

import app.opal.core.data.InstalledApp
import app.opal.core.model.settings.SplitTunnelMode
import app.opal.core.model.settings.SplitTunnelSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsStateTest {
    private val apps =
        listOf(
            InstalledApp("org.mozilla.firefox", "Firefox", isSystem = false),
            InstalledApp("ru.sberbankmobile", "СберБанк Онлайн", isSystem = false),
            InstalledApp("ru.rostel", "Госуслуги", isSystem = false),
            InstalledApp("com.android.chrome", "Chrome", isSystem = true),
            InstalledApp("com.google.android.gms", "Google Play services", isSystem = true),
        )

    @Test
    fun `system apps hidden unless shown or selected`() {
        val split = SplitTunnelSettings(excluded = setOf("com.google.android.gms"))
        val rows = AppsViewModel.build(apps, "", split, null).rows.map { it.packageName }
        assertEquals(
            listOf(
                "org.mozilla.firefox",
                "ru.sberbankmobile",
                "ru.rostel",
                "com.google.android.gms",
            ),
            rows,
        )
        val all = AppsViewModel.build(apps, "", split.copy(showSystemApps = true), null).rows
        assertEquals(5, all.size)
    }

    @Test
    fun `checked follows the list of the current mode`() {
        val split =
            SplitTunnelSettings(
                excluded = setOf("org.mozilla.firefox"),
                included = setOf("ru.rostel"),
            )
        val allExcept = AppsViewModel.build(apps, "", split, null)
        assertTrue(allExcept.rows.first { it.packageName == "org.mozilla.firefox" }.checked)
        assertFalse(allExcept.rows.first { it.packageName == "ru.rostel" }.checked)
        val only =
            AppsViewModel.build(apps, "", split.copy(mode = SplitTunnelMode.OnlySelected), null)
        assertTrue(only.rows.first { it.packageName == "ru.rostel" }.checked)
        assertEquals(1, only.selectedCount)
    }

    @Test
    fun `search matches label and package, case-insensitive`() {
        val s = SplitTunnelSettings()
        assertEquals(
            listOf("ru.sberbankmobile"),
            AppsViewModel.build(apps, "сбер", s, null).rows.map { it.packageName },
        )
        assertEquals(
            listOf("org.mozilla.firefox"),
            AppsViewModel.build(apps, "MOZILLA", s, null).rows.map { it.packageName },
        )
    }

    @Test
    fun `preset counts installed apps and is applied only when all are direct`() {
        val none = AppsViewModel.build(apps, "", SplitTunnelSettings(), null)
        assertEquals(2, none.presetInstalled)
        assertFalse(none.presetApplied)
        val applied =
            AppsViewModel.build(
                apps,
                "",
                SplitTunnelSettings(excluded = setOf("ru.sberbankmobile", "ru.rostel")),
                null,
            )
        assertTrue(applied.presetApplied)
        val otherMode =
            AppsViewModel.build(
                apps,
                "",
                SplitTunnelSettings(
                    mode = SplitTunnelMode.OnlySelected,
                    excluded = setOf("ru.sberbankmobile", "ru.rostel"),
                ),
                null,
            )
        assertFalse(otherMode.presetApplied)
    }

    @Test
    fun `loading until the app list arrives`() {
        assertTrue(AppsViewModel.build(null, "", SplitTunnelSettings(), null).loading)
        assertFalse(AppsViewModel.build(apps, "", SplitTunnelSettings(), true).loading)
    }
}
