package app.themesh.mobile.core;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/**
 * Полученные файлы на виду у человека. Узел кладёт принятый файл в папку приложения
 * ({@code <filesDir>/Downloads/The Mesh}), которой человек не видит. Когда передача от другого устройства
 * закончилась (то же событие, что даёт уведомление «Файл получен»), копия файла кладётся в общую папку
 * «Загрузки/The Mesh». Своя копия остаётся: на неё ссылается интерфейс.
 *
 * <p>Здесь только решения («копировать ли, что сказать человеку»), а само копирование — за {@link Store}: на
 * телефоне это MediaStore, в модульных тестах — подделка.
 */
public final class ReceivedFiles {
    /** Папка внутри «Загрузок», куда ложатся копии. */
    public static final String FOLDER = "The Mesh";

    /** Куда копируем. */
    public interface Store {
        /** Можно ли сейчас писать в «Загрузки»: с Android 10 всегда, на Android 8–9 — если выдано разрешение. */
        boolean canWrite();

        /**
         * Кладёт копию файла в «Загрузки/The Mesh».
         *
         * @param name желаемое имя (уже безопасное); если оно занято, хранилище подбирает другое
         * @param mime тип файла из события узла; может быть пустым
         * @return имя, под которым копия легла в папку
         */
        String copy(File source, String name, String mime) throws IOException;
    }

    /** Чем кончилось. */
    public enum Outcome {
        /** Копия сделана. */
        COPIED,
        /** Человек выключил «Сохранять полученные файлы в «Загрузки»». */
        OFF,
        /** Нет разрешения на запись (Android 8–9): копию не делали. */
        NO_PERMISSION,
        /** Копировать нечего или нельзя: файла нет или он лежит не в папке приложения. */
        SKIPPED,
        /** Копировать пробовали, но не вышло. */
        FAILED
    }

    /** Итог для одного события. */
    public static final class Result {
        public final Outcome outcome;
        /** Имя файла в папке приложения. */
        public final String name;
        /** Имя копии в «Загрузках» (только при {@link Outcome#COPIED}), иначе пустая строка. */
        public final String savedName;
        /** Что пошло не так (для журнала; имён файлов здесь нет), иначе пустая строка. */
        public final String error;

        Result(Outcome outcome, String name, String savedName, String error) {
            this.outcome = outcome;
            this.name = name;
            this.savedName = savedName;
            this.error = error;
        }

        /** Копия легла под другим именем, чем лежит файл у приложения (имя в «Загрузках» уже было занято). */
        public boolean renamed() {
            return outcome == Outcome.COPIED && !savedName.equals(name);
        }

        @Override
        public String toString() {
            return "Result{" + outcome + ", " + name + (savedName.isEmpty() ? "" : " -> " + savedName) + "}";
        }
    }

    private final Store store;
    private final BooleanSupplier enabled;
    private final File appFiles;
    private final File nodeData;
    private final SeenKeys handled = new SeenKeys();

    /**
     * @param enabled  включена ли настройка «Сохранять полученные файлы в «Загрузки»» (спрашивается для каждого файла)
     * @param appFiles каталог файлов приложения: копируется только то, что лежит внутри него (остальное и так на виду)
     * @param nodeData каталог данных узла внутри {@code appFiles}: ключи и база, их не копируем никогда
     */
    public ReceivedFiles(Store store, BooleanSupplier enabled, File appFiles, File nodeData) {
        this.store = store;
        this.enabled = enabled;
        this.appFiles = appFiles;
        this.nodeData = nodeData;
    }

    /** Это окончание передачи файла от другого устройства (и ничего больше). */
    public static boolean isFinishedIncoming(String kind, JSONObject d) {
        return "transfer".equals(kind) && d != null
                && "in".equals(Json.str(d, "dir")) && "done".equals(Json.str(d, "state"));
    }

    /**
     * Обрабатывает событие узла. Каждая передача обрабатывается один раз, сколько бы раз ни пришло событие.
     *
     * @return итог или {@code null}, если событие не об окончании приёма файла (или о нём уже было)
     */
    public Result onEvent(String kind, JSONObject d) {
        if (!isFinishedIncoming(kind, d)) {
            return null; // исходящие передачи, предложения, ход загрузки — копировать нечего
        }
        String id = Json.str(d, "id");
        if (id.isEmpty() || !handled.add("done:" + id)) {
            return null;
        }
        String path = Json.str(d, "path");
        String shown = path.isEmpty() ? Json.str(d, "name") : new File(path).getName();
        if (!enabled.getAsBoolean()) {
            return result(Outcome.OFF, shown, "", "");
        }
        File source = checkedSource(path);
        if (source == null) {
            return result(Outcome.SKIPPED, shown, "", "the file is missing or lies outside the app's files");
        }
        String name = FileNames.safeName(source.getName());
        if (!store.canWrite()) {
            return result(Outcome.NO_PERMISSION, name, "", "");
        }
        try {
            String saved = store.copy(source, name, Json.str(d, "mime"));
            return result(Outcome.COPIED, name, saved == null || saved.isEmpty() ? name : saved, "");
        } catch (IOException | RuntimeException e) {
            return result(Outcome.FAILED, name, "", e.getClass().getSimpleName()); // без текста: в нём бывает имя файла
        }
    }

    private static Result result(Outcome outcome, String name, String saved, String error) {
        return new Result(outcome, name, saved, error);
    }

    /** Файл по пути из события, если его можно копировать: он есть, лежит в папке приложения и это не данные узла. */
    private File checkedSource(String path) {
        if (path.isEmpty()) {
            return null;
        }
        File f = new File(path);
        if (!f.isAbsolute()) {
            return null;
        }
        try {
            File real = f.getCanonicalFile();
            if (!real.isFile() || !inside(real, appFiles) || inside(real, nodeData)) {
                return null;
            }
            return real;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean inside(File file, File dir) throws IOException {
        String root = dir.getCanonicalPath();
        if (!root.endsWith(File.separator)) {
            root += File.separator;
        }
        return file.getPath().startsWith(root);
    }

    /**
     * Строка для уведомления «Файл получен»: куда делась копия (или почему её нет); пустая, если сказать нечего
     * (копию выключили или копировать было нечего).
     */
    public static String describe(Result r, Texts t) {
        if (r == null) {
            return "";
        }
        switch (r.outcome) {
            case COPIED:
                return r.renamed() ? t.copiedAs(FOLDER, r.savedName) : t.copiedTo(FOLDER);
            case NO_PERMISSION:
                return t.copyNeedsPermission();
            case FAILED:
                return t.copyFailed();
            default:
                return "";
        }
    }
}
