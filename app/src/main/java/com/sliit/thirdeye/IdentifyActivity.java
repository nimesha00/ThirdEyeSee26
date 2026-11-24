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
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;

import java.nio.ByteBuffer;
import java.util.Locale;

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

    private TextureView preview;
    private TextView resultView;
    private ImageCapture imageCapture;
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
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        preview = findViewById(R.id.identifyPreview);
        resultView = findViewById(R.id.identifyResult);
        visionClient = new VisionClient(this);
        mode = VisionClient.Mode.fromWire(getIntent().getStringExtra(EXTRA_MODE));
        question = getIntent().getStringExtra(EXTRA_QUESTION);

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

        findViewById(R.id.identifyRoot).setOnClickListener(v -> captureAndIdentify());
        preview.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateTransform());

        if (CameraPermissions.ensure(this, false)) {
            preview.post(this::startCamera);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (CameraPermissions.onResult(this, requestCode, grantResults)) {
            preview.post(this::startCamera);
        }
    }

    private void startCamera() {
        Preview cameraPreview = new Preview(new PreviewConfig.Builder().build());
        cameraPreview.setOnPreviewOutputUpdateListener(output -> {
            // Re-attaching the TextureView is required for CameraX 1.0.0-alpha05 to show the new surface.
            ViewGroup parent = (ViewGroup) preview.getParent();
            parent.removeView(preview);
            parent.addView(preview, 0);
            preview.setSurfaceTexture(output.getSurfaceTexture());
            updateTransform();
        });

        imageCapture = new ImageCapture(new ImageCaptureConfig.Builder()
                .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .build());

        CameraX.bindToLifecycle(this, cameraPreview, imageCapture);

        speak(introFor(mode));
        handler.postDelayed(this::captureAndIdentify, FIRST_CAPTURE_DELAY_MS);
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
        show("Looking…");

        imageCapture.takePicture(new ImageCapture.OnImageCapturedListener() {
            @Override
            public void onCaptureSuccess(ImageProxy image, int rotationDegrees) {
                Bitmap photo = toBitmap(image, rotationDegrees);
                image.close();
                if (photo == null) {
                    runOnUiThread(() -> finishAttempt("Sorry, the photo didn't work. Tap the screen to try again."));
                    return;
                }
                runOnUiThread(() -> sendToVision(photo));
            }

            @Override
            public void onError(ImageCapture.UseCaseError error, String message, Throwable cause) {
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

    private void updateTransform() {
        if (preview.getDisplay() == null) return;
        float cx = preview.getWidth() / 2f;
        float cy = preview.getHeight() / 2f;
        float degrees;
        switch (preview.getDisplay().getRotation()) {
            case Surface.ROTATION_90: degrees = 90f; break;
            case Surface.ROTATION_180: degrees = 180f; break;
            case Surface.ROTATION_270: degrees = 270f; break;
            default: degrees = 0f;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(-degrees, cx, cy);
        preview.setTransform(matrix);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
