package app.opal.core.data

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import app.opal.core.model.settings.AppLanguage
import java.io.File
import java.util.Locale

/**
 * Per-app language. Android 13+: the platform [LocaleManager] (persisted by the system, applied to
 * every process of the app, also shown in system settings). Below 13: the tag is stored in a small
 * file and applied by wrapping the base context of the activity and the tunnel service — a file,
 * not DataStore, because it must be readable synchronously before the first frame.
 */
object AppLocaleStore {

    fun current(context: Context): AppLanguage {
        val tag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context
                    .getSystemService(LocaleManager::class.java)
                    ?.applicationLocales
                    ?.takeUnless { it.isEmpty }
                    ?.get(0)
                    ?.language
            } else {
                readLegacy(context)
            }
        return AppLanguage.entries.firstOrNull { it.tag != null && it.tag == tag }
            ?: AppLanguage.System
    }

    /** Returns true when the caller must recreate its activity (below Android 13). */
    fun set(context: Context, language: AppLanguage): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) }
                    ?: LocaleList.getEmptyLocaleList()
            return false
        }
        val file = legacyFile(context)
        if (language.tag == null) file.delete() else file.writeText(language.tag!!)
        return true
    }

    /**
     * Wraps [base] with the stored language (no-op on Android 13+, where the system does it). The
     * app ships as APKs (all languages inside) and bundles disable language splits, so no Play Core
     * language download is needed.
     */
    @SuppressLint("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = readLegacy(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    private fun legacyFile(context: Context) = File(context.noBackupFilesDir, "app_language")

    private fun readLegacy(context: Context): String? =
        legacyFile(context).takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
}
