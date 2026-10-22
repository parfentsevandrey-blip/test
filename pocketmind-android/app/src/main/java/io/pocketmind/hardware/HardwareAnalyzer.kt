package io.pocketmind.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import io.pocketmind.engine.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HardwareAnalyzer(
    private val context: Context,
    private val engine: LlamaEngine,
) {
    suspend fun analyze(onStep: (String) -> Unit = {}): DeviceProfile = withContext(Dispatchers.Default) {
        onStep("Определяю процессор")
        val cpu = readCpuInfo()

        onStep("Проверяю оперативную память")
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

        onStep("Смотрю графический ускоритель")
        val gpu = runCatching { readGpuInfo() }.getOrNull()

        onStep("Измеряю пропускную способность памяти")
        val bandwidth = runCatching { engine.benchmarkMemory(cpu.cores) }
            .getOrNull()
            ?.takeIf { it.isFinite() && it > 0.5 }

        onStep("Проверяю хранилище и температуру")
        val storageDir = context.getExternalFilesDir(null) ?: context.filesDir
        val stat = StatFs(storageDir.absolutePath)

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        DeviceProfile(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            socName = readSocName(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cpu = cpu,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            isLowRamDevice = activityManager.isLowRamDevice,
            storageFreeBytes = stat.availableBytes,
            storageTotalBytes = stat.totalBytes,
            gpu = gpu,
            memoryBandwidthGbs = bandwidth,
            thermal = readThermal(),
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            isCharging = plugged != 0,
            mediaPerformanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0,
        )
    }

    private fun readSocName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Build.HARDWARE.takeIf { it.isNotBlank() }
        val model = Build.SOC_MODEL.takeUnless { it.isBlank() || it == Build.UNKNOWN } ?: return Build.HARDWARE
        val vendor = Build.SOC_MANUFACTURER.takeUnless { it.isBlank() || it == Build.UNKNOWN }
        return SocNames.prettify(vendor, model)
    }

    private fun readThermal(): ThermalLevel {
        val power = context.getSystemService(PowerManager::class.java) ?: return ThermalLevel.Unknown
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.Normal
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.Light
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.Moderate
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.Severe
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.Critical
            else -> ThermalLevel.Unknown
        }
    }

    private fun readCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val cpuinfo = runCatching { File("/proc/cpuinfo").readLines() }.getOrDefault(emptyList())

        val features = cpuinfo.firstOrNull { it.startsWith("Features") }
            ?.substringAfter(':')?.trim()?.split(' ')?.filter { it.isNotBlank() }?.toSet()
            .orEmpty()

        // "CPU implementer" / "CPU part" pairs appear once per core, in core order.
        val implementers = cpuinfo.filter { it.startsWith("CPU implementer") }.map { it.substringAfter(':').trim() }
        val parts = cpuinfo.filter { it.startsWith("CPU part") }.map { it.substringAfter(':').trim() }

        val perCore = (0 until cores).map { index ->
            val freq = readSysInt("/sys/devices/system/cpu/cpu$index/cpufreq/cpuinfo_max_freq")?.div(1000)
            val name = CpuParts.name(implementers.getOrNull(index), parts.getOrNull(index))
            name to freq
        }
        val clusters = perCore
            .groupBy { it }
            .map { (key, list) -> CpuCluster(coreName = key.first, cores = list.size, maxFreqMhz = key.second) }
            .sortedByDescending { it.maxFreqMhz ?: 0 }

        val topFreq = clusters.mapNotNull { it.maxFreqMhz }.maxOrNull()
        val performanceCores = if (topFreq == null) {
            (cores / 2).coerceAtLeast(1)
        } else {
            clusters.filter { (it.maxFreqMhz ?: 0) >= topFreq * 0.6 }.sumOf { it.cores }
        }

        return CpuInfo(cores, clusters, features, performanceCores.coerceIn(1, cores))
    }

    private fun readSysInt(path: String): Int? =
        runCatching { File(path).readText().trim().toInt() }.getOrNull()

    private fun readGpuInfo(): GpuInfo? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
            val config = configs[0]
            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            val surface = EGL14.eglCreatePbufferSurface(
                display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            try {
                EGL14.eglMakeCurrent(display, surface, surface, context)
                return GpuInfo(
                    renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty(),
                    vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty(),
                    glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
                        .removePrefix("OpenGL ES ").substringBefore(' '),
                    vulkanVersion = readVulkanVersion(),
                )
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    private fun readVulkanVersion(): String? {
        val feature = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION } ?: return null
        val v = feature.version
        return "${v shr 22}.${(v shr 12) and 0x3ff}"
    }
}

