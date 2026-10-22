package io.pocketmind.models

import io.pocketmind.hardware.DeviceProfile
import kotlin.math.roundToInt

enum class Verdict(val label: String) {
    Excellent("Летает"),
    Good("Комфортно"),
    Slow("Тяжеловато"),
    TooHeavy("Не потянет"),
}

data class ModelAssessment(
    val model: LlmModel,
    val verdict: Verdict,
    val estimatedTokensPerSecond: Double,
    /** Share of the safe memory budget the model would take (1.0 = the whole budget). */
    val memoryLoad: Double,
    val fitsStorage: Boolean,
    val reasons: List<String>,
)

enum class DeviceTier(val label: String) {
    Flagship("Флагман"), High("Высокий класс"), Mid("Средний класс"), Entry("Начальный класс"),
}

data class Recommendation(
    val tier: DeviceTier,
    /** 0..100 */
    val score: Int,
    val memoryBudgetBytes: Long,
    val effectiveBandwidthGbs: Double,
    val contextSize: Int,
    val threads: Int,
    val best: ModelAssessment?,
    val assessments: List<ModelAssessment>,
)

/**
 * Decides which models a device can run without memory pressure or overheating.
 *
 * Memory: Android keeps the system, launcher and recent apps resident, so only a
 * share of physical RAM is safe to use before the low-memory killer kicks in.
 * Speed: token generation on phone CPUs is memory-bandwidth bound, so the speed
 * estimate is bandwidth divided by the bytes of weights read per token.
 */
object Recommender {
    private const val MIN_COMFORT_TPS = 6.0
    private const val MIN_EXCELLENT_TPS = 12.0
    private const val MIN_USABLE_TPS = 2.5

    fun memoryBudget(totalRamBytes: Long): Long {
        val gb = totalRamBytes / DeviceProfile.GB
        val share = when {
            gb <= 3.2 -> 0.35
            gb <= 4.5 -> 0.40
            gb <= 6.5 -> 0.45
            gb <= 8.5 -> 0.50
            gb <= 12.5 -> 0.55
            else -> 0.60
        }
        return (totalRamBytes * share).toLong()
    }

    /** Bandwidth that inference can realistically sustain, in GB/s. */
    fun effectiveBandwidth(profile: DeviceProfile): Double {
        val measured = profile.memoryBandwidthGbs
        val fallback = when {
            profile.totalRamGb <= 4.5 -> 8.0
            profile.totalRamGb <= 6.5 -> 12.0
            profile.totalRamGb <= 8.5 -> 18.0
            else -> 25.0
        }
        val raw = (measured ?: fallback).coerceIn(4.0, 80.0)
        // Matrix kernels without int8 dot-product instructions are compute bound well before memory.
        val cpuFactor = when {
            profile.cpu.hasI8mm -> 0.65
            profile.cpu.hasDotProd -> 0.55
            else -> 0.30
        }
        // Thermal throttling cuts sustained clocks.
        val thermalFactor = when (profile.thermal.ordinal) {
            0, 1, 5 -> 1.0
            2 -> 0.8
            else -> 0.6
        }
        return raw * cpuFactor * thermalFactor
    }

    fun contextSize(profile: DeviceProfile): Int = when {
        profile.totalRamGb <= 4.5 -> 2048
        profile.totalRamGb <= 8.5 -> 4096
        else -> 8192
    }

    fun threads(profile: DeviceProfile): Int = profile.cpu.performanceCores.coerceIn(2, 6)

    fun assess(profile: DeviceProfile, model: LlmModel): ModelAssessment {
        val budget = memoryBudget(profile.totalRamBytes)
        val load = model.runtimeRamBytes.toDouble() / budget
        val tps = effectiveBandwidth(profile) * 1e9 / model.activeBytes
        // Keep 500 MB of headroom on the storage after the download.
        val fitsStorage = profile.storageFreeBytes - model.sizeBytes > 500L * 1024 * 1024

        val reasons = buildList {
            add("Займёт ≈${formatGb(model.runtimeRamBytes)} из ${formatGb(budget)} безопасного объёма ОЗУ")
            add("Скорость ≈${tps.roundToInt().coerceAtLeast(1)} токенов/с")
            if (!fitsStorage) add("Не хватает места: нужно ${formatGb(model.sizeBytes)}")
        }

        val verdict = when {
            load > 1.25 || tps < MIN_USABLE_TPS -> Verdict.TooHeavy
            load > 1.0 || tps < MIN_COMFORT_TPS -> Verdict.Slow
            load <= 0.75 && tps >= MIN_EXCELLENT_TPS -> Verdict.Excellent
            else -> Verdict.Good
        }
        return ModelAssessment(model, verdict, tps, load, fitsStorage, reasons)
    }

    fun recommend(profile: DeviceProfile, catalog: List<LlmModel> = ModelCatalog.models): Recommendation {
        val assessments = catalog.map { assess(profile, it) }
        val comfortable = assessments.filter {
            it.fitsStorage && (it.verdict == Verdict.Excellent || it.verdict == Verdict.Good)
        }
        val best = comfortable.maxWithOrNull(
            compareBy<ModelAssessment> { it.model.qualityRank }.thenBy { it.estimatedTokensPerSecond },
        ) ?: assessments.filter { it.verdict == Verdict.Slow && it.fitsStorage }
            .minByOrNull { it.model.runtimeRamBytes }

        val score = deviceScore(profile)
        return Recommendation(
            tier = when {
                score >= 80 -> DeviceTier.Flagship
                score >= 60 -> DeviceTier.High
                score >= 35 -> DeviceTier.Mid
                else -> DeviceTier.Entry
            },
            score = score,
            memoryBudgetBytes = memoryBudget(profile.totalRamBytes),
            effectiveBandwidthGbs = effectiveBandwidth(profile),
            contextSize = contextSize(profile),
            threads = threads(profile),
            best = best,
            assessments = assessments,
        )
    }

    /** 0..100 score of how well the device suits local LLMs. */
    fun deviceScore(profile: DeviceProfile): Int {
        val ramScore = (profile.totalRamGb / 16.0).coerceAtMost(1.0) * 50
        val bwScore = (effectiveBandwidth(profile) / 30.0).coerceAtMost(1.0) * 35
        val cpuScore = when {
            profile.cpu.hasI8mm -> 15.0
            profile.cpu.hasDotProd -> 10.0
            else -> 3.0
        }
        return (ramScore + bwScore + cpuScore).roundToInt().coerceIn(1, 100)
    }

    fun formatGb(bytes: Long): String {
        val gb = bytes / DeviceProfile.GB
        return if (gb < 1) "${(bytes / (1024.0 * 1024)).roundToInt()} МБ"
        else String.format(java.util.Locale.ROOT, "%.1f ГБ", gb).replace('.', ',')
    }
}
