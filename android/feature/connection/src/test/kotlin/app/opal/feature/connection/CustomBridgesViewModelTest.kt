package app.opal.feature.connection

import androidx.datastore.core.DataStore
import app.opal.core.data.SettingsRepository
import app.opal.core.model.bridge.BridgeLine
import app.opal.core.model.settings.AppSettings
import app.opal.core.model.settings.ConnectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomBridgesViewModelTest {
    // Synthetic lines (documentation addresses, made-up keys and domain): the format only.
    private val obfs4 =
        "obfs4 192.0.2.10:443 0123456789ABCDEF0123456789ABCDEF01234567 cert=AAAA iat-mode=0"
    private val dnstt =
        "dnstt 192.0.2.5:1 89ABCDEF0123456789ABCDEF0123456789ABCDEF " +
            "doh=https://doh.example/dns-query pubkey=${"0123456789abcdef".repeat(4)} " +
            "domain=t.example.com"

    private class MemoryStore(initial: AppSettings) : DataStore<AppSettings> {
        val value = MutableStateFlow(initial)
        override val data: Flow<AppSettings> = value

        override suspend fun updateData(
            transform: suspend (t: AppSettings) -> AppSettings
        ): AppSettings = transform(value.value).also { value.value = it }
    }

    private fun viewModel(store: MemoryStore) = CustomBridgesViewModel(SettingsRepository(store))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saved bridges are loaded into the editor`() = runTest {
        val vm = viewModel(MemoryStore(AppSettings(customBridges = listOf(obfs4))))
        assertEquals(obfs4, vm.text)
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.state.value.saved)
        assertEquals(1, vm.state.value.valid.size)
    }

    /** The field reads the text back at once, not through a StateFlow collected a frame later. */
    @Test
    fun `an edit is the editor text right away`() = runTest {
        val vm = viewModel(MemoryStore(AppSettings()))
        vm.updateText("obfs4 192.0.2.300:443")
        assertEquals("obfs4 192.0.2.300:443", vm.text)
        assertFalse(vm.state.value.saved)
        assertEquals(BridgeLine.Reason.BadAddress, vm.state.value.invalid.single().reason)
    }

    @Test
    fun `lines from a QR code or the clipboard are appended once`() = runTest {
        val vm = viewModel(MemoryStore(AppSettings(customBridges = listOf(obfs4))))
        assertEquals(1, vm.addFrom("Your bridges:\n$dnstt\n$obfs4"))
        assertEquals("$obfs4\n$dnstt", vm.text)
        assertEquals(0, vm.addFrom(dnstt))
        assertEquals(2, vm.state.value.valid.size)
    }

    @Test
    fun `saving switches to own bridges, saving none switches back`() = runTest {
        val store = MemoryStore(AppSettings())
        val vm = viewModel(store)
        var custom: Boolean? = null
        vm.updateText("$dnstt\nnot a bridge")
        vm.save { custom = it }
        assertEquals(true, custom)
        assertEquals(listOf(dnstt), store.value.value.customBridges)
        assertEquals(ConnectionMode.Custom, store.value.value.connectionMode)
        assertTrue(vm.state.value.saved)

        vm.updateText("")
        vm.save { custom = it }
        assertEquals(false, custom)
        assertEquals(emptyList<String>(), store.value.value.customBridges)
        assertEquals(ConnectionMode.Snowflake, store.value.value.connectionMode)
    }
}
