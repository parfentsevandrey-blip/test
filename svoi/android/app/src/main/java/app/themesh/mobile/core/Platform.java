package app.themesh.mobile.core;

/**
 * Как узел называет систему, на которой работает. Программа внутри приложения — сборка для Linux (Go), и сама она
 * сказала бы «linux/arm64»: другие устройства показывали бы телефон как сервер на Linux. Приложение знает лучше и сообщает
 * «android/arm64» через {@code THEMESH_PLATFORM}: так телефон остаётся телефоном в списках устройств и в запросе «добавьте меня».
 */
public final class Platform {
    private Platform() {
    }

    /**
     * @param abis {@code Build.SUPPORTED_ABIS}: первый — тот, под которым работает приложение
     * @return «android/&lt;процессор Go&gt;» («arm64», «arm», «amd64», «386»), или просто «android», если процессор неизвестен
     */
    public static String android(String[] abis) {
        String abi = abis == null || abis.length == 0 || abis[0] == null ? "" : abis[0];
        switch (abi) {
            case "arm64-v8a":
                return "android/arm64";
            case "armeabi-v7a":
                return "android/arm";
            case "x86_64":
                return "android/amd64";
            case "x86":
                return "android/386";
            default:
                return "android";
        }
    }
}
