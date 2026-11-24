package com.sliit.thirdeye;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;

import android.annotation.SuppressLint;
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
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class TextRecognitionCameraActivity2 extends AppCompatActivity {

    private TextureView textureView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_recognition_camera2);

        textureView = findViewById(R.id.textureView);

        // Captures are saved to MediaStore, which needs storage access before Android 10.
        if (CameraPermissions.ensure(this, true)) {
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

        ImageButton button = findViewById(R.id.imageButton);
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {

                imageCapture.takePicture(new ImageCapture.OnImageCapturedListener()
                {
                    @Override
                    public void onCaptureSuccess(ImageProxy image, int rotationDegrees)
                    {
//                        ImageView imageView = findViewById(R.id.imageView);
//                        imageView.setImageBitmap(imageProxyToBitmap(image));
//                        imageView.setRotation(rotationDegrees);
//                        image.close();
//
//
                        Bitmap bitmap = imageProxyToBitmap(image);
//                        ImageView imageView = findViewById(R.id.imageView);
//                        imageView.setImageBitmap(bitmap);
//                        imageView.setRotation(rotationDegrees);
                        image.close();

                        SaveImgInToMediaStorage(bitmap);

//

                    }
                });

            }
        });

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


    private Bitmap imageProxyToBitmap(ImageProxy image)
    {
        ImageProxy.PlaneProxy planeProxy = image.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void SaveImgInToMediaStorage(Bitmap bitmap){
        // Save captured image to Media Storage Images
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

        try (OutputStream outputStream = contentResolver.openOutputStream(imageUri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Toast.makeText(this,"Img Saved", Toast.LENGTH_SHORT).show();
            OpennExtaActivity(imageUri.getPath().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void OpennExtaActivity(String piclink){
        Intent intent = new Intent(TextRecognitionCameraActivity2.this,TextRecognitionResultActivity.class);
        intent.putExtra("piclink", piclink);
        startActivity(intent);
    }

}