/** Maps Arm "CPU part" ids from /proc/cpuinfo to marketing core names. */
private object CpuParts {
    private val arm = mapOf(
        "0xd03" to "Cortex-A53", "0xd04" to "Cortex-A35", "0xd05" to "Cortex-A55",
        "0xd07" to "Cortex-A57", "0xd08" to "Cortex-A72", "0xd09" to "Cortex-A73",
        "0xd0a" to "Cortex-A75", "0xd0b" to "Cortex-A76", "0xd0d" to "Cortex-A77",
        "0xd41" to "Cortex-A78", "0xd44" to "Cortex-X1", "0xd46" to "Cortex-A510",
        "0xd47" to "Cortex-A710", "0xd48" to "Cortex-X2", "0xd4d" to "Cortex-A715",
        "0xd4e" to "Cortex-X3", "0xd80" to "Cortex-A520", "0xd81" to "Cortex-A720",
        "0xd82" to "Cortex-X4", "0xd85" to "Cortex-X925", "0xd87" to "Cortex-A725",
        "0xd88" to "Cortex-A520AE", "0xd8e" to "Cortex-A720AE",
        "0xd8a" to "C1-Nano", "0xd8b" to "C1-Pro", "0xd8c" to "C1-Ultra", "0xd90" to "C1-Premium",
    )

    fun name(implementer: String?, part: String?): String {
        if (part == null) return "Ядро"
        val key = part.lowercase()
        return when (implementer?.lowercase()) {
            "0x41" -> arm[key] ?: "Arm $key"
            "0x51" -> if (key == "0x001") "Oryon" else "Kryo"
            "0x53" -> "Samsung Mongoose"
            else -> arm[key] ?: "Ядро $key"
        }
    }
}

/** Friendly names for the most common SoC model codes reported by Build.SOC_MODEL. */
private object SocNames {
    private val known = mapOf(
        "SM8850" to "Snapdragon 8 Elite Gen 5", "SM8750" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3", "SM8635" to "Snapdragon 8s Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2", "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1", "SM8350" to "Snapdragon 888",
        "SM8250" to "Snapdragon 865", "SM8150" to "Snapdragon 855",
        "SM7750" to "Snapdragon 7 Gen 4", "SM7675" to "Snapdragon 7+ Gen 3", "SM7550" to "Snapdragon 7 Gen 3",
        "SM7475" to "Snapdragon 7+ Gen 2", "SM7450" to "Snapdragon 7 Gen 1",
        "SM7325" to "Snapdragon 778G", "SM6450" to "Snapdragon 6 Gen 1", "SM6375" to "Snapdragon 695",
        "SM4450" to "Snapdragon 4 Gen 2", "SM4375" to "Snapdragon 4 Gen 1",
        "MT6993" to "Dimensity 9500", "MT6991" to "Dimensity 9400", "MT6989" to "Dimensity 9300",
        "MT6985" to "Dimensity 9200", "MT6983" to "Dimensity 9000", "MT6899" to "Dimensity 8400",
        "MT6897" to "Dimensity 8300", "MT6896" to "Dimensity 8200", "MT6895" to "Dimensity 8100",
        "MT6878" to "Dimensity 7300", "MT6886" to "Dimensity 7200", "MT6877" to "Dimensity 1080",
        "MT6835" to "Dimensity 6300", "MT6833" to "Dimensity 700", "MT6789" to "Helio G99",
        "MT6769" to "Helio G85", "MT6768" to "Helio G85",
        "s5e9955" to "Exynos 2500", "s5e9945" to "Exynos 2400", "s5e9925" to "Exynos 2200",
        "s5e8855" to "Exynos 1580", "s5e8845" to "Exynos 1480", "s5e8835" to "Exynos 1380",
    )

    fun prettify(vendor: String?, model: String): String {
        known[model]?.let { return it }
        known.entries.firstOrNull { model.startsWith(it.key, ignoreCase = true) }?.let { return it.value }
        return when {
            vendor == null -> model
            model.contains(vendor, ignoreCase = true) -> model
            else -> "$vendor $model"
        }
    }
}
