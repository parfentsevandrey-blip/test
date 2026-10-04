package app.themesh.mobile.core;

import java.util.Locale;

/**
 * Что приложение сообщает о себе в user agent страницы: что это окно телефона, с какого вида начинать («Роса») и — если
 * телефон не потянет полные эффекты — с какого уровня движения. Страница разбирает это в js/boot.js; то, что человек выбрал
 * в «Настройках → Внешний вид → Эффекты», сильнее.
 */
public final class AppAgent {
    private AppAgent() {
    }

    /**
     * Хвост user agent'а: «TheMeshAndroid/0.1.0 (android; skin=rosa)» и, где нужно, «; fx=calm» или «; fx=still».
     * Эмулятору (отрисовка программная: размытие и мерцание звёзд на нём едят всё время процессора, а снимки должны быть
     * спокойными) — «still»; телефону с малой памятью (Android Go) — «calm»; остальным — полные эффекты по умолчанию.
     */
    public static String suffix(String version, boolean lowRam, boolean emulator) {
        StringBuilder b = new StringBuilder(" TheMeshAndroid/").append(version).append(" (android; skin=rosa");
        if (emulator) {
            b.append("; fx=still");
        } else if (lowRam) {
            b.append("; fx=calm");
        }
        return b.append(')').toString();
    }

    /** Похоже ли устройство на эмулятор Android (по тому, что он пишет о себе в {@code Build}). */
    public static boolean looksLikeEmulator(String fingerprint, String model, String hardware, String product) {
        String f = lower(fingerprint), m = lower(model), h = lower(hardware), p = lower(product);
        return f.startsWith("generic") || f.contains("emulator") || f.contains("sdk_gphone") || f.contains("test-keys") && f.contains("sdk")
                || m.contains("emulator") || m.contains("android sdk built for") || m.contains("sdk_gphone")
                || h.equals("goldfish") || h.equals("ranchu") || h.contains("vbox")
                || p.contains("sdk_gphone") || p.equals("sdk") || p.contains("emulator");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
