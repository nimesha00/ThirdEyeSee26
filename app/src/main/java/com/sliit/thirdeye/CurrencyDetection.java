package com.sliit.thirdeye;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewStub;
import android.widget.TextView;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

public class CurrencyDetection extends AbstractCameraXActivity<CurrencyDetection.AnalysisResult> {

    private Module mModule = null;
    private ResultView mResultView;

    private TextView Result_textView2;

    private TextToSpeech tts;

    static class AnalysisResult {
        private final ArrayList<Result> mResults;

        public AnalysisResult(ArrayList<Result> results) {
            mResults = results;
        }
    }

    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_currency_detection;
    }

    @Override
    protected PreviewView getCameraPreviewView() {
        mResultView = findViewById(R.id.resultView);
        return ((ViewStub) findViewById(R.id.object_detection_texture_view_stub)).inflate().findViewById(R.id.object_detection_texture_view);
    }

    @Override
    protected void applyToUiAnalyzeImageResult(AnalysisResult result) {
        mResultView.setResults(result.mResults);
        mResultView.invalidate();
    }

    private Bitmap imgToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {

        final ArrayList<Result> results;

        try {
            if (mModule == null) {
                mModule = LiteModuleLoader.load(Home.assetFilePath(getApplicationContext(), "money4best.torchscript.ptl"));
            }
            PrePostProcessor.ensureClassesLoaded(getApplicationContext());

            Bitmap bitmap = imgToBitmap(image);
            Matrix matrix = new Matrix();
            matrix.postRotate(90.0f);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);

            final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
            IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
//            IValue[] outputTuple = new IValue[] { IValue.from(inputTensor) };
            final Tensor outputTensor = outputTuple[0].toTensor();
            final float[] outputs = outputTensor.getDataAsFloatArray();

            float imgScaleX = (float) bitmap.getWidth() / PrePostProcessor.mInputWidth;
            float imgScaleY = (float) bitmap.getHeight() / PrePostProcessor.mInputHeight;
            float ivScaleX = (float) mResultView.getWidth() / bitmap.getWidth();
            float ivScaleY = (float) mResultView.getHeight() / bitmap.getHeight();

            results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);

            Result_textView2 = findViewById(R.id.Result_textView2);

            for (Result result : results){

                String clz_text = PrePostProcessor.mClasses[result.classIndex].toString();

                switch (clz_text){
                    case "100":
                        Speak_AIAssistant("100 LKR note detected");
                        break;
                    case "1000":
                        Speak_AIAssistant("1000 LKR note detected");
                        break;
                    case "20":
                        Speak_AIAssistant("20 LKR note detected");
                        break;
                    case "50":
                        Speak_AIAssistant("50 LKR note detected");
                        break;
                    case "500":
                        Speak_AIAssistant("500 LKR note detected");
                        break;
                    case "5000":
                        Speak_AIAssistant("5000 LKR note detected");
                        break;
                }
            }




        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            return null;
        } catch (Exception e){
            Log.e("Exception", "Exception", e);
            return null;
        }

        return new AnalysisResult(results);
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