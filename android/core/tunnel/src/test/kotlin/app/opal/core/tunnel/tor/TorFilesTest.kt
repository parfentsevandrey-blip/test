package app.opal.core.tunnel.tor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Library assets are still `geoip*.gz` here (the APK packager unpacks them later, see TorFiles):
 * this covers the gzip fallback; the plain-name path is what release APKs use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TorFilesTest {
    @Test
    fun `geoip databases are unpacked once per version`() = runTest {
        val files = TorFiles(RuntimeEnvironment.getApplication())
        files.prepare()
        assertTrue(files.ensureGeoIp(versionCode = 1))
        val header = files.geoIp.bufferedReader().use { it.readLine() }
        assertTrue(
            header,
            header.startsWith("# This file has been converted from the IPFire Location database"),
        )
        assertTrue(files.geoIp6.length() > 1_000_000)
        val stamp = files.geoIp.lastModified()
        assertTrue(files.ensureGeoIp(versionCode = 1))
        assertTrue(files.geoIp.lastModified() == stamp)
    }
}
