package com.example.painter;

import android.graphics.Path;
import android.graphics.Typeface;

public class DrawableElement {

    public Typeface typeface;

    public enum Type {
        LINE,
        RECT,
        CIRCLE,
        PATH,
        TEXT,
        DRAWABLE
    }

    public Type type;

    // Координаты для фигур
    public float startX, startY, endX, endY;

    public Path path;

    public int color;
    public float strokeWidth;

    // Для Drawable
    public int drawableResId;
    public float x, y, width, height;

    // Для текста
    public float textSize;
    public boolean bold, italic, strike;
    public String text;
}