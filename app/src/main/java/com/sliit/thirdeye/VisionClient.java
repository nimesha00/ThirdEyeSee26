package com.sliit.thirdeye;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends one photo to the Third Eye vision backend and returns the sentence to speak.
 * The backend holds the Claude API key; this app only knows the backend URL and a
 * shared app token, both read from local.properties at build time.
 */
final class VisionClient {

    /** What the user wants from the photo. Values match the backend's "mode" field. */
    enum Mode {
        DESCRIBE("describe"), MONEY("money"), DISPLAY("display"), TEXT("text");

        final String wire;

        Mode(String wire) {
            this.wire = wire;
        }

        static Mode fromWire(String wire) {
            for (Mode m : values()) {
                if (m.wire.equals(wire)) return m;
            }
            return DESCRIBE;
        }
    }

    interface Callback {
        void onAnswer(String speech);

        /** offline is true when the phone could not reach the backend at all. */
        void onError(String speech, boolean offline);
    }

    // Long edge sent to the model. Large enough to read note serials and small print,
    // small enough to upload quickly on mobile data.
    private static final int MAX_EDGE_PX = 1568;
    // Claude can think before answering; leave room for that plus a slow upload.
    private static final int TIMEOUT_MS = 45_000;

    private final RequestQueue queue;

    VisionClient(Context context) {
        queue = Volley.newRequestQueue(context.getApplicationContext());
    }

    static boolean isConfigured() {
        return !BuildConfig.VISION_API_URL.isEmpty() && !BuildConfig.VISION_APP_TOKEN.isEmpty();
    }

    void identify(Bitmap photo, Mode mode, String question, Callback callback) {
        if (!isConfigured()) {
            callback.onError("The vision service is not set up in this build of the app.", false);
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("image_base64", toJpegBase64(photo));
            body.put("mode", mode.wire);
            if (question != null && !question.trim().isEmpty()) {
                body.put("question", question.trim());
            }
        } catch (JSONException e) {
            callback.onError("Sorry, I couldn't prepare the photo.", false);
            return;
        }

        String url = BuildConfig.VISION_API_URL.replaceAll("/+$", "") + "/identify";

        JsonObjectRequest request = new JsonObjectRequest(com.android.volley.Request.Method.POST, url, body,
                response -> {
                    String speech = response.optString("speech", "");
                    if (speech.isEmpty()) {
                        callback.onError("Sorry, I didn't get an answer. Please try again.", false);
                    } else {
                        callback.onAnswer(speech);
                    }
                },
                error -> {
                    if (error instanceof NoConnectionError) {
                        callback.onError("I can't reach the internet.", true);
                    } else if (error instanceof TimeoutError) {
                        callback.onError("The answer took too long. Please try again.", true);
                    } else {
                        NetworkResponse nr = error.networkResponse;
                        int status = nr == null ? 0 : nr.statusCode;
                        if (status == 429) {
                            callback.onError("The service is busy. Please try again in a moment.", false);
                        } else if (status == 401 || status == 503) {
                            callback.onError("The vision service is not set up correctly.", false);
                        } else {
                            callback.onError("Sorry, something went wrong. Please try again.", status == 0);
                        }
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-App-Token", BuildConfig.VISION_APP_TOKEN);
                return headers;
            }
        };
        // No automatic retry: a retried upload would bill the model twice and keep the user waiting.
        request.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_MS, 0, 1f));
        queue.add(request);
    }

    private static String toJpegBase64(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        float scale = Math.min(1f, (float) MAX_EDGE_PX / Math.max(w, h));
        Bitmap scaled = scale < 1f
                ? Bitmap.createScaledBitmap(src, Math.round(w * scale), Math.round(h * scale), true)
                : src;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
