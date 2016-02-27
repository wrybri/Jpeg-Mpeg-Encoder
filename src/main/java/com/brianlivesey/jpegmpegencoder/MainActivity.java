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
    DCT jpeg = null;

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
        source = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.baboon80);
        sourceDrawable = new BitmapDrawable(getResources(), source);
        frameList.add(sourceDrawable);
    }

    public void convert1(View view) {
        int height = source.getHeight();
        int width = source.getWidth();
        int Cwidth = width / 2 + width % 2;

        // Create pixel array from source image
        int[] sourcePixels = new int[height * width];
        int[] YCbCrPixels = new int[height * width];
        byte[] YPixels = new byte[height * width];
        byte[] CrPixels = new byte[(height / 2 + height % 2) * (width / 2 + width % 2)];
        byte[] CbPixels = new byte[(height / 2 + height % 2) * (width / 2 + width % 2)];
        int[] outputPixels = new int[height * width];

        // RGB pixel arrays to display YCbCr channels
        int[] YDisplay = new int[height * width];
        int[] CrDisplay = new int[height * width];
        int[] CbDisplay = new int[height * width];

        // Load pixel data into bitmap
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height);

        // Bitmaps to hold output data
        Bitmap Y = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap Cr = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap Cb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Convert RGB to YCbCr
        for (int i = 0 ; i < sourcePixels.length ; ++i) {
            int Cbbits, Crbits;
            int x = i % width;
            int y = i / width;
            YCbCrPixels[i] = convert(sourcePixels[i]);
            int Ytemp = Color.alpha(YCbCrPixels[i]);
            YPixels[i] = (byte)(Ytemp - 128);
            YDisplay[i] = 0xFF000000 | Ytemp << 16 | Ytemp << 8 | Ytemp;
            if ( x % 2 == 0) {
                if ( y % 2 == 0) { // We're keeping this one!
                    Crbits = Color.red(YCbCrPixels[i]);
                    Cbbits = Color.blue(YCbCrPixels[i]);
                    CrDisplay[i] = unconvert((byte) 0, (byte) 0, Crbits - 128);
                    CbDisplay[i] = unconvert((byte)0, Cbbits - 128, (byte)0);
                    CrPixels[(y / 2) * Cwidth + x / 2] = (byte)(Crbits - 128);
                    CbPixels[(y / 2) * Cwidth + x / 2] = (byte)(Cbbits - 128);
                } else {    // below "real" pixel
                    // Subsample
                    Crbits = Color.red( YCbCrPixels[i - width ]);
                    Cbbits = Color.blue(YCbCrPixels[i - width ]);
                    CrDisplay[i] = unconvert((byte)0, (byte)0, Crbits - 128);
                    CbDisplay[i] = unconvert((byte)0, Cbbits - 128, (byte)0);
                }
            } else {
                if ( y % 2 == 0) {  // right of "real" pixel
                    // Subsample
                    Crbits = Color.red( YCbCrPixels[i - 1 ]);
                    Cbbits = Color.blue(YCbCrPixels[i - 1 ]);
                    CrDisplay[i] = unconvert((byte)0, (byte)0, Crbits - 128);
                    CbDisplay[i] = unconvert((byte)0, Cbbits - 128, (byte)0);
                } else {        // right and down from "real" pixel
                    // Subsample
                    Crbits = Color.red( YCbCrPixels[i - (width + 1) ]);
                    Cbbits = Color.blue(YCbCrPixels[i - (width + 1) ]);
                    CrDisplay[i] = unconvert((byte)0, (byte)0, Crbits - 128);
                    CbDisplay[i] = unconvert((byte)0, Cbbits - 128, (byte)0);
                }
            }
            // Debug
            /*
            if (i % 1000 == 0) {
                Log.d("PIXEL", "Y: " + Integer.toString(Yval)
                        + "  Cr: " + Color.red(YCbCrPixels[i])
                        + "  Cb: " + Color.blue(YCbCrPixels[i]));
            }
            */

            // Recombine YCbCr channels into RGB pixels
            // outputPixels[i] = unconvert(YPixels[i], Cbbits - 128, Crbits - 128);
        }

        // Reconstitute
        for (int i = 0 ; i < YPixels.length ; ++i) {
            byte Cbtemp, Crtemp;
            int x = i % width;
            int y = i / width;

            // Realized late that the round-down does all the work for me
            Cbtemp = CbPixels[(y / 2) * Cwidth + x / 2];
            Crtemp = CrPixels[(y / 2) * Cwidth + x / 2];

            outputPixels[i] = unconvert(YPixels[i], Cbtemp, Crtemp);

        }


        // setPixels example for reference
        Y.setPixels(YDisplay, 0, width, 0, 0, width, height);
        Cr.setPixels(CrDisplay, 0, width, 0, 0, width, height);
        Cb.setPixels(CbDisplay, 0, width, 0, 0, width, height);
        output.setPixels(outputPixels, 0, width, 0, 0, width, height);

        // Create drawables to display bitmaps
        BitmapDrawable YDrawable = new BitmapDrawable(getResources(), Y);
        BitmapDrawable CrDrawable = new BitmapDrawable(getResources(), Cr);
        BitmapDrawable CbDrawable = new BitmapDrawable(getResources(), Cb);
        BitmapDrawable outputDrawable = new BitmapDrawable(getResources(), output);

        /*
        frameList.add(outputDrawable);
        frameList.add(YDrawable);
        frameList.add(CbDrawable);
        frameList.add(CrDrawable);
        */

        // Open file for writing
        // File myFile = new File("/Download/digibro.txt");


        File myFile = new File(getApplicationContext().getFilesDir(), "digibro.txt");
        FileOutputStream fOut = null;
        try {
            myFile.createNewFile();
            fOut = new FileOutputStream(myFile);



            // Begin DCT
            jpeg = new DCT(YPixels, CbPixels, CrPixels, width, fOut);
            jpeg.write();
            /*
            byte[][] bt = jpeg.getBlock(50, width, jpeg.Y);
            double[][] dctOutput = jpeg.discreteCosine(50, jpeg.Y);
            byte[][] quantized = jpeg.quantize(dctOutput, true);
            byte[] zigzagged = jpeg.zigzag(quantized);
            byte[] encoded = jpeg.rle(zigzagged);

            // Write that bitch to file:

            // File myFile = new File("/Download/digibro.txt");
            // File myFile = new File(getApplicationContext().getFilesDir(), "digibro.txt");
            // myFile.createNewFile();
            // FileOutputStream fOut = new FileOutputStream(myFile);
            byte previous = 1;
            int i = 0;
            // Find index of last byte to write (second zero in terminating '00', or index 63)
            for (; i < encoded.length ; ++i) {
                if (previous == 0 && encoded[i] == 0) {
                    break;
                }
                previous = encoded[i];
            }
            fOut.write(encoded, 0, ++i);
            fOut.close();
            */
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.d("writeError", e.getMessage());
        }

        // Read data back from file


        // byte[] fileBytes = new byte[64];
        DCT input = null;
        try {
            FileInputStream inFile = openFileInput("digibro.txt");
            input = new DCT(inFile);
            // input.readChannels();
            // inFile.close();

        } catch (Exception e) {
            Toast.makeText(getBaseContext(), e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.d("readError", e.getMessage());
        }

        // Check Y channel for mojo
        /*
        int[] Ydecompressed = new int[input.Y.length];
        for (int i = 0 ; i < Ydecompressed.length ; ++i) {
            Ydecompressed[i] = unconvert(input.Y[i], (byte)0, (byte)0);
        }
        Bitmap YdecBMP = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        YdecBMP.setPixels(Ydecompressed, 0, width, 0, 0, width, height);
        BitmapDrawable YdecDrawable = new BitmapDrawable(getResources(), YdecBMP);
        */

        // frameList.add(YdecDrawable);

        // Reconstitute, again :P
        int[] CbDecoded = new int[input.Y.length];
        int[] CrDecoded = new int[input.Y.length];

        for (int i = 0 ; i < input.Y.length ; ++i) {
            byte Cbtemp, Crtemp;
            int x = i % width;
            int y = i / width;

            // Realized late that the round-down does all the work for me
            Cbtemp = input.Cb[(y / 2) * Cwidth + x / 2];
            Crtemp = input.Cr[(y / 2) * Cwidth + x / 2];

            CbDecoded[i] = unconvert((byte)0, Cbtemp, (byte)0);
            CrDecoded[i] = unconvert((byte)0, (byte)0, Crtemp);
        }

        Bitmap decodedBMP = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decodedBMP.setPixels(input.getOutput(), 0, width, 0, 0, width, height);
        BitmapDrawable decodedDrawable = new BitmapDrawable(getResources(), decodedBMP);

        Bitmap decodedCbBMP = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decodedCbBMP.setPixels(CbDecoded, 0, width, 0, 0, width, height);
        BitmapDrawable decodedCbDrawable = new BitmapDrawable(getResources(), decodedCbBMP);

        Bitmap decodedCrBMP = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        decodedCrBMP.setPixels(CrDecoded, 0, width, 0, 0, width, height);
        BitmapDrawable decodedCrDrawable = new BitmapDrawable(getResources(), decodedCrBMP);

        frameList.add(outputDrawable);
        frameList.add(decodedDrawable);
        frameList.add(decodedCbDrawable);
        frameList.add(decodedCrDrawable);

        // frameList.add(YDrawable);
        // frameList.add(CbDrawable);
        // frameList.add(CrDrawable);


        /*
        byte[] unencoded = jpeg.unrle(encoded);
        byte[][] unzigzagged = jpeg.unzigzag(zigzagged);
        double[][] unquantized = jpeg.unquantize(quantized, true);
        byte[][] idctOutput = jpeg.indiscreteCosine(unquantized);



        /*
        for (int i = 0 ; i < 8 ; ++i) {
            String temp = "";
            for (int j = 0 ; j < 8 ; ++j) {
                temp += bt[i][j] + " ";
            }
            Log.d("PIXEL", temp);
        }

        Log.d("-", "-");

        for (int i = 0 ; i < 8 ; ++i) {
            String temp = "";
            for (int j = 0 ; j < 8 ; ++j) {
                temp += dctOutput[i][j] + " ";
            }
            Log.d("DCT", temp);
        }

        Log.d("-", "-");

        for (int i = 0 ; i < 8 ; ++i) {
            String temp = "";
            for (int j = 0 ; j < 8 ; ++j) {
                temp += (quantized[i][j] + 0) + " ";
            }
            Log.d("QUANT", temp);
        }

        String temp3 = "";
        for (int i = 0 ; i < 64 ; ++i) {
            temp3 += zigzagged[i] + " ";
        }
        Log.d("ZIG", temp3);
        Log.d("-", "-");

        temp3 = "";
        for (int i = 0 ; i < 64 ; ++i) {
            temp3 += encoded[i] + " ";
        }
        Log.d("RLE", temp3);
        Log.d("-", "-");

        temp3 = "";
        for (int i = 0 ; i < 64 ; ++i) {
            temp3 += fileBytes[i] + " ";
        }
        Log.d("READ_FILE", temp3);
        Log.d("-", "-");

        temp3 = "";
        for (int i = 0 ; i < 64 ; ++i) {
            temp3 += unencoded[i] + " ";
        }
        Log.d("unRLE", temp3);
        Log.d("-", "-");

        for (int i = 0 ; i < 8 ; ++i) {
            String temp = "";
            for (int j = 0 ; j < 8 ; ++j) {
                temp += unzigzagged[i][j] + " ";
            }
            Log.d("unZIG", temp);
        }
        Log.d("-", "-");

        for (int i = 0 ; i < 8 ; ++i) {
            String temp = "";
            for (int j = 0 ; j < 8 ; ++j) {
                temp += unquantized[i][j] + " ";
            }
            Log.d("unQUANT", temp);
        }
        Log.d("-", "-");
        for (int i = 0 ; i < 8 ; ++i) {
            String temp = "";
            for (int j = 0 ; j < 8 ; ++j) {
                temp += idctOutput[i][j] + " ";
            }
            Log.d("iDCT", temp);
        }
        */
    }

    // Button WRITE FILE
    public void writeFile(View view) {
        /*
        if (jpeg != null) {
            jpeg.write();
        }
        */
    }

    // Button READ FILE
    public void readFile(View view) {

    }

    // Convert RGB to YCbCr
    int convert(int c) {
        int r = Color.red(c);
        int g = Color.green(c);
        int b = Color.blue(c);

        int Y = (int)Math.round((0 + 0.299 * r) + (0.587 * g) + (0.114 * b));
        int Cb = (int)Math.round(128 - (0.168736 * r) - (0.331264 * g) + (0.5 * b));
        int Cr = (int)Math.round(128 + (0.5 * r) - (0.418688 * g) - (0.081312 * b));

        // Stuffing Cb into b channel, Cr into r channel, and Y into Alpha channel
        return (Y << 24) | (Cr << 16) | Cb;
    }

    // Convert YCbCr to RGB
    int unconvert(int Y, int Cb, int Cr) {
        Y += 128;
        Cb += 128;
        Cr += 128;
        int r = (int)Math.min(Math.max(Math.round(Y + 1.402 * (Cr - 128)), 0), 255);
        int g = (int)Math.min(Math.max(Math.round(Y - 0.34414 * (Cb - 128) - 0.71414 * (Cr - 128)), 0), 255);
        int b = (int)Math.min(Math.max(Math.round(Y + 1.772 * (Cb - 128)), 0), 255);

        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
