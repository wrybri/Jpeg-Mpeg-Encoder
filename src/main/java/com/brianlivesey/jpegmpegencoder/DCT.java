package com.brianlivesey.jpegmpegencoder;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

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
    int macroBlocks;
    int macroBlockWidth;
    int P = 15;                          // search radius for MEPG compression
    int IObytes;
    boolean pFrame = false;
    FileOutputStream fOut;
    FileInputStream fIn;
    DCT iFrame;
    boolean showVectors = false;

    // Debug
    int pblocks = 0;

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

    // Output Iframe based on passed Y Cb Cr channels
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

        writeIframe();
    }

    // Output Iframe based on passed bitmap
    public DCT(Bitmap input, FileOutputStream fos) {
        fOut = fos;
        this.width = input.getWidth();
        this.height = input.getHeight();
        blockWidth = (int) Math.ceil((width / (double) BLOCKSIZE));
        blocks = (int) (blockWidth * Math.ceil(height / (double) BLOCKSIZE));
        cwidth = width / 2 + width % 2;
        cheight = height / 2 + height % 2;
        cblockWidth = (int) Math.ceil((cwidth /(double) BLOCKSIZE));
        cblocks = (int) (cblockWidth * Math.ceil(cheight / (double) BLOCKSIZE));

        // Convert RGB to YCbCr
        toYCbCr(input);

        writeIframe();
    }

    // Output pFrame based on the passed iFrame
    public DCT(Bitmap input, DCT iFrame, FileOutputStream fos) {
        pFrame = true;
        this.iFrame = iFrame;
        fOut = fos;
        this.width = input.getWidth();
        this.height = input.getHeight();
        blockWidth = (int) Math.ceil((width / (double) BLOCKSIZE));
        blocks = (int) (blockWidth * Math.ceil(height / (double) BLOCKSIZE));
        cwidth = width / 2 + width % 2;
        cheight = height / 2 + height % 2;
        cblockWidth = (int) Math.ceil((cwidth /(double) BLOCKSIZE));
        cblocks = (int) (cblockWidth * Math.ceil(cheight / (double) BLOCKSIZE));
        macroBlocks = (int)(Math.ceil(width / (2 * (double)BLOCKSIZE)) * Math.ceil(height / (2 * (double)BLOCKSIZE)));
        macroBlockWidth = (int) Math.ceil(width / ((double) BLOCKSIZE * 2));

        // Convert RGB to YCbCr
        toYCbCr(input);

        writePframe();

    }

    // Read I or P frame from file handle
    public DCT(FileInputStream rleData, DCT iFrame, boolean showVecs) {
        fIn = rleData;
        readDimensions();       // Read width & height from file
        blockWidth = (int) Math.ceil((width / (double) BLOCKSIZE));
        blocks = (int)(blockWidth * Math.ceil(height / (double)BLOCKSIZE));
        cwidth = width / 2 + width % 2;
        cheight = height / 2 + height % 2;
        cblockWidth = (int) Math.ceil((cwidth / (double) BLOCKSIZE));
        cblocks = (int) (cblockWidth * Math.ceil(cheight / (double) BLOCKSIZE));
        macroBlocks = (int)(Math.ceil(width / (2 * (double)BLOCKSIZE)) * Math.ceil(height / (2 * (double)BLOCKSIZE)));
        macroBlockWidth = (int) Math.ceil(width / ((double) BLOCKSIZE * 2));
        this.iFrame = iFrame;
        showVectors = showVecs;

        // Debug
        Log.d("Pframe", "begin");
        Log.d("BLOCKS", Integer.toString(blocks));
        Log.d("BLOCKWIDTH", Integer.toString(blockWidth));
        Log.d("CBLOCKS", Integer.toString(cblocks));
        Log.d("CBLOCKWIDTH", Integer.toString(cblockWidth));
        Log.d("MacroBLOCKS", Integer.toString(macroBlocks));
        Log.d("MacroBLOCKWIDTH", Integer.toString(macroBlockWidth));

        // Read I or P frame from file
        if (iFrame == null) {
            // Read Iframe
            read(true);
        } else {
            readPframe();
        }
    }

    int getFileSize() {
        return IObytes;
    }

    // Read Iframe from file handle
    public DCT(FileInputStream rleData) {
        this(rleData, null, false);
    }

    public void toYCbCr(Bitmap source) {
        // Convert RGB to YCbCr
        int[] sourcePixels = new int[height * width];
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height);
        int[] YCbCrPixels = new int[height * width];
        Y = new byte[height * width];
        Cr = new byte[(height / 2 + height % 2) * (width / 2 + width % 2)];
        Cb = new byte[(height / 2 + height % 2) * (width / 2 + width % 2)];

        for (int i = 0 ; i < sourcePixels.length ; ++i) {
            int Cbbits, Crbits;
            int x = i % width;
            int y = i / width;
            YCbCrPixels[i] = convert(sourcePixels[i]);
            int Ytemp = Color.alpha(YCbCrPixels[i]);
            Y[i] = (byte)(Ytemp - 128);

            if ( x % 2 == 0 && y % 2 == 0) {
                    Crbits = Color.red(YCbCrPixels[i]);
                    Cbbits = Color.blue(YCbCrPixels[i]);
                    Cr[(y / 2) * cwidth + x / 2] = (byte)(Crbits - 128);
                    Cb[(y / 2) * cwidth + x / 2] = (byte)(Cbbits - 128);
            }
        }

    }

    // Generate a bitmap from decoded YCbCr data
    public Bitmap toRGB() {
        // Reconstitute for comparison display
        int[] outputPixels = new int[width * height];
        for (int i = 0 ; i < Y.length ; ++i) {
            byte Cbtemp, Crtemp;
            int x = i % width;
            int y = i / width;

            // Realized late that the round-down does all the work for me
            Cbtemp = Cb[(y / 2) * cwidth + x / 2];
            Crtemp = Cr[(y / 2) * cwidth + x / 2];

            outputPixels[i] = unconvert(Y[i], Cbtemp, Crtemp);
        }
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.setPixels(outputPixels, 0, width, 0, 0, width, height);
        return output;
    }

    // Write an Iframe to file
    public void writeIframe() {
        writeDimensions();      // Write image dimensions to file
        writeChannel(Y);
        writeChannel(Cb);
        writeChannel(Cr);
    }

    // Write a Pframe to file
    public void writePframe() {
        writeDimensions();      // Write image dimensions to file
        // ArrayList<Pair<Integer, Integer>> moVs = new ArrayList<>();
        for (int i = 0 ; i < macroBlocks ; ++i) {
            Pair<Integer, Integer> moV;
            moV = findMV(i);
            writeMacroBlock(i, moV);
        }
        // Debug
        Log.d("MacroBlocksWritten", Integer.toString(macroBlocks));
    }


    // Read a P frame from file
    public void readPframe() {
        Y = new byte[width * height];
        Cb = new byte[cwidth * cheight];
        Cr = new byte[cwidth * cheight];
        outputPixels = new int[width * height];

        for (int i = 0 ; i < macroBlocks ; ++i) {
            Pair<Integer, Integer> moV = readMotionVector();
            readMacroBlock(i, moV);
        }

        combineChannels();
        // Debug
        Log.d("MacroBlocksRead", Integer.toString(macroBlocks));
    }


    // Read
    public void read(boolean isIframe) {
        Y = new byte[width * height];
        Cb = new byte[cwidth * cheight];
        Cr = new byte[cwidth * cheight];
        outputPixels = new int[width * height];
        readChannel(Y, isIframe);
        readChannel(Cb, isIframe);
        readChannel(Cr, isIframe);

        combineChannels();
    }


    // Find motion vector for a P-frame macroblock
    private Pair<Integer, Integer> findMV(int macroBlockNo) {
        int min = 1000000000;
        int minX = 0;
        int minY = 0;

        // Prevent searching beyond image boundaries
        int maxXp = 2 * P + 1;
        int maxYp = 2 * P + 1;
        int minYpos = 0;
        int x = 0;
        int y = 0;

        /*

        int macroBlockX = macroBlockToX(macroBlockNo, width);
        int macroBlockY = macroBlockToY(macroBlockNo, width);

        // If minimum P would pull search past x = 0
        if (macroBlockX - P < 0) {
            x = P - macroBlockX;
            // If maximum P would push search-area past maximum x index (double-checked)
        } else if (macroBlockX + (2 * BLOCKSIZE - 1) + P > width - 1) {
            // Reduce maximum P to bring search just to edge of image
            maxXp = width - (macroBlockX + 2 * BLOCKSIZE - 1) + P;
            // Partial X macroblock
        } else if (macroBlockX + (2 * BLOCKSIZE - 1) > width - 1) {
            maxXp = (2 * P + 1) - (macroBlockX + (2 * BLOCKSIZE - 1) - (width - 1));
        }

        if (macroBlockY - P < 0) {
            minYpos = P - macroBlockY;
            // Max P exceeds image height
        } else if (macroBlockY + (2 * BLOCKSIZE - 1) + P > height - 1) {
            maxYp = height - (macroBlockY + 2 * BLOCKSIZE - 1) + P;
            // Partial Y macroblock
        } else if (macroBlockY + (2 * BLOCKSIZE - 1) > height - 1) {
            maxYp = (2 * P + 1) - (macroBlockY + (2 * BLOCKSIZE - 1) - (height - 1));
        }

        */
        for ( ; x < maxXp ; ++x) {
            y = minYpos;
            for ( ; y < maxYp ; ++y) {
                int diff = absDiff(macroBlockNo, x, y);
                if (diff < min) {
                    min = diff;
                    minX = x;
                    minY = y;
                }
            }
        }

        Pair<Integer, Integer> moV =  new Pair<>(minX - P, minY - P);

        // Debug
        Log.d("findMV", "MacroBlock " + Integer.toString(macroBlockNo) + ": " + Integer.toString(moV.first) + ", " + Integer.toString(moV.second));

        return moV;
    }


    // Calculate absolute difference for a macroblock vs an I-frame offset
    int absDiff(int macroBlockNo, int x, int y) {
        x -= P;
        y -= P;
        int mbX = macroBlockToX(macroBlockNo, width);
        int mbY = macroBlockToY(macroBlockNo, width);
        int offset = macroBlockToIndex(macroBlockNo, width);
        // int coffset = mbX / 2 + cwidth * mbY / 2;
        int iFrameYOffset = y * width + x;
        // int iFrameCOffset = (y / 2) * cwidth + x / 2;
        int diff = 0;

        for (int u = 0 ; u < 2 * BLOCKSIZE ; ++u) {
            for (int v = 0 ; v < 2 * BLOCKSIZE ; ++v) {
                int diffX = mbX + u + x;
                int diffY = mbY + v + y;
                int localX = mbX + u;
                int localY = mbY + v;

                int delta = u + width * v;
                // int cdelta = u / 2 + cwidth * v / 2;


                // P-frame pixel is beyond image boundary (due to partial macroblock)
                if (localX < 0 || localX >= width || localY < 0 || localY >= height) {
                    // do nothing

                // I-frame pixel is beyond image boundary due to motion-vector
                } else if (diffX < 0 || diffX >= width || diffY < 0 || diffY >= height) {
                    diff += Math.abs(Y[offset + delta]);
                    // Sorry Chroma!  -BL
                    // diff += Math.abs(Cb[coffset + cdelta]);
                    // diff += Math.abs(Cr[coffset + cdelta]);

                } else {
                    diff += Math.abs(Y[offset + delta] - iFrame.Y[offset + iFrameYOffset + delta]);
                    // diff += Math.abs(Cb[coffset + cdelta] - iFrame.Cb[coffset + iFrameCOffset + cdelta]);
                    // diff += Math.abs(Cr[coffset + cdelta] - iFrame.Cr[coffset + iFrameCOffset + cdelta]);
                }
            }
        }
        return diff;
    }

    // Write a Pframe macro block to file (4xY, 1xCb, 1xCr, 1 motion-vector)
    public void writeMacroBlock(int macroBlockNo, Pair<Integer, Integer> moV) {
        int baseBlock = macroBlockToBlock(macroBlockNo);
        byte[] channel = Y;
        int blockNo = -1;
        boolean halfWidth = false;
        boolean halfHeight = false;

        // Write motion-vector to file
        writeMoV(moV);

        // Determine if this is a half-width or half-height macroblock
        halfWidth = (baseBlock + 1) % blockWidth == 0 && blockWidth % 2 == 1;
        halfHeight = baseBlock >= blocks - blockWidth && (blocks / blockWidth) % 2 == 1;

        // Write the 6 blocks
        for (int i = 0 ; i < 6 ; ++i) {
            boolean skipBlock = false;
            switch (i) {
                case 0:
                    blockNo = baseBlock;
                    break;
                case 1:
                    blockNo = baseBlock + 1;
                    skipBlock = halfWidth;
                    break;
                case 2:
                    blockNo = baseBlock + blockWidth;
                    skipBlock = halfHeight;
                    break;
                case 3:
                    blockNo = baseBlock + blockWidth + 1;
                    skipBlock = halfWidth || halfHeight;
                    break;
                case 4:
                    channel = Cb;
                    blockNo = macroBlockNo;
                    break;
                case 5:
                    channel = Cr;
                    blockNo = macroBlockNo;
                    break;
            }

            if (!skipBlock) {
                double[][] dctOutput = discreteCosine(blockNo, channel, moV);
                byte[][] quantized = quantize(dctOutput, channel == Y);
                byte[] zigzagged = zigzag(quantized);
                byte[] encoded = rle(zigzagged);
                writeBlock(encoded);
            }
        }
    }


    // Read a Pframe macroblock from file (4xY, 1xCb, 1xCr, 1 motion-vector)
    public void readMacroBlock(int macroBlockNo, Pair<Integer, Integer> moV) {
        int baseBlock = macroBlockToBlock(macroBlockNo);
        byte[] channel = Y;
        int blockNo = -1;
        boolean halfWidth = false;
        boolean halfHeight = false;

        // Determine if this is a half-width or half-height macroblock
        halfWidth = (baseBlock + 1) % blockWidth == 0 && blockWidth % 2 == 1;
        halfHeight = baseBlock >= blocks - blockWidth && (blocks / blockWidth) % 2 == 1;

        // Read the 6 blocks
        for (int i = 0 ; i < 6 ; ++i) {
            boolean skipBlock = false;
            switch (i) {
                case 0:
                    blockNo = baseBlock;
                    break;
                case 1:
                    blockNo = baseBlock + 1;
                    skipBlock = halfWidth;
                    break;
                case 2:
                    blockNo = baseBlock + blockWidth;
                    skipBlock = halfHeight;
                    break;
                case 3:
                    blockNo = baseBlock + blockWidth + 1;
                    skipBlock = halfWidth || halfHeight;
                    break;
                case 4:
                    channel = Cb;
                    blockNo = macroBlockNo;
                    break;
                case 5:
                    channel = Cr;
                    blockNo = macroBlockNo;
                    break;
            }

            if (!skipBlock) {
                // moV = readMotionVector(); // Do this in calling function
                byte[] fileBytes = readBlock();
                byte[] unencoded = unrle(fileBytes);
                byte[][] unzigzagged = unzigzag(unencoded);
                double[][] unquantized = unquantize(unzigzagged, channel == Y);
                byte[][] undct = indiscreteCosine(unquantized);
                undct = diffDecode(undct, blockNo, channel, moV);
                putBlock(blockNo, undct, channel);
                }
        }
        Log.d("MacroBlock read: " ,Integer.toString(macroBlockNo) + ", moV: " + Integer.toString(moV.first) + ", " + Integer.toString(moV.second));
    }


    public void writeChannel(byte[] channel) {
        int i = 0;
        int blocksToWrite = channel == Y ? blocks : cblocks;
        for ( ; i < blocksToWrite ; ++i) {
            double[][] dctOutput = null;
            try {
                dctOutput = discreteCosine(i, channel, null);
            } catch (Exception e) {
                Log.d("ErrDCT", "Block # " + Integer.toString(i));
            }
            byte[][] quantized = quantize(dctOutput, channel == Y);
            byte[] zigzagged = zigzag(quantized);
            byte[] encoded = rle(zigzagged);
            writeBlock(encoded);

            // Write unquantized unDCT pixel values back to source for later I-frame comparison
            double[][] unquantized = unquantize(quantized, channel == Y);
            byte[][] idctOutput = indiscreteCosine(unquantized);
            putBlock(i, idctOutput, channel);

        }
        Log.d("BlocksWritten", Integer.toString(i));
    }

    // (Iframe only) Read in a Y Cb or Cr channel
    public void readChannel(byte[] channel, boolean isIframe) {
        int cWidth = width / 2 + width % 2;
        int cHeight = height / 2 + height % 2;
        int blocksToRead = channel == Y ? blocks : cblocks;

        int i = 0;
        for ( ; i < blocksToRead ; ++i) {
            try {
                byte[] fileBytes = readBlock();
                Pair<Integer, Integer> moV = null;
                if (!isIframe) {
                    moV = readMotionVector();
                }
                byte[] unencoded = unrle(fileBytes);
                byte[][] unzigzagged = unzigzag(unencoded);
                double[][] unquantized = unquantize(unzigzagged, channel == Y);
                byte[][] undct = indiscreteCosine(unquantized);
                if (!isIframe) {
                    undct = diffDecode(undct, i, channel, moV);
                }
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


    // Convert DCT block-indices to pixel array offsets
    private int blockToIndex(int blockNo, int arrayWidth) {
        int blocksPerRow = (int)Math.ceil(arrayWidth / (double)BLOCKSIZE);
        int blockX = blockNo % blocksPerRow;
        int blockY = blockNo / blocksPerRow;
        return blockY * BLOCKSIZE * arrayWidth + blockX * BLOCKSIZE;
    }

    private int blockToX(int blockNo, int arrayWidth) {
        int blocksPerRow = (int)Math.ceil(arrayWidth / (double)BLOCKSIZE);
        return (blockNo % blocksPerRow) * BLOCKSIZE;
    }

    private int blockToY(int blockNo, int arrayWidth) {
        int blocksPerRow = (int)Math.ceil(arrayWidth / (double)BLOCKSIZE);
        return (blockNo / blocksPerRow) * BLOCKSIZE;
    }


    // Convert macroblock indices to pixel array offsets
    private int macroBlockToIndex(int macroBlockNo, int arrayWidth) {
        int macroBlocksPerRow = (int)Math.ceil(arrayWidth / ((double)BLOCKSIZE * 2));
        int blockX = macroBlockNo % macroBlocksPerRow;
        int blockY = macroBlockNo / macroBlocksPerRow;
        return blockY * BLOCKSIZE * 2 * arrayWidth + blockX * BLOCKSIZE * 2;
    }

    private int macroBlockToX(int macroBlockNo, int arrayWidth) {
        int macroBlocksPerRow = (int)Math.ceil(arrayWidth / ((double)BLOCKSIZE * 2));
        return (macroBlockNo % macroBlocksPerRow) * 2 * BLOCKSIZE;
    }

    private int macroBlockToY(int macroBlockNo, int arrayWidth) {
        int macroBlocksPerRow = (int)Math.ceil(arrayWidth / ((double)BLOCKSIZE * 2));
        return (macroBlockNo / macroBlocksPerRow) * BLOCKSIZE * 2;
    }

    // Returns the blockNo for the upper-left Y block in a macroblock.
    // This does compensate for edge macroblocks that are <= to one block in width
    private int macroBlockToBlock(int macroBlockNo) {
        int blockY =  2 * (macroBlockNo / macroBlockWidth);
        int blockX = 2 * (macroBlockNo % macroBlockWidth);
        return blockY * blockWidth + blockX;
    }
    

    // Return block of raw pixel values (1 channel)
    public byte[][] getBlock(int blockNo, int arrayWidth, byte[] source, Pair<Integer, Integer> moV) {
        byte[][] output = new byte[BLOCKSIZE][BLOCKSIZE];
        int startIndex = blockToIndex(blockNo, arrayWidth);
        startIndex += moV.first;
        startIndex += moV.second * arrayWidth;
        int arrayHeight = source == iFrame.Y ? height : cheight;
        int xOffset = startIndex % arrayWidth;
        int yOffset = startIndex / arrayWidth;

        // Zero-pad partial blocks
        int xBoundary = 0;
        int yBoundary = 0;
        boolean partialX = (startIndex % arrayWidth + BLOCKSIZE - 1) >= arrayWidth;
        boolean partialY = (startIndex + (BLOCKSIZE - 1) + (BLOCKSIZE - 1) * arrayWidth) >= source.length;

        xBoundary = partialX ? arrayWidth - xOffset : BLOCKSIZE;
        yBoundary = partialY ? arrayHeight - yOffset : BLOCKSIZE;

        for (int x = 0; x < xBoundary; ++x) {
            for (int y = 0; y < yBoundary; ++y) {
                int sourceOffset = startIndex + y * arrayWidth + x;
                output[x][y] = source[sourceOffset];
            }
        }
        return output;
    }

    // Write an input block to the main arrays
    public void putBlock(int blockNo, byte[][] input, byte[] storage) {
        int arrayWidth = storage == Y ? width : cwidth;
        int arrayHeight = storage == Y ? height : cheight;
        int arrayBlockWidth = storage == Y ? blockWidth : cblockWidth;
        int arrayBlocks = storage == Y ? blocks : cblocks;

        int startIndex = blockToIndex(blockNo, arrayWidth);

        // Handle partial blocks
        int partialWidth = arrayWidth % BLOCKSIZE;
        int partialHeight = arrayHeight % BLOCKSIZE;
        // Is block being put a partial X and/or partial Y block
        boolean partialX = (partialWidth > 0 && (blockNo + 1) % arrayBlockWidth == 0);
        boolean partialY = (partialHeight > 0 && blockNo >= arrayBlocks - arrayBlockWidth - 1);
        boolean partialBlock = blockNo > 0 && (partialX || partialY);

        // Separate loops to improve efficiency
        if (partialBlock) {
            ++pblocks;
            // If block is not partial in one direction, default that direction to blocksize
            partialWidth = partialWidth == 0 || !partialX ? BLOCKSIZE : partialWidth;
            partialHeight = partialHeight == 0 || !partialY ? BLOCKSIZE : partialHeight;

            for (int x = 0; x < partialWidth; ++x) {
                for (int y = 0; y < partialHeight; ++y) {
                    storage[startIndex + y * arrayWidth + x] = input[x][y];
                }
            }
        } else {
            for (int x = 0; x < BLOCKSIZE; ++x) {
                for (int y = 0; y < BLOCKSIZE; ++y) {
                    storage[startIndex + y * arrayWidth + x] = input[x][y];
                }
            }
        }
        Log.d("putBlock", Integer.toString(blockNo) + ", " + (storage == Y ? "Luma" : "Chroma"));
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



    public double[][] discreteCosine (int blockNo, byte[] source, Pair<Integer, Integer> moV) {
        double a = 1 / Math.sqrt(2);
        double[][] output = new double[BLOCKSIZE][ BLOCKSIZE];
        int arrayWidth = source == Y ? width : cwidth;
        int arrayHeight = source == Y ? height : cheight;
        int offset = blockToIndex(blockNo, arrayWidth);

        // Handle partial blocks
        int partialWidth = arrayWidth % BLOCKSIZE;
        int partialHeight = arrayHeight % BLOCKSIZE;
        boolean partialBlock = partialWidth > 0 || partialHeight > 0;
        int partialWidthOffset = 0;
        int partialHeightOffset = 0;

        // For Cb Cr channels, motion vectors need to be div/2
        int moVX = 0;
        int moVY = 0;
        if (source != Y && moV != null) {
            moVX = moV.first / 2;
            moVY = moV.second / 2;
        }

        // Determine I-frame source array
        byte[] iSource = null;
            if (moV != null) {
                if (source == Y) {
                    iSource = iFrame.Y;
                } else if (source == Cb) {
                    iSource = iFrame.Cb;
                } else if (source == Cr) {
                    iSource = iFrame.Cr;
                }
            }

        // Debug
        boolean pBlockDetect = false;


        for (int u = 0 ; u < BLOCKSIZE ; ++u) {
            for (int v = 0; v < BLOCKSIZE; ++v) {
                double sum = 0;
                for (int x = 0; x < BLOCKSIZE; ++x) {
                    for (int y = 0; y < BLOCKSIZE; ++y) {
                        // partial blocks feed duplicate edge values into DCT for past-edge values
                        boolean partialX = (offset % arrayWidth + x) >= arrayWidth;
                        boolean partialY = (offset + x + y * arrayWidth) >= source.length;
                        if (partialBlock && (partialX || partialY)) {
                            // Debug
                            pBlockDetect = true;

                            partialWidthOffset = partialX ? (x < partialWidth ? 0 : x - partialWidth + 1) : 0;
                            partialHeightOffset = partialY ? (y < partialHeight ? 0 : y - partialHeight + 1) : 0;
                        } else {
                            partialWidthOffset = 0;
                            partialHeightOffset = 0;
                        }
                        int g = source[offset + y * arrayWidth + x - partialWidthOffset - partialHeightOffset * arrayWidth];

                        // P-frame differential compression
                        if (moV != null) {
                            // Apply motion-vector to DCT difference value instead of directly DCTing raw pixel value
                            int iSourceOffset = offset + (y + moVY) * arrayWidth + x + moVX - partialWidthOffset - partialHeightOffset * arrayWidth;
                            g -= iSource[iSourceOffset];
                        }
                        sum += g * Math.cos((2 * x + 1) * u * Math.PI / 16) * Math.cos((2 * y + 1) * v * Math.PI / 16);
                    }
                }
                // Assign a single DCT coefficient to output array
                output[u][v] = 0.25 * ( u == 0 ? a : 1) * ( v == 0 ? a : 1) * sum;
            }
        }
        // Debug
        pblocks += pBlockDetect ? 1 : 0;
        Log.d("Block", (source == Y ? "Luma" : "Chroma") + Integer.toString(blockNo) + " - " +
                (pFrame ? "pFrame" : "iFrame") + (pBlockDetect ? " Partial" : "-"));

        return output;
    }

    public byte[][] indiscreteCosine (double[][] input) {
        double a = 1 / Math.sqrt(2);
        byte[][] output = new byte[BLOCKSIZE][ BLOCKSIZE];

        for (int x = 0 ; x < BLOCKSIZE ; ++x) {
            for (int y = 0; y < BLOCKSIZE; ++y) {
                double sum = 0;
                for (int u = 0; u < BLOCKSIZE; ++u) {
                    for (int v = 0; v < BLOCKSIZE; ++v) {
                        double F = input[u][v];
                        sum += ( u == 0 ? a : 1) * ( v == 0 ? a : 1) * F *
                                Math.cos((2 * x + 1) * u * Math.PI / 16) * Math.cos((2 * y + 1) * v * Math.PI / 16);
                    }
                }
                // Assign a single pixel value to output array (1 channel)
                output[x][y] += restrictByte(Math.round((0.25 * sum)));
            }
        }
        return output;
    }


    // Add Iframe values to decoded Pframe values
    public byte[][] diffDecode (byte[][] input, int blockNo, byte[] source, Pair<Integer, Integer> moV) {
        int arrayWidth = source == Y ? width : cwidth;
        int offset = blockToIndex(blockNo, arrayWidth);

        // Determine I-frame source array
        byte[] iSource = null;
        if (source == Y) {
            iSource = iFrame.Y;
        } else {
            if (source == Cb) {
                iSource = iFrame.Cb;
            } else if (source == Cr) {
                iSource = iFrame.Cr;
            }
            // Reduce motion-vector by half for Cb, Cr blocks
            moV = new Pair<>(moV.first/2, moV.second/2);
        }

        // Pull Iframe data
        byte[][] output = getBlock(blockNo, arrayWidth, iSource, moV);

        for(int x = 0 ; x < BLOCKSIZE ; ++x) {
            for(int y = 0 ; y < BLOCKSIZE ; ++y) {
                // Add Iframe to Pframe data, but restrict values from -128 to +127
                output[x][y] = restrictByte(output[x][y] + input[x][y]);
            }
        }

        // Display motion-vectors for Dennis
        if (showVectors && source == Y && (Math.abs(moV.first) >= 1 || Math.abs(moV.second) >= 1)) {
            output[3][3] = 127;
            output[3][4] = 127;
            output[4][3] = 127;
            output[4][4] = 127;
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
            IObytes += i;
        } catch (IOException e) {
            Log.d("IOerror", "writeBlock");
        }
    }


    private void writeMoV(Pair<Integer, Integer> moV) {
        try {
            // Write motion-vector
            fOut.write(moV.first);
            fOut.write(moV.second);
            IObytes += 2;
        } catch (Exception e) {
            Log.d("IOerror", "write motion vector");
        }
    }


    // Write width and height to file
    private void writeDimensions() {
        try {
            // Little endian, but I dare you to say that to his face
            fOut.write(width);
            fOut.write(width >> 8);
            fOut.write(height);
            fOut.write(height >> 8);
        } catch (IOException e) {
            Log.d("Oh no", "writeDimensions()");
        }
        IObytes += 4;
    }


    // read width and height from file
    private void readDimensions() {
        try {
            // Little endian
            int first = fIn.read();
            width = fIn.read() << 8 | first;
            first = fIn.read();
            height = fIn.read() << 8 | first;
        } catch (IOException e) {
            Log.d("Oh no", "readDimensions()");
        }
        IObytes += 4;
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

    public Pair<Integer, Integer> readMotionVector() {
        Pair<Integer, Integer> p = null;
        try {
            int first = fIn.read();
            int second = fIn.read();
            if (first == -1 || second == -1) {
                throw new IOException();
            }
            p = new Pair<Integer, Integer>((int)((byte)first), (int)((byte)second));
        } catch (IOException e) {
            Log.d("IOException", "readMotionVector()");
        }
        return p;
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
