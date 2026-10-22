package io.pocketmind.models

data class LlmModel(
    val id: String,
    val name: String,
    val developer: String,
    val parameters: String,
    val quantization: String,
    val repo: String,
    val fileName: String,
    /** Exact size of the GGUF file, used to verify the download. */
    val sizeBytes: Long,
    /** Weights streamed from RAM for every generated token (drives the speed estimate). */
    val activeBytes: Long,
    /** Estimated resident memory with a 4K context: weights + KV cache + runtime buffers. */
    val runtimeRamBytes: Long,
    /** Relative answer quality, higher is better; used to pick the best model that fits. */
    val qualityRank: Int,
    val russian: LanguageSupport,
    val description: String,
    val license: String,
) {
    val downloadUrl get() = "https://huggingface.co/$repo/resolve/main/$fileName?download=true"
    val pageUrl get() = "https://huggingface.co/$repo"
}

enum class LanguageSupport(val label: String) {
    Excellent("Отличный русский"),
    Good("Хороший русский"),
    Basic("Базовый русский"),
    EnglishOnly("Только английский"),
}
