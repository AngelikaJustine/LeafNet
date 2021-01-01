package com.example.leafnet;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    protected Interpreter tflite;

    private Bitmap bitmap;
    private List<String> labels;

    ImageView imageView;
    Uri imageuri;
    Button buclassify;
    TextView classitext, certaintyText;
    ImageView helpimg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.image);
        buclassify = findViewById(R.id.classify);
        classitext = findViewById(R.id.classifytext);
        helpimg = findViewById(R.id.img_help);
        certaintyText = findViewById(R.id.certaintyText);

        helpimg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), HelpActivity.class);
                startActivity(intent);
            }
        });

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,"Select Picture"),12);
            }
        });

        try{
            tflite=new Interpreter(loadmodelfile(this));
        }catch (Exception e) {
            e.printStackTrace();
        }

        buclassify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int imageTensorIndex = 0;
                int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}

                int x = imageShape[1];
                int y = imageShape[2];
                int componentsPerPixel = 3;
                int totalPixels = x * y;

                bitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true);

                int[] argbPixels = new int[totalPixels];
                float[][][][] rgbValuesFinal = new float[1][x][y][componentsPerPixel];

                bitmap.getPixels(argbPixels, 0, x, 0, 0, x, y);

                double[][] reds = new double[x][y];
                double[][] greens = new double[x][y];
                double[][] blues = new double[x][y];

                for (int i = 0; i < x; i++) {
                    for (int j = 0; j < y; j++) {
                        int argbPixel = bitmap.getPixel(i, j);
                        int red = Color.red(argbPixel);
                        int green = Color.green(argbPixel);
                        int blue = Color.blue(argbPixel);
                        rgbValuesFinal[0][i][j][0] = (float) (red/255.0);
                        rgbValuesFinal[0][i][j][1] = (float) (green/255.0);
                        rgbValuesFinal[0][i][j][2] = (float) (blue/255.0);
                        reds[i][j] = red;
                        greens[i][j] = green;
                        blues[i][j] = blue;
                    }
                }

                float[][] outputs = new float[1][15];

                tflite.run(rgbValuesFinal, outputs);

                float[] output = outputs[0];

                int maxProbs = maxProbability(output);

                String diseaseClass = labels.get(maxProbs);
                classitext.setText(diseaseClass);

                Float certainty = output[maxProbs];
                certaintyText.setText("" + new DecimalFormat("##.##%").format(certainty));
                certaintyText.setVisibility(View.VISIBLE);

                Log.d("Output", diseaseClass);
            }
        });
    }

    private MappedByteBuffer loadmodelfile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor=activity.getAssets().openFd("LeafDisease.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startoffset = fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startoffset,declaredLength);
    }

    private int maxProbability(float[] output){
        try{
            labels = FileUtil.loadLabels(this, "leafDiseaseClass.txt");
        }catch (Exception e){
            e.printStackTrace();
        }

        int maxAt = 0;
        for (int i = 0; i < output.length; i++) {
            maxAt = output[i] > output[maxAt] ? i : maxAt;
        }

        Log.d("Output", String.valueOf(maxAt));

        return maxAt;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode==12 && resultCode==RESULT_OK && data!=null) {
            imageuri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageuri);
                imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}