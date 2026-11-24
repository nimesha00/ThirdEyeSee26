package com.sliit.thirdeye;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

public class RedGreenFilterActivity extends AppCompatActivity {

    private TextureView textureView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_red_green_filter);

        textureView = findViewById(R.id.redgreenTV);

        if (CameraPermissions.ensure(this, false)) {
            textureView.post(this::startCamera);
        }

        textureView.addOnLayoutChangeListener(new View.OnLayoutChangeListener()
        {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
            {
                updateTransform();
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (CameraPermissions.onResult(this, requestCode, grantResults)) {
            textureView.post(this::startCamera);
        }
    }

    private void startCamera()
    {
        PreviewConfig.Builder previewConfig = new PreviewConfig.Builder();

        Preview preview = new Preview(previewConfig.build());

        preview.setOnPreviewOutputUpdateListener(new Preview.OnPreviewOutputUpdateListener()
        {
            @Override
            public void onUpdated(Preview.PreviewOutput output)
            {
                ViewGroup parent = (ViewGroup) textureView.getParent();
                parent.removeView(textureView);
                parent.addView(textureView, 0);

                textureView.setSurfaceTexture(output.getSurfaceTexture());
                updateTransform();
            }
        });

        ImageCaptureConfig.Builder imageCaptureConfig = new ImageCaptureConfig.Builder();

        final ImageCapture imageCapture = new ImageCapture(imageCaptureConfig.build());


        CameraX.bindToLifecycle(this, preview, imageCapture);
    }

    private void updateTransform()
    {
        Matrix matrix = new Matrix();

        float centerX = textureView.getWidth() / 2f;
        float centerY = textureView.getHeight() / 2f;

        float rotationDegrees = 0f;
        switch (textureView.getDisplay().getRotation())
        {
            case Surface.ROTATION_0:
                rotationDegrees = 0f;
                break;

            case Surface.ROTATION_90:
                rotationDegrees = 90f;
                break;

            case Surface.ROTATION_180:
                rotationDegrees = 180f;
                break;

            case Surface.ROTATION_270:
                rotationDegrees = 270f;
                break;

            default:
                return;
        }

        matrix.postRotate(-rotationDegrees, centerX, centerY);

        textureView.setTransform(matrix);
    }

}