package app.themesh.mobile.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Как телефон называет свою систему узлу: Android и процессор в терминах Go. */
public class PlatformTest {
    @Test
    public void everyAbiOfAPhoneHasItsGoName() {
        assertEquals("android/arm64", Platform.android(new String[] {"arm64-v8a", "armeabi-v7a", "armeabi"}));
        assertEquals("android/arm", Platform.android(new String[] {"armeabi-v7a", "armeabi"}));
        assertEquals("android/amd64", Platform.android(new String[] {"x86_64", "x86"}));
        assertEquals("android/386", Platform.android(new String[] {"x86"}));
    }

    @Test
    public void anUnknownOrMissingAbiStillSaysAndroid() {
        assertEquals("android", Platform.android(new String[] {"mips64"}));
        assertEquals("android", Platform.android(new String[0]));
        assertEquals("android", Platform.android(null));
        assertEquals("android", Platform.android(new String[] {null}));
    }
}
