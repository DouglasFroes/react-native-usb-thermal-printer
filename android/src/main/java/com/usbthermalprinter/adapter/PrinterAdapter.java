package com.usbthermalprinter.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.telecom.Call;

import java.util.List;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;

import java.io.IOException;

public interface PrinterAdapter {

    public void init(int productID, ReactApplicationContext ctx);

    public List<PrinterDevice> getDeviceList() throws IOException;

    public void open() throws IOException;

    public void close() throws IOException;

    public void printRawData(String rawBase64Data, Promise promise) throws IOException;

    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Promise promise) throws IOException;

    public void printImageBase64(Bitmap imageUrl, int imageWidth, int imageHeight, Promise promise) throws IOException;

    public void printCut(boolean tailingLine, boolean beep, Promise promise) throws IOException;
}
