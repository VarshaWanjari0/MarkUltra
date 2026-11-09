package com.markultra.editor.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatEditText;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.OverScroller;


/**
 * Plain-text editor surface with an optional line-number gutter, a current-line
 * highlight and a "typewriter" scroll mode that keeps the caret vertically still.
 *
 * <p>Deliberately extends AppCompatEditText (not a code-editor library) so the
 * whole component costs zero extra download bytes.</p>
 */
public class CodeEditor extends AppCompatEditText {

    /** Lets the host take over a paste, for example to convert HTML to Markdown. */
    public interface PasteInterceptor {
        /**
         * @return true when the paste was handled and the default behaviour must not run
         */
        boolean onPaste();
    }

    private PasteInterceptor pasteInterceptor;

    private OverScroller scroller;
    private GestureDetector gestureDetector;


    public void setPasteInterceptor(PasteInterceptor interceptor) {
        this.pasteInterceptor = interceptor;
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste && pasteInterceptor != null && pasteInterceptor.onPaste()) {
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    /** Notified whenever the caret or selection moves. */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int start, int end);
    }

    private OnSelectionChangedListener selectionListener;

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void setSpellCheck(boolean enabled) {
        int type = getInputType();
        if (enabled) {
            type &= ~android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        } else {
            type |= android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        }
        Typeface face = getTypeface();
        setInputType(type);
        // setInputType can reset the typeface on some OEM builds
        if (face != null) setTypeface(face);
        setHorizontallyScrolling(!softWrap);
    }

    public boolean isSoftWrap() {
        return softWrap;
    }

    private final Paint gutterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightRect = new RectF();

    private boolean showLineNumbers;
    private boolean typewriter;
    private boolean softWrap = true;
    private boolean showCurrentLine = true;

    private int gutterTextColor = 0xFF5E6673;
    private int gutterBackground = 0x00000000;
    private int highlightColor = 0x12FFFFFF;
    private int measuredGutterWidth;
    private int basePaddingLeft;
    private int basePaddingTop;
    private int basePaddingRight;
    private int basePaddingBottom;

    private int cachedTextLength = -1;
    private int cachedLayoutLines = -1;
    private int[] visualNumbers = new int[0];
    /** Line starts, maintained as the text changes; see {@link LineMap}. */
    private final LineMap lines = new LineMap();

    public CodeEditor(Context context) {
        super(context);
        init();
    }

    public CodeEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CodeEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setGravity(Gravity.TOP | Gravity.START);
        setBackground(null);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        setLineSpacing(0f, 1.15f);
        setIncludeFontPadding(false);
        setPadding(dp(14), dp(12), dp(14), dp(28));
        gutterPaint.setTypeface(Typeface.MONOSPACE);
        gutterPaint.setTextAlign(Paint.Align.RIGHT);
        highlightPaint.setStyle(Paint.Style.FILL);
        scroller = new OverScroller(getContext());
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                int maxX = softWrap ? 0 : getMaxScrollX();
                int maxY = getMaxScrollY();
                if (maxY <= 0 && maxX <= 0) return false;

                int startX = Math.max(0, Math.min(maxX, getScrollX()));
                int startY = Math.max(0, Math.min(maxY, getScrollY()));
                int velX = softWrap ? 0 : (int) -velocityX;
                int velY = (int) -velocityY;

                scroller.fling(startX, startY, velX, velY, 0, maxX, 0, maxY);
                postInvalidateOnAnimation();
                return true;
            }
        });

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                lines.beforeChange(s, start, count);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lines.afterChange(s, start, before, count);
                cachedTextLength = -1;
            }

            @Override
            public void afterTextChanged(Editable s) {
                invalidate();
            }
        });
    }

    // ---------------------------------------------------------------------------------
    // configuration
    // ---------------------------------------------------------------------------------

    public void setEditorColors(int textColor, int hintColor, int textHighlight,
                                int gutterText, int gutterBg, int currentLine) {
        setTextColor(textColor);
        setHintTextColor(hintColor);
        setHighlightColor(textHighlight);
        this.gutterTextColor = gutterText;
        this.gutterBackground = gutterBg;
        this.highlightColor = currentLine;
        gutterPaint.setColor(gutterText);
        highlightPaint.setColor(currentLine);
        invalidate();
    }

    public void setEditorTypeface(Typeface typeface) {
        setTypeface(typeface);
        invalidate();
    }

    public void setLineNumbers(boolean enabled) {
        if (this.showLineNumbers == enabled) return;
        this.showLineNumbers = enabled;
        updatePadding();
        invalidate();
    }

    public boolean hasLineNumbers() {
        return showLineNumbers;
    }

    public void setTypewriter(boolean enabled) {
        this.typewriter = enabled;
        if (enabled) centerCaret();
    }

    public void setShowCurrentLine(boolean show) {
        this.showCurrentLine = show;
        invalidate();
    }

    public void setSoftWrapEnabled(boolean enabled) {
        this.softWrap = enabled;
        setHorizontallyScrolling(!enabled);
        if (enabled) {
            setMaxLines(Integer.MAX_VALUE);
            setMinLines(3);
        } else {
            setMaxLines(Integer.MAX_VALUE);
        }
        requestLayout();
    }

    private void updatePadding() {
        if (basePaddingLeft == 0) {
            basePaddingLeft = getPaddingLeft();
            basePaddingTop = getPaddingTop();
            basePaddingRight = getPaddingRight();
            basePaddingBottom = getPaddingBottom();
        }
        int left = basePaddingLeft + (showLineNumbers ? gutterWidth() + dp(8) : 0);
        setPadding(left, basePaddingTop, basePaddingRight, basePaddingBottom);
    }

    private int gutterWidth() {
        if (measuredGutterWidth == 0) {
            gutterPaint.setTextSize(sp(11.5f));
            measuredGutterWidth = (int) Math.ceil(gutterPaint.measureText("00000")) + dp(10);
        }
        return measuredGutterWidth;
    }

    // ---------------------------------------------------------------------------------
    // caret / scrolling
    // ---------------------------------------------------------------------------------

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selectionListener != null) selectionListener.onSelectionChanged(selStart, selEnd);
        if (typewriter) centerCaret();
        if (showCurrentLine) invalidate();
    }

    private void centerCaret() {
        post(new Runnable() {
            @Override
            public void run() {
                Layout layout = getLayout();
                if (layout == null) return;
                int caret = getSelectionStart();
                if (caret < 0) return;
                int line = layout.getLineForOffset(caret);
                int top = layout.getLineTop(line);
                int bottom = layout.getLineBottom(line);
                int target = (top + bottom) / 2 - getHeight() / 2 + getTotalPaddingTop();
                int max = Math.max(0, layout.getHeight() - (getHeight() - getTotalPaddingTop()
                        - getTotalPaddingBottom()));
                scrollTo(0, Math.max(0, Math.min(max, target)));
            }
        });
    }

    public void centerCaretNow() {
        centerCaret();
    }

    // ---------------------------------------------------------------------------------
    // drawing
    // ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null && showCurrentLine && highlightColor != 0) {
            int caret = getSelectionStart();
            if (caret >= 0) {
                int line = layout.getLineForOffset(caret);
                highlightRect.set(getTotalPaddingLeft() - dp(6), layout.getLineTop(line),
                        getWidth() - getTotalPaddingRight() + dp(6), layout.getLineBottom(line));
                canvas.drawRoundRect(highlightRect, dp(4), dp(4), highlightPaint);
            }
        }
        super.onDraw(canvas);
        if (showLineNumbers && layout != null) {
            drawGutter(canvas, layout);
        }
    }

    private void drawGutter(Canvas canvas, Layout layout) {
        int width = gutterWidth();
        if (gutterBackground != 0) {
            gutterPaint.setColor(gutterBackground);
            canvas.drawRect(0, 0, width + dp(4), getHeight(), gutterPaint);
            gutterPaint.setColor(gutterTextColor);
        }
        ensureNumbers(layout);
        gutterPaint.setTextSize(sp(11.5f));
        int first = Math.max(0, layout.getLineForVertical(getScrollY()));
        int last = Math.min(layout.getLineCount() - 1,
                layout.getLineForVertical(getScrollY() + getHeight()) + 1);
        float baselineOffset = gutterPaint.getTextSize() * 0.36f;
        for (int line = first; line <= last && line < visualNumbers.length; line++) {
            int number = visualNumbers[line];
            if (number <= 0) continue;
            float baseline = layout.getLineBaseline(line) + baselineOffset;
            canvas.drawText(String.valueOf(number), width, baseline, gutterPaint);
        }
        gutterPaint.setColor(gutterTextColor);
    }

    private void ensureNumbers(Layout layout) {
        int layoutLines = layout.getLineCount();
        if (cachedTextLength == getText().length() && cachedLayoutLines == layoutLines) return;
        cachedTextLength = getText().length();
        cachedLayoutLines = layoutLines;
        if (visualNumbers.length < layoutLines) visualNumbers = new int[layoutLines + 8];
        CharSequence text = getText();
        // The line index already knows where every line starts, so a visual line is
        // either the first visual line of its logical line (the character before it is
        // a newline or the document start) or a soft-wrapped continuation.
        for (int i = 0; i < layoutLines; i++) {
            int offset = layout.getLineStart(i);
            boolean continuation = offset > 0 && offset <= text.length()
                    && text.charAt(offset - 1) != '\n';
            visualNumbers[i] = continuation ? 0 : lines.lineNumberAt(text, offset);
        }
    }

    /** Line starts maintained across edits, for the status bar and the gutter. */
    public LineMap lineMap() {
        return lines;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    public int getMaxScrollY() {
        Layout layout = getLayout();
        if (layout == null) return 0;
        int visibleHeight = getHeight() - getTotalPaddingTop() - getTotalPaddingBottom();
        return Math.max(0, layout.getHeight() - visibleHeight);
    }

    public int getMaxScrollX() {
        int range = computeHorizontalScrollRange();
        int visibleWidth = getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
        return Math.max(0, range - visibleWidth);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (scroller != null && !scroller.isFinished()) {
                scroller.abortAnimation();
            }
        }
        boolean gestureHandled = false;
        if (gestureDetector != null) {
            gestureHandled = gestureDetector.onTouchEvent(event);
        }
        boolean superHandled = super.onTouchEvent(event);
        return gestureHandled || superHandled;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller != null && scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

}
