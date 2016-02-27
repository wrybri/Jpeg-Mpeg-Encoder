package com.brianlivesey.jpegmpegencoder;

import android.graphics.Color;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Brian on 2016-02-16.
 */
public class DCT {
    final int BLOCKSIZE = 8;
    byte[] Y;
    byte[] Cb;
    byte[] Cr;
    int[] outputPixels;
    int width;
    int height;
    int blocks;
    int blockWidth;
    int cwidth;
    int cheight;
    int cblocks;
    int cblockWidth;
    FileOutputStream fOut;
    FileInputStream fIn;

    // Q-tables courtesy of Grayden Holmes, what a guy
    int[][] luminanceTable = {
            {16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55},
            {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62},
            {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92},
            {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}};

    int[][] chrominanceTable = {
            {17, 18, 24, 27, 47, 99, 99, 99},
            {18, 21, 26, 66, 99, 99, 99, 99},
            {24, 26, 56, 99, 99, 99, 99, 99},
            {47, 66, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99}};

    // Zigzag index courtesy of Spencer Pollock, loves typing WAY more than me..
    byte[][] zigzagArray = {
            {0, 1, 5, 6, 14, 15, 27, 28},
            {2, 4, 7, 13, 16, 26, 29, 42},
            {3, 8, 12, 17, 25, 30, 41, 43},
            {9, 11, 18, 24, 31, 40, 44, 53},
            {10, 19, 23, 32, 39, 45, 52, 54},
            {20, 22, 33, 38, 46, 51, 55, 60},
            {21, 34, 37, 47, 50, 56, 59, 61},
            {35, 36, 48, 49, 57, 58, 62, 63}
    };

    public DCT(byte[] Y, byte[] Cb, byte[] Cr, int width, FileOutputStream fos) {
        fOut = fos;
        this.Y = Y;
        this.Cb = Cb;
        this.Cr = Cr;
        this.width = width;
        this.height = Y.length / width;
        blockWidth = (int) Math.ceil((width / (double) BLOCKSIZE));
        blocks = (int) (blockWidth * Math.ceil(height / (double) BLOCKSIZE));
        cwidth = width / 2 + width % 2;
        cheight = height / 2 + height % 2;
        cblockWidth = (int) Math.ceil((cwidth /(double) BLOCKSIZE));
        cblocks = (int) (cblockWidth * Math.ceil(cheight / (double) BLOCKSIZE));
    }

    public DCT(FileInputStream rleData) {
        this.width = 80;        // Gonna have to read this from file header later
        this.height = 80;
        fIn = rleData;
        blockWidth = (int) Math.ceil((width / (double) BLOCKSIZE));
        blocks = (int)(blockWidth * Math.ceil(height / (double)BLOCKSIZE));
        cwidth = width / 2 + width % 2;
        cheight = height / 2 + height % 2;
        cblockWidth = (int) Math.ceil((cwidth /(double) BLOCKSIZE));
        cblocks = (int) (cblockWidth * Math.ceil(cheight / (double) BLOCKSIZE));

        // Debug
        Log.d("BLOCKS", Integer.toString(blocks));
        Log.d("BLOCKWIDTH", Integer.toString(blockWidth));
        Log.d("CBLOCKS", Integer.toString(cblocks));
        Log.d("CBLOCKWIDTH", Integer.toString(cblockWidth));

        // Read all channels from file
        read();
    }

    public void write() {
        writeChannel(Y);
        writeChannel(Cb);
        writeChannel(Cr);
        try {
            fOut.close();
        } catch (IOException e) {}
    }

    public void read() {
        Y = new byte[width * height];
        Cb = new byte[cwidth * cheight];
        Cr = new byte[cwidth * cheight];
        outputPixels = new int[width * height];
        readChannel(Y);
        readChannel(Cb);
        readChannel(Cr);
        try {
            fIn.close();
        } catch (IOException e) {}

        combineChannels();
    }

    public void writeChannel(byte[] channel) {
        int i = 0;
        int blocksToWrite = channel == Y ? blocks : cblocks;
        for ( ; i < blocksToWrite ; ++i) {
            double[][] dctOutput = null;
            try {
                dctOutput = discreteCosine(i, channel);
            } catch (Exception e) {
                Log.d("ErrDCT", "Block # " + Integer.toString(i));
            }
            byte[][] quantized = quantize(dctOutput, channel == Y);
            byte[] zigzagged = zigzag(quantized);
            byte[] encoded = rle(zigzagged);
            writeBlock(encoded);
        }
        Log.d("BlocksWritten", Integer.toString(i));
    }

