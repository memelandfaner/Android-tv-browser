package com.example.tvbrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

public class CursorOverlay extends View {
    private float mCursorX = 960f;
    private float mCursorY = 540f;
    private final Paint mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnEdgeScrollListener {
        void onEdgeScroll(int scrollY);
    }

    private OnEdgeScrollListener mScrollListener;

    public CursorOverlay(Context context) {
        super(context);
        init();
    }

    public CursorOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CursorOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mCenterPaint.setColor(Color.WHITE);
        mCenterPaint.setStyle(Paint.Style.FILL);

        mRingPaint.setColor(Color.parseColor("#00d2ff"));
        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStrokeWidth(4f);

        mGlowPaint.setColor(Color.parseColor("#ffd700"));
        mGlowPaint.setStyle(Paint.Style.STROKE);
        mGlowPaint.setStrokeWidth(2f);

        setFocusable(false);
        setClickable(false);
    }

    public void setOnEdgeScrollListener(OnEdgeScrollListener listener) {
        mScrollListener = listener;
    }

    public float getCursorX() {
        return mCursorX;
    }

    public float getCursorY() {
        return mCursorY;
    }

    public void moveCursor(float dx, float dy) {
        int w = getWidth() > 0 ? getWidth() : 1920;
        int h = getHeight() > 0 ? getHeight() : 1080;

        mCursorX = Math.max(10f, Math.min(w - 10f, mCursorX + dx));
        mCursorY = Math.max(10f, Math.min(h - 10f, mCursorY + dy));

        // Edge scroll detection
        if (mScrollListener != null) {
            if (mCursorY < 100) {
                mScrollListener.onEdgeScroll(-40);
            } else if (mCursorY > h - 100) {
                mScrollListener.onEdgeScroll(40);
            }
        }

        invalidate();
    }

    public void clickAtCursor(WebView webView) {
        if (webView == null) return;

        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, mCursorX, mCursorY, 0);
        webView.dispatchTouchEvent(down);
        down.recycle();

        final WebView targetView = webView;
        postDelayed(new Runnable() {
            @Override
            public void run() {
                long eventTime = SystemClock.uptimeMillis();
                MotionEvent up = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, mCursorX, mCursorY, 0);
                targetView.dispatchTouchEvent(up);
                up.recycle();
            }
        }, 80);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 && oldh == 0) {
            mCursorX = w / 2f;
            mCursorY = h / 2f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Outer glow halo
        canvas.drawCircle(mCursorX, mCursorY, 22f, mGlowPaint);
        // Cyan precision focus ring
        canvas.drawCircle(mCursorX, mCursorY, 15f, mRingPaint);
        // Center pointer point
        canvas.drawCircle(mCursorX, mCursorY, 6f, mCenterPaint);
    }
}
