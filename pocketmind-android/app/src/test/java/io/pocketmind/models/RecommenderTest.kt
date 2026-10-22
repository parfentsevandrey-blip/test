package io.pocketmind.models

import io.pocketmind.hardware.ThermalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    @Test
    fun `flagship gets a large model that still runs comfortably`() {
        val result = Recommender.recommend(TestProfiles.flagship12)
        val best = result.best!!
        assertTrue("expected a 4B+ class model, got ${best.model.id}", best.model.qualityRank >= 9)
        assertTrue(best.verdict == Verdict.Excellent || best.verdict == Verdict.Good)
        assertTrue(best.memoryLoad <= 1.0)
        assertEquals(DeviceTier.Flagship, result.tier)
    }

    @Test
    fun `16 GB flagship can run the 9B model`() {
        val result = Recommender.recommend(TestProfiles.flagship16)
        val nineB = result.assessments.first { it.model.id == "qwen3.5-9b" }
        assertTrue(nineB.verdict != Verdict.TooHeavy)
    }

    @Test
    fun `mid-range phone gets a 2B class model, not a 4B one`() {
        val best = Recommender.recommend(TestProfiles.mid8).best!!
        assertTrue(best.model.runtimeRamBytes <= 2_000L * 1024 * 1024)
        assertTrue(best.model.qualityRank >= 5)
    }

    @Test
    fun `budget phone never gets a model above its memory budget`() {
        val result = Recommender.recommend(TestProfiles.budget4)
        val best = result.best!!
        assertTrue(best.model.runtimeRamBytes <= result.memoryBudgetBytes)
        assertEquals(2048, result.contextSize)
        val heavy = result.assessments.filter { it.model.runtimeRamBytes > 3_000L * 1024 * 1024 }
        assertTrue(heavy.all { it.verdict == Verdict.TooHeavy })
    }

    @Test
    fun `legacy phone gets only the tiniest models`() {
        val result = Recommender.recommend(TestProfiles.legacy2)
        val best = result.best
        assertTrue(best == null || best.model.runtimeRamBytes <= 900L * 1024 * 1024)
        assertEquals(DeviceTier.Entry, result.tier)
    }

    @Test
    fun `no storage means no recommendation`() {
        val profile = TestProfiles.profile(ramGib = 7.4, bandwidth = 15.0, features = setOf("asimddp"), storageFreeGib = 0.3)
        val result = Recommender.recommend(profile)
        assertNull(result.best)
        assertTrue(result.assessments.none { it.fitsStorage })
    }

    @Test
    fun `overheating lowers the speed estimate`() {
        val hot = TestProfiles.profile(ramGib = 11.2, bandwidth = 40.0, features = setOf("asimddp", "i8mm"), thermal = ThermalLevel.Severe)
        val model = ModelCatalog.byId("qwen3.5-4b")!!
        assertTrue(
            Recommender.assess(hot, model).estimatedTokensPerSecond <
                Recommender.assess(TestProfiles.flagship12, model).estimatedTokensPerSecond,
        )
    }

    @Test
    fun `missing benchmark falls back to a RAM based estimate`() {
        val profile = TestProfiles.profile(ramGib = 7.4, bandwidth = null, features = setOf("asimddp"))
        assertTrue(Recommender.effectiveBandwidth(profile) > 0)
        assertNotNull(Recommender.recommend(profile).best)
    }

    @Test
    fun `marketed RAM size rounds up kernel-reserved memory`() {
        assertEquals(8, TestProfiles.mid8.marketedRamGb)
        assertEquals(12, TestProfiles.flagship12.marketedRamGb)
        assertEquals(4, TestProfiles.budget4.marketedRamGb)
        assertEquals(16, TestProfiles.flagship16.marketedRamGb)
    }

    @Test
    fun `catalog entries are consistent`() {
        val ids = ModelCatalog.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ModelCatalog.models.forEach {
            assertTrue(it.fileName.endsWith(".gguf"))
            assertTrue(it.activeBytes <= it.sizeBytes * 1.05)
            assertTrue(it.downloadUrl.startsWith("https://huggingface.co/${it.repo}/resolve/main/"))
        }
    }
}
