package io.pocketmind.hardware

data class CpuCluster(val coreName: String, val cores: Int, val maxFreqMhz: Int?)

data class CpuInfo(
    val cores: Int,
    val clusters: List<CpuCluster>,
    val features: Set<String>,
    /** Cores fast enough to be worth running inference threads on. */
    val performanceCores: Int,
) {
    val hasDotProd get() = "asimddp" in features
    val hasI8mm get() = "i8mm" in features
    val hasSve get() = "sve" in features || "sve2" in features
    val hasFp16 get() = "asimdhp" in features
    val maxFreqMhz get() = clusters.mapNotNull { it.maxFreqMhz }.maxOrNull()

    /** Human readable cluster layout, e.g. "1× Cortex-X4 · 3× Cortex-A720 · 4× Cortex-A520". */
    val layout get() = clusters.joinToString(" · ") { "${it.cores}× ${it.coreName}" }
}

data class GpuInfo(val renderer: String, val vendor: String, val glVersion: String, val vulkanVersion: String?)

enum class ThermalLevel(val label: String) {
    Normal("Норма"), Light("Лёгкий нагрев"), Moderate("Умеренный нагрев"),
    Severe("Сильный нагрев"), Critical("Перегрев"), Unknown("Нет данных"),
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val socName: String?,
    val abi: String,
    val cpu: CpuInfo,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowRamDevice: Boolean,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val gpu: GpuInfo?,
    /** Measured sequential memory read bandwidth, GB/s; null if the benchmark failed. */
    val memoryBandwidthGbs: Double?,
    val thermal: ThermalLevel,
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val mediaPerformanceClass: Int,
) {
    val totalRamGb get() = totalRamBytes / GB

    /** RAM size as marketed: the kernel reserves part of it, so 8 GB phones report ~7.4 GB. */
    val marketedRamGb: Int
        get() = MARKETED_SIZES.firstOrNull { it >= totalRamGb * 0.98 } ?: kotlin.math.ceil(totalRamGb).toInt()
    val deviceName get() = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

    companion object {
        const val GB = 1024.0 * 1024 * 1024
        private val MARKETED_SIZES = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32)
    }
}
