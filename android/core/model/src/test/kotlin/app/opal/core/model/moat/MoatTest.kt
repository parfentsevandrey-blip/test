package app.opal.core.model.moat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoatTest {

    @Test
    fun `settings response from rdsys docs`() {
        val body =
            """
            {"settings":[
              {"bridges":{"type":"obfs4","source":"builtin","bridge_strings":["obfs4 209.148.46.65:443 74FAD13168806246602538555B5521A0383A1875 cert=ssH+9rP8dG2NLDN2XuFw63hIO/9MNNinLmxQDpVa+7kTOa9/m+tGWT1SmSYpQ9uTBGa6Hw iat-mode=0"]}},
              {"bridges":{"type":"snowflake","source":"builtin"}}
            ],"country":"ru"}
            """
        val response = MoatApi.json.decodeFromString(SettingsResponse.serializer(), body)
        assertEquals("ru", response.country)
        val sets = response.toBridgeSets()
        assertEquals(1, sets.size) // entries without bridge_strings are not usable on their own
        assertEquals("obfs4", sets[0].type)
    }

    @Test
    fun `error response`() {
        val body =
            """{"errors":[{"code":406,"detail":"Could not find country code for circumvention settings"}]}"""
        val response = MoatApi.json.decodeFromString(SettingsResponse.serializer(), body)
        assertNull(response.settings)
        assertEquals(406, response.errors!!.single().code)
    }

    @Test
    fun `request omits null country`() {
        val text = MoatApi.json.encodeToString(SettingsRequest.serializer(), SettingsRequest())
        assertEquals("""{"transports":["obfs4","snowflake","webtunnel","meek"]}""", text)
        val withCountry =
            MoatApi.json.encodeToString(
                SettingsRequest.serializer(),
                SettingsRequest(country = "ru"),
            )
        assertEquals(
            """{"country":"ru","transports":["obfs4","snowflake","webtunnel","meek"]}""",
            withCountry,
        )
    }
}
