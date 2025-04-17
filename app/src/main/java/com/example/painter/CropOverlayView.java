package com.example.painter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropOverlayView extends View {
    private RectF cropRect;
    private Paint borderPaint, handlePaint;
    private static final float HANDLE_RADIUS = 30f;
    private static final float MIN_CROP_SIZE = 100f;

    private enum TouchArea {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private TouchArea currentTouchArea = TouchArea.NONE;
    private float lastTouchX, lastTouchY;

    public CropOverlayView(Context context) {
        super(context);
        init();
    }

    public CropOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);

        handlePaint = new Paint();
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);

        cropRect = new RectF(200, 400, 800, 1000); // начальный произвольный rect
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(cropRect, borderPaint);
        drawHandles(canvas);
    }

    private void drawHandles(Canvas canvas) {
        canvas.drawCircle(cropRect.left, cropRect.top, HANDLE_RADIUS, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, HANDLE_RADIUS, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, HANDLE_RADIUS, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, HANDLE_RADIUS, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentTouchArea = getTouchArea(x, y);
                lastTouchX = x;
                lastTouchY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;

                switch (currentTouchArea) {
                    case MOVE:
                        cropRect.offset(dx, dy);
                        break;
                    case TOP_LEFT:
                        cropRect.left = Math.min(x, cropRect.right - MIN_CROP_SIZE);
                        cropRect.top = Math.min(y, cropRect.bottom - MIN_CROP_SIZE);
                        break;
                    case TOP_RIGHT:
                        cropRect.right = Math.max(x, cropRect.left + MIN_CROP_SIZE);
                        cropRect.top = Math.min(y, cropRect.bottom - MIN_CROP_SIZE);
                        break;
                    case BOTTOM_LEFT:
                        cropRect.left = Math.min(x, cropRect.right - MIN_CROP_SIZE);
                        cropRect.bottom = Math.max(y, cropRect.top + MIN_CROP_SIZE);
                        break;
                    case BOTTOM_RIGHT:
                        cropRect.right = Math.max(x, cropRect.left + MIN_CROP_SIZE);
                        cropRect.bottom = Math.max(y, cropRect.top + MIN_CROP_SIZE);
                        break;
                }

                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                currentTouchArea = TouchArea.NONE;
                return true;
        }

        return super.onTouchEvent(event);
    }

    private TouchArea getTouchArea(float x, float y) {
        if (isInHandle(x, y, cropRect.left, cropRect.top)) return TouchArea.TOP_LEFT;
        if (isInHandle(x, y, cropRect.right, cropRect.top)) return TouchArea.TOP_RIGHT;
        if (isInHandle(x, y, cropRect.left, cropRect.bottom)) return TouchArea.BOTTOM_LEFT;
        if (isInHandle(x, y, cropRect.right, cropRect.bottom)) return TouchArea.BOTTOM_RIGHT;
        if (cropRect.contains(x, y)) return TouchArea.MOVE;
        return TouchArea.NONE;
    }

    private boolean isInHandle(float x, float y, float cx, float cy) {
        return Math.hypot(x - cx, y - cy) <= HANDLE_RADIUS;
    }

    public RectF getCropRect() {
        return new RectF(cropRect); // всегда возвращай копию!
    }
}