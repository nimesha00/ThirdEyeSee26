package com.sliit.thirdeye;

import static android.content.ContentValues.TAG;
import static com.android.volley.toolbox.Volley.newRequestQueue;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The voice assistant. The user touches the bottom half of the screen and speaks; the
 * command is matched on the phone first and anything unrecognised goes to the chat server.
 */
public class Home extends AppCompatActivity {

    // Speech recognition request codes, one per question the assistant can ask.
    private static final int RC_COMMAND = 100;
    private static final int RC_PLACE = 101;
    private static final int RC_CALL_NAME = 102;
    private static final int RC_MSG_RECIPIENT = 103;
    private static final int RC_MSG_BODY = 104;
    private static final int RC_MSG_CONFIRM = 105;

    // Runtime permission request codes.
    private static final int REQUEST_LOCATION = 1;
    private static final int REQUEST_PERMS_FOR_ACTION = 123;

    private static final String CHAT_SERVER_URL = "https://thirdeye-37h3nbeslq-el.a.run.app";
    private static final int MAX_MESSAGES_READ = 5;

    private enum Channel { SMS, WHATSAPP }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private Runnable pendingSpeechThen;
    private final Map<String, Runnable> afterSpeech = new ConcurrentHashMap<>();
    private int utteranceSeq;

    private FlashlightUtils flashlightUtils;
    private LocationManager locationManager;
    private String latitude, longitude;
    private volatile String weather;

    // A command waiting for the user to grant permissions.
    private Runnable pendingPermissionAction;
    private String pendingPermissionReason;

