package com.example.painter;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class DrawingView extends View {

    public enum Mode {
        DRAW,
        LINE,
        RECT,
        CIRCLE,
        TEXT,
        ERASER,
        DRAWABLE}

    private Bitmap backgroundBitmap;
    private Mode currentMode = Mode.DRAW;
    private Paint paint;
    private Typeface currentTypeface = Typeface.DEFAULT;
    private boolean bold = false;
    private boolean italic = false;
    private boolean strike = false;

    private List<DrawableElement> elements = new ArrayList<>();
    private Stack<CanvasState> undoStack = new Stack<>();
    private Stack<CanvasState> redoStack = new Stack<>();

    private DrawableElement currentPathElement;
    private DrawableElement previewElement = null;
    private float currentScale = 1.0f;
    private ScaleGestureDetector scaleDetector;

    private String pendingText = null;
    private DrawableElement pendingDrawable = null;

    private DrawableElement selectedElement = null;
    private float lastTouchX, lastTouchY;

    private Callback callback;

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.BLACK);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setMode(Mode mode) {
        currentMode = mode;
        currentPathElement = null;
        previewElement = null;
    }

    public void setStrokeWidth(float width) {
        paint.setStrokeWidth(width);
    }

    public float getStrokeWidth() {
        return paint.getStrokeWidth();
    }

    public void setCurrentColor(int color) {
        paint.setColor(color);
    }

    public int getCurrentColor() {
        return paint.getColor();
    }

    public void setTypeface(Typeface typeface) {
        currentTypeface = typeface;
    }

    public void setBold(boolean isBold) {
        this.bold = isBold;
        updateTypeface();
    }

    public void setItalic(boolean isItalic) {
        this.italic = isItalic;
        updateTypeface();
    }

    public void setStrike(boolean isStrike) {
        this.strike = isStrike;
    }

    public void setTextSize(float textSize) {
        paint.setTextSize(textSize);
    }

    public float getTextSize() {
        return paint.getTextSize();
    }

    private void updateTypeface() {
        int style = Typeface.NORMAL;
        if (bold && italic) style = Typeface.BOLD_ITALIC;
        else if (bold) style = Typeface.BOLD;
        else if (italic) style = Typeface.ITALIC;
        this.currentTypeface = Typeface.create(Typeface.DEFAULT, style);
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        backgroundBitmap = bitmap;
        saveState(); // Добавляем сохранение состояния
        invalidate();
    }


    public Bitmap mergeWithBackground() {
        if (backgroundBitmap == null) return null;
        Bitmap mergedBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mergedBitmap);
        canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        for (DrawableElement element : elements) {
            drawElement(canvas, element);
        }
        return mergedBitmap;
    }

    public void prepareTextPlacement(String text) {
        pendingText = text;
    }

    public void prepareDrawablePlacement(@DrawableRes int drawableResId, float width, float height) {
        DrawableElement element = new DrawableElement();
        element.type = DrawableElement.Type.DRAWABLE;
        element.drawableResId = drawableResId;
        element.width = width;
        element.height = height;
        this.pendingDrawable = element;
    }

    public void addText(String text, float x, float y, float textSize) {
        DrawableElement textElement = new DrawableElement();
        textElement.type = DrawableElement.Type.TEXT;
        textElement.text = text;
        textElement.x = x;
        textElement.y = y;
        textElement.textSize = textSize;
        textElement.color = paint.getColor();
        textElement.bold = bold;
        textElement.italic = italic;
        textElement.strike = strike;
        textElement.typeface = currentTypeface;
        elements.add(textElement);
        saveState();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(currentScale, currentScale);
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, 0, 0, null);
        }
        for (DrawableElement element : elements) {
            drawElement(canvas, element);
        }
        if (currentPathElement != null) {
            drawElement(canvas, currentPathElement);
        }
        if (previewElement != null) {
            drawElement(canvas, previewElement);
        }
        canvas.restore();
    }

    private void drawElement(Canvas canvas, DrawableElement element) {
        paint.setColor(element.color);
        paint.setStrokeWidth(element.strokeWidth);
        paint.setStyle(Paint.Style.STROKE);

        switch (element.type) {
            case PATH:
                canvas.drawPath(element.path, paint);
                break;
            case LINE:
                canvas.drawLine(element.startX, element.startY, element.endX, element.endY, paint);
                break;
            case RECT:
                canvas.drawRect(element.startX, element.startY, element.endX, element.endY, paint);
                break;
            case CIRCLE:
                float radius = (float) Math.sqrt(Math.pow(element.endX - element.startX, 2) + Math.pow(element.endY - element.startY, 2));
                canvas.drawCircle(element.startX, element.startY, radius, paint);
                break;
            case TEXT:
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(element.textSize);
                int style = Typeface.NORMAL;
                if (element.bold && element.italic) style = Typeface.BOLD_ITALIC;
                else if (element.bold) style = Typeface.BOLD;
                else if (element.italic) style = Typeface.ITALIC;
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, style));
                canvas.drawText(element.text, element.x, element.y, paint);
                break;
            case DRAWABLE:
                Drawable drawable = ContextCompat.getDrawable(getContext(), element.drawableResId);
                if (drawable != null) {
                    drawable.setBounds((int) element.x, (int) element.y, (int) (element.x + element.width), (int) (element.y + element.height));
                    drawable.draw(canvas);
                }
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        float x = event.getX() / currentScale;
        float y = event.getY() / currentScale;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleActionDown(x, y);
            case MotionEvent.ACTION_MOVE:
                return handleActionMove(x, y);
            case MotionEvent.ACTION_UP:
                return handleActionUp(x, y);
        }

        return false;
    }

    private boolean handleActionDown(float x, float y) {
        if (currentMode == Mode.ERASER) {
            DrawableElement elementToRemove = findElementAt(x, y);
            if (elementToRemove != null) {
                elements.remove(elementToRemove);
                saveState();
                invalidate();
                return true;
            }
            return false;
        }

        // Check if we are placing text or drawable
        if (pendingText != null) {
            addText(pendingText, x, y, 48f);
            pendingText = null;
            return true;
        } else if (pendingDrawable != null) {
            pendingDrawable.x = x - pendingDrawable.width / 2;
            pendingDrawable.y = y - pendingDrawable.height / 2;
            elements.add(pendingDrawable);
            saveState();
            pendingDrawable = null;
            setMode(Mode.DRAW); // Return to draw mode after placing
            invalidate();
            return true;
        }

        /*
        // Check if we are starting a new drawing
        if (currentMode == Mode.DRAW) {
            return startDrawing(x, y);
        }*/

        // Check if we are starting a new shape
        if (currentMode == Mode.LINE || currentMode == Mode.RECT || currentMode == Mode.CIRCLE) {
            return startShape(x, y);
        }

        // Check if we are selecting an element for moving
        selectedElement = findElementAt(x, y);
        if (selectedElement != null) {
            lastTouchX = x;
            lastTouchY = y;
            return true;
        }

        return false;
    }

    private boolean handleActionMove(float x, float y) {
        if (currentPathElement != null) {
            continueDrawing(x, y);
            return true;
        }

        if (previewElement != null) {
            updateShape(x, y);
            return true;
        }

        if (selectedElement != null) {
            moveElement(selectedElement, x - lastTouchX, y - lastTouchY);
            lastTouchX = x;
            lastTouchY = y;
            invalidate();
            return true;
        }

        return false;
    }

    private boolean handleActionUp(float x, float y) {
        if (currentPathElement != null) {
            endDrawing(x, y);
            return true;
        }

        if (previewElement != null) {
            endShape(x, y);
            return true;
        }

        if (selectedElement != null) {
            endMove();
            return true;
        }

        return false;
    }

    private boolean startDrawing(float x, float y) {
        currentPathElement = new DrawableElement();
        currentPathElement.type = DrawableElement.Type.PATH;
        currentPathElement.path = new Path();
        currentPathElement.color = paint.getColor();
        currentPathElement.strokeWidth = paint.getStrokeWidth();
        currentPathElement.path.moveTo(x, y);
        invalidate();
        return true;
    }

    private void continueDrawing(float x, float y) {
        currentPathElement.path.lineTo(x, y);
        invalidate();
    }

    private void endDrawing(float x, float y) {
        currentPathElement.path.lineTo(x, y);
        elements.add(currentPathElement);
        saveState();
        currentPathElement = null;
        invalidate();
    }

    private boolean startShape(float x, float y) {
        previewElement = new DrawableElement();
        previewElement.type = modeToElementType(currentMode);
        previewElement.startX = x;
        previewElement.startY = y;
        previewElement.endX = x;
        previewElement.endY = y;
        previewElement.color = paint.getColor();
        previewElement.strokeWidth = paint.getStrokeWidth();
        invalidate();
        return true;
    }

    private void updateShape(float x, float y) {
        previewElement.endX = x;
        previewElement.endY = y;
        invalidate();
    }

    private void endShape(float x, float y) {
        previewElement.endX = x;
        previewElement.endY = y;
        elements.add(previewElement);
        saveState();
        previewElement = null;
        setMode(Mode.DRAW);
        invalidate();
    }

    private void endMove() {
        saveState();
        selectedElement = null;
        invalidate();
    }


    private DrawableElement.Type modeToElementType(Mode mode) {
        switch (mode) {
            case LINE:
                return DrawableElement.Type.LINE;
            case RECT:
                return DrawableElement.Type.RECT;
            case CIRCLE:
                return DrawableElement.Type.CIRCLE;
            default:
                return null;
        }
    }

    private void saveState() {
        Bitmap backgroundCopy = null;
        if (backgroundBitmap != null) {
            backgroundCopy = backgroundBitmap.copy(backgroundBitmap.getConfig(), true);
        }
        undoStack.push(new CanvasState(backgroundCopy, elements));
        redoStack.clear();
        if (callback != null) {
            callback.onDrawingViewChange();
        }
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            CanvasState currentState = new CanvasState(backgroundBitmap, elements);
            redoStack.push(currentState);

            CanvasState previousState = undoStack.pop();
            this.backgroundBitmap = previousState.bitmap;
            this.elements = new ArrayList<>(previousState.elements);
            invalidate();

            if (callback != null) {
                callback.onDrawingViewChange();
            }
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            CanvasState currentState = new CanvasState(backgroundBitmap, elements);
            undoStack.push(currentState);

            CanvasState nextState = redoStack.pop();
            this.backgroundBitmap = nextState.bitmap;
            this.elements = new ArrayList<>(nextState.elements);
            invalidate();

            if (callback != null) {
                callback.onDrawingViewChange();
            }
        }
    }

    public List<DrawableElement> getElements() {
        return new ArrayList<>(elements);
    }

    public void setElements(List<DrawableElement> elements) {
        this.elements = new ArrayList<>(elements);
        invalidate();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            currentScale *= detector.getScaleFactor();
            currentScale = Math.max(0.1f, Math.min(currentScale, 5.0f));
            invalidate();
            return true;
        }
    }

    private DrawableElement findElementAt(float x, float y) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            DrawableElement element = elements.get(i);
            switch (element.type) {
                case CIRCLE:
                    float radius = (float) Math.sqrt(Math.pow(element.endX - element.startX, 2) + Math.pow(element.endY - element.startY, 2));
                    float dx = x - element.startX;
                    float dy = y - element.startY;
                    if (Math.sqrt(dx * dx + dy * dy) <= radius + 20) return element;
                    break;
                case RECT:
                    if (x >= Math.min(element.startX, element.endX) - 20 && x <= Math.max(element.startX, element.endX) + 20 &&
                            y >= Math.min(element.startY, element.endY) - 20 && y <= Math.max(element.startY, element.endY) + 20) return element;
                    break;
                case LINE:
                    float dist = distanceFromLine(element.startX, element.startY, element.endX, element.endY, x, y);
                    if (dist < 20) return element;
                    break;
                case TEXT:
                    Rect bounds = new Rect();
                    paint.setTextSize(element.textSize);
                    paint.getTextBounds(element.text, 0, element.text.length(), bounds);
                    if (x >= element.x && x <= element.x + bounds.width() &&
                            y >= element.y - bounds.height() && y <= element.y) return element;
                    break;
                case DRAWABLE:
                    if (x >= element.x && x <= element.x + element.width &&
                            y >= element.y && y <= element.y + element.height) return element;
                    break;
            }
        }
        return null;
    }

    private float distanceFromLine(float x1, float y1, float x2, float y2, float px, float py) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) return (float) Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        float t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        float projX = x1 + t * dx;
        float projY = y1 + t * dy;
        return (float) Math.sqrt((projX - px) * (projX - px) + (projY - py) * (projY - py));
    }

    private void moveElement(DrawableElement el, float dx, float dy) {
        switch (el.type) {
            case LINE:
            case RECT:
            case CIRCLE:
                el.startX += dx;
                el.startY += dy;
                el.endX += dx;
                el.endY += dy;
                break;
            case TEXT:
            case DRAWABLE:
                el.x += dx;
                el.y += dy;
                break;
        }
    }

    private boolean isPointNearElement(float x, float y, DrawableElement element) {
        switch (element.type) {
            case LINE:
                // Check if the point is near the line
                float distance = distanceToLine(x, y, element.startX, element.startY, element.endX, element.endY);
                return distance < 20; // Proximity threshold
            case RECT:
                // Check if the point is inside the rectangle
                return x >= element.startX && x <= element.endX && y >= element.startY && y <= element.endY;
            case CIRCLE:
                // Check if the point is inside the circle
                float distanceToCenter = (float) Math.sqrt(Math.pow(x - element.startX, 2) + Math.pow(y - element.startY, 2));
                float radius = (float) Math.sqrt(Math.pow(element.endX - element.startX, 2) + Math.pow(element.endY - element.startY, 2));
                return distanceToCenter <= radius;
            case TEXT:
            case DRAWABLE:
                // Check if the point is within the bounds of the text or drawable
                return x >= element.x && x <= element.x + element.width && y >= element.y && y <= element.y + element.height;
            case PATH:
                return false;
            default:
                return false;
        }
    }

    private float distanceToLine(float x, float y, float x1, float y1, float x2, float y2) {
        float A = x - x1;
        float B = y - y1;
        float C = x2 - x1;
        float D = y2 - y1;

        float dot = A * C + B * D;
        float len_sq = C * C + D * D;
        float param = -1;
        if (len_sq != 0) //in case of 0 length line
            param = dot / len_sq;

        float xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        return (float) Math.sqrt(Math.pow(x - xx, 2) + Math.pow(y - yy, 2));
    }

    public interface Callback {
        void onDrawingViewChange();
    }
}