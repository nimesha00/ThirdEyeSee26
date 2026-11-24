package com.sliit.thirdeye;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class IshiharaResult extends AppCompatActivity {

    TextView textView;

    Button btn_checkAgain,btn_goto_cbc,btn_goback_activity;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ishihara_result);

        textView = findViewById(R.id.textView);
        btn_checkAgain = findViewById(R.id.btn_checkAgain);
        btn_goto_cbc = findViewById(R.id.btn_goto_cbc);
        btn_goback_activity = findViewById(R.id.btn_goback_activity);

        Intent intent = getIntent();
        String result = intent.getStringExtra("result");

        textView.setText(result);

        btn_checkAgain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startNewActivity(IshiharaResult.this,IshiharaTest.class);
            }
        });

        btn_goback_activity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startNewActivity(IshiharaResult.this,ColorBlindnessTest.class);

            }
        });

        btn_goto_cbc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startNewActivity(IshiharaResult.this,CameraFilter.class);

            }
        });

    }

    // function for start a new activity
    public void startNewActivity(Context context, Class<?> activityClass) {
        Intent intent = new Intent(context, activityClass);
        context.startActivity(intent);
    }


}