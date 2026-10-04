package app.themesh.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Меню приложения («⋮»). Стандартный PopupMenu обрезает длинные пункты («Сохранять полученные файлы в..», а смысл —
 * в конце), а здесь они переносятся на вторую строку. Пункты и их галочки берутся из готового {@link Menu}
 * (menu/main.xml), выбор отдаётся обработчику пунктов меню.
 *
 * <p>Меню — стеклянная панель в углу окна, под строкой состояния: с Android 12 система размывает за ней страницу
 * (см. {@link Glass}), на старых телефонах заливка плотнее. Цвета текста берутся у вида страницы (светлая тема
 * или тёмная), а не у темы системы: человек выбирает тему в самом интерфейсе.
 */
final class MenuPopup {
    /** Нажатие на пункт. */
    interface Listener {
        void onItem(MenuItem item);
    }

    private MenuPopup() {
    }

    /**
     * Показывает меню; возвращённое окно нужно закрыть, когда окно приложения уходит с экрана.
     *
     * @param top   отступ от верха окна приложения, где меню начинается (под строкой состояния и верхней панелью страницы)
     * @param light светлая ли тема страницы
     * @param text  цвет текста пунктов
     * @param accent цвет галочек
     * @param rosaSmoke цвет дымчатого стекла «Росы» (см. {@code ThemeColor.rosaSmoke}) или 0, если страница не «Роса»
     */
    static Dialog show(Activity activity, Menu menu, int top, boolean light, int text, int accent, int rosaSmoke, Listener listener) {
        float density = activity.getResources().getDisplayMetrics().density;
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screen - (int) (24 * density), (int) (360 * density));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (6 * density);
        content.setPadding(0, pad, 0, pad);
        Dialog dialog = new Dialog(activity, R.style.Theme_TheMesh_Menu);
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) {
                continue;
            }
            boolean checkable = item.isCheckable();
            View row = inflater.inflate(checkable ? R.layout.menu_item_check : R.layout.menu_item_plain, content, false);
            TextView label = (TextView) row;
            label.setText(item.getTitle());
            label.setTextColor(text);
            if (checkable) {
                CheckedTextView check = (CheckedTextView) row;
                check.setChecked(item.isChecked());
                check.setCheckMarkTintList(ColorStateList.valueOf(accent));
            }
            row.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onItem(item);
            });
            content.addView(row);
        }

        dialog.setContentView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Window window = dialog.getWindow();
        if (window != null) {
            boolean frosted = Glass.frosted(activity);
            window.setBackgroundDrawable(rosaSmoke != 0 ? Glass.rosaPanel(activity, light, rosaSmoke, 24, frosted) : Glass.panel(activity, light, 22, frosted));
            if (frosted && android.os.Build.VERSION.SDK_INT >= 31) {
                window.setBackgroundBlurRadius((int) (28 * density));
            }
            window.setGravity(Gravity.TOP | Gravity.END);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = width;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.x = (int) (12 * density);
            lp.y = top;
            lp.dimAmount = 0f;
            window.setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        return dialog;
    }
}
