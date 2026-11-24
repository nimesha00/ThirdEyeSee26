package com.sliit.thirdeye;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Splash screen shown briefly while the app starts, then replaced by Home. */
public class MainActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1500;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler.postDelayed(() -> {
            startActivity(new Intent(MainActivity.this, Home.class));
            // Close the splash so Back from Home leaves the app instead of returning here.
            finish();
        }, SPLASH_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
