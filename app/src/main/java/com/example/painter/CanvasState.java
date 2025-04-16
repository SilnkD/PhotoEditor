package com.example.painter;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

public class CanvasState {
    Bitmap bitmap;
    List<DrawableElement> elements;

    public CanvasState(Bitmap bitmap, List<DrawableElement> elements) {
        // Копируем битмап, чтобы предотвратить изменения
        this.bitmap = bitmap.copy(bitmap.getConfig(), true);
        this.elements = new ArrayList<>(elements); // Копируем список
    }
}