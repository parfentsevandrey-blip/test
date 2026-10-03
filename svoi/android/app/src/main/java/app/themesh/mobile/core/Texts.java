package app.themesh.mobile.core;

/**
 * Тексты, которые нужны логике уведомлений и строке состояния. В приложении их отдают строковые ресурсы
 * (values/ — русский, values-en/ — английский); в модульных тестах — те же файлы, прочитанные напрямую.
 */
public interface Texts {
    /** «The Mesh». */
    String appName();

    /** «хочет отправить вам файл «X»». */
    String offerBody(String file);

    /** «Файл получен». */
    String receivedTitle();

    /** ««X» — от устройства Y». */
    String receivedBody(String file, String peer);

    /** «Копия — в «Загрузки/The Mesh»». */
    String copiedTo(String folder);

    /** «Копия — в «Загрузки/The Mesh» под именем «X»» (имя в «Загрузках» уже было занято). */
    String copiedAs(String folder, String name);

    /** «Копия в «Загрузки» не сделана: нет разрешения на запись в память». */
    String copyNeedsPermission();

    /** «Копию в «Загрузки» сделать не удалось». */
    String copyFailed();

    /** «Прислал вложение». */
    String chatAttachment();

    /** «Новое письмо от X». */
    String mailTitle(String from);

    /** «Новое письмо» (когда не знаем, от кого). */
    String mailTitleUnknown();

    /** «(без темы)». */
    String noSubject();

    /** «Запускаем…». */
    String statusStarting();

    /** «Сеть ещё не создана». */
    String statusNoNetwork();

    /** «Пока только это устройство». */
    String statusAlone();

    /** «На связи 2 из 3». */
    String statusOnline(int online, int total);

    /** «Не работает». */
    String statusStopped();
}
