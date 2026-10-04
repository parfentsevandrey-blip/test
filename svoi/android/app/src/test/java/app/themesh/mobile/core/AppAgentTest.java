package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** То, что приложение говорит странице о себе в user agent. */
public class AppAgentTest {
    @Test
    public void anOrdinaryPhoneStartsWithFullEffects() {
        assertEquals(" TheMeshAndroid/0.1.0 (android; skin=rosa)", AppAgent.suffix("0.1.0", false, false));
    }

    @Test
    public void aPhoneWithLittleMemoryStartsCalm() {
        assertEquals(" TheMeshAndroid/0.1.0 (android; skin=rosa; fx=calm)", AppAgent.suffix("0.1.0", true, false));
    }

    @Test
    public void anEmulatorStartsStillWhateverItsMemory() {
        assertEquals(" TheMeshAndroid/0.1.0 (android; skin=rosa; fx=still)", AppAgent.suffix("0.1.0", false, true));
        assertEquals(" TheMeshAndroid/0.1.0 (android; skin=rosa; fx=still)", AppAgent.suffix("0.1.0", true, true));
    }

    @Test
    public void emulatorsAreToldFromPhones() {
        assertTrue(AppAgent.looksLikeEmulator("generic_x86_64/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036.A1/11228894:userdebug/dev-keys", "sdk_gphone64_x86_64", "ranchu", "sdk_gphone64_x86_64"));
        assertTrue(AppAgent.looksLikeEmulator("google/sdk_gphone_x86/generic_x86:11/RSR1/1:user/release-keys", "Android SDK built for x86", "goldfish", "sdk_gphone_x86"));
        assertTrue(AppAgent.looksLikeEmulator("", "Emulator", "", ""));
        assertFalse(AppAgent.looksLikeEmulator("google/panther/panther:14/UP1A.231105.001/11010452:user/release-keys", "Pixel 7", "panther", "panther"));
        assertFalse(AppAgent.looksLikeEmulator("samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXU3BXA1:user/release-keys", "SM-S911B", "qcom", "dm1qxxx"));
        assertFalse(AppAgent.looksLikeEmulator(null, null, null, null));
    }
}