    public void readChannel(byte[] channel) {
        // Gonna have to figure this out later
        // blocks = 100;
        int cWidth = width / 2 + width % 2;
        int cHeight = height / 2 + height % 2;
        int blocksToRead = channel == Y ? blocks : cblocks;

        int i = 0;
        for ( ; i < blocksToRead ; ++i) {
            try {
                byte[] fileBytes = readBlock();
                byte[] unencoded = unrle(fileBytes);
                byte[][] unzigzagged = unzigzag(unencoded);
                double[][] unquantized = unquantize(unzigzagged, channel == Y);
                byte[][] undct = indiscreteCosine(unquantized);
                putBlock(i, undct, channel);
            }
            // Debug trying to sniff out overindexing
            catch (Exception e) {
                Log.d("Stack", e.getStackTrace().toString());
                Log.d("ErrRdChannel", "Block # " + Integer.toString(i));
            }
        }

        // Debug
        Log.d("BlocksRead", Integer.toString(i));
    }


    public int blockToIndex(int blockNo, int arrayWidth) {
        int blocksPerRow = arrayWidth / BLOCKSIZE + arrayWidth % BLOCKSIZE;
        int blockX = blockNo % blocksPerRow;
        int blockY = blockNo / blocksPerRow;
        return blockY * BLOCKSIZE * arrayWidth + blockX * BLOCKSIZE;
    }

    // FOR TESTING - return block of raw pixel values (1 channel)
    public byte[][] getBlock(int blockNo, int arrayWidth, byte[] source) {
        byte[][] output = new byte[BLOCKSIZE][BLOCKSIZE];
        int startIndex = blockToIndex(blockNo, arrayWidth);
        for (int x = 0; x < BLOCKSIZE; ++x) {
            for (int y = 0; y < BLOCKSIZE; ++y) {
                output[x][y] = source[startIndex + y * arrayWidth + x];
            }
        }
        return output;
    }

    public void putBlock(int blockNo, byte[][] input, byte[] storage) {
        int arrayWidth = storage == Y ? width : (width / 2 + width % 2);

        int startIndex = blockToIndex(blockNo, arrayWidth);
        for (int x = 0; x < BLOCKSIZE; ++x) {
            for (int y = 0; y < BLOCKSIZE; ++y) {
                storage[startIndex + y * arrayWidth + x] = input[x][y];
            }
        }
    }

    public byte[] rle(byte[] input) {
        byte[] output = new byte[BLOCKSIZE * BLOCKSIZE * 2];
        byte previous = (byte) (input[0] == 0 ? 1 : 0);
        byte run = 0;
        int oindex = 0;
        boolean running = false;
        int i = 0;
        for (; i < input.length; ++i) {
            if (input[i] != 0) {
                output[oindex++] = run;
                output[oindex++] = input[i];
                run = 0;
                running = false;
            } else {
                if (running) {
                    ++run;
                } else {
                    running = true;
                    ++run;
                }
            }
        }

        if (running) {
            output[oindex++] = 0;
            output[oindex++] = 0;
        }

        return output;
    }

    public byte[] unrle(byte[] input) {
        byte[] output = new byte[BLOCKSIZE * BLOCKSIZE];
        int oindex = 0;
        for (int i = 0; i < input.length - 1; ++i) {
            for (int numZeroes = input[i++]; numZeroes > 0; --numZeroes) {
                output[oindex++] = 0;
            }
            if (input[i] != 0) {
                output[oindex++] = input[i];
            } else {
                // If double-zero encountered, pad 0's until end of array
                for (; ++oindex < output.length; ++oindex) {
                    output[oindex] = 0;
                }
                break;
            }
        }
        return output;
    }



    public double[][] discreteCosine (int blockNo, byte[] source) {
        double a = 1 / Math.sqrt(2);
        double[][] output = new double[BLOCKSIZE][ BLOCKSIZE];
        int arrayWidth = source == Y ? width : (width / 2 + width % 2);
        int offset = blockToIndex(blockNo, arrayWidth);


        for (int u = 0 ; u < BLOCKSIZE ; ++u) {
            for (int v = 0; v < BLOCKSIZE; ++v) {
                double sum = 0;
                for (int x = 0; x < BLOCKSIZE; ++x) {
                    for (int y = 0; y < BLOCKSIZE; ++y) {
                        // byte g = getByte(x, y, offset, arrayWidth, source);
                        // Subtract 128 before DCT calculation for zero-average
                        int g = source[offset + y * arrayWidth + x];
                        sum += g * Math.cos((2 * x + 1) * u * Math.PI / 16) * Math.cos((2 * y + 1) * v * Math.PI / 16);
                    }
                }
                // Assign a single DCT coefficient to output array
                output[u][v] = 0.25 * ( u == 0 ? a : 1) * ( v == 0 ? a : 1) * sum;
            }
        }
        return output;
    }

