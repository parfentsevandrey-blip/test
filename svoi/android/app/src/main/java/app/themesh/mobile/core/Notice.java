package app.themesh.mobile.core;

/** Одно уведомление о событии узла: что показать и куда перейти по нажатию. */
public final class Notice {
    /** Вид события: от него зависит канал и значок. */
    public enum Kind {
        /** Кто-то предлагает прислать файл. */
        OFFER,
        /** Файл получен. */
        RECEIVED,
        /** Сообщение в чате. */
        CHAT,
        /** Новое письмо. */
        MAIL
    }

    public final Kind kind;
    /** Ключ события: одно и то же событие не показывается дважды. */
    public final String key;
    /** Новое уведомление с таким же тегом заменяет прежнее (чат: одно на собеседника). */
    public final String tag;
    public final String title;
    public final String body;
    /** Куда перейти в интерфейсе по нажатию («#/chat/<id>»); всегда проходит {@link Route#isValid}. */
    public final String route;

    public Notice(Kind kind, String key, String tag, String title, String body, String route) {
        this.kind = kind;
        this.key = key;
        this.tag = tag;
        this.title = title;
        this.body = body;
        this.route = route;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Notice)) {
            return false;
        }
        Notice n = (Notice) o;
        return kind == n.kind && key.equals(n.key) && tag.equals(n.tag) && title.equals(n.title)
                && body.equals(n.body) && route.equals(n.route);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "Notice{" + key + " | " + title + " | " + body + " | " + route + "}";
    }
}
