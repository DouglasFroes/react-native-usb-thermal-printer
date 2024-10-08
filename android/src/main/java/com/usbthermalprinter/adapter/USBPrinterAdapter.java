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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import android.graphics.Matrix;

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

        UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

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
          // int b = mUsbDeviceConnection.controlTransfer(0x40, 0x03, 0x4138, 0, command, command.length, 1000);

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

    @Override
    public void qrCode(String text, int size,  Promise promise) throws IOException {
        try {
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, CENTER_ALIGN, 0, CENTER_ALIGN.length, 1000);
            // Set QR code size (GS ( k)
            byte[] setSize = new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte) size};
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, setSize, 0, setSize.length, 1000);

            // Store QR code data (GS ( k)
            byte[] storeDataHeader = new byte[]{0x1D, 0x28, 0x6B, (byte) (text.length() + 3), 0x00, 0x31, 0x50, 0x30};
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, storeDataHeader, 0, storeDataHeader.length, 1000);
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, text.getBytes(), 0, text.length(), 1000);

            // Print the QR code (GS ( k)
            byte[] printQrCode = new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30};
            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, printQrCode, 0, printQrCode.length, 1000);

            mUsbDeviceConnection.bulkTransfer(usbEndpointOut, SET_LINE_SPACE_32, 0, SET_LINE_SPACE_32.length, 1000);
            int b = mUsbDeviceConnection.bulkTransfer(usbEndpointOut, LINE_FEED, 0, LINE_FEED.length, 1000);

            if (b < 0) {
                String msg = "failed to print qr code";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print qr code");
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    @Override
    public void barCode(String text, int width, int height, Promise promise) throws IOException {
      try {
          Bitmap barcodeBitmap = generateBarcodeBitmap(text);

          // 2. Rotacionar o Bitmap
          Bitmap rotatedBitmap = rotateBitmap(barcodeBitmap);

          // 3. Converter o Bitmap rotacionado em bytes
          byte[] imageBytes = convertBitmapToBytes(rotatedBitmap);

          // 4. Enviar os bytes para a impressora
          int[][] pixels = getPixelsSlow(rotatedBitmap, width, height);

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
              String msg = "failed to print barcode";
              Log.v(LOG_TAG, msg);
              promise.reject(msg);
          } else {
              promise.resolve("success to print barcode");
          }
      } catch (Exception e) {
          e.printStackTrace();
          promise.reject(e.getMessage());
          throw new IOException(e);
      }
    }

    private Bitmap generateBarcodeBitmap(String text) throws Exception {
        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.CODE_128, 600, 300);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        return bitmap;
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);  // Rotaciona 90 graus no sentido horário
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public byte[] convertBitmapToBytes(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        byte[] imageBytes = new byte[width * height];
        int k = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;

                // Converta para escala de cinza
                int gray = (red + green + blue) / 3;
                imageBytes[k++] = (byte) (gray < 128 ? 1 : 0);  // Definir como 1 para preto e 0 para branco
            }
        }

        return imageBytes;
    }
}
