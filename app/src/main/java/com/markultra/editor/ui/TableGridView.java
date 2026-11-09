package com.markultra.editor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Interactive rows x columns picker used by the table insertion dialog.
 * Drag to grow the selection; the current size is reported through a listener.
 */
public class TableGridView extends View {

    public interface OnSizePicked {
        void onSizePicked(int rows, int columns);
    }

    private static final int MAX_ROWS = 10;
    private static final int MAX_COLUMNS = 10;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int selectedRows = 3;
    private int selectedColumns = 3;
    private int accent = 0xFF7C4DFF;
    private int gridColor = 0x33888888;
    private float cellSize;
    private OnSizePicked listener;

    public TableGridView(Context context) {
        super(context);
        init();
    }

    public TableGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TableGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        cellPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1.2f));
        setClickable(true);
    }

    public void setColors(int accent, int grid) {
        this.accent = accent;
        this.gridColor = grid;
        invalidate();
    }

    public void setOnSizePicked(OnSizePicked listener) {
        this.listener = listener;
    }

    public void setSelection(int rows, int columns) {
        this.selectedRows = Math.max(1, Math.min(MAX_ROWS, rows));
        this.selectedColumns = Math.max(1, Math.min(MAX_COLUMNS, columns));
        invalidate();
        if (listener != null) listener.onSizePicked(selectedRows, selectedColumns);
    }

    public int selectedRows() {
        return selectedRows;
    }

    public int selectedColumns() {
        return selectedColumns;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) width = dp(280);
        cellSize = (width - dp(2)) / (float) MAX_COLUMNS;
        int height = (int) (cellSize * MAX_ROWS) + dp(2);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(1.5f);
        float radius = Math.max(dp(2), cellSize * 0.18f);
        for (int r = 0; r < MAX_ROWS; r++) {
            for (int c = 0; c < MAX_COLUMNS; c++) {
                float left = c * cellSize + pad;
                float top = r * cellSize + pad;
                rect.set(left, top, left + cellSize - pad * 2, top + cellSize - pad * 2);
                boolean active = r < selectedRows && c < selectedColumns;
                if (active) {
                    fillPaint.setColor(accent);
                    fillPaint.setAlpha(190);
                    canvas.drawRoundRect(rect, radius, radius, fillPaint);
                } else {
                    cellPaint.setColor(gridColor);
                    cellPaint.setAlpha(46);
                    canvas.drawRoundRect(rect, radius, radius, cellPaint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                int c = (int) (event.getX() / cellSize) + 1;
                int r = (int) (event.getY() / cellSize) + 1;
                int nc = Math.max(1, Math.min(MAX_COLUMNS, c));
                int nr = Math.max(1, Math.min(MAX_ROWS, r));
                if (nr != selectedRows || nc != selectedColumns) {
                    selectedRows = nr;
                    selectedColumns = nc;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    if (listener != null) listener.onSizePicked(nr, nc);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
