package io.pocketmind.models

import io.pocketmind.hardware.CpuCluster
import io.pocketmind.hardware.CpuInfo
import io.pocketmind.hardware.DeviceProfile
import io.pocketmind.hardware.GpuInfo
import io.pocketmind.hardware.ThermalLevel

object TestProfiles {
    private const val GIB = 1024L * 1024 * 1024

    private val armv9Features = setOf("fp", "asimd", "asimdhp", "asimddp", "i8mm", "sve", "sve2", "bf16")
    private val armv82Features = setOf("fp", "asimd", "asimdhp", "asimddp")
    private val armv80Features = setOf("fp", "asimd")

    fun profile(
        ramGib: Double,
        bandwidth: Double?,
        features: Set<String>,
        storageFreeGib: Double = 64.0,
        thermal: ThermalLevel = ThermalLevel.Normal,
        clusters: List<CpuCluster> = listOf(CpuCluster("Cortex-A55", 8, 2000)),
    ) = DeviceProfile(
        manufacturer = "Test",
        model = "Phone",
        androidRelease = "17",
        sdkInt = 37,
        socName = "Test SoC",
        abi = "arm64-v8a",
        cpu = CpuInfo(clusters.sumOf { it.cores }, clusters, features, performanceCores = 4),
        totalRamBytes = (ramGib * GIB).toLong(),
        availableRamBytes = (ramGib * 0.4 * GIB).toLong(),
        isLowRamDevice = ramGib < 2,
        storageFreeBytes = (storageFreeGib * GIB).toLong(),
        storageTotalBytes = 256 * GIB,
        gpu = GpuInfo("Adreno (TM) 750", "Qualcomm", "3.2", "1.3"),
        memoryBandwidthGbs = bandwidth,
        thermal = thermal,
        batteryPercent = 80,
        isCharging = false,
        mediaPerformanceClass = 34,
    )

    /** 16 GB Armv9 flagship. */
    val flagship16 = profile(
        ramGib = 15.0, bandwidth = 45.0, features = armv9Features,
        clusters = listOf(CpuCluster("Cortex-X925", 1, 3630), CpuCluster("Cortex-X4", 3, 3300), CpuCluster("Cortex-A720", 4, 2400)),
    )

    /** 12 GB Armv9 flagship. */
    val flagship12 = profile(ramGib = 11.2, bandwidth = 40.0, features = armv9Features)

    /** 8 GB mid-range phone. */
    val mid8 = profile(ramGib = 7.4, bandwidth = 15.0, features = armv82Features)

    /** 4 GB budget phone. */
    val budget4 = profile(ramGib = 3.6, bandwidth = 8.0, features = armv82Features)

    /** 2 GB Cortex-A53 phone without dot-product instructions. */
    val legacy2 = profile(ramGib = 1.8, bandwidth = 4.5, features = armv80Features)
}
