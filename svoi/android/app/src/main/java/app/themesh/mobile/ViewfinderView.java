package app.themesh.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Рамка-видоискатель поверх превью камеры: четыре уголка квадрата посередине экрана. Только подсказка, куда навести
 * камеру: код ищется на всём кадре, а не внутри рамки. Касания пропускает к превью (там фокус по нажатию).
 */
public final class ViewfinderView extends View {
    /** Сторона рамки — доля меньшей стороны экрана. */
    private static final float SIDE = 0.62f;
    /** Длина уголка — доля стороны рамки. */
    private static final float ARM = 0.17f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path corners = new Path();
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
        canvas.drawPath(corners, paint);
    }
}
