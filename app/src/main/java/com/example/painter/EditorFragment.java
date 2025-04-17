package com.example.painter;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.github.dhaval2404.colorpicker.ColorPickerDialog;
import com.github.dhaval2404.colorpicker.listener.ColorListener;
import com.github.dhaval2404.colorpicker.model.ColorShape;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class EditorFragment extends Fragment {

    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> captureImageLauncher;

    private ImageView imageView;
    private DrawingView drawingView;
    private CropOverlayView cropOverlayView;
    private Bitmap currentBitmap;

    private LinearLayout drawingToolbar;
    private ImageButton buttonDrawMode, buttonCamera, buttonGallery, buttonCrop,
            buttonSave, buttonDrawLine, buttonDrawSquare, buttonDrawCircle,
            buttonAddText, buttonDrawDrawable, buttonColorPicker, buttonEraser,
            buttonUndo, buttonRedo, buttonRotate, buttonMirror, buttonApplyCrop;
    private SeekBar strokeWidthSeekBar;

    private Stack<CanvasState> undoStack = new Stack<>();
    private Stack<CanvasState> redoStack = new Stack<>();
    private boolean stateChanged = false;
    private Uri photoUri;

    private File createImageFile() throws IOException {
        String fileName = "JPEG_" + System.currentTimeMillis();
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(fileName, ".jpg", storageDir);
        return image;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_editor, container, false);

        imageView = view.findViewById(R.id.image_view);
        drawingView = view.findViewById(R.id.drawing_view);
        cropOverlayView = view.findViewById(R.id.crop_overlay);
        drawingToolbar = view.findViewById(R.id.drawing_toolbar);
        buttonApplyCrop = view.findViewById(R.id.button_confirm_crop);
        strokeWidthSeekBar = view.findViewById(R.id.stroke_width_seekbar);

        initButtons(view);
        setupButtonListeners();
        setupSeekBar();

        drawingView.setCallback(this::onDrawingViewChange); // Set the callback
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            currentBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
                            updateImageViews();
                            updateCanvasState();
                        } catch (IOException e) {
                            e.printStackTrace();
                            showToast("Ошибка при загрузке изображения");
                        }
                    }
                }
        );

        captureImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        try {
                            currentBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), photoUri);
                            updateImageViews();
                            updateCanvasState();
                        } catch (IOException e) {
                            e.printStackTrace();
                            showToast("Ошибка при загрузке изображения");
                        }
                    }
                }
        );


        return view;
    }

    // Callback method implementation
    private void onDrawingViewChange() {
        stateChanged = true;
    }

    private void initButtons(View view) {
        buttonDrawMode = view.findViewById(R.id.button_draw_mode);
        buttonCamera = view.findViewById(R.id.button_camera);
        buttonGallery = view.findViewById(R.id.button_gallery);
        buttonCrop = view.findViewById(R.id.button_crop);
        buttonSave = view.findViewById(R.id.button_save);
        buttonDrawLine = view.findViewById(R.id.button_draw_line);
        buttonDrawSquare = view.findViewById(R.id.button_draw_square);
        buttonDrawCircle = view.findViewById(R.id.button_draw_circle);
        buttonAddText = view.findViewById(R.id.button_add_text);
        buttonDrawDrawable = view.findViewById(R.id.button_draw_drawable);
        buttonColorPicker = view.findViewById(R.id.button_color_picker);
        buttonEraser = view.findViewById(R.id.button_eraser);
        buttonUndo = view.findViewById(R.id.button_undo);
        buttonRedo = view.findViewById(R.id.button_redo);
        buttonRotate = view.findViewById(R.id.button_rotate);
        buttonMirror = view.findViewById(R.id.button_mirror);
    }

    private void setupButtonListeners() {
        buttonCamera.setOnClickListener(v -> dispatchTakePictureIntent());
        buttonGallery.setOnClickListener(v -> pickImageFromGallery());

        buttonDrawMode.setOnClickListener(v -> {
            if (buttonDrawLine.getVisibility() == View.GONE) {
                buttonDrawLine.setVisibility(View.VISIBLE);
                buttonDrawSquare.setVisibility(View.VISIBLE);
                buttonDrawCircle.setVisibility(View.VISIBLE);
            } else {
                buttonDrawLine.setVisibility(View.GONE);
                buttonDrawSquare.setVisibility(View.GONE);
                buttonDrawCircle.setVisibility(View.GONE);
            }
        });
        buttonDrawLine.setOnClickListener(v -> setDrawingMode(DrawingView.Mode.LINE));
        buttonDrawSquare.setOnClickListener(v -> setDrawingMode(DrawingView.Mode.RECT));
        buttonDrawCircle.setOnClickListener(v -> setDrawingMode(DrawingView.Mode.CIRCLE));
        buttonEraser.setOnClickListener(v -> setDrawingMode(DrawingView.Mode.ERASER));

        buttonAddText.setOnClickListener(v -> showAddTextDialog());
        buttonDrawDrawable.setOnClickListener(v -> {
            drawingView.prepareDrawablePlacement(R.drawable.ic_star, 200, 200);
            showToast("Теперь нажмите на холст, чтобы разместить изображение");
            setDrawingMode(DrawingView.Mode.DRAWABLE);
        });

        buttonUndo.setOnClickListener(v -> drawingView.undo());
        buttonRedo.setOnClickListener(v -> drawingView.redo());
        buttonColorPicker.setOnClickListener(v -> showColorPickerDialog());
        buttonSave.setOnClickListener(v -> saveImageToGallery());
        buttonRotate.setOnClickListener(v -> rotateImage());
        buttonMirror.setOnClickListener(v -> mirrorImage());

        buttonCrop.setOnClickListener(v -> toggleCropMode(true));
        buttonApplyCrop.setOnClickListener(v -> applyCropAndExit());
    }

    private void setDrawingMode(DrawingView.Mode mode) {
        drawingView.setMode(mode);
    }

    private void setupSeekBar() {
        strokeWidthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                drawingView.setStrokeWidth(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateCanvasState(); // Save state after changing stroke width
            }
        });
    }

    private void updateCanvasState() {
        stateChanged = true;
    }

    private void toggleCropMode(boolean enable) {
        int visibility = enable ? View.VISIBLE : View.GONE;
        cropOverlayView.setVisibility(visibility);
        buttonApplyCrop.setVisibility(visibility);
        drawingToolbar.setVisibility(enable ? View.GONE : View.VISIBLE);
        toggleMainUIElements(!enable);
    }

    private void toggleMainUIElements(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        buttonCamera.setVisibility(visibility);
        buttonGallery.setVisibility(visibility);
        buttonCrop.setVisibility(visibility);
        buttonRotate.setVisibility(visibility);
        buttonMirror.setVisibility(visibility);
        buttonSave.setVisibility(visibility);
        buttonUndo.setVisibility(visibility);
        buttonRedo.setVisibility(visibility);
    }

    private void applyCropAndExit() {
        applyCrop();
        toggleCropMode(false);
    }

    private void applyCrop() {
        if (currentBitmap == null) return;

        RectF cropRect = cropOverlayView.getCropRect();

        // Получаем матрицу трансформации из imageView
        Matrix imageMatrix = imageView.getImageMatrix();
        float[] matrixValues = new float[9];
        imageMatrix.getValues(matrixValues);

        float scaleX = matrixValues[Matrix.MSCALE_X];
        float scaleY = matrixValues[Matrix.MSCALE_Y];
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];

        // Вычисляем координаты обрезки в битмапе
        float left = (cropRect.left - transX) / scaleX;
        float top = (cropRect.top - transY) / scaleY;
        float right = (cropRect.right - transX) / scaleX;
        float bottom = (cropRect.bottom - transY) / scaleY;

        int cropLeft = Math.max(0, Math.min(currentBitmap.getWidth(), (int) left));
        int cropTop = Math.max(0, Math.min(currentBitmap.getHeight(), (int) top));
        int cropRight = Math.max(0, Math.min(currentBitmap.getWidth(), (int) right));
        int cropBottom = Math.max(0, Math.min(currentBitmap.getHeight(), (int) bottom));

        int cropWidth = cropRight - cropLeft;
        int cropHeight = cropBottom - cropTop;

        if (cropWidth <= 0 || cropHeight <= 0) {
            showToast("Неверная область обрезки");
            return;
        }

        Bitmap croppedBitmap = Bitmap.createBitmap(currentBitmap, cropLeft, cropTop, cropWidth, cropHeight);

        currentBitmap = croppedBitmap;
        updateImageViews();
        drawingView.clear();
        updateCanvasState();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageLauncher.launch(intent);
    }

    private void dispatchTakePictureIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            try {
                File photoFile = createImageFile();
                photoUri = FileProvider.getUriForFile(requireContext(), "com.example.painter.fileprovider", photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                captureImageLauncher.launch(intent);
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Ошибка создания файла изображения");
            }
        }
    }


    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        openGallery();
    }

    private void showColorPickerDialog() {
        new ColorPickerDialog.Builder(requireContext())
                .setTitle("Выберите цвет")
                .setColorShape(ColorShape.SQAURE)
                .setDefaultColor(drawingView.getCurrentColor())
                .setColorListener((ColorListener) (color, fromUser) -> drawingView.setCurrentColor(color))
                .show();
    }

    private void showAddTextDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Добавить текст");

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText input = new EditText(requireContext());
        input.setHint("Введите текст");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(input);

        List<File> fontFiles = getSystemFontFiles();
        List<String> fontNames = new ArrayList<>();
        for (File file : fontFiles) fontNames.add(file.getName());

        final AutoCompleteTextView fontInput = new AutoCompleteTextView(requireContext());
        fontInput.setHint("Выберите шрифт");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, fontNames);
        fontInput.setAdapter(adapter);
        layout.addView(fontInput);

        final EditText sizeInput = new EditText(requireContext());
        sizeInput.setHint("Размер шрифта (по умолчанию 48)");
        sizeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(sizeInput);

        CheckBox checkBold = new CheckBox(requireContext());
        checkBold.setText("Жирный");
        layout.addView(checkBold);

        CheckBox checkItalic = new CheckBox(requireContext());
        checkItalic.setText("Курсив");
        layout.addView(checkItalic);

        CheckBox checkStrike = new CheckBox(requireContext());
        checkStrike.setText("Зачеркнутый");
        layout.addView(checkStrike);

        builder.setView(layout)
                .setPositiveButton("OK", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        showToast("Введите текст");
                        return;
                    }

                    String fontName = fontInput.getText().toString().trim();
                    File selectedFile = null;
                    for (File file : fontFiles) {
                        if (file.getName().equals(fontName)) {
                            selectedFile = file;
                            break;
                        }
                    }

                    Typeface typeface = (selectedFile != null && selectedFile.exists()) ?
                            Typeface.createFromFile(selectedFile) : Typeface.DEFAULT;
                    drawingView.setTypeface(typeface);

                    drawingView.setBold(checkBold.isChecked());
                    drawingView.setItalic(checkItalic.isChecked());
                    drawingView.setStrike(checkStrike.isChecked());

                    int size = 48;
                    try {
                        size = Integer.parseInt(sizeInput.getText().toString());
                    } catch (NumberFormatException ignored) {
                    }
                    drawingView.setTextSize(size);

                    drawingView.prepareTextPlacement(text);
                    showToast("Теперь нажмите на холст, чтобы разместить текст");
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.cancel())
                .show();

        fontInput.post(fontInput::showDropDown);
    }

    private List<File> getSystemFontFiles() {
        File fontDir = new File("/system/fonts");
        File[] files = fontDir.listFiles((dir, name) -> name.endsWith(".ttf") || name.endsWith(".otf"));
        return files != null ? Arrays.asList(files) : new ArrayList<>();
    }

    private void rotateImage() {
        if (currentBitmap == null) return;
        showToast("Поворот изображения вправо");
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        currentBitmap = Bitmap.createBitmap(currentBitmap, 0, 0,
                currentBitmap.getWidth(), currentBitmap.getHeight(), matrix, true);
        updateImageViews();
        updateCanvasState();
    }

    private void mirrorImage() {
        if (currentBitmap == null) return;
        showToast("Отражение изображения");
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);
        currentBitmap = Bitmap.createBitmap(currentBitmap, 0, 0,
                currentBitmap.getWidth(), currentBitmap.getHeight(), matrix, true);
        updateImageViews();
        updateCanvasState();
    }

    private void updateImageViews() {
        if (currentBitmap != null) {
            imageView.setVisibility(View.GONE);
            drawingView.setBaseImage(currentBitmap);
            drawingView.invalidate();
        }
    }

    private void saveImageToGallery() {
        if (currentBitmap == null) {
            showToast("Нет изображения для сохранения");
            return;
        }

        Bitmap result = drawingView.mergeWithBackground();
        if (result == null) {
            showToast("Ошибка при объединении изображения");
            return;
        }

        saveBitmapToGallery(result);
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        ContentResolver resolver = requireContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Painter_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Painter");

        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (imageUri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(imageUri)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                showToast("Изображение сохранено");
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Ошибка сохранения изображения");
            }
        } else {
            showToast("Не удалось создать файл изображения");
        }
    }

    private void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

}