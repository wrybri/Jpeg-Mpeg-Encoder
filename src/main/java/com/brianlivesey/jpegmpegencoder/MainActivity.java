package com.brianlivesey.jpegmpegencoder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    ArrayList<BitmapDrawable> frameList = new ArrayList<>();

    // Constants from ITU-R BT.709 conversion
    final static double Kb = 0.0722;
    final static double Kr = 0.2126;

    Bitmap source;
    BitmapDrawable sourceDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set-up seekbar listener
        final SeekBar sk=(SeekBar) findViewById(R.id.seekBar);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Log.d("SEEKBAR", String.valueOf(progress);
                View view = findViewById(R.id.imageView);
                int frameNo = (int) Math.floor((double) progress / (seekBar.getMax() + 1) * frameList.size());

                // Set displayed frame
                view.setBackgroundDrawable(frameList.get(frameNo));

            }
        });

        // Set up default images
        source = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.nomad1);
        sourceDrawable = new BitmapDrawable(getResources(), source);
        frameList.add(sourceDrawable);
    }

    // Button WRITE FILE
    public void writeFile(View view) {
        int height = source.getHeight();
        int width = source.getWidth();
        int Cwidth = width / 2 + width % 2;

        DCT jpeg = null;

        int[] outputPixels = new int[height * width];

        // RGB pixel arrays to display YCbCr channels
        int[] YDisplay = new int[height * width];
        int[] CrDisplay = new int[height * width];
        int[] CbDisplay = new int[height * width];

        // Bitmaps to hold output data
        Bitmap Y = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap Cr = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap Cb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        File myFile = new File(getApplicationContext().getFilesDir(), "digibro.txt");
        FileOutputStream fOut = null;
        try {
            myFile.createNewFile();
            fOut = new FileOutputStream(myFile);
            // Old constructor:
            // jpeg = new DCT(YPixels, CbPixels, CrPixels, width, fOut);
        } catch (Exception e) {
            // Toast.makeText(getBaseContext(), e.getMessage(),
            // Toast.LENGTH_SHORT).show();
            Log.d("File open error", e.getMessage());
        }

        Bitmap source2 = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.nomad2);
        // BitmapDrawable source2Drawable = new BitmapDrawable(getResources(), source);

        jpeg = new DCT(source, fOut);
        DCT jpeg2 = new DCT(source2, jpeg, fOut);
        int jpegFilesize = jpeg.getFileSize() + jpeg2.getFileSize();

        Log.d("TotalFilesize", Integer.toString(jpegFilesize));
        Log.d("FilesizeI", Integer.toString(jpeg.getFileSize()));
        Log.d("FilesizeP", Integer.toString(jpeg2.getFileSize()));

        setTitle("Orig: " + Integer.toString(width * height * 3 * 2) + "b, Mpeg: " + Integer.toString(jpegFilesize) + "b");

        try {
            fOut.close();
        } catch (IOException e) {
            Log.d("File close error", "");
        }

        // Debug
        Log.d("JPEGpblocks", Integer.toString(jpeg.pblocks));
    }


    // Button READ FILE
    public void readFile(View view) {
        // Read data back from file
        DCT input = null;
        DCT input2 = null;
        FileInputStream inFile = null;
        CheckBox vecCheck = (CheckBox)findViewById(R.id.checkBox);
        try {
            inFile = openFileInput("digibro.txt");
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.d("FileOpenError", e.getMessage());
        }
        input = new DCT(inFile);
        input2 = new DCT(inFile, input, vecCheck.isChecked());

        // Pull decoded BMP right off of DCT object, ohh yeah
        Bitmap decodedBMP = input.toRGB();
        BitmapDrawable decodedDrawable = new BitmapDrawable(getResources(), decodedBMP);
        Bitmap decodedBMP2 = input2.toRGB();
        BitmapDrawable decodedDrawable2 = new BitmapDrawable(getResources(), decodedBMP2);

        frameList.add(decodedDrawable);
        frameList.add(decodedDrawable2);

        // Debug
        Log.d("INPUTpartialBlocks", Integer.toString(input.pblocks));
    }
}
