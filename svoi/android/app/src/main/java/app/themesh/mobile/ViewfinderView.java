package app.themesh.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Рамка-видоискатель поверх превью камеры: четыре уголка квадрата посередине экрана, а вокруг него кадр чуть притемнён,
 * как у стеклянных плашек вокруг. Только подсказка, куда навести камеру: код ищется на всём кадре, а не внутри рамки.
 * Касания пропускает к превью (там фокус по нажатию).
 */
public final class ViewfinderView extends View {
    /** Сторона рамки — доля меньшей стороны экрана. */
    private static final float SIDE = 0.62f;
    /** Длина уголка — доля стороны рамки. */
    private static final float ARM = 0.17f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path corners = new Path();
    private final Path scrim = new Path();
    private final RectF arc = new RectF();
    private final float radius;

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        radius = 14 * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4 * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xF2FFFFFF);
        paint.setShadowLayer(6 * density, 0, 0, 0x66000000); // светлые уголки читаются и на светлом кадре
        setLayerType(LAYER_TYPE_SOFTWARE, null); // тень у линии (setShadowLayer) аппаратно не рисуется
        scrimPaint.setStyle(Paint.Style.FILL);
        scrimPaint.setColor(0x4D000000);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        corners.reset();
        float side = Math.min(w, h) * SIDE;
        float left = (w - side) / 2f;
        float top = (h - side) / 2f;
        float right = left + side;
        float bottom = top + side;
        float arm = side * ARM;
        float d = radius * 2;
        scrim.reset();
        scrim.setFillType(Path.FillType.EVEN_ODD);
        scrim.addRect(0, 0, w, h, Path.Direction.CW);
        scrim.addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW);
        // верхний левый: вниз по левой стороне, дуга, вправо по верхней
        corners.moveTo(left, top + arm);
        corners.lineTo(left, top + radius);
        arc.set(left, top, left + d, top + d);
        corners.arcTo(arc, 180, 90);
        corners.lineTo(left + arm, top);
        // верхний правый
        corners.moveTo(right - arm, top);
        corners.lineTo(right - radius, top);
        arc.set(right - d, top, right, top + d);
        corners.arcTo(arc, 270, 90);
        corners.lineTo(right, top + arm);
        // нижний правый
        corners.moveTo(right, bottom - arm);
        corners.lineTo(right, bottom - radius);
        arc.set(right - d, bottom - d, right, bottom);
        corners.arcTo(arc, 0, 90);
        corners.lineTo(right - arm, bottom);
        // нижний левый
        corners.moveTo(left + arm, bottom);
        corners.lineTo(left + radius, bottom);
        arc.set(left, bottom - d, left + d, bottom);
        corners.arcTo(arc, 90, 90);
        corners.lineTo(left, bottom - arm);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(scrim, scrimPaint);
        canvas.drawPath(corners, paint);
    }
}
