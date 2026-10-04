package app.themesh.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Стекло «Росы» для окон приложения (меню, диалоги, заставка, карточка ошибки): дымчатая заливка в цвете неба над головой
 * (светлая молочная над светлым небом), толстый светящийся ободок, который ярче всего вверху слева и уходит в голубоватый
 * внизу справа (так свет ложится на край толстого стекла), мягкий блик по диагонали и яркая искра на верхней кромке.
 * Это то же, что страница рисует для своих карточек (css/rosa.css: {@code --glass-fill}, {@code --glass-rim}, {@code --glass-sheen}).
 */
final class RosaGlass extends Drawable {
    private final float radius;
    private final float rim;
    private final boolean light;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;

    /**
     * @param smoke   цвет дымчатого стекла (см. {@code ThemeColor.rosaSmoke}); у светлого стекла не используется
     * @param frosted размоет ли систему то, что за окном (иначе заливка плотнее, чтобы текст читался на любом фоне)
     */
    RosaGlass(Context context, boolean light, int smoke, float radiusDp, boolean frosted) {
        density = context.getResources().getDisplayMetrics().density;
        this.light = light;
        radius = radiusDp * density;
        rim = Math.max(1f, 1.5f * density);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(light ? Color.argb(frosted ? 220 : 238, 255, 255, 255) : (smoke & 0x00FFFFFF) | ((frosted ? 0xDC : 0xEE) << 24));
        sheen.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(rim);
        hairline.setStyle(Paint.Style.STROKE);
        hairline.setStrokeWidth(Math.max(1f, density * 0.75f));
        hairline.setColor(light ? Color.argb(40, 27, 32, 48) : Color.argb(60, 0, 0, 0));
        glint.setStyle(Paint.Style.STROKE);
        glint.setStrokeCap(Paint.Cap.ROUND);
        glint.setStrokeWidth(Math.max(1.5f, 1.8f * density));
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float w = bounds.width();
        float h = bounds.height();
        sheen.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left + w * 0.75f, bounds.top + h * 0.7f,
                Color.argb(light ? 150 : 52, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
        // ободок: белый вверху слева, почти прозрачный посередине, голубоватый внизу справа
        int bright = Color.argb(light ? 250 : 190, 255, 255, 255);
        int mid = Color.argb(light ? 150 : 46, 255, 255, 255);
        int low = light ? Color.argb(120, 120, 176, 236) : Color.argb(110, 140, 222, 255);
        edge.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[] {bright, mid, mid, low}, new float[] {0f, 0.38f, 0.66f, 1f}, Shader.TileMode.CLAMP));
        float sparkAt = bounds.left + w * 0.72f;
        float half = Math.min(w * 0.12f, 26 * density);
        glint.setShader(new LinearGradient(sparkAt - half, 0, sparkAt + half, 0,
                new int[] {Color.argb(0, 255, 255, 255), Color.argb(light ? 255 : 230, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float inset = rim / 2f;
        box.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        canvas.drawRoundRect(box, radius, radius, fill);
        canvas.drawRoundRect(box, radius, radius, sheen);
        canvas.drawRoundRect(box, radius, radius, edge);
        canvas.drawRoundRect(box, radius, radius, hairline);
        float y = b.top + rim * 0.9f;
        canvas.drawLine(b.left + b.width() * 0.72f - Math.min(b.width() * 0.12f, 26 * density), y, b.left + b.width() * 0.72f + Math.min(b.width() * 0.12f, 26 * density), y, glint);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
