package com.sliit.thirdeye;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline text reader: takes a photo, saves it, and hands it to on-phone ML Kit text recognition. */
public class TextRecognitionCameraActivity2 extends AppCompatActivity {

    private static final String TAG = "TextCamera";

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_recognition_camera2);

        previewView = findViewById(R.id.textureView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        ImageButton button = findViewById(R.id.imageButton);
        button.setOnClickListener(v -> takePhoto());

        // Captures are saved to MediaStore, which needs storage access before Android 10.
        if (CameraPermissions.ensure(this, true)) {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (CameraPermissions.onResult(this, requestCode, grantResults)) {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            ProcessCameraProvider provider;
            try {
                provider = future.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera provider unavailable", e);
                Toast.makeText(this, "The camera couldn't start", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();
            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        // Decode and save on a background thread so the screen doesn't freeze.
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = toUprightBitmap(image);
                image.close();
                if (bitmap == null) {
                    runOnUiThread(() -> Toast.makeText(TextRecognitionCameraActivity2.this,
                            "The photo didn't work, try again", Toast.LENGTH_SHORT).show());
                    return;
                }
                saveAndOpenResult(bitmap);
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Log.e(TAG, "Capture failed", e);
                runOnUiThread(() -> Toast.makeText(TextRecognitionCameraActivity2.this,
                        "The camera couldn't take a photo", Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** Text recognition reads sideways text badly, so rotate the photo upright first. */
    private static Bitmap toUprightBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        int rotation = image.getImageInfo().getRotationDegrees();
        if (bitmap == null || rotation == 0) return bitmap;
        Matrix m = new Matrix();
        m.postRotate(rotation);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    /** Runs on the camera executor. */
    private void saveAndOpenResult(Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis());
        String imageName = "IMG_" + timeStamp + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, imageName);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageName);
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image captured using CameraX");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        }

        ContentResolver contentResolver = getContentResolver();
        Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (imageUri == null) {
            runOnUiThread(() -> Toast.makeText(this, "Couldn't save the photo", Toast.LENGTH_SHORT).show());
            return;
        }

        try (OutputStream outputStream = contentResolver.openOutputStream(imageUri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        } catch (Exception e) {
            Log.e(TAG, "Saving photo failed", e);
            runOnUiThread(() -> Toast.makeText(this, "Couldn't save the photo", Toast.LENGTH_SHORT).show());
            return;
        }

        String piclink = imageUri.getPath();
        runOnUiThread(() -> {
            Toast.makeText(this, "Img Saved", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, TextRecognitionResultActivity.class);
            intent.putExtra("piclink", piclink);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        cameraExecutor.shutdown();
        super.onDestroy();
    }
}
