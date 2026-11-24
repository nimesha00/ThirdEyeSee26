package com.sliit.thirdeye;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.Locale;

public class TextRecognitionResultActivity extends AppCompatActivity {

    private ShapeableImageView imageIv;
    private EditText recognizedTextEt;

    private static final String TAG = "MAIN_TAG";
    private Uri imageUri = null;

    private ProgressDialog progressDialog;

    private TextRecognizer textRecognizer;

    private String piclink;

    private TextToSpeech tts;

    private Button btnCheckagain;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_recognition_result);

        imageIv = findViewById(R.id.imageIv);
        recognizedTextEt = findViewById(R.id.recognizedTextEt);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);


        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        btnCheckagain = (Button) findViewById(R.id.btnCheckagain);

        btnCheckagain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TextRecognitionResultActivity.this,TextRecognitionCameraActivity2.class);
                startActivity(intent);
            }
        });

        try{

            piclink = getIntent().getStringExtra("piclink");

            imageUri = Uri.parse("content://media/"+piclink);
            String[] projection = { MediaStore.Images.Media.DATA };
            Cursor cursor = getContentResolver().query(imageUri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                String imagePath = cursor.getString(columnIndex);

                if (imagePath != null && !imagePath.isEmpty()) {
                    Bitmap bitmap2 = BitmapFactory.decodeFile(imagePath);
                    imageIv.setImageBitmap(bitmap2);
                }

                cursor.close();
            }

            if(imageUri == null){
                Toast.makeText(TextRecognitionResultActivity.this, "Pick image first...", Toast.LENGTH_SHORT).show();
            }
            else {
                recognizedTextFromImage();
            }

        }catch (Exception e){
            System.out.println(e.getMessage());
        }


    }

    private void recognizedTextFromImage() {

        Log.d(TAG, "recognizedTextFromImage: ");
        progressDialog.setMessage("Preparing image....");
        progressDialog.show();

        try {
            InputImage inputImage = InputImage.fromFilePath(this,imageUri);

            progressDialog.setMessage("Recognizing Text...");

            Task<Text> textTaskResult = textRecognizer.process(inputImage)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text text) {
                            progressDialog.dismiss();
                            String recognizedText = text.getText();
                            Log.d(TAG, "onSuccess: recognizedText:"+recognizedText);
                            recognizedTextEt.setText(recognizedText);

                            if(recognizedText.isEmpty() || recognizedText == null){
                                Speak_AIAssistant("Nothing is Recognized! Tap the bottom of your screen to try again");
                            }else{
                                Speak_AIAssistant("Text recognition success ! "+ recognizedText +"Tap the bottom of your screen to check again");

                            }

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Log.e(TAG, "onFailure: ", e);
                            Toast.makeText(TextRecognitionResultActivity.this, "Failed recognizing text due to"+e.getMessage(), Toast.LENGTH_SHORT).show();
                            Speak_AIAssistant("Text recognition Failed ! ");
                        }
                    });

        } catch (Exception e) {
            progressDialog.dismiss();
            Log.e(TAG, "recognizedTextFromImage: ", e);
            Toast.makeText(this, "Failed preparing image due to "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    // function for speaking
    private void Speak_AIAssistant(String text) {
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                //select Language
                tts.setLanguage(new Locale("EN", "LK"));
                tts.setSpeechRate(1f);
                tts.setPitch(1);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        });
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