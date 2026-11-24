package com.sliit.thirdeye;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared runtime-permission handling for the screens that drive the camera directly
 * (OCR capture and the colour filters). Without it those screens open a camera they
 * were never granted, which shows a black preview on a fresh install.
 */
final class CameraPermissions {

    static final int REQUEST_CODE = 300;

    private CameraPermissions() {
    }

    /**
     * Returns true when the camera (and, if requested, storage on API 28 and below) is
     * already granted. Otherwise asks for it and returns false; the caller then starts
     * the camera from {@link #onResult}.
     */
    static boolean ensure(Activity activity, boolean needsStorageWrite) {
        List<String> missing = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CAMERA);
        }
        // MediaStore inserts need WRITE_EXTERNAL_STORAGE before Android 10.
        if (needsStorageWrite && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (missing.isEmpty()) {
            return true;
        }
        ActivityCompat.requestPermissions(activity, missing.toArray(new String[0]), REQUEST_CODE);
        return false;
    }

    /**
     * Call from onRequestPermissionsResult. Returns true when everything asked for was
     * granted. When anything was denied (or the dialog was dismissed) it tells the user
     * and closes the screen, so it never re-prompts in a loop.
     */
    static boolean onResult(Activity activity, int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_CODE) {
            return false;
        }
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if (!granted) {
            Toast.makeText(activity, "This feature needs camera permission", Toast.LENGTH_LONG).show();
            activity.finish();
        }
        return granted;
    }
}
