package com.sliit.thirdeye;

import static com.android.volley.toolbox.Volley.newRequestQueue;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IshiharaTest extends AppCompatActivity {

    // variable for response
    private String resmsg;

    // Declaring Button instance named "btn_submit" for submit the form
    private Button btn_submit;

    // Declaring EditText instances for getting user inputs from EditTexts
    private EditText textViewCard1,textViewCard2,textViewCard3,textViewCard4,textViewCard5,textViewCard6,textViewCard7,textViewCard8,textViewCard9,textViewCard10,textViewCard11,textViewCard12,textViewCard13,textViewCard14,textViewCard15,textViewCard16,textViewCard17,textViewCard22,textViewCard23,textViewCard24,textViewCard25;

    // Declaring RadioGroup instance for getting user inputs from Cards that have radio groups
    private RadioGroup radioGroup1_Card18,radioGroup2_Card19,radioGroup3_Card20,radioGroup4_Card21,radioGroup5_Card26,radioGroup6_Card27,radioGroup7_Card28,radioGroup8_Card29,radioGroup9_Card30,radioGroup10_Card31,radioGroup11_Card32,radioGroup12_Card33,radioGroup13_Card34,radioGroup14_Card35,radioGroup15_Card36,radioGroup16_Card37,radioGroup17_Card38;

    // Declaring RadioButton instance for accessing radio button that user selected
    private RadioButton radioButton_Card18,radioButton_Card19,radioButton_Card20,radioButton_Card21,radioButton_Card26,radioButton_Card27,radioButton_Card28,radioButton_Card29,radioButton_Card30,radioButton_Card31,radioButton_Card32,radioButton_Card33,radioButton_Card34,radioButton_Card35,radioButton_Card36,radioButton_Card37,radioButton_Card38;

    // Declaring String variables for storing user inputs
    private String Card1_text,Card2_text,Card3_text,Card4_text,Card5_text,Card6_text,Card7_text,Card8_text,Card9_text,Card10_text,Card11_text,Card12_text,Card13_text,Card14_text,Card15_text,Card16_text,Card17_text,Card18_text,Card19_text,Card20_text,Card21_text,Card22_text,Card23_text,Card24_text,Card25_text,Card26_text,Card27_text,Card28_text,Card29_text,Card30_text,Card31_text,Card32_text,Card33_text,Card34_text,Card35_text,Card36_text,Card37_text,Card38_text;

    // Declaring Integer variables for storing the id of radiobutton that user have selected
    private int selectedId1,selectedId2,selectedId3,selectedId4,selectedId5,selectedId6,selectedId7,selectedId8,selectedId9,selectedId10,selectedId11,selectedId12,selectedId13,selectedId14,selectedId15,selectedId16,selectedId17;


    //Progress bar
    ProgressDialog pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ishihara_test);

        // Start Initializing TextViews of the cards
        textViewCard1 = findViewById(R.id.textViewCard1);
        textViewCard2 = findViewById(R.id.textViewCard2);
        textViewCard3 = findViewById(R.id.textViewCard3);
        textViewCard4 = findViewById(R.id.textViewCard4);
        textViewCard5 = findViewById(R.id.textViewCard5);
        textViewCard6 = findViewById(R.id.textViewCard6);
        textViewCard7 = findViewById(R.id.textViewCard7);
        textViewCard8 = findViewById(R.id.textViewCard8);
        textViewCard9 = findViewById(R.id.textViewCard9);
        textViewCard10 = findViewById(R.id.textViewCard10);
        textViewCard11 = findViewById(R.id.textViewCard11);
        textViewCard12 = findViewById(R.id.textViewCard12);
        textViewCard13 = findViewById(R.id.textViewCard13);
        textViewCard14 = findViewById(R.id.textViewCard14);
        textViewCard15 = findViewById(R.id.textViewCard15);
        textViewCard16 = findViewById(R.id.textViewCard16);
        textViewCard17 = findViewById(R.id.textViewCard17);
        textViewCard22 = findViewById(R.id.textViewCard22);
        textViewCard23 = findViewById(R.id.textViewCard23);
        textViewCard24 = findViewById(R.id.textViewCard24);
        textViewCard25 = findViewById(R.id.textViewCard25);
        // End Initializing TextViews of the cards

        // Start Radio Group Initialize
        radioGroup1_Card18 = findViewById(R.id.radioGroup1);
        radioGroup2_Card19 = findViewById(R.id.radioGroup2);
        radioGroup3_Card20 = findViewById(R.id.radioGroup3);
        radioGroup4_Card21 = findViewById(R.id.radioGroup4);
        radioGroup5_Card26 = findViewById(R.id.radioGroup5);
        radioGroup6_Card27 = findViewById(R.id.radioGroup6);
        radioGroup7_Card28 = findViewById(R.id.radioGroup7);
        radioGroup8_Card29 = findViewById(R.id.radioGroup8);
        radioGroup9_Card30 = findViewById(R.id.radioGroup9);
        radioGroup10_Card31 = findViewById(R.id.radioGroup10);
        radioGroup11_Card32 = findViewById(R.id.radioGroup12);
        radioGroup12_Card33 = findViewById(R.id.radioGroup11);
        radioGroup13_Card34 = findViewById(R.id.radioGroup13);
        radioGroup14_Card35 = findViewById(R.id.radioGroup14);
        radioGroup15_Card36 = findViewById(R.id.radioGroup15);
        radioGroup16_Card37 = findViewById(R.id.radioGroup16);
        radioGroup17_Card38 = findViewById(R.id.radioGroup17);
        // End Radio Group Initialize

        // Start Initializing buttons of the layout
        btn_submit = findViewById(R.id.submitbutton);
        // End Initializing buttons of the layout

        // Start calling userdefined functions
        submit_onclick();
        // End calling userdefined functions

        pd = new ProgressDialog(IshiharaTest.this);

    }

    // Start Function for validating User inputs
    private Boolean validateinput(int selectedId1,int selectedId2,int selectedId3,int selectedId4,int selectedId5,int selectedId6,int selectedId7,int selectedId8,int selectedId9,int selectedId10,int selectedId11,int selectedId12,int selectedId13,int selectedId14,int selectedId15,int selectedId16,int selectedId17){

        // Start validating EditTexts of cards
        if(textViewCard1.length()==0){
            textViewCard1.requestFocus();
            textViewCard1.setError("Field Cannot empty");
            return false;
        } else if(textViewCard1.length()==0){
            textViewCard1.requestFocus();
            textViewCard1.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard2.length()==0){
            textViewCard2.requestFocus();
            textViewCard2.setError("Field Cannot empty");
            return false;
        } else if(textViewCard2.length()==0){
            textViewCard2.requestFocus();
            textViewCard2.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard3.length()==0){
            textViewCard3.requestFocus();
            textViewCard3.setError("Field Cannot empty");
            return false;
        } else if(textViewCard3.length()==0){
            textViewCard3.requestFocus();
            textViewCard3.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard4.length()==0){
            textViewCard4.requestFocus();
            textViewCard4.setError("Field Cannot empty");
            return false;
        } else if(textViewCard4.length()==0){
            textViewCard4.requestFocus();
            textViewCard4.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard5.length()==0){
            textViewCard5.requestFocus();
            textViewCard5.setError("Field Cannot empty");
            return false;
        } else if(textViewCard5.length()==0){
            textViewCard5.requestFocus();
            textViewCard5.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard6.length()==0){
            textViewCard6.requestFocus();
            textViewCard6.setError("Field Cannot empty");
            return false;
        } else if(textViewCard6.length()==0){
            textViewCard6.requestFocus();
            textViewCard6.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard7.length()==0){
            textViewCard7.requestFocus();
            textViewCard7.setError("Field Cannot empty");
            return false;
        } else if(textViewCard7.length()==0){
            textViewCard7.requestFocus();
            textViewCard7.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard8.length()==0){
            textViewCard8.requestFocus();
            textViewCard8.setError("Field Cannot empty");
            return false;
        } else if(textViewCard8.length()==0){
            textViewCard8.requestFocus();
            textViewCard8.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard9.length()==0){
            textViewCard9.requestFocus();
            textViewCard9.setError("Field Cannot empty");
            return false;
        } else if(textViewCard9.length()==0){
            textViewCard9.requestFocus();
            textViewCard9.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard10.length()==0){
            textViewCard10.requestFocus();
            textViewCard10.setError("Field Cannot empty");
            return false;
        } else if(textViewCard10.length()==0){
            textViewCard10.requestFocus();
            textViewCard10.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard11.length()==0){
            textViewCard11.requestFocus();
            textViewCard11.setError("Field Cannot empty");
            return false;
        } else if(textViewCard11.length()==0){
            textViewCard11.requestFocus();
            textViewCard11.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard12.length()==0){
            textViewCard12.requestFocus();
            textViewCard12.setError("Field Cannot empty");
            return false;
        } else if(textViewCard12.length()==0){
            textViewCard12.requestFocus();
            textViewCard12.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard13.length()==0){
            textViewCard13.requestFocus();
            textViewCard13.setError("Field Cannot empty");
            return false;
        } else if(textViewCard13.length()==0){
            textViewCard13.requestFocus();
            textViewCard13.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard14.length()==0){
            textViewCard14.requestFocus();
            textViewCard14.setError("Field Cannot empty");
            return false;
        } else if(textViewCard14.length()==0){
            textViewCard14.requestFocus();
            textViewCard14.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard15.length()==0){
            textViewCard15.requestFocus();
            textViewCard15.setError("Field Cannot empty");
            return false;
        } else if(textViewCard15.length()==0){
            textViewCard15.requestFocus();
            textViewCard15.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard16.length()==0){
            textViewCard16.requestFocus();
            textViewCard16.setError("Field Cannot empty");
            return false;
        } else if(textViewCard16.length()==0){
            textViewCard16.requestFocus();
            textViewCard16.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        if(textViewCard17.length()==0){
            textViewCard17.requestFocus();
            textViewCard17.setError("Field Cannot empty");
            return false;
        } else if(textViewCard17.length()==0){
            textViewCard17.requestFocus();
            textViewCard17.setError(" You cannot input more than 2 numbers ");
            return false;
        } else if(textViewCard22.length()==0){
            textViewCard22.requestFocus();
            textViewCard22.setError(" You cannot input more than 2 numbers ");
            return false;
        } else if(textViewCard23.length()==0){
            textViewCard23.requestFocus();
            textViewCard23.setError(" You cannot input more than 2 numbers ");
            return false;
        } else if(textViewCard24.length()==0){
            textViewCard24.requestFocus();
            textViewCard24.setError(" You cannot input more than 2 numbers ");
            return false;
        } else if(textViewCard25.length()==0){
            textViewCard25.requestFocus();
            textViewCard25.setError(" You cannot input more than 2 numbers ");
            return false;
        }
        // End validating EditTexts of cards

        // Start validating RadioButtons of cards
        if (selectedId1 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId2 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId3 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId4 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId5 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId6 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId7 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId8 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId9 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId10 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId11 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId12 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId13 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId14 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId15 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId16 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        } else if (selectedId17 == -1) {
            // Show an error message
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return false;
        }
        // End validating RadioButtons of cards

        return true;
    }
    // End Function for validating User inputs

    // Start Function for on submit button click listener
    private void submit_onclick(){
        btn_submit.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){

                // Start getting checked radio button Id to int variables
                selectedId1 = radioGroup1_Card18.getCheckedRadioButtonId();
                selectedId2 = radioGroup2_Card19.getCheckedRadioButtonId();
                selectedId3 = radioGroup3_Card20.getCheckedRadioButtonId();
                selectedId4 = radioGroup4_Card21.getCheckedRadioButtonId();
                selectedId5 = radioGroup5_Card26.getCheckedRadioButtonId();
                selectedId6 = radioGroup6_Card27.getCheckedRadioButtonId();
                selectedId7 = radioGroup7_Card28.getCheckedRadioButtonId();
                selectedId8 = radioGroup8_Card29.getCheckedRadioButtonId();
                selectedId9 = radioGroup9_Card30.getCheckedRadioButtonId();
                selectedId10 = radioGroup10_Card31.getCheckedRadioButtonId();
                selectedId11 = radioGroup11_Card32.getCheckedRadioButtonId();
                selectedId12 = radioGroup12_Card33.getCheckedRadioButtonId();
                selectedId13 = radioGroup13_Card34.getCheckedRadioButtonId();
                selectedId14 = radioGroup14_Card35.getCheckedRadioButtonId();
                selectedId15 = radioGroup15_Card36.getCheckedRadioButtonId();
                selectedId16 = radioGroup16_Card37.getCheckedRadioButtonId();
                selectedId17 = radioGroup17_Card38.getCheckedRadioButtonId();
                // End getting checked radio button Id to int variables

                // Start Find user selected radio buttion by id to getting user input
                radioButton_Card18 = findViewById(selectedId1);
                radioButton_Card19 = findViewById(selectedId2);
                radioButton_Card20 = findViewById(selectedId3);
                radioButton_Card21 = findViewById(selectedId4);
                radioButton_Card26 = findViewById(selectedId5);
                radioButton_Card27 = findViewById(selectedId6);
                radioButton_Card28 = findViewById(selectedId7);
                radioButton_Card29 = findViewById(selectedId8);
                radioButton_Card30 = findViewById(selectedId9);
                radioButton_Card31 = findViewById(selectedId10);
                radioButton_Card32 = findViewById(selectedId11);
                radioButton_Card33 = findViewById(selectedId12);
                radioButton_Card34 = findViewById(selectedId13);
                radioButton_Card35 = findViewById(selectedId14);
                radioButton_Card36 = findViewById(selectedId15);
                radioButton_Card37 = findViewById(selectedId16);
                radioButton_Card38 = findViewById(selectedId17);
                // End Find user selected radio buttion by id to getting user input



                // Assign the Result of validate user input or not to a boolean variable
                Boolean a = validateinput(selectedId1,selectedId2,selectedId3,selectedId4,selectedId5,selectedId6,selectedId7,selectedId8,selectedId9,selectedId10,selectedId11,selectedId12,selectedId13,selectedId14,selectedId15,selectedId16,selectedId17);

                if(a){
                    //  Start Getting user inputs from edittext and assign them to string variables
                    Card1_text = textViewCard1.getText().toString();
                    Card2_text = textViewCard2.getText().toString();
                    Card3_text = textViewCard3.getText().toString();
                    Card4_text = textViewCard4.getText().toString();
                    Card5_text = textViewCard5.getText().toString();
                    Card6_text  = textViewCard6.getText().toString();
                    Card7_text  = textViewCard7.getText().toString();
                    Card8_text  = textViewCard8.getText().toString();
                    Card9_text  = textViewCard9.getText().toString();
                    Card10_text  = textViewCard10.getText().toString();
                    Card11_text  = textViewCard11.getText().toString();
                    Card12_text = textViewCard12.getText().toString();
                    Card13_text = textViewCard13.getText().toString();
                    Card14_text = textViewCard14.getText().toString();
                    Card15_text = textViewCard15.getText().toString();
                    Card16_text = textViewCard16.getText().toString();
                    Card17_text = textViewCard17.getText().toString();
                    Card22_text = textViewCard22.getText().toString();
                    Card23_text = textViewCard22.getText().toString();
                    Card24_text = textViewCard22.getText().toString();
                    Card25_text = textViewCard22.getText().toString();
                    //  End Getting user inputs from edittext and assign them to string variables
                    //  Start Getting user inputs from radiobutton and assign them to string variables
                    Card18_text = radioButton_Card18.getText().toString();
                    Card19_text = radioButton_Card19.getText().toString();
                    Card20_text = radioButton_Card20.getText().toString();
                    Card21_text = radioButton_Card21.getText().toString();
                    Card26_text = radioButton_Card26.getText().toString();
                    Card27_text = radioButton_Card27.getText().toString();
                    Card28_text = radioButton_Card28.getText().toString();
                    Card29_text = radioButton_Card29.getText().toString();
                    Card30_text = radioButton_Card30.getText().toString();
                    Card31_text = radioButton_Card31.getText().toString();
                    Card32_text = radioButton_Card32.getText().toString();
                    Card33_text = radioButton_Card33.getText().toString();
                    Card34_text = radioButton_Card34.getText().toString();
                    Card35_text = radioButton_Card35.getText().toString();
                    Card36_text = radioButton_Card36.getText().toString();
                    Card37_text = radioButton_Card37.getText().toString();
                    Card38_text = radioButton_Card38.getText().toString();
                    //  End Getting user inputs from radiobutton and assign them to string variables

                    sendinputs();
                    Toast.makeText(IshiharaTest.this, "Data inserted", Toast.LENGTH_SHORT).show();

                }else{

                    Toast.makeText(IshiharaTest.this, "Validation faild", Toast.LENGTH_SHORT).show();

                }

            }
        });
    }
    // End Function for on submit button click listener

    // Convert User input to int

    private void sendinputs(){

        int card1,card2,card3,card4,card5,card6,card7,card8,card9,card10,card11,card12,card13,card14,card15,card16,card17,card18,card19,card20,card21,card22,card23,card24,card25, card26 = 0,card27 = 0,card28= 0,card29= 0,card30= 0,card31= 0,card32= 0,card33= 0,card34= 0,card35= 0,card36= 0,card37= 0,card38= 0;

        card1 = Integer.parseInt(Card1_text);
        card2 = Integer.parseInt(Card2_text);
        card3 = Integer.parseInt(Card3_text);
        card4 = Integer.parseInt(Card4_text);
        card5 = Integer.parseInt(Card5_text);
        card6 = Integer.parseInt(Card6_text);
        card7 = Integer.parseInt(Card7_text);
        card8 = Integer.parseInt(Card8_text);
        card9 = Integer.parseInt(Card9_text);
        card10 = Integer.parseInt(Card10_text);
        card11 = Integer.parseInt(Card11_text);
        card12 = Integer.parseInt(Card12_text);
        card13 = Integer.parseInt(Card13_text);
        card14 = Integer.parseInt(Card14_text);
        card15 = Integer.parseInt(Card15_text);
        card16 = Integer.parseInt(Card16_text);
        card17 = Integer.parseInt(Card17_text);
        card22 = Integer.parseInt(Card22_text);
        card23 = Integer.parseInt(Card23_text);
        card24 = Integer.parseInt(Card24_text);
        card25 = Integer.parseInt(Card25_text);

        // Card 18
        if(Card18_text.equals("Nothing") ){
            card18 = 0;
        }else{
            card18 = Integer.parseInt(Card18_text);
        }

        // Card 19
        if(Card19_text.equals("Nothing")){
            card19 = 0;
        }else{
            card19 = Integer.parseInt(Card19_text);
        }


        // Card 20
        if(Card20_text.equals("Nothing")){
            card20 = 0;
        }else{
            card20 = Integer.parseInt(Card19_text);
        }

        // Card 21
        if(Card21_text.equals("Nothing")){
            card21 = 0;
        }else{
            card21 = Integer.parseInt(Card19_text);
        }

        // Card 26
        if(Card26_text.equals("Purple and red spots")){
            card26 = 100;
        }else if( Card26_text.equals("Only the purple line")){
            card26 = 101;
        }else if(Card26_text.equals("Only the red line")){
            card26 = 102;
        }

        // Card 27
        if(Card27_text.equals("Purple and red spots")){
            card27 = 100;
        }else if(Card27_text.equals("Only the purple line")){
            card27 = 101;
        }else if(Card27_text.equals("Only the red line")){
            card27 = 102;
        }

        // Card 28
        if(Card28_text.equals("Nothing")){
            card28 = 0;
        }else if(Card28_text.equals("Line")){
            card28 = 103;
        }

        // Card 29
        if(Card29_text.equals("Nothing")){
            card29 = 0;
        }else if(Card29_text.equals("Line")){
            card29 = 103;
        }

        // Card 30
        if(Card30_text.equals("Nothing")){
            card30 = 0;
        }else if(Card30_text.equals("Blue-Green line")){
            card30 = 104;
        }

        // Card 31
        if(Card31_text.equals("Nothing")){
            card31 = 0;
        }else if(Card31_text.equals("Blue-GreenLine")){
            card31 = 104;
        }

        // Card 32
        if(Card32_text.equals("Nothing")){
            card32 = 0;
        }else if(Card32_text.equals("Orange Line")){
            card32 = 105;
        }else if(Card32_text.equals("Flase Line")){
            card32 = 105;
        }

        // Card 33
        if(Card33_text.equals("Orange Line")){
            card33 = 105;
        }else if(Card33_text.equals("Nothing")){
            card33 = 0;
        }else if(Card33_text.equals("Flase Line")){
            card33 = 105;
        }

        // Card 34
        if(Card34_text.equals("Blue-green and yellow-green line")){
            card34 = 106;
        }else if(Card34_text.equals("Only red-green and violet line")){
            card34 = 107;
        }

        // Card 35
        if(Card35_text.equals("Blue-green and yellow-green line")){
            card35 = 106;
        }else if(Card35_text.equals("Blue-green and violet line")){
            card35 = 109;
        }

        // Card 36
        if(Card36_text.equals("Violet and orange line")){
            card36 = 108;
        }else if(Card36_text.equals("Blue-green and violet line")){
            card36 = 109;
        }

        // Card 37
        if(Card37_text.equals("Violet and orange line")){
            card37 = 108;
        }else if(Card36_text.equals("Blue-green and violet line")){
            card37 = 109;
        }

        if(Card38_text.equals("Only Line")){
            card38 = 110;
        }


//        int[] myArray = new int[]{card1,card2,card3,card4,card5,card6,card7,card8,card9,card10,card11,card12,card13,card14,card15,card16,card17,card18,card19,card20,card21,card22,card23,card24,card25,card26,card27,card28,card29,card30,card31,card32,card33,card34,card35,card36,card37,card38};

        JSONArray msgArray = new JSONArray();
        msgArray.put(card1);
        msgArray.put(card2);
        msgArray.put(card3);
        msgArray.put(card4);
        msgArray.put(card5);
        msgArray.put(card6);
        msgArray.put(card7);
        msgArray.put(card8);
        msgArray.put(card9);
        msgArray.put(card10);
        msgArray.put(card11);
        msgArray.put(card12);
        msgArray.put(card13);
        msgArray.put(card14);
        msgArray.put(card15);
        msgArray.put(card16);
        msgArray.put(card17);
        msgArray.put(card18);
        msgArray.put(card19);
        msgArray.put(card20);
        msgArray.put(card21);
        msgArray.put(card22);
        msgArray.put(card23);
        msgArray.put(card24);
        msgArray.put(card25);
        msgArray.put(card26);
        msgArray.put(card27);
        msgArray.put(card28);
        msgArray.put(card29);
        msgArray.put(card30);
        msgArray.put(card31);
        msgArray.put(card32);
        msgArray.put(card33);
        msgArray.put(card34);
        msgArray.put(card35);
        msgArray.put(card36);
        msgArray.put(card37);
        msgArray.put(card38);






        postrequest(msgArray);

    }

    // function for http post request by using volley
    private void postrequest(JSONArray msgArray) {

        //Set title of progress bar
        pd.setTitle("Getting Predictions...");
        pd.show();

        String url = "https://ishiharatest-37h3nbeslq-as.a.run.app";

        RequestQueue queue = newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

                try {
                    pd.dismiss();
                    resmsg = response.getString("msg");
                    System.out.println(resmsg);
                    open_ishiharatestResult(resmsg);


                } catch (JSONException e) {

                    e.printStackTrace();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                pd.dismiss();
                resmsg = "sorry network error occors. ";

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
                    jsonObject.put("msg", msgArray);
                    String requestBody = jsonObject.toString();
                    return requestBody.getBytes("utf-8");


                } catch (JSONException | UnsupportedEncodingException uee) {

                    pd.dismiss();
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

    private void open_ishiharatestResult(String result){

        Intent intent =  new Intent(IshiharaTest.this,IshiharaResult.class);
        intent.putExtra("result",result);
        startActivity(intent);

    }

}