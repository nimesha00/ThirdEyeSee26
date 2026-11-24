package com.sliit.thirdeye;

import static android.content.ContentValues.TAG;
import static com.android.volley.toolbox.Volley.newRequestQueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;


import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Home extends AppCompatActivity {

    private static final int REQUEST_LOCATION=1;
    LocationManager locationManager;
    public String latitude,longitude;
    private String weather;

    Button btn_colorblind, btn_speaktoai;
    private TextToSpeech tts;
    private Handler handler;
    private String input, resmsg;

    private Module mModule = null;

    private FlashlightUtils flashlightUtils;

    private static final int REQUEST_PHONE_PERMISSIONS = 123;


    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Add Permission
        ActivityCompat.requestPermissions(this,new String[]
                {Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION);
        setContentView(R.layout.activity_home);

        btn_colorblind = (Button) findViewById(R.id.button);
        btn_speaktoai = (Button) findViewById(R.id.button2);

        handler = new Handler();

        // Initialize the FlashlightUtils in your activity or wherever required
        flashlightUtils = new FlashlightUtils(getApplicationContext());


        locationManager=(LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //Check gps is enable or not

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        {
            OnGps();
        }
        else
        {
            //Gps is already on then
            getlocayion();
        }

        description_webscrape dw = new description_webscrape();
        dw.execute();

        // colorblind features open when button click
        btn_colorblind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open_ColorBlindnessTest();
            }
        });

        btn_speaktoai.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = "Speak now";
                Speak_AIAssistant(text);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Listen_AIAssistant(100);
                    }
                }, 1000);
            }
        });

        Speak_AIintro();

    }



    // function for listing
    private void Listen_AIAssistant(int rqcode) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-LK");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Start Speaking");
        startActivityForResult(intent, rqcode);
    }

    //  function for getting results
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK) {
            input = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            if(input == "can you read this to me" || input == "what does this say"){
                Speak_AIAssistant("Sure. Please show the image to mobile camera that you want read.");
                open_Ocr();
            }else if(input == "can you identify this note" || input == "what is this note"){
                Speak_AIAssistant("sure. currency reader openning. please show the money to camera one by one ");
                open_CurrencyDetection();
            }else{
                postrequest(input);
            }
            Log.d(TAG, "onActivityResult: " + input);
        } else if (requestCode == 101 && resultCode == RESULT_OK) {
            input = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            open_navigation(input);
        } else if (requestCode == 102 && resultCode == RESULT_OK){
            input = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            mycall(input);
        }
    }


    // function for http post request by using volley
    private void postrequest(String RQmsg) {


        String url = "https://thirdeye-37h3nbeslq-el.a.run.app";

        RequestQueue queue = newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

                try {
                    resmsg = response.getString("msg");
                    AI_Brain(resmsg);

                } catch (JSONException e) {

                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                resmsg = "sorry network error occors. ";
                AI_Brain(resmsg);
            }
        }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("msg", RQmsg);
                    String requestBody = jsonObject.toString();
                    return requestBody.getBytes("utf-8");

                } catch (JSONException | UnsupportedEncodingException uee) {

                    return null;
                }
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Content-Type", "application/json");

                return params;
            }
        };
        // below line is to make
        // a json object request.
        queue.add(request);

    }

    // AI Brain function. this checks the ai model response and execute a function according to the response
    private void AI_Brain(String airesmsg) {

        // switch cases are used to check responses by AI and get decisions to execute functions.
        switch (airesmsg) {
            case "sure. Ishihara test Opening":
            case "Here is ishihara test":
                Speak_AIAssistant(airesmsg);
                break;
            case "sure. Where do u want to go?":
                Speak_AIAssistant(airesmsg);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Listen_for_place();
                    }
                }, 3000);
                break;
            case "Who do you want to call":
                if (!hasPhonePermissions()) {
                    // Asking for the name is pointless until contacts/call access is granted;
                    // onRequestPermissionsResult resumes the flow.
                    requestPhonePermissions();
                    break;
                }
                Speak_AIAssistant(airesmsg);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Listen_for_call();
                    }
                }, 3000);
                break;
            case "sure. please show the page to mobile camera that you want to read":
                Speak_AIAssistant("Sure. Please show the image to mobile camera that you want read.");
                open_Ocr();
                break;
            case "sure. currency reader openning. please show the money to camera one by one ":
                Speak_AIAssistant(airesmsg);
                open_CurrencyDetection();
                break;
            case "Opening Third eye object detection":
                Speak_AIAssistant(airesmsg);
                open_DistanceEstimation();
                break;
            case "Opening weather":
                Speak_AIAssistant(weather);
                break;
            case "flasher is turning on":
                Speak_AIAssistant(airesmsg);
                turn_onFlashlight();
                break;
            case "flasher is turning off":
                Speak_AIAssistant(airesmsg);
                turn_offFlashlight();
                break;
            default:
                Speak_AIAssistant(airesmsg);

        }

    }

    // function for AI assistant intro
    private void Speak_AIintro() {

        String text = "Hello! My name is 3rd Eye. I'm your AI Assistant. If you want any help please touch the bottom of your screen";
        Speak_AIAssistant(text);
    }

    // function for speaking
    private void Speak_AIAssistant(String text) {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {

                //select Language
                tts.setLanguage(new Locale("EN", "LK",""));
                tts.setSpeechRate(0.9f);
                tts.setPitch(-5);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        });
    }

    // open ColorBlindness Test activity
    private void open_ColorBlindnessTest() {
        startNewActivity(Home.this, ColorBlindnessTest.class);
    }

    // open CurrencyDetection activity
    private void open_CurrencyDetection() {
        try {
            mModule = LiteModuleLoader.load(Home.assetFilePath(getApplicationContext(), "money4best.torchscript.ptl"));
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("classes_money.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        } catch (Exception e){
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
        startNewActivity(Home.this, CurrencyDetection.class);

    }

    // open DistanceEstimation activity
    private void open_DistanceEstimation() {
        startNewActivity(Home.this, ObjDetection.class);
    }

    // open Ocr activity
    private void open_Ocr() {

        startNewActivity(Home.this, TextRecognitionCameraActivity2.class);
    }

    // This function Listen for the place
    private void Listen_for_place() {
        Listen_AIAssistant(101);
    }

    private void Listen_for_call(){
        Listen_AIAssistant(102);
    }

    // trun by turn navigation
    private void open_navigation(String place) {
        Speak_AIAssistant(place);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + place + "&mode=w"));
        intent.setPackage("com.google.android.apps.maps");
        startActivity(intent);

    }

    // turn on flash light
    private void turn_onFlashlight(){
        // To turn on the flashlight
        flashlightUtils.turnOnFlashlight();
    }

    private void turn_offFlashlight(){
        // To turn off the flashlight
        flashlightUtils.turnOffFlashlight();
    }

    private void requestPhonePermissions() {
        String[] permissions = {
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS
        };

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PHONE_PERMISSIONS);
    }

    @SuppressLint("Range")
    private String getPhoneNumberFromContactName(String contactName) {
        String phoneNumber = null;
        ContentResolver contentResolver = getContentResolver();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " = ?";
        String[] selectionArgs = {contactName};

        Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, null);

        if (cursor != null && cursor.moveToFirst()) {
            phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();
        }

        return phoneNumber;
    }

    private void makePhoneCall(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + phoneNumber));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent);
        }
    }

    private boolean hasPhonePermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void mycall(String Name){
        if (!hasPhonePermissions()) {
            requestPhonePermissions();
            return;
        }
        String pno = getPhoneNumberFromContactName(Name);
        if (pno == null) {
            Speak_AIAssistant("Sorry, I could not find " + Name + " in your contacts.");
            return;
        }
        makePhoneCall(pno);
    }


    // function for start a new activity
    public void startNewActivity(Context context, Class<?> activityClass) {
        Intent intent = new Intent(context, activityClass);
        context.startActivity(intent);
    }

