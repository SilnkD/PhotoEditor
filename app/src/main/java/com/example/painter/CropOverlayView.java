package com.example.painter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropOverlayView extends View {

    private final Paint borderPaint = new Paint();
    private final RectF cropRect = new RectF(200, 200, 800, 800);
    private float lastX, lastY;

    public CropOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        borderPaint.setColor(0xAAFFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);
        setVisibility(GONE);
    }

    public RectF getCropRect() {
        return cropRect;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(cropRect, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                cropRect.offset(dx, dy);
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
        }
        return false;
    }
}