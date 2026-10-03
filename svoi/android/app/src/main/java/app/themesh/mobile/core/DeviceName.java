package app.themesh.mobile.core;

/**
 * Имя, которое телефон предлагает узлу (флаг {@code --name}): имя устройства из настроек Android, а если его нет —
 * модель телефона, а если и её нет — «android». Без этого узел берёт имя хоста, а на Android это всегда
 * «localhost». Узел сам превращает имя в метку DNS («Pixel 7 Pro» → {@code pixel-7-pro}), поэтому здесь имя только
 * очищается: пробелы схлопываются, управляющие и невидимые знаки убираются, длина ограничена.
 */
public final class DeviceName {
    /** Не больше стольких символов (кодовых точек Unicode: эмодзи считается за один). */
    public static final int MAX_LENGTH = 40;
    /** Имя, когда у телефона нет ни имени устройства, ни модели. */
    public static final String FALLBACK = "android";

    private DeviceName() {
    }

    /**
     * @param deviceName {@code Settings.Global.DEVICE_NAME} (может быть {@code null} или пустым)
     * @param model      {@code Build.MODEL}
     * @return первое из них, что осталось непустым после {@link #clean}, иначе {@link #FALLBACK}
     */
    public static String choose(String deviceName, String model) {
        String name = clean(deviceName);
        if (name.isEmpty()) {
            name = clean(model);
        }
        return name.isEmpty() ? FALLBACK : name;
    }

    /**
     * Убирает пробелы по краям, любую последовательность пробельных знаков (в том числе перевод строки, табуляцию,
     * неразрывный пробел) заменяет одним пробелом, выбрасывает управляющие знаки, невидимые знаки форматирования
     * (в том числе переключатели направления письма, которыми можно подделать имя) и «битые» суррогаты, и обрезает
     * результат до {@link #MAX_LENGTH} символов, не разрезая эмодзи и не оставляя пробела в конце.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (isSpace(cp)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (isInvisible(cp)) {
                continue;
            }
            int need = pendingSpace ? 2 : 1;
            if (count + need > MAX_LENGTH) {
                break;
            }
            if (pendingSpace) {
                out.append(' ');
                count++;
                pendingSpace = false;
            }
            out.appendCodePoint(cp);
            count++;
        }
        return out.toString();
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean isInvisible(int cp) {
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE || cp == 0xFFFD;
    }
}
