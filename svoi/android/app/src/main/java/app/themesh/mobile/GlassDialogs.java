package app.themesh.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * Обычные окна-вопросы приложения («О программе», просьба про уведомления) в виде стеклянной панели: тот же вид,
 * что у меню (см. {@link MenuPopup}), и цвета страницы, а не темы системы.
 */
final class GlassDialogs {
    private GlassDialogs() {
    }

    /** Показывает окно и перекрашивает его. У заголовка AlertDialog нет публичного идентификатора: ищем его по имени. */
    @SuppressLint("DiscouragedApi")
    static AlertDialog show(Activity activity, AlertDialog.Builder builder, boolean light, int text, int muted, int accent) {
        AlertDialog dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            boolean frosted = Glass.frosted(activity);
            window.setBackgroundDrawable(Glass.panel(activity, light, 28, frosted));
            if (frosted && android.os.Build.VERSION.SDK_INT >= 31) {
                window.setBackgroundBlurRadius((int) (28 * activity.getResources().getDisplayMetrics().density));
            }
        }
        TextView title = dialog.findViewById(activity.getResources().getIdentifier("alertTitle", "id", "android"));
        if (title != null) {
            title.setTextColor(text);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(muted);
        }
        for (int which : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button b = dialog.getButton(which);
            if (b != null) {
                b.setTextColor(accent);
            }
        }
        return dialog;
    }
}
