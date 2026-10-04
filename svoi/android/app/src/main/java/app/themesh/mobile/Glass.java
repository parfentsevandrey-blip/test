package app.themesh.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.WindowManager;

/**
 * Стеклянная панель для окон приложения (меню, диалоги, карточка ошибки): полупрозрачная заливка, мягкий блик
 * по диагонали и светлый ободок, как у карточек страницы (css/glass.css: {@code --glass-fill}, {@code --glass-sheen},
 * {@code --glass-rim}). Размывать то, что лежит за панелью, умеет только система и только с Android 12 (и не на каждом
 * телефоне): там окно просит у неё размытие ({@link #frosted}), и заливка прозрачнее; без размытия заливка плотнее,
 * чтобы текст читался на любом фоне. Даже «с размытием» заливка остаётся плотной (около 85 %): система может сказать, что
 * размывает, и не размыть (так бывает на эмуляторе), и тогда сквозь слишком прозрачную панель просвечивал бы текст страницы.
 */
final class Glass {
    private Glass() {
    }

    /** Размоет ли система то, что за окном приложения (Android 12+, если телефон это позволяет). */
    static boolean frosted(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return false;
        }
        WindowManager wm = context.getSystemService(WindowManager.class);
        return wm != null && wm.isCrossWindowBlurEnabled();
    }

    /**
     * Стеклянная панель со скруглёнными углами.
     *
     * @param light   светлая ли тема страницы
     * @param frosted размоет ли система фон за панелью (иначе заливка плотнее)
     */
    static Drawable panel(Context context, boolean light, float radiusDp, boolean frosted) {
        float density = context.getResources().getDisplayMetrics().density;
        float radius = radiusDp * density;
        int fillColor;
        int sheenFrom;
        int rim;
        if (light) {
            fillColor = Color.argb(frosted ? 214 : 232, 255, 255, 255);
            sheenFrom = Color.argb(frosted ? 140 : 110, 255, 255, 255);
            rim = Color.argb(217, 255, 255, 255);
        } else {
            fillColor = Color.argb(frosted ? 220 : 238, 22, 31, 36);
            sheenFrom = Color.argb(frosted ? 44 : 36, 255, 255, 255);
            rim = Color.argb(56, 255, 255, 255);
        }
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.RECTANGLE);
        fill.setCornerRadius(radius);
        fill.setColor(fillColor);

        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {sheenFrom, sheenFrom & 0x00FFFFFF, sheenFrom & 0x00FFFFFF});
        sheen.setShape(GradientDrawable.RECTANGLE);
        sheen.setCornerRadius(radius);

        GradientDrawable edge = new GradientDrawable();
        edge.setShape(GradientDrawable.RECTANGLE);
        edge.setCornerRadius(radius);
        edge.setColor(Color.TRANSPARENT);
        edge.setStroke(Math.max(1, Math.round(density)), rim);

        return new LayerDrawable(new Drawable[] {fill, sheen, edge});
    }

    /** Таблетка стекла для кнопок поверх камеры: тёмная, потому что кадр под ней может быть любым. */
    static Drawable darkPill(Context context) {
        return panel(context, false, 40, false);
    }
}
