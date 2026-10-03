package app.themesh.mobile;

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * Меню «⋮» в верхней полосе окна. Стандартный PopupMenu обрезает длинные пункты («Сохранять полученные файлы в..»,
 * а смысл — в конце), а здесь они переносятся на вторую строку. Пункты и их галочки берутся из готового {@link Menu}
 * (menu/main.xml), выбор отдаётся обработчику пунктов меню.
 */
final class MenuPopup {
    /** Нажатие на пункт. */
    interface Listener {
        void onItem(MenuItem item);
    }

    private MenuPopup() {
    }

    /** Показывает меню; возвращённое окно нужно закрыть, когда окно приложения уходит с экрана. */
    static PopupWindow show(Activity activity, View anchor, Menu menu, Listener listener) {
        float density = activity.getResources().getDisplayMetrics().density;
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screen - (int) (32 * density), (int) (360 * density));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        LayoutInflater inflater = LayoutInflater.from(activity);
        PopupWindow popup = new PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) {
                continue;
            }
            boolean checkable = item.isCheckable();
            View row = inflater.inflate(checkable ? R.layout.menu_item_check : R.layout.menu_item_plain, content, false);
            ((TextView) row).setText(item.getTitle());
            if (checkable) {
                ((CheckedTextView) row).setChecked(item.isChecked());
            }
            row.setOnClickListener(v -> {
                popup.dismiss();
                listener.onItem(item);
            });
            content.addView(row);
        }

        // Фон и тень, как у прежнего меню: цвет фона окна (тема приложения: бежевый днём, тёмный ночью), скруглённые углы
        int color = Color.WHITE;
        TypedArray a = activity.obtainStyledAttributes(new int[] {android.R.attr.colorBackground});
        try {
            color = a.getColor(0, color);
        } finally {
            a.recycle();
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(4 * density);
        popup.setBackgroundDrawable(background);
        popup.setElevation(8 * density);
        popup.setOutsideTouchable(true);
        popup.showAsDropDown(anchor, 0, 0, Gravity.END);
        return popup;
    }
}
