package com.usbthermalprinter.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.telecom.Call;

import java.util.List;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.Promise;

public interface PrinterAdapter {
    public void init(ReactApplicationContext reactContext);

    public List<PrinterDevice> getDeviceList();

    public String selectDevice(PrinterDeviceId printerDeviceId);

    public void closeConnectionIfExists();

    public void printRawData(String rawBase64Data, Promise promise);

    public void printImageData(String imageUrl, int imageWidth, int imageHeight, Promise promise);

    public void printImageBase64(Bitmap imageUrl, int imageWidth, int imageHeight, Promise promise);

    public void printCut(boolean tailingLine, boolean beep, Promise promise);
}
