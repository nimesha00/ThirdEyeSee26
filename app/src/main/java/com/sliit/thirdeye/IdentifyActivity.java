package com.sliit.thirdeye;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

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

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Takes a photo, asks the vision backend what is in it, and speaks the answer.
 * Photographs automatically after a short pause; tapping anywhere takes another.
 * When there is no internet, money and object requests fall back to the on-phone models.
 */
public class IdentifyActivity extends AppCompatActivity {

    static final String EXTRA_MODE = "mode";
    static final String EXTRA_QUESTION = "question";

    // Time to hold the phone still after the screen opens, before the first photo.
    private static final long FIRST_CAPTURE_DELAY_MS = 1800;

    private PreviewView preview;
    private TextView resultView;
    private View progress;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private VisionClient visionClient;
    private VisionClient.Mode mode;
    private String question;

    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;

    private boolean busy;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context context, VisionClient.Mode mode, String question) {
        Intent intent = new Intent(context, IdentifyActivity.class);
        intent.putExtra(EXTRA_MODE, mode.wire);
        intent.putExtra(EXTRA_QUESTION, question);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identify);
        preview = findViewById(R.id.identifyPreview);
        resultView = findViewById(R.id.identifyResult);
        progress = findViewById(R.id.identifyProgress);
        visionClient = new VisionClient(this);
        mode = VisionClient.Mode.fromWire(getIntent().getStringExtra(EXTRA_MODE));
        question = getIntent().getStringExtra(EXTRA_QUESTION);

        ((TextView) findViewById(R.id.identifyTitle)).setText(titleFor(mode));
        findViewById(R.id.identifyClose).setOnClickListener(v -> finish());

        // One engine for the whole screen; creating one per sentence leaks engines and cuts speech off.
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("en", "LK"));
                tts.setSpeechRate(0.95f);
                ttsReady = true;
                if (pendingSpeech != null) {
                    speak(pendingSpeech);
                    pendingSpeech = null;
                }
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        findViewById(R.id.identifyRoot).setOnClickListener(v -> captureAndIdentify());

        if (CameraPermissions.ensure(this, false)) {
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
                Log.e("Identify", "Camera provider unavailable", e);
                finishAttempt("Sorry, the camera couldn't start.");
                return;
            }

            Preview cameraPreview = new Preview.Builder().build();
            cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

            provider.unbindAll();
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, imageCapture);

            // Only schedule the first photo once the camera is actually bound.
            speak(introFor(mode));
            handler.postDelayed(this::captureAndIdentify, FIRST_CAPTURE_DELAY_MS);
        }, ContextCompat.getMainExecutor(this));
    }

    private static int titleFor(VisionClient.Mode mode) {
        switch (mode) {
            case MONEY:
                return R.string.identify_title_money;
            case DISPLAY:
                return R.string.identify_title_display;
            case TEXT:
                return R.string.identify_title_text;
            default:
                return R.string.identify_title_describe;
        }
    }

    private static String introFor(VisionClient.Mode mode) {
        switch (mode) {
            case MONEY:
                return "Hold the money flat in front of the camera.";
            case DISPLAY:
                return "Point the camera at the display and hold steady.";
            case TEXT:
                return "Hold the page in front of the camera.";
            default:
                return "Hold the phone steady.";
        }
    }

    private void captureAndIdentify() {
        if (busy || imageCapture == null) return;
        busy = true;
        progress.setVisibility(View.VISIBLE);
        show("Looking…");

        // Decode and rotate on the camera thread; only the result goes back to the UI thread.
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap photo = toBitmap(image, image.getImageInfo().getRotationDegrees());
                image.close();
                if (photo == null) {
                    runOnUiThread(() -> finishAttempt("Sorry, the photo didn't work. Tap the screen to try again."));
                    return;
                }
                runOnUiThread(() -> sendToVision(photo));
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Log.e("Identify", "Capture failed", e);
                runOnUiThread(() -> finishAttempt("Sorry, the camera couldn't take a photo. Tap the screen to try again."));
            }
        });
    }

    private void sendToVision(Bitmap photo) {
        speak("Checking.");
        visionClient.identify(photo, mode, question, new VisionClient.Callback() {
            @Override
            public void onAnswer(String speech) {
                finishAttempt(speech);
            }

            @Override
            public void onError(String speech, boolean offline) {
                if (offline && mode == VisionClient.Mode.MONEY) {
                    openOffline(speech + " Opening the offline note reader.", CurrencyDetection.class);
                } else if (offline && mode == VisionClient.Mode.DESCRIBE) {
                    openOffline(speech + " Opening offline object detection.", ObjDetection.class);
                } else if (offline && mode == VisionClient.Mode.TEXT) {
                    openOffline(speech + " Opening the offline text reader.", TextRecognitionCameraActivity2.class);
                } else {
                    finishAttempt(speech + " Tap the screen to try again.");
                }
            }
        });
    }

    private void openOffline(String speech, Class<?> activity) {
        busy = false;
        progress.setVisibility(View.GONE);
        show(speech);
        speak(speech);
        // Let the explanation start before the camera screen changes underneath it.
        handler.postDelayed(() -> {
            startActivity(new Intent(this, activity));
            finish();
        }, 2500);
    }

    private void finishAttempt(String speech) {
        busy = false;
        progress.setVisibility(View.GONE);
        show(speech);
        speak(speech);
    }

    private void show(String text) {
        resultView.setText(text);
    }

    private void speak(String text) {
        if (!ttsReady) {
            pendingSpeech = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "identify");
    }

    private static Bitmap toBitmap(ImageProxy image, int rotationDegrees) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null || rotationDegrees == 0) return bitmap;
        // Upright photos read far better, especially for text and note values.
        Matrix m = new Matrix();
        m.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
