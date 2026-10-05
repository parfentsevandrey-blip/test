package app.themesh.mobile;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Системный запрос разрешения («Разрешить The Mesh снимать фото и видео?») на экране: нажать в нём «Не разрешать». Разрешение
 * на камеру в тесте, который идёт в процессе самого приложения, не отозвать — система убивает процесс, и тест вместе с ним
 * (проверено: «Process crashed»), — поэтому отказ даёт настоящий отказ человека в настоящем диалоге. Кнопку ищем по
 * идентификатору ({@code …:id/permission_deny_button}: пакет PermissionController на образах AOSP и Google API называется
 * по-разному, поэтому смотрим только на хвост) и, если не нашлось, по тексту.
 *
 * <p>Ищем во всех окнах, а не только в активном: на только что загруженном эмуляторе поверх запроса бывает окно системы «Pixel
 * Launcher isn't responding» (так и упал первый прогон в CI), и «активным» оказывается оно. Если кнопки отказа нет ни в одном
 * окне, а такое окно есть, нажимаем в нём «Подождать» и ищем снова на следующем круге.
 */
final class PermissionDialog {
    private PermissionDialog() {
    }

    /** Что нашлось на экране: кнопка нажата или нет, и что вообще на экране (для сообщения об ошибке). */
    static final class Result {
        final boolean clicked;
        final String screen;

        Result(boolean clicked, String screen) {
            this.clicked = clicked;
            this.screen = screen;
        }
    }

    /** Чтобы видеть окна системы и идентификаторы кнопок в них. */
    private static void seeAllWindows(UiAutomation automation) {
        AccessibilityServiceInfo info = automation.getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
        }
    }

    /**
     * Нажимает «Подождать» в окне системы «Приложение не отвечает», если оно на экране (на медленном эмуляторе так бывает у лаунчера, пока
     * окно приложения рисуется программно), чтобы оно не закрывало снимок. {@code true}, если нажато; окно может появиться снова.
     */
    static boolean waitOut(UiAutomation automation) {
        seeAllWindows(automation);
        for (AccessibilityNodeInfo root : roots(automation)) {
            // это окно держит система («android»), а не приложение: в окне приложения кнопку «Wait» нажимать не за что
            if (!"android".contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                continue;
            }
            AccessibilityNodeInfo wait = find(root, new StringBuilder(), 0, true);
            if (wait != null && wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }
        return false;
    }

    /** Нажимает «Не разрешать», если запрос разрешения на экране. */
    static Result deny(UiAutomation automation) {
        seeAllWindows(automation);
        List<AccessibilityNodeInfo> roots = roots(automation);
        if (roots.isEmpty()) {
            return new Result(false, "окон нет");
        }
        StringBuilder screen = new StringBuilder();
        AccessibilityNodeInfo wait = null;
        for (AccessibilityNodeInfo root : roots) {
            screen.append(screen.length() == 0 ? "" : " | ").append(root.getPackageName()).append(':');
            AccessibilityNodeInfo button = find(root, screen, 0, false);
            if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return new Result(true, screen.toString());
            }
            if (wait == null) {
                wait = find(root, new StringBuilder(), 0, true);
            }
        }
        if (wait != null && wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            screen.append(" [нажато «Подождать» в окне «не отвечает»]");
        }
        return new Result(false, screen.toString());
    }

    private static List<AccessibilityNodeInfo> roots(UiAutomation automation) {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = automation.getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) {
                    roots.add(root);
                }
            }
        }
        if (roots.isEmpty()) {
            AccessibilityNodeInfo active = automation.getRootInActiveWindow();
            if (active != null) {
                roots.add(active);
            }
        }
        return roots;
    }

    /** Кнопка отказа ({@code waitButton == false}) или кнопка «Подождать» окна «не отвечает» ({@code true}); {@code null} — нет. */
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, StringBuilder screen, int depth, boolean waitButton) {
        if (node == null || depth > 30) {
            return null;
        }
        String id = node.getViewIdResourceName();
        CharSequence text = node.getText();
        if (text != null && text.length() > 0 && screen.length() < 600) {
            screen.append(" «").append(text).append('»');
        }
        if (node.isClickable() && (waitButton ? isWait(id, text) : (isDenyId(id) || isDenyText(text)))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), screen, depth + 1, waitButton);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean isDenyId(String id) {
        return id != null && (id.endsWith(":id/permission_deny_button") || id.endsWith(":id/permission_deny_and_dont_ask_again_button"));
    }

    private static boolean isDenyText(CharSequence text) {
        if (text == null) {
            return false;
        }
        String t = text.toString().trim().toLowerCase(Locale.ROOT).replace('’', '\'');
        return t.equals("don't allow") || t.equals("deny") || t.equals("не разрешать") || t.equals("запретить");
    }

    /** «Подождать» в окне «Приложение не отвечает» ({@code android:id/aerr_wait}). */
    private static boolean isWait(String id, CharSequence text) {
        if (id != null && id.endsWith(":id/aerr_wait")) {
            return true;
        }
        String t = text == null ? "" : text.toString().trim().toLowerCase(Locale.ROOT);
        return t.equals("wait") || t.equals("подождать");
    }
}
