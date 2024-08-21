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
import android.widget.Toast;
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
    @SuppressLint("StaticFieldLeak")
    private static USBPrinterAdapter mInstance;


    private final String LOG_TAG = "RNUSBPrinter";
    private Context mContext;
    private UsbManager mUSBManager;
    private PendingIntent mPermissionIndent;
    private UsbDevice mUsbDevice;
    private UsbDeviceConnection mUsbDeviceConnection;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mEndPoint;
    private static final String ACTION_USB_PERMISSION = "com.usbprinter.USB_PERMISSION";
    private static final String EVENT_USB_DEVICE_ATTACHED = "usbAttached";

    private final static char ESC_CHAR = 0x1B;
    private static final byte[] SELECT_BIT_IMAGE_MODE = {0x1B, 0x2A, 33};
    private final static byte[] SET_LINE_SPACE_24 = new byte[]{ESC_CHAR, 0x33, 24};
    private final static byte[] SET_LINE_SPACE_32 = new byte[]{ESC_CHAR, 0x33, 32};
    private final static byte[] LINE_FEED = new byte[]{0x0A};
    private static final byte[] CENTER_ALIGN = {0x1B, 0X61, 0X31};

    private USBPrinterAdapter() {
    }

    public static USBPrinterAdapter getInstance() {
        if (mInstance == null) {
            mInstance = new USBPrinterAdapter();
        }
        return mInstance;
    }

    private final BroadcastReceiver mUsbDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        assert usbDevice != null;
                        Log.i(LOG_TAG, "success to grant permission for device " + usbDevice.getDeviceId() + ", vendor_id: " + usbDevice.getVendorId() + " product_id: " + usbDevice.getProductId());
                        mUsbDevice = usbDevice;
                    } else {
                        assert usbDevice != null;
                        Toast.makeText(context, "User refuses to obtain USB device permissions" + usbDevice.getDeviceName(), Toast.LENGTH_LONG).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (mUsbDevice != null) {
                    Toast.makeText(context, "USB device has been turned off", Toast.LENGTH_LONG).show();
                    closeConnectionIfExists();
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action) || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    if (mContext != null) {
                        ((ReactApplicationContext) mContext).getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit(EVENT_USB_DEVICE_ATTACHED, null);
                    }
                }
            }
        }
    };

    @SuppressLint("UnspecifiedImmutableFlag")
    public void init(ReactApplicationContext reactContext) {
        this.mContext = reactContext;
        this.mUSBManager = (UsbManager) this.mContext.getSystemService(Context.USB_SERVICE);

        this.mPermissionIndent = PendingIntent.getBroadcast(
                this.mContext,
                0,
                new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE | 0
        );

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        this.mContext.registerReceiver(mUsbDeviceReceiver, filter);
        IntentFilter filter2 = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);

        this.mContext.registerReceiver(mUsbDeviceReceiver, filter2);
        IntentFilter filter3 = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);

        this.mContext.registerReceiver(mUsbDeviceReceiver, filter3);
        IntentFilter filter4 = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        this.mContext.registerReceiver(mUsbDeviceReceiver, filter4);

        Log.i(LOG_TAG, "USB Printer Adapter initialized");
    }


    public void closeConnectionIfExists() {
        if (mUsbDeviceConnection != null) {
            mUsbDeviceConnection.releaseInterface(mUsbInterface);
            mUsbDeviceConnection.close();
            mUsbInterface = null;
            mEndPoint = null;
            mUsbDeviceConnection = null;
        }
    }

    public List<PrinterDevice> getDeviceList() {
        List<PrinterDevice> lists = new ArrayList<>();
        if (mUSBManager == null) {
            this.sendEvent("USBManager is not initialized while get device list");
            return lists;
        }

        for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
            lists.add(new USBPrinterDevice(usbDevice));
        }
        return lists;
    }

    @Override
    public String selectDevice(PrinterDeviceId printerDeviceId) {
        if (printerDeviceId == null) {
            return "failed to select device, device id is null";
        }
        if (mUSBManager == null) {
            return "failed to select device, USBManager is not initialized";
        }


        USBPrinterDeviceId usbPrinterDeviceId = (USBPrinterDeviceId) printerDeviceId;

        closeConnectionIfExists();

        if (mUSBManager.getDeviceList().size() == 0) {
            return "No device found";
        }

        boolean deviceFound = false;

        for (UsbDevice usbDevice : mUSBManager.getDeviceList().values()) {
            if (
              usbDevice.getVendorId() == usbPrinterDeviceId.getVendorId() &&
              usbDevice.getProductId() == usbPrinterDeviceId.getProductId()
              ) {
                mUSBManager.requestPermission(usbDevice, mPermissionIndent);
                deviceFound = true;
            }
        }

        if (!deviceFound) {
            return "failed to find device with vendor_id: " + usbPrinterDeviceId.getVendorId() + " product_id: " + usbPrinterDeviceId.getProductId();
        }

        return "success to select device with vendor_id: " + usbPrinterDeviceId.getVendorId() + " product_id: " + usbPrinterDeviceId.getProductId();
   }


    private boolean openConnection() {
        if (mUsbDevice == null) {
            return false;
        }

        if (mUsbDeviceConnection != null) {
          return true;
        }

        UsbInterface usbInterface = null;
        UsbEndpoint endPoint = null;

        for (int i = 0; i < mUsbDevice.getInterfaceCount(); i++) {
            UsbInterface tempInterface = mUsbDevice.getInterface(i);
            if (tempInterface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                usbInterface = tempInterface;
                break;
            }
        }

        if (usbInterface == null) {
            return false;
        }

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint tempEndPoint = usbInterface.getEndpoint(i);
            if (tempEndPoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                endPoint = tempEndPoint;
                break;
            }
        }

        if (endPoint == null) {
            return false;
        }

        UsbDeviceConnection connection = mUSBManager.openDevice(mUsbDevice);
        if (connection == null) {
            return false;
        }

        if (connection.claimInterface(usbInterface, true)) {
            mUsbDeviceConnection = connection;
            mUsbInterface = usbInterface;
            mEndPoint = endPoint;
            return true;
        } else {
            connection.close();
            return false;
        }
    }


    public void printRawData(String rawData, Promise promise) {
        Log.v(LOG_TAG, "start to print raw data " + rawData);

        boolean isConnected = openConnection();

        if (isConnected) {
            byte[] bytes = Base64.decode(rawData, Base64.DEFAULT);
            int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, bytes, bytes.length, 0);

            if (b < 0) {
                String msg = "failed to print raw data";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print raw data");
            }
        } else {
            String msg = "failed to connected to device";
            Log.v(LOG_TAG, msg);
            promise.reject(msg);
            sendEvent(msg);
        }
    }

    public static Bitmap getBitmapFromURL(String src) {
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
    public void printImageData(final String imageUrl, int imageWidth, int imageHeight, Promise promise) {
        final Bitmap bitmapImage = getBitmapFromURL(imageUrl);

        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        Log.v(LOG_TAG, "start to print image data " + bitmapImage);
        boolean isConnected = openConnection();

        if (isConnected) {
            Log.v(LOG_TAG, "Connected to device");
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_24, SET_LINE_SPACE_24.length, 0);
            mUsbDeviceConnection.bulkTransfer(mEndPoint, CENTER_ALIGN, CENTER_ALIGN.length, 0);

            for (int y = 0; y < pixels.length; y += 24) {
                mUsbDeviceConnection.bulkTransfer(mEndPoint, SELECT_BIT_IMAGE_MODE, SELECT_BIT_IMAGE_MODE.length, 0);

                byte[] row = new byte[]{(byte) (0x00ff & pixels[y].length)
                        , (byte) ((0xff00 & pixels[y].length) >> 8)};

                mUsbDeviceConnection.bulkTransfer(mEndPoint, row, row.length, 0);

                for (int x = 0; x < pixels[y].length; x++) {
                    byte[] slice = recollectSlice(y, x, pixels);
                    mUsbDeviceConnection.bulkTransfer(mEndPoint, slice, slice.length, 0);
                }

                mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 0);
            }

            mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_32, SET_LINE_SPACE_32.length, 0);
            int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 0);

            if (b < 0) {
                String msg = "failed to print image data";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print image data");
            }
        } else {
            String msg = "failed to connected to device";
            Log.v(LOG_TAG, msg);
            this.sendEvent(msg);
            promise.reject(msg);
        }
    }

    @Override
    public void printImageBase64(final Bitmap bitmapImage, int imageWidth, int imageHeight, Promise promise) {
        if (bitmapImage == null) {
            promise.reject("image not found");
            return;
        }

        Log.v(LOG_TAG, "start to print image data " + bitmapImage);
        boolean isConnected = openConnection();

        if (isConnected) {
            Log.v(LOG_TAG, "Connected to device");
            int[][] pixels = getPixelsSlow(bitmapImage, imageWidth, imageHeight);

            mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_24, SET_LINE_SPACE_24.length, 0);
            mUsbDeviceConnection.bulkTransfer(mEndPoint, CENTER_ALIGN, CENTER_ALIGN.length, 0);

            for (int y = 0; y < pixels.length; y += 24) {
                mUsbDeviceConnection.bulkTransfer(mEndPoint, SELECT_BIT_IMAGE_MODE, SELECT_BIT_IMAGE_MODE.length, 0);

                byte[] row = new byte[]{(byte) (0x00ff & pixels[y].length)
                        , (byte) ((0xff00 & pixels[y].length) >> 8)};

                mUsbDeviceConnection.bulkTransfer(mEndPoint, row, row.length, 0);

                for (int x = 0; x < pixels[y].length; x++) {
                    byte[] slice = recollectSlice(y, x, pixels);
                    mUsbDeviceConnection.bulkTransfer(mEndPoint, slice, slice.length, 0);
                }

                mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 0);
            }

            mUsbDeviceConnection.bulkTransfer(mEndPoint, SET_LINE_SPACE_32, SET_LINE_SPACE_32.length, 0);
            int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, LINE_FEED, LINE_FEED.length, 0);

            if (b < 0) {
                String msg = "failed to print image data";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print image data");
            }
        } else {
            String msg = "failed to connected to device";
            Log.v(LOG_TAG, msg);
            this.sendEvent(msg);
            promise.reject(msg);
        }
    }

    public void printCut(boolean tailingLine, boolean beep, Promise promise) {
        boolean isConnected = openConnection();

        if (isConnected) {
            byte[] bytes = new byte[]{0x1D, 0x56, 0x42, 0x00};

            if (tailingLine) {
                bytes[3] = 0x01;
            }
            if (beep) {
                bytes[3] = 0x03;
            }

            int b = mUsbDeviceConnection.bulkTransfer(mEndPoint, bytes, bytes.length, 0);

            if (b < 0) {
                String msg = "failed to print raw data";
                Log.v(LOG_TAG, msg);
                promise.reject(msg);
            } else {
                promise.resolve("success to print raw data");
            }
        } else {
            String msg = "failed to connected to device";
            Log.v(LOG_TAG, msg);
            sendEvent(msg);
            promise.reject(msg);
        }
    }

    private void sendEvent(String msg) {
      if (mContext != null) {
          ((ReactApplicationContext) mContext)
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("com.usbprinter", msg);
      }
    }
}
