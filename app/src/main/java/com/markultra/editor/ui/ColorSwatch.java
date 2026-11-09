package com.markultra.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A single accent colour swatch with a selection ring. */
public class ColorSwatch extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int color = 0xFF7C4DFF;
    private int ringColor = 0xFFE7EAF0;
    private int surfaceColor = 0xFF151922;
    private boolean selected;

    public ColorSwatch(Context context) {
        super(context);
        init();
    }

    public ColorSwatch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        setClickable(true);
        setFocusable(true);
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public int color() {
        return color;
    }

    public void setPalette(int ringColor, int surfaceColor) {
        this.ringColor = ringColor;
        this.surfaceColor = surfaceColor;
        invalidate();
    }

    public void setSelectedSwatch(boolean selected) {
        this.selected = selected;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = (int) dp(48);
        int width = resolveSize(size, widthMeasureSpec);
        int height = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float pad = size * 0.16f;
        oval.set(pad, pad, size - pad, size - pad);
        paint.setColor(color);
        canvas.drawOval(oval, paint);
        if (!selected) {
            ringPaint.setColor(surfaceColor);
            ringPaint.setStrokeWidth(size * 0.035f);
            float outer = size * 0.5f - size * 0.03f;
            oval.set(size / 2 - outer, size / 2 - outer, size / 2 + outer, size / 2 + outer);
            canvas.drawOval(oval, ringPaint);
        }
        if (selected) {
            ringPaint.setColor(ringColor);
            ringPaint.setStrokeWidth(size * 0.065f);
            float outer = size * 0.5f - size * 0.06f;
            oval.set(size / 2 - outer, size / 2 - outer, size / 2 + outer, size / 2 + outer);
            canvas.drawOval(oval, ringPaint);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
