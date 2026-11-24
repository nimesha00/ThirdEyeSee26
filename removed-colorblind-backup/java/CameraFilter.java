package com.sliit.thirdeye;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class CameraFilter extends AppCompatActivity {

    private Button btn_redgreeneye,btn_pp,btn_dd;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_filter);

        btn_redgreeneye = (Button)findViewById(R.id.btn_redgreeneye);
        btn_pp = (Button)findViewById(R.id.btn_pp);
        btn_dd = (Button)findViewById(R.id.btn_dd);

        btn_redgreeneye.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewActivity(CameraFilter.this, RedGreenFilterActivity.class);
            }
        });

        btn_pp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewActivity(CameraFilter.this, ProtanopiaFilterActivity.class);
            }
        });

        btn_dd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewActivity(CameraFilter.this, DeuteranopiaFilterActivity.class);
            }
        });

    }

    // function for start a new activity
    public void startNewActivity(Context context, Class<?> activityClass) {
        Intent intent = new Intent(context, activityClass);
        context.startActivity(intent);
    }

}