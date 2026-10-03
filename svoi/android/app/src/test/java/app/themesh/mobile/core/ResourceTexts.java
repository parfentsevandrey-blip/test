package app.themesh.mobile.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Тексты из настоящих файлов strings.xml (русский — values/, английский — values-en/): модульные тесты проверяют ровно то,
 * что увидит человек, а не копию строк в тестах.
 */
final class ResourceTexts implements Texts {
    private final Map<String, String> strings;

    private ResourceTexts(Map<String, String> strings) {
        this.strings = strings;
    }

    static ResourceTexts ru() throws Exception {
        return new ResourceTexts(load("src/main/res/values/strings.xml"));
    }

    static ResourceTexts en() throws Exception {
        return new ResourceTexts(load("src/main/res/values-en/strings.xml"));
    }

    static Map<String, String> load(String path) throws Exception {
        File f = new File(path);
        if (!f.isFile()) {
            throw new IllegalStateException("не найден " + f.getAbsolutePath() + " (тесты запускаются из каталога app/)");
        }
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
        NodeList list = doc.getElementsByTagName("string");
        Map<String, String> out = new TreeMap<>();
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            out.put(e.getAttribute("name"), unescape(e.getTextContent()));
        }
        return out;
    }

    /** Как aapt2 читает строку: обратная косая перед n, апострофом, кавычкой, @, ? и четыре hex-цифры после u. */
    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n':
                    sb.append('\n');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                    break;
                default:
                    sb.append(n); // \' \" \\ \@ \?
            }
        }
        return sb.toString();
    }

    Map<String, String> all() {
        return strings;
    }

    String get(String name, Object... args) {
        String raw = strings.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("нет строки " + name);
        }
        return args.length == 0 ? raw : String.format(Locale.ROOT, raw, args);
    }

    @Override
    public String appName() {
        return get("app_name");
    }

    @Override
    public String offerBody(String file) {
        return get("notif_offer_body", file);
    }

    @Override
    public String receivedTitle() {
        return get("notif_received_title");
    }

    @Override
    public String receivedBody(String file, String peer) {
        return get("notif_received_body", file, peer);
    }

    @Override
    public String chatAttachment() {
        return get("notif_chat_attachment");
    }

    @Override
    public String mailTitle(String from) {
        return get("notif_mail_title", from);
    }

    @Override
    public String mailTitleUnknown() {
        return get("notif_mail_title_unknown");
    }

    @Override
    public String noSubject() {
        return get("notif_no_subject");
    }

    @Override
    public String statusStarting() {
        return get("status_starting");
    }

    @Override
    public String statusNoNetwork() {
        return get("status_no_network");
    }

    @Override
    public String statusAlone() {
        return get("status_alone");
    }

    @Override
    public String statusOnline(int online, int total) {
        return get("status_online", online, total);
    }

    @Override
    public String statusStopped() {
        return get("status_stopped");
    }
}
