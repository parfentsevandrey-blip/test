package app.themesh.mobile;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Небо «Росы» за окном: гладкий переход от цвета неба у верхнего края страницы к цвету у нижнего (их сообщает страница,
 * см. js/sky-palette.js, {@code --sky-top} и {@code --sky-bottom}). Саму страницу красит она сама (звёзды, солнце, свечение
 * у горизонта), а это — то, что видно вокруг неё: полосы под строкой состояния и навигационной панелью (края совпадают с краями
 * неба страницы, поэтому шва нет) и заставка, пока страница не загрузилась.
 */
final class SkyDrawable extends Drawable {
    private final int top;
    private final int bottom;
    private final Paint paint = new Paint();

    SkyDrawable(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        paint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom, top, bottom, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
