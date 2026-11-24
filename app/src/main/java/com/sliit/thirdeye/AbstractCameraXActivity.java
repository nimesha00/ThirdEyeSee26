package com.sliit.thirdeye;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Live camera preview plus throttled frame analysis, for the on-phone note reader. */
public abstract class AbstractCameraXActivity<R> extends BaseModuleActivity {

    private static final int REQUEST_CODE_CAMERA_PERMISSION = 200;
    private static final String[] PERMISSIONS = {Manifest.permission.CAMERA};
    // Analyse at most twice a second; the model is too slow for every frame anyway.
    private static final long MIN_ANALYSIS_INTERVAL_MS = 500;

    private long mLastAnalysisResultTime;
    private ExecutorService analysisExecutor;

    protected abstract int getContentViewLayoutId();

    protected abstract PreviewView getCameraPreviewView();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutId());

        analysisExecutor = Executors.newSingleThreadExecutor();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE_CAMERA_PERMISSION);
        } else {
            setupCameraX();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "The note reader needs camera permission", Toast.LENGTH_LONG).show();
                finish();
            } else {
                setupCameraX();
            }
        }
    }

    private void setupCameraX() {
        final PreviewView previewView = getCameraPreviewView();
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            ProcessCameraProvider provider;
            try {
                provider = future.get();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera provider unavailable", e);
                Toast.makeText(this, "The camera couldn't start", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            Preview preview = new Preview.Builder().build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            imageAnalysis.setAnalyzer(analysisExecutor, image -> {
                // Every frame must be closed, or CameraX stops delivering new ones.
                try {
                    if (SystemClock.elapsedRealtime() - mLastAnalysisResultTime < MIN_ANALYSIS_INTERVAL_MS) {
                        return;
                    }
                    final R result = analyzeImage(image, image.getImageInfo().getRotationDegrees());
                    if (result != null) {
                        mLastAnalysisResultTime = SystemClock.elapsedRealtime();
                        runOnUiThread(() -> applyToUiAnalyzeImageResult(result));
                    }
                } finally {
                    image.close();
                }
            });

            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
        }, ContextCompat.getMainExecutor(this));
    }

    @WorkerThread
    @Nullable
    protected abstract R analyzeImage(ImageProxy image, int rotationDegrees);

    @UiThread
    protected abstract void applyToUiAnalyzeImageResult(R result);

    @Override
    protected void onDestroy() {
        if (analysisExecutor != null) analysisExecutor.shutdown();
        super.onDestroy();
    }
}
