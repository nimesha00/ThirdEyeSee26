package com.sliit.thirdeye;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


public class ColorBlindnessTest extends AppCompatActivity {
    private Button buttonishiharatest,btn_color_blindness_eye;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_blindness_test);

        buttonishiharatest = (Button)findViewById(R.id.button3);
        btn_color_blindness_eye = (Button) findViewById(R.id.btn_color_blindness_eye);

        buttonishiharatest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewActivity(ColorBlindnessTest.this, IshiharaTest.class);

            }
        });

        btn_color_blindness_eye.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewActivity(ColorBlindnessTest.this, CameraFilter.class);
            }
        });


    }
    // function for start a new activity
    public void startNewActivity(Context context, Class<?> activityClass) {
        Intent intent = new Intent(context, activityClass);
        context.startActivity(intent);
    }


}