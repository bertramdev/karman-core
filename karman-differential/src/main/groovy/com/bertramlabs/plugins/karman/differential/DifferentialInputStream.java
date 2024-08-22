package com.bertramlabs.plugins.karman.differential;

import com.bertramlabs.plugins.karman.CloudFile;
import com.bertramlabs.plugins.karman.CloudFileInterface;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class DifferentialInputStream extends InputStream {

    public ManifestData manifestData;
    private CloudFileInterface baseFile;
    private InputStream sourceManifest;
    private long totalBlocks;
    DifferentialInputStream(CloudFileInterface baseFile, InputStream sourceManifest) throws IOException {
        //lets load the header info
        this.sourceManifest = sourceManifest;
        StringBuilder headerStringB = new StringBuilder();
        int b = sourceManifest.read();
        boolean lastNewLine = false;
        while(b != -1) {
            headerStringB.append((char)b);
            if(b == 10) {
                if(lastNewLine) {
                    break;
                } else {
                    lastNewLine = true;
                }
            } else {
                lastNewLine = false;
            }
            b = sourceManifest.read();
        }
        String headerString = headerStringB.toString();
        //parse header string
        String[] headerParts = headerString.split("\n");
        ManifestData manifestData = new ManifestData();
        for(String headerPart : headerParts) {
            String[] headerPartParts = headerPart.split(":");
            if(headerPartParts[0].equalsIgnoreCase("blockSize")) {
                manifestData.blockSize = Integer.parseInt(headerPartParts[1]);
            }else if(headerPartParts[0].equalsIgnoreCase("fileSize")) {
                manifestData.fileSize = Long.parseLong(headerPartParts[1]);
            } else if(headerPartParts[0].equalsIgnoreCase("fileName")) {
                manifestData.fileName = headerPartParts[1];
            } else if(headerPartParts[0].equalsIgnoreCase("files")) {
                String[] filePaths = headerPartParts[1].split(",");
                manifestData.sourceFiles = new ArrayList<>(Arrays.asList(filePaths));
            } else if(headerPartParts[0].equalsIgnoreCase("version")) {
                manifestData.version = Integer.parseInt(headerPartParts[1]);
            }
        }
        this.baseFile = baseFile;
        this.manifestData = manifestData;
    }
    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an {@code int} in the range {@code 0} to
     * {@code 255}. If no byte is available because the end of the stream
     * has been reached, the value {@code -1} is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * <p> A subclass must provide an implementation of this method.
     *
     * @return the next byte of data, or {@code -1} if the end of the
     * stream is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
        if(currentBlockData == null) {
            loadCurrentBlock();
        }
        if(currentBlockInputStream != null) {
            int c = currentBlockInputStream.read();
            if(c == -1) {
                try {
                    currentBlockInputStream.close();
                } catch(Exception e) {
                    //ignore
                }
                currentBlockData = null;
                currentBlockInputStream = null;

                loadCurrentBlock();
                if(currentBlockInputStream != null) {
                    return currentBlockInputStream.read();
                } else {
                    return -1;
                }
            } else {
                return c;
            }
        } else {
            return -1; //we are done
        }

    }

    private ManifestData.BlockData currentBlockData = null;
    private InputStream currentBlockInputStream = null;

    private void loadCurrentBlock() throws IOException {
        currentBlockData = getNextBlockData();
        if(currentBlockData != null) {
            if(currentBlockData.zeroFilled) {
                currentBlockInputStream = new ByteArrayInputStream(new byte[currentBlockData.blockSize]);
            } else {
                XZInputStream xzInputStream = new XZInputStream(baseFile.getParent().getFile(getBlockPath(currentBlockData)).getInputStream());
                currentBlockInputStream = xzInputStream;
            }
        } else {
            currentBlockInputStream = null;
        }

    }

    private String getBlockPath(ManifestData.BlockData blockData) {
        String filePath = ManifestData.BlockData.getBlockPath(baseFile, blockData.block,blockData.fileIndex, manifestData);
        if(blockData.fileIndex > 1) {
            //we should verify it exists
            CloudFileInterface file = baseFile.getParent().getFile(filePath);
            int currentFileIndex = blockData.fileIndex;
            while (!file.exists() && currentFileIndex > 1) { //<= instead of < because we subtract 1 in getBlockPath as index 0 means no reference
                currentFileIndex--;
                filePath = ManifestData.BlockData.getBlockPath(baseFile, blockData.block,currentFileIndex, manifestData);
                file = baseFile.getParent().getFile(filePath);
            }
        }
        return filePath;
    }



    public ManifestData.BlockData getNextBlockData() throws IOException {
        ManifestData.BlockData blockData = new ManifestData.BlockData();
        byte[] blockDataBytes = new byte[ManifestData.BlockData.SIZE];
        int bytesRead = sourceManifest.read(blockDataBytes);
        if(bytesRead == -1) {
            return null;
        } else if(bytesRead < 44) {
            //we have a problem and need to hold until the data is available
            while(sourceManifest.available() <= 44-bytesRead) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            sourceManifest.read(blockDataBytes, bytesRead, 44-bytesRead);
        }
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) + (blockDataBytes[i] & 0xff);
        }
        blockData.block = value;

        int blSize=0;
        for (int i = 8; i < 12; i++) {
            blSize = (blSize << 8) + (blockDataBytes[i] & 0xff);
        }
        blockData.blockSize = blSize;
        int fileIndex=0;
        for (int i = 12; i < 16; i++) {
            fileIndex = (fileIndex << 8) + (blockDataBytes[i] & 0xff);
        }
        blockData.fileIndex= fileIndex;
        blockData.hash = Arrays.copyOfRange(blockDataBytes, 16, blockDataBytes.length);
        //check if blockData.hash is an empty byte array
        blockData.zeroFilled = true;
        for(byte b : blockData.hash) {
            if(b != 0) {
                blockData.zeroFilled = false;
                break;
            }
        }

        //System.out.println("Block Data: " + blockData.block + "hash: " + hexString + " s:" + blockData.blockSize + " f:" + blockData.fileIndex + " z" + blockData.zeroFilled);

        return blockData;
    }
}
