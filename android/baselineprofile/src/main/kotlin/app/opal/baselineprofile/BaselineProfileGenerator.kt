package app.opal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline and Startup Profiles of the app:
 * `./gradlew :app:generateBaselineProfile` (a connected device with API 28+, rooted or API 33+).
 * The result lands in app/src/release/generated/baselineProfiles and replaces the hand-written
 * app/src/main/baseline-prof.txt for the code paths it covers.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            skipOnboardingIfShown()
            visitTabs()
        }
}
