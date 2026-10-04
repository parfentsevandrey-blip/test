package app.themesh.mobile;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

/**
 * Системный запрос разрешения («Разрешить The Mesh снимать фото и видео?») на экране: нажать в нём «Не разрешать». Разрешение
 * на камеру в тесте, который идёт в процессе самого приложения, не отозвать — система убивает процесс, и тест вместе с ним
 * (проверено: «Process crashed»), — поэтому отказ даёт настоящий отказ человека в настоящем диалоге. Кнопку ищем по
 * идентификатору ({@code …:id/permission_deny_button}: пакет PermissionController на образах AOSP и Google API называется
 * по-разному, поэтому смотрим только на хвост) и, если не нашлось, по тексту.
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

    /** Нажимает «Не разрешать», если запрос разрешения на экране. */
    static Result deny(UiAutomation automation) {
        AccessibilityServiceInfo info = automation.getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            automation.setServiceInfo(info);
        }
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) {
            return new Result(false, "активного окна нет");
        }
        StringBuilder screen = new StringBuilder(String.valueOf(root.getPackageName())).append(':');
        AccessibilityNodeInfo button = find(root, screen, 0);
        return new Result(button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK), screen.toString());
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, StringBuilder screen, int depth) {
        if (node == null || depth > 30) {
            return null;
        }
        String id = node.getViewIdResourceName();
        CharSequence text = node.getText();
        if (text != null && text.length() > 0 && screen.length() < 600) {
            screen.append(" «").append(text).append('»');
        }
        if (node.isClickable() && (isDenyId(id) || isDenyText(text))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), screen, depth + 1);
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
}
