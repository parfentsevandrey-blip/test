package app.themesh.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import app.themesh.mobile.core.ThemeColor;

/**
 * Мягкий цветной фон «под стеклом»: те же пятна света, что рисует страница при стеклянном виде (css/glass.css,
 * {@code --aurora}). Окно рисует его само, за прозрачной страницей, чтобы строка состояния, навигационная панель и
 * страница были одной картинкой (иначе у панелей был бы свой, плоский цвет и на стыке с фоном страницы виднелся бы шов).
 *
 * <p>Пятна — эллиптические радиальные градиенты: центр в долях окна, радиусы в долях большей стороны («vmax» в CSS),
 * прозрачность к краю падает до нуля на {@code fade} радиуса. Картинка плавная, поэтому один раз рисуется в уменьшенную
 * копию, а на каждом кадре окно рисует одну текстуру вместо пяти заливок на весь экран.
 */
final class AuroraDrawable extends Drawable {
    /** Во сколько раз копия меньше окна. */
    private static final int SCALE = 4;

    /** Пятно: цвет, непрозрачность в центре, центр (доли ширины и высоты), радиусы (доли большей стороны), где оно гаснет. */
    private static final class Blob {
        final int rgb;
        final float alpha;
        final float cx;
        final float cy;
        final float rx;
        final float ry;
        final float fade;

        Blob(int rgb, float alpha, float cx, float cy, float rx, float ry, float fade) {
            this.rgb = rgb;
            this.alpha = alpha;
            this.cx = cx;
            this.cy = cy;
            this.rx = rx;
            this.ry = ry;
            this.fade = fade;
        }
    }

    private static final Blob[] LIGHT = {
            new Blob(0x3AD6A8, .50f, .06f, .02f, .62f, .46f, .62f),
            new Blob(0x609EFF, .46f, .98f, .04f, .56f, .44f, .60f),
            new Blob(0xB28AFF, .38f, .90f, 1.00f, .62f, .50f, .62f),
            new Blob(0xFFBA7E, .42f, .02f, .98f, .54f, .40f, .60f),
            new Blob(0x6EE2EC, .26f, .52f, .48f, .38f, .30f, .70f),
    };
    private static final Blob[] DARK = {
            new Blob(0x0EB292, .36f, .06f, .02f, .62f, .46f, .62f),
            new Blob(0x3A68E2, .36f, .98f, .04f, .56f, .44f, .60f),
            new Blob(0x7A4CDE, .32f, .90f, 1.00f, .62f, .50f, .62f),
            new Blob(0xD27646, .20f, .02f, .98f, .54f, .40f, .60f),
            new Blob(0x1E96AA, .20f, .52f, .48f, .38f, .30f, .70f),
    };

    private final boolean light;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap cache;

    AuroraDrawable(boolean light) {
        this.light = light;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        cache = null;
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        int w = Math.max(1, bounds.width() / SCALE);
        int h = Math.max(1, bounds.height() / SCALE);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        render(new Canvas(bitmap), w, h, light);
        cache = bitmap;
    }

    /** Рисует основу и пять пятен в прямоугольник w×h. */
    static void render(Canvas canvas, int w, int h, boolean light) {
        canvas.drawColor(light ? ThemeColor.GLASS_LIGHT_BG : ThemeColor.GLASS_DARK_BG);
        float vmax = Math.max(w, h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Blob b : light ? LIGHT : DARK) {
            int from = ((Math.round(b.alpha * 255f) & 0xFF) << 24) | b.rgb;
            int to = b.rgb; // тот же цвет с нулевой прозрачностью: иначе по краю пятна пошёл бы серый ореол
            RadialGradient g = new RadialGradient(0f, 0f, 1f, new int[] {from, to}, new float[] {0f, b.fade}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(b.rx * vmax, b.ry * vmax);
            m.postTranslate(b.cx * w, b.cy * h);
            g.setLocalMatrix(m);
            p.setShader(g);
            canvas.drawRect(0, 0, w, h, p);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap bitmap = cache;
        if (bitmap == null) {
            canvas.drawColor(light ? ThemeColor.GLASS_LIGHT_BG : ThemeColor.GLASS_DARK_BG);
        } else {
            canvas.drawBitmap(bitmap, null, getBounds(), paint);
        }
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