    public byte[][] indiscreteCosine (double[][] input) {
        double a = 1 / Math.sqrt(2);
        byte[][] output = new byte[BLOCKSIZE][ BLOCKSIZE];
        // int offset = blockToIndex(blockNo, arrayWidth);
        // int arrayWidth = source == Y ? width : (width / 2 + width % 2);

        for (int x = 0 ; x < BLOCKSIZE ; ++x) {
            for (int y = 0; y < BLOCKSIZE; ++y) {
                double sum = 0;
                for (int u = 0; u < BLOCKSIZE; ++u) {
                    for (int v = 0; v < BLOCKSIZE; ++v) {
                        // byte g = getByte(x, y, offset, arrayWidth, source);
                        // Subtract 128 before DCT calculation for zero-average
                        double F = input[u][v];
                        sum += ( u == 0 ? a : 1) * ( v == 0 ? a : 1) * F *
                                Math.cos((2 * x + 1) * u * Math.PI / 16) * Math.cos((2 * y + 1) * v * Math.PI / 16);
                    }
                }
                // Assign a single pixel value to output array (1 channel)
                output[x][y] = restrictByte(Math.round((0.25 * sum)));
            }
        }
        return output;
    }

    public byte getByte(int x, int y, int blockOffset, int arrayWidth, byte[] source) {
        return source[blockOffset + y * arrayWidth + x];
    }

    public byte[][] quantize (double[][] input, boolean lumaFlag) {
        byte[][] output = new byte[BLOCKSIZE][BLOCKSIZE];
        int[][] qtable = lumaFlag ? luminanceTable : chrominanceTable;

        for (int x = 0 ; x < BLOCKSIZE ; ++x) {
            for (int y = 0 ; y < BLOCKSIZE ; ++y) {
                output[x][y] = restrictByte(Math.round(input[x][y] / qtable[x][y]));
            }
        }

        return output;
    }

    private byte restrictByte(long input) {
        return (byte)Math.min(Math.max(input, -128), 127);
    }

    public double[][] unquantize (byte[][] input, boolean lumaFlag) {
        double[][] output = new double[BLOCKSIZE][BLOCKSIZE];
        int[][] qtable = lumaFlag ? luminanceTable : chrominanceTable;

        for (int x = 0 ; x < BLOCKSIZE ; ++x) {
            for (int y = 0 ; y < BLOCKSIZE ; ++y) {
                output[x][y] = input[x][y] * qtable[x][y];
            }
        }

        return output;
    }

    // Zig-zag an 8x8 2d array into a 64-byte 1d array
    byte[] zigzag(byte[][] input) {
        byte[] output = new byte[BLOCKSIZE * BLOCKSIZE];
        for (int i = 0 ; i < BLOCKSIZE ; ++i) {
            for (int j = 0 ; j < BLOCKSIZE ; ++j) {
                output[zigzagArray[i][j]] = input[i][j];
            }
        }
        return output;
    }

    // de-Zig-zag a 64-byte 1d array into an 8x8 2d array
    byte[][] unzigzag(byte[] input) {
        byte[][] output = new byte[BLOCKSIZE][BLOCKSIZE];
        for (int i = 0 ; i < BLOCKSIZE ; ++i) {
            for (int j = 0 ; j < BLOCKSIZE ; ++j) {
                output[i][j] = input[zigzagArray[i][j]];
            }
        }
        return output;
    }

    private void writeBlock(byte[] rleBlock) {
        try {
            byte previous = 1;
            int i = 0;
            // Find index of last byte to write (second zero in terminating '00', or index 63)
            for (; i < rleBlock.length; ++i) {
                if (previous == 0 && rleBlock[i] == 0) {
                    break;
                }
                previous = rleBlock[i];
            }
            fOut.write(rleBlock, 0, ++i);
            // fOut.close(); // Need to leave open for next block!
        } catch (Exception e) {
            // Toast.makeText(getBaseContext(), e.getMessage(),
                    // Toast.LENGTH_SHORT).show();
            Log.d("ERROR", e.getMessage());
        }
    }


    public byte[] readBlock() {
        // Read data block back from file (1 block from 1 channel)

        byte[] fileBytes = new byte[64];
        try {
            int previous = 1;
            for (int i = 0 ; i < fileBytes.length ; ++i) {
                fileBytes[i] = (byte)fIn.read();
                if (previous == 0 && fileBytes[i] == 0) {
                    break;
                }
                previous = fileBytes[i];

            }
        } catch (Exception e) {
            // Toast.makeText(getBaseContext(), e.getMessage(),
                    // Toast.LENGTH_SHORT).show();
            Log.d("ERROR", e.getMessage());
        }

        return fileBytes;
    }


    void combineChannels() {
        for (int i = 0 ; i < Y.length ; ++i) {
            byte Cbtemp, Crtemp;
            int x = i % width;
            int y = i / width;

            // Realized late that the round-down does all the work for me
            Cbtemp = Cb[(y / 2) * cwidth + x / 2];
            Crtemp = Cr[(y / 2) * cwidth + x / 2];

            outputPixels[i] = unconvert(Y[i], Cbtemp, Crtemp);
        }

    }


    int[] getOutput() {
        return outputPixels;
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