//    get location
    private void getlocayion() {
        //check permission again
        if(ActivityCompat.checkSelfPermission(Home.this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(Home.this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,new String[]
                    {Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION);
        }
        else
        {
            Location LocationGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location LocationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location LocationPassive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

            if(LocationGps != null)
            {
                double lat = LocationGps.getLatitude();
                double longi = LocationGps.getLongitude();

                latitude=String.valueOf(lat);
                longitude=String.valueOf(longi);

//                showLocationTxt.setText("Your Location:"+"\n"+"Latitude= "+latitude+"\n"+"Longitude= "+longitude);
            }
            if(LocationNetwork != null)
            {
                double lat = LocationNetwork.getLatitude();
                double longi = LocationNetwork.getLongitude();

                latitude=String.valueOf(lat);
                longitude=String.valueOf(longi);

//                showLocationTxt.setText("Your Location:"+"\n"+"Latitude= "+latitude+"\n"+"Longitude= "+longitude);
            }
            if(LocationPassive != null)
            {
                double lat = LocationPassive.getLatitude();
                double longi = LocationPassive.getLongitude();

                latitude=String.valueOf(lat);
                longitude=String.valueOf(longi);

//                showLocationTxt.setText("Your Location:"+"\n"+"Latitude= "+latitude+"\n"+"Longitude= "+longitude);
            }
            else{
                Toast.makeText(this,"Can't Get your location ",Toast.LENGTH_SHORT).show();
            }
        }
    }

//    use gps
    private void OnGps(){
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Enable GPS").setCancelable(false).setPositiveButton("YES", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        }).setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        final AlertDialog alertDialog= builder.create();
        alertDialog.show();
    }

//    html parse and get weather
    private class description_webscrape extends AsyncTask<Void,Void,Void> {

        @Override
        protected void onPreExecute(){
            super.onPreExecute();
        }
        @Override
        protected Void doInBackground(Void... voids){
            // Default answer if anything below fails; the AI "Opening weather" branch speaks this.
            weather = "Sorry, I could not get the weather right now.";

            // Without a location the URL would be ".../l/null,null".
            if (latitude == null || longitude == null) {
                return null;
            }

            try {
                org.jsoup.nodes.Document document =
                        Jsoup.connect("https://weather.com/weather/today/l/"+latitude+","+longitude).get();
                org.jsoup.select.Elements elements1 = document.getElementsByClass("CurrentConditions--tempValue--MHmYY");
                org.jsoup.select.Elements elements2 = document.getElementsByClass("CurrentConditions--phraseValue--mZC_p");
                String elementtext1 = elements1.text();
                String elementtext2 = elements2.text();

                // Class names on weather.com change; an empty match must not crash the app.
                String remDeg = elementtext1.replace("\u00b0", "").trim();
                if (remDeg.isEmpty()) {
                    return null;
                }

                int Fahrenheit = Integer.parseInt(remDeg);
                int Celsius = ((Fahrenheit-32)*5)/9;
                String cel = String.valueOf(Celsius)+"\u00b0"+"C";
                weather ="Today weather is "+elementtext2+" and Temperature is " +cel ;
            } catch (IOException | NumberFormatException e) {
                Log.e(TAG, "Weather lookup failed", e);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void aVoid){

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION) {
            // On first launch the weather lookup ran before location was allowed; retry now.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                getlocayion();
                new description_webscrape().execute();
            }
        } else if (requestCode == REQUEST_PHONE_PERMISSIONS) {
            if (hasPhonePermissions()) {
                Speak_AIAssistant("Who do you want to call");
                handler.postDelayed(this::Listen_for_call, 3000);
            } else {
                Speak_AIAssistant("I need permission to read contacts and make calls to do that.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
