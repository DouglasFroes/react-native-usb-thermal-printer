package com.usbthermalprinter.adapter;

import static com.usbthermalprinter.adapter.UtilsImage.getPixelsSlow;
import static com.usbthermalprinter.adapter.UtilsImage.recollectSlice;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import android.util.Base64;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.Promise;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class USBPrinterAdapter implements PrinterAdapter {
    private final String LOG_TAG = "USBPrinterDSF";

    private static final String ACTION_USB_PERMISSION = "com.usbprinter.USB_PERMISSION";

    private PendingIntent mPermissionIntent;
    private UsbManager mUSBManager;

    private Context mContext;
    private int productID;

    private UsbDevice mUsbDevice;
    private UsbInterface mUsbInterface;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbEndpoint usbEndpointOut = null;
    private UsbEndpoint usbEndpointIn = null;

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33};
    private final static byte[] SET_LINE_SPACE_24 = new byte[]{ESC_CHAR, 0x33, 24};
    private final static byte[] SET_LINE_SPACE_32 = new byte[]{ESC_CHAR, 0x33, 32};
    private final static byte[] LINE_FEED = new byte[]{0x0A};
    private static final byte[] CENTER_ALIGN = {0x1B, 0X61, 0X31};

    @Override
    public void init(int productID, ReactApplicationContext ctx) {
        this.productID = productID;
        this.mContext = ctx;

        this.mUSBManager = (UsbManager) this.mContext.getSystemService(Context.USB_SERVICE);
        this.mPermissionIntent = PendingIntent.getBroadcast(
                this.mContext,
                0,
                new Intent(ACTION_USB_PERMISSION),
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0
        );

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.mContext.registerReceiver(mUsbDeviceReceiver, filter);
    }

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(LOG_TAG, "action: " + action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                       sendEvent("USB permission granted");
                    } else {
                        sendEvent("USB permission denied");
                    }
                }
            }
        }
    };

    @Override
    public List<PrinterDevice> getDeviceList() throws IOException{
        List<PrinterDevice> lists = new ArrayList<>();
        // if (mUSBManager == null) {
        //     this.sendEvent("USBManager is not initialized while get device list");
        //     return lists;
        // }
        ReactApplicationContext ctx = (ReactApplicationContext) mContext;

          UsbManager manager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);

        for (UsbDevice usbDevice : manager.getDeviceList().values()) {
            lists.add(new USBPrinterDevice(usbDevice));
        }
        return lists;
    }

    @Override
    public Boolean open() throws IOException {
      Boolean connect = false;

      try {
            for (UsbDevice device : mUSBManager.getDeviceList().values()) {
                if (device.getProductId() == this.productID) {
                    mUsbDevice = device;
                    mUSBManager.requestPermission(mUsbDevice, mPermissionIntent);
                    mUsbDeviceConnection = mUSBManager.openDevice(mUsbDevice);
                    UsbInterface mUsbInterface = findHidInterface();

                    mUsbDeviceConnection.claimInterface(mUsbInterface, true);

                    Log.w(LOG_TAG,  String.valueOf(mUsbInterface.getEndpointCount()));

                    for (int i = 0; i < mUsbInterface.getEndpointCount(); i++) {
                        UsbEndpoint usbEndpoint = mUsbInterface.getEndpoint(i);
                        Log.w(LOG_TAG, String.valueOf(usbEndpoint.getType()));
                        Log.w(LOG_TAG, String.valueOf(usbEndpoint.getDirection()));
                        Log.w(LOG_TAG, String.valueOf(usbEndpoint.getType()));

                        if (usbEndpoint.getType() == UsbConstants.USB_CLASS_COMM) {
                            if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                                usbEndpointOut = usbEndpoint;
                                connect = true;
                            } else {
                                usbEndpointIn = usbEndpoint;
                            }
                        }
                    }

                    mUSBManager.requestPermission(mUsbDevice, mPermissionIntent);  // Valida a permissão de impressão na Intent

                    break;
                }
            }

            return connect;
      } catch (Exception e) {
          e.printStackTrace();
          throw new IOException(e);
      }
   }

    @Override
    public void close() throws IOException {
        mUsbDeviceConnection.close();
        mContext.unregisterReceiver(mUsbDeviceReceiver);
    }

    @Override
    public void clean(Promise promise) throws IOException {
        mUsbDeviceConnection.controlTransfer(0x21, 0x22, 0, 0, new byte[0], 0, 1000);
        mUsbDeviceConnection.releaseInterface(mUsbInterface);
        mUsbDeviceConnection.close();
        ((ReactApplicationContext) mContext).unregisterReceiver(mUsbDeviceReceiver);

        promise.resolve("success to clean");
    }

    @Override
    public void printRawData(String rawData, Promise promise)  throws IOException  {
        Log.w(LOG_TAG, "start to print raw data");
        try{
          byte[] command = Base64.decode(rawData, Base64.DEFAULT);
          int b = mUsbDeviceConnection.bulkTransfer(usbEndpointOut, command, 0, command.length, 1000);

          if (b < 0) {
              String msg = "failed to print raw data - " + b;
              Log.v(LOG_TAG, msg);
              promise.reject(msg);
              sendEvent(msg);
          } else {
              promise.resolve("success to print raw data - " + b);
          }
        } catch (Exception e) {
          e.printStackTrace();
          sendEvent(e.getMessage());
          promise.reject(e.getMessage());
         throw new IOException(e);
        }
    }

    public static Bitmap getBitmapFromURL(String src) throws IOException  {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            myBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    @Override
    public void printImageData(final String imageUrl, int imageWidth, int imageHeight, Promise promise)  throws IOException {
      try{
        final Bitmap bitmapImage = getBitmapFromURL(imageUrl);

        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        Log.w(LOG_TAG, "start to print image data");

        int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

        mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SET_LINE_SPACE_24, 0, SET_LINE_SPACE_24.length, 1000);
        mUsbDeviceConnection.bulkTransfer(usbEndpointOut, CENTER_ALIGN, 0, CENTER_ALIGN.length, 1000);

        for (int y = 0; y < pixels.length; y += 24) {
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SELECT_BIT_IMAGE_MODE, 0, SELECT_BIT_IMAGE_MODE.length, 1000);

            byte[] row = new byte[]{(byte) (0x00ff & pixels[y].length)
                    , (byte) ((0xff00 & pixels[y].length) >> 8)};

            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, row, 0, row.length, 1000);

            for (int x = 0; x < pixels[y].length; x++) {
                byte[] slice = recollectSlice(y, x, pixels);
                mUsbDeviceConnection.bulkTransfer(usbEndpointOut, slice, 0, slice.length, 1000);
            }

            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, LINE_FEED, 0, LINE_FEED.length, 1000);
        }

        mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SET_LINE_SPACE_32, 0, SET_LINE_SPACE_32.length, 1000);
        int b = mUsbDeviceConnection.bulkTransfer(usbEndpointOut, LINE_FEED, 0, LINE_FEED.length, 1000);

        if (b < 0) {
            String msg = "failed to print image data";
            Log.w(LOG_TAG, msg);
            promise.reject(msg);
        } else {
            promise.resolve("success to print image data");
        }
      } catch (Exception e) {
        e.printStackTrace();
        sendEvent(e.getMessage());
        promise.reject(e.getMessage());
       throw new IOException(e);
      }
    }

    @Override
    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, Promise promise) throws IOException {
        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }
        try {
            Log.v(LOG_TAG, "Connected to device");
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SET_LINE_SPACE_24, 0, SET_LINE_SPACE_24.length, 1000);
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, CENTER_ALIGN, 0, CENTER_ALIGN.length, 1000);

            for (int y = 0; y < pixels.length; y += 24) {
                mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SELECT_BIT_IMAGE_MODE, 0, SELECT_BIT_IMAGE_MODE.length, 1000);

                byte[] row = new byte[]{(byte) (0x00ff & pixels[y].length)
                        , (byte) ((0xff00 & pixels[y].length) >> 8)};

                mUsbDeviceConnection.bulkTransfer(usbEndpointOut, row, 0, row.length, 1000);

                for (int x = 0; x < pixels[y].length; x++) {
                    byte[] slice = recollectSlice(y, x, pixels);
                    mUsbDeviceConnection.bulkTransfer(usbEndpointOut, slice, 0, slice.length, 1000);
                }

                mUsbDeviceConnection.bulkTransfer(usbEndpointOut, LINE_FEED, 0, LINE_FEED.length, 1000);
            }

            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SET_LINE_SPACE_32, 0, SET_LINE_SPACE_32.length, 1000);
            int b = mUsbDeviceConnection.bulkTransfer(usbEndpointOut, LINE_FEED, 0, LINE_FEED.length, 1000);

            if (b < 0) {
                String msg = "failed to print image data";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print image data");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendEvent(e.getMessage());
            promise.reject(e.getMessage());
           throw new IOException(e);
        }
    }

    @Override
    public void printCut(boolean tailingLine, boolean beep, Promise promise)  throws IOException{
        try {

            if (tailingLine) {
              byte[] cmd = new byte[]{0x1b,0x33,24};
              int b = mUsbDeviceConnection.bulkTransfer(usbEndpointOut, cmd, 0, cmd.length, 1000);
            }

            byte[] command = new byte[]{0x1D, 0x56, 0x00};
            int b = mUsbDeviceConnection.bulkTransfer(usbEndpointOut, command, 0, command.length, 1000);

            if (beep) {
              byte[] cmd = new byte[]{0x1b,0x42,0x05,0x09};
              mUsbDeviceConnection.bulkTransfer(usbEndpointOut, cmd, 0, cmd.length, 1000);
            }

            if (b < 0) {
                String msg = "failed to print raw data";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print raw data");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendEvent(e.getMessage());
            promise.reject(e.getMessage());
           throw new IOException(e);
        }
    }

    private void sendEvent(String msg) {
      if (mContext != null) {
          ((ReactApplicationContext) mContext)
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("com.usbprinter", msg);
      }
    }

    private UsbInterface findHidInterface() {
        if (mUsbDevice != null) {
            final int interfaceCount = mUsbDevice.getInterfaceCount();

            for (int interfaceIndex = 0; interfaceIndex < interfaceCount; interfaceIndex++) {
                UsbInterface usbInterface = mUsbDevice.getInterface(interfaceIndex);
                return usbInterface;
            }
            Log.w(LOG_TAG, "HID interface not found.");
        }
        return null;
    }
}