    // Message being composed.
    private Channel msgChannel;
    private SmsHelper.Contact msgContact;
    private String msgBody;

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }
        try (InputStream is = context.getAssets().open(assetName);
             OutputStream os = new FileOutputStream(file)) {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
        return file.getAbsolutePath();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        flashlightUtils = new FlashlightUtils(getApplicationContext());
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        initSpeech();

        Button speakButton = findViewById(R.id.button2);
        speakButton.setOnClickListener(v -> speakThen("Speak now.", () -> listen(RC_COMMAND)));

        // Shortcut tiles run the same actions as the matching voice commands.
        findViewById(R.id.tileDescribe).setOnClickListener(v ->
                IdentifyActivity.start(this, VisionClient.Mode.DESCRIBE, null));
        findViewById(R.id.tileMoney).setOnClickListener(v ->
                IdentifyActivity.start(this, VisionClient.Mode.MONEY, null));
        findViewById(R.id.tileText).setOnClickListener(v ->
                IdentifyActivity.start(this, VisionClient.Mode.TEXT, null));
        findViewById(R.id.tileDisplay).setOnClickListener(v ->
                IdentifyActivity.start(this, VisionClient.Mode.DISPLAY, null));
        findViewById(R.id.tileAround).setOnClickListener(v -> {
            speak("Opening live object detection.");
            startActivity(new Intent(this, ObjDetection.class));
        });
        findViewById(R.id.tileMessages).setOnClickListener(v -> readMessages());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            askToEnableGps();
        } else {
            readLocation();
            new WeatherLookup().execute();
        }

        speak("Hello! I'm Third Eye, your assistant. Touch the big button at the bottom of the screen and tell me what you need. "
                + "For example, say: what is this, what note is this, or read my messages.");
    }

    // ---------------------------------------------------------------- speech out

    private void initSpeech() {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int lang = tts.setLanguage(new Locale("en", "LK"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.UK);
            }
            tts.setSpeechRate(0.95f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                }

                @Override
                public void onDone(String id) {
                    runAfter(id);
                }

                @Override
                public void onError(String id) {
                    runAfter(id);
                }
            });
            ttsReady = true;
            if (pendingSpeech != null) {
                speakThen(pendingSpeech, pendingSpeechThen);
                pendingSpeech = null;
                pendingSpeechThen = null;
            }
        });
    }

    private void runAfter(String utteranceId) {
        Runnable then = afterSpeech.remove(utteranceId);
        if (then != null) handler.post(then);
    }

    private void speak(String text) {
        speakThen(text, null);
    }

    /** Speaks, then runs {@code then} once speaking has finished (e.g. to start listening). */
    private void speakThen(String text, @Nullable Runnable then) {
        if (!ttsReady) {
            pendingSpeech = text;
            pendingSpeechThen = then;
            return;
        }
        String id = "u" + (++utteranceSeq);
        if (then != null) afterSpeech.put(id, then);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    // ----------------------------------------------------------------- speech in

    private void listen(int requestCode) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-LK");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Start speaking");
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            speak("Speech recognition isn't available on this phone. Please install the Google app.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode < RC_COMMAND || requestCode > RC_MSG_CONFIRM) return;

        ArrayList<String> results = (resultCode == RESULT_OK && data != null)
                ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
        String said = (results == null || results.isEmpty()) ? null : results.get(0);
        if (said == null || said.trim().isEmpty()) {
            clearMessageDraft();
            speak("I didn't catch that. Touch the bottom of the screen to try again.");
            return;
        }
        Log.d(TAG, "heard (" + requestCode + "): " + said);

        switch (requestCode) {
            case RC_COMMAND:
                route(said);
                break;
            case RC_PLACE:
                openNavigation(said);
                break;
            case RC_CALL_NAME:
                callContact(said);
                break;
            case RC_MSG_RECIPIENT:
                onMessageRecipient(said);
                break;
            case RC_MSG_BODY:
                onMessageBody(said);
                break;
            case RC_MSG_CONFIRM:
                onMessageConfirm(said);
                break;
        }
    }

    // -------------------------------------------------------------------- router

    /**
     * Matches a spoken command on the phone. Order matters: more specific phrases
     * ("read my messages", "what note is this") are checked before general ones
     * ("read", "what is this").
     */
    private void route(String said) {
        String s = said.toLowerCase(Locale.ROOT).trim();

        if (s.contains("delete") && (s.contains("message") || s.contains("sms"))) {
            speak("Sorry, Android only lets your main messaging app delete messages, so I can't do that.");
        } else if (s.contains("whatsapp")) {
            startMessage(Channel.WHATSAPP, nameAfterTo(s));
        } else if ((s.contains("send") && (s.contains("message") || s.contains("sms") || s.contains("text")))
                || s.startsWith("message ") || s.startsWith("text ")) {
            // Before "read messages": "send a new message to Amma" also contains "new".
            startMessage(Channel.SMS, nameAfterTo(s));
        } else if (s.contains("message") && (s.contains("read") || s.contains("check") || s.contains("new")
                || s.contains("any") || s.contains("my messages"))) {
            readMessages();
        } else if (containsAny(s, "note", "money", "cash", "rupee", "currency", "coin", "how much")) {
            IdentifyActivity.start(this, VisionClient.Mode.MONEY, said);
        } else if (containsAny(s, "display", "screen", "meter", "atm", "board", "monitor")) {
            IdentifyActivity.start(this, VisionClient.Mode.DISPLAY, said);
        } else if (containsAny(s, "read", "what does this say", "what does it say", "what's written", "what is written")) {
            IdentifyActivity.start(this, VisionClient.Mode.TEXT, said);
        } else if (containsAny(s, "obstacle", "walking", "around me", "object detection", "live detection")) {
            speak("Opening live object detection.");
            startActivity(new Intent(this, ObjDetection.class));
        } else if (containsAny(s, "what is this", "what's this", "what is that", "what's that", "in front",
                "describe", "identify", "what do you see", "what can you see", "look at", "what am i holding",
                "colour", "color")) {
            IdentifyActivity.start(this, VisionClient.Mode.DESCRIBE, said);
        } else if (containsAny(s, "weather", "temperature", "rain")) {
            speakWeather();
        } else if (containsAny(s, "torch", "flash", "light")) {
            if (s.contains("off")) {
                flashlightUtils.turnOffFlashlight();
                speak("Torch off.");
            } else {
                flashlightUtils.turnOnFlashlight();
                speak("Torch on.");
            }
        } else if (s.startsWith("call ") || s.equals("call") || s.startsWith("phone ")) {
            String name = s.replaceFirst("^(call|phone)\\s*", "").trim();
            if (name.isEmpty()) {
                askWhoToCall();
            } else {
                callContact(name);
            }
        } else if (containsAny(s, "take me to", "navigate to", "directions to", "go to", "how do i get to")) {
            String place = s.replaceFirst("^.*?(take me to|navigate to|directions to|go to|how do i get to)\\s*", "").trim();
            if (place.isEmpty()) {
                speakThen("Where do you want to go?", () -> listen(RC_PLACE));
            } else {
                openNavigation(place);
            }
        } else if (containsAny(s, "help", "what can you do")) {
            speak("You can say: what is this, what note is this, read this, read the display, "
                    + "what's around me, read my messages, send a message to someone, send a WhatsApp, "
                    + "call someone, take me to a place, what's the weather, or turn the torch on.");
        } else {
            askChatServer(said);
        }
    }

    private static boolean containsAny(String s, String... phrases) {
        for (String p : phrases) {
            if (s.contains(p)) return true;
        }
        return false;
    }

    /** "send a message to amma" -> "amma"; "" when no name was given. */
    private static String nameAfterTo(String s) {
        int i = s.lastIndexOf(" to ");
        return i < 0 ? "" : s.substring(i + 4).trim();
    }

    // ------------------------------------------------------------------ messages

    private void readMessages() {
        withPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS},
                "read your messages", () -> {
                    List<SmsHelper.Message> unread;
                    try {
                        unread = SmsHelper.unreadMessages(this, MAX_MESSAGES_READ);
                    } catch (SecurityException e) {
                        speak("I'm not allowed to read your messages.");
                        return;
                    }
                    if (unread.isEmpty()) {
                        speak("You have no new messages.");
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(unread.size() == 1 ? "You have one new message. "
                            : "Here are your " + unread.size() + " newest unread messages. ");
                    for (int i = 0; i < unread.size(); i++) {
                        SmsHelper.Message m = unread.get(i);
                        if (unread.size() > 1) sb.append("Message ").append(i + 1).append(". ");
                        sb.append("From ").append(m.from).append(". ").append(m.body).append(". ");
                    }
                    speak(sb.toString());
                });
    }

    private void startMessage(Channel channel, String name) {
        String[] perms = channel == Channel.SMS
                ? new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS}
                : new String[]{Manifest.permission.READ_CONTACTS};
        withPermissions(perms, channel == Channel.SMS ? "send text messages" : "find your contacts", () -> {
            clearMessageDraft();
            msgChannel = channel;
            if (name.isEmpty()) {
                speakThen("Who do you want to message?", () -> listen(RC_MSG_RECIPIENT));
            } else {
                onMessageRecipient(name);
            }
        });
    }

    private void onMessageRecipient(String name) {
        if (msgChannel == null) return;
        SmsHelper.Contact contact = SmsHelper.findContact(this, name);
        if (contact == null) {
            clearMessageDraft();
            speak("I couldn't find " + name + " in your contacts.");
            return;
        }
        msgContact = contact;
        speakThen("What should the message to " + contact.name + " say?", () -> listen(RC_MSG_BODY));
    }

    private void onMessageBody(String body) {
        if (msgContact == null) return;
        msgBody = body;
        speakThen("Your message to " + msgContact.name + " says: " + body + ". Should I send it? Say yes or no.",
                () -> listen(RC_MSG_CONFIRM));
    }

    private void onMessageConfirm(String answer) {
        if (msgContact == null || msgBody == null) return;
        String a = answer.toLowerCase(Locale.ROOT);
        boolean yes = containsAny(a, "yes", "yeah", "yep", "send", "ok", "sure", "correct");
        boolean no = containsAny(a, "no", "don't", "cancel", "stop", "wait");
        if (!yes || no) {
            clearMessageDraft();
            speak("Okay, I didn't send it.");
            return;
        }

        SmsHelper.Contact to = msgContact;
        String body = msgBody;
        Channel channel = msgChannel;
        clearMessageDraft();

        if (channel == Channel.WHATSAPP) {
            if (SmsHelper.openWhatsApp(this, to.number, body)) {
                speak("WhatsApp is open with your message to " + to.name
                        + ". Press the green send button at the bottom right of the screen.");
            } else {
                speak("WhatsApp isn't installed on this phone.");
            }
            return;
        }
        try {
            SmsHelper.sendSms(this, to.number, body);
            speak("Message sent to " + to.name + ".");
        } catch (RuntimeException e) {
            Log.e(TAG, "SMS send failed", e);
            speak("Sorry, the message could not be sent.");
        }
    }

    private void clearMessageDraft() {
        msgChannel = null;
        msgContact = null;
        msgBody = null;
    }

    // --------------------------------------------------------------------- calls

    private void askWhoToCall() {
        withPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS},
                "make calls", () -> speakThen("Who do you want to call?", () -> listen(RC_CALL_NAME)));
    }

    private void callContact(String name) {
        withPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS},
                "make calls", () -> {
                    SmsHelper.Contact contact = SmsHelper.findContact(this, name);
                    if (contact == null) {
                        speak("Sorry, I could not find " + name + " in your contacts.");
                        return;
                    }
                    speak("Calling " + contact.name + ".");
                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + contact.number));
                    try {
                        startActivity(intent);
                    } catch (SecurityException | ActivityNotFoundException e) {
                        speak("Sorry, I couldn't start the call.");
                    }
                });
    }

    // ---------------------------------------------------------------- navigation

    private void openNavigation(String place) {
        speak("Starting walking directions to " + place + ".");
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=" + Uri.encode(place) + "&mode=w"));
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            speak("Google Maps isn't installed on this phone.");
        }
    }

    // --------------------------------------------------------------- permissions

    /** Runs {@code action} now if all permissions are granted, otherwise after the user grants them. */
    private void withPermissions(String[] permissions, String reason, Runnable action) {
        List<String> missing = new ArrayList<>();
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (missing.isEmpty()) {
            action.run();
            return;
        }
        pendingPermissionAction = action;
        pendingPermissionReason = reason;
        speak("Please allow access so I can " + reason + ".");
        ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQUEST_PERMS_FOR_ACTION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    readLocation();
                    new WeatherLookup().execute();
                } else {
                    askToEnableGps();
                }
            }
            return;
        }

        if (requestCode == REQUEST_PERMS_FOR_ACTION) {
            Runnable action = pendingPermissionAction;
            String reason = pendingPermissionReason;
            pendingPermissionAction = null;
            pendingPermissionReason = null;

            boolean granted = grantResults.length > 0;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            }
            if (granted && action != null) {
                action.run();
            } else if (reason != null) {
                speak("Without that permission I can't " + reason + ".");
            }
        }
    }

    // ------------------------------------------------------------ chat fallback

    /** Anything the phone doesn't recognise goes to the existing chat server. */
    private void askChatServer(String said) {
        RequestQueue queue = newRequestQueue(this);
        JSONObject body = new JSONObject();
        try {
            body.put("msg", said);
        } catch (JSONException e) {
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, CHAT_SERVER_URL, body,
                response -> handleChatReply(response.optString("msg", "")),
                error -> speak("Sorry, I couldn't reach the assistant. Please check your internet connection."));
        queue.add(request);
    }

    /** The chat server answers with fixed phrases for some intents; map them to actions. */
    private void handleChatReply(String reply) {
        switch (reply) {
            case "sure. Ishihara test Opening":
            case "Here is ishihara test":
                speak("Sorry, colour blindness testing is not available in this app.");
                break;
            case "sure. Where do u want to go?":
                speakThen("Where do you want to go?", () -> listen(RC_PLACE));
                break;
            case "Who do you want to call":
                askWhoToCall();
                break;
            case "sure. please show the page to mobile camera that you want to read":
                IdentifyActivity.start(this, VisionClient.Mode.TEXT, null);
                break;
            case "sure. currency reader openning. please show the money to camera one by one ":
                IdentifyActivity.start(this, VisionClient.Mode.MONEY, null);
                break;
            case "Opening Third eye object detection":
                speak("Opening live object detection.");
                startActivity(new Intent(this, ObjDetection.class));
                break;
            case "Opening weather":
                speakWeather();
                break;
            case "flasher is turning on":
                flashlightUtils.turnOnFlashlight();
                speak("Torch on.");
                break;
            case "flasher is turning off":
                flashlightUtils.turnOffFlashlight();
                speak("Torch off.");
                break;
            default:
                speak(reply.isEmpty() ? "Sorry, I didn't understand. Say help to hear what I can do." : reply);
        }
    }

    // ------------------------------------------------------------------- weather

    private void speakWeather() {
        String w = weather;
        speak(w != null ? w : "I'm still checking the weather. Please ask again in a moment.");
    }

    private void readLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            Location loc = locationManager.getLastKnownLocation(provider);
            if (loc != null) {
                latitude = String.valueOf(loc.getLatitude());
                longitude = String.valueOf(loc.getLongitude());
                return;
            }
        }
    }

    private void askToEnableGps() {
        new AlertDialog.Builder(this)
                .setMessage("Enable GPS")
                .setCancelable(false)
                .setPositiveButton("YES", (d, i) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("NO", (d, i) -> d.cancel())
                .show();
    }

    /** Scrapes today's weather from weather.com for the last known location. */
    private class WeatherLookup extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            weather = "Sorry, I could not get the weather right now.";
            if (latitude == null || longitude == null) {
                return null;
            }
            try {
                org.jsoup.nodes.Document document =
                        Jsoup.connect("https://weather.com/weather/today/l/" + latitude + "," + longitude).get();
                String temp = document.getElementsByClass("CurrentConditions--tempValue--MHmYY").text();
                String phrase = document.getElementsByClass("CurrentConditions--phraseValue--mZC_p").text();

                // Class names on weather.com change; an empty match must not crash the app.
                String digits = temp.replace("°", "").trim();
                if (digits.isEmpty()) {
                    return null;
                }
                int fahrenheit = Integer.parseInt(digits);
                int celsius = ((fahrenheit - 32) * 5) / 9;
                weather = "Today the weather is " + phrase + " and the temperature is " + celsius + " degrees Celsius.";
            } catch (IOException | NumberFormatException e) {
                Log.e(TAG, "Weather lookup failed", e);
            }
            return null;
        }
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
