package com.usbthermalprinter

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.Arguments

import com.usbthermalprinter.adapter.PrinterAdapter
import com.usbthermalprinter.adapter.USBPrinterAdapter
import com.usbthermalprinter.adapter.USBPrinterDeviceId

// import java.lang.Exception

class UsbThermalPrinterModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private val context = reactContext

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "UsbThermalPrinter"
  }

  @ReactMethod
  fun getDeviceList(promise: Promise) {
    try{
        val adapter: PrinterAdapter = USBPrinterAdapter()
        val pairedDeviceList:WritableArray = Arguments.createArray()

        val printerDevices = adapter.getDeviceList()

        if (printerDevices?.size!! > 0) {
          for (printerDevice in printerDevices) {
            pairedDeviceList.pushMap(printerDevice.toRNWritableMap())
          }
        }

        promise.resolve(pairedDeviceList)
    } catch (e: Exception) {
        promise.reject(e.message)
    }
  }

  @ReactMethod
  fun RawData(base64Data: String, id: Double,  promise: Promise) {
    // adapter?.printRawData(base64Data, promise)
    try {
       val adapter: PrinterAdapter = USBPrinterAdapter()
       adapter.init(id.toInt(), context)

        adapter.open()
        adapter.printRawData(base64Data, promise)
        adapter.close()
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun printImageURL(imageUrl: String, imageWidth: Double, imageHeight: Double, id:Double, promise: Promise) {
    // adapter?.printImageData(imageUrl, imageWidth.toInt(), imageHeight.toInt(), promise)
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      adapter.open()
      adapter.printImageData(imageUrl, imageWidth.toInt(), imageHeight.toInt(), promise)
      adapter.close()
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun printImageBase64(base64: String, imageWidth: Double, imageHeight: Double, id:Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      val decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
      val decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

      adapter.open()
      adapter.printImageBase64(decodedByte, imageWidth.toInt(), imageHeight.toInt(), promise)
      adapter.close()
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun printCut(tailingLine: Boolean, beep: Boolean, id:Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      adapter.open()
      adapter.printCut(tailingLine, beep, promise)
      adapter.close()
    } catch (e: Exception) {
      promise.reject(e)
    }
  }
}
