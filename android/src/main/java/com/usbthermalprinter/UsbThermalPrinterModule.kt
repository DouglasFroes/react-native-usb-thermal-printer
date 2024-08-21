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

class UsbThermalPrinterModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private val context = reactContext
  private var adapter: PrinterAdapter? = null

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "UsbThermalPrinter"
  }

  // @ReactMethod
  // fun multiply(a: Double, b: Double, promise: Promise) {
  //   promise.resolve(a * b)
  // }

  @ReactMethod
  fun init() {
      adapter = USBPrinterAdapter.getInstance();
      adapter?.init(context)
  }

  @ReactMethod
  fun getDeviceList(promise: Promise) {
    val printerDevices = adapter?.getDeviceList()
    val pairedDeviceList:WritableArray = Arguments.createArray()

    if (printerDevices?.size!! > 0) {
      for (printerDevice in printerDevices) {
        pairedDeviceList.pushMap(printerDevice.toRNWritableMap())
      }
    }

    promise.resolve(pairedDeviceList)
  }

  @ReactMethod
  fun connect(vendorId: Double, productId: Double, promise: Promise) {
    val result= adapter?.selectDevice(
        USBPrinterDeviceId.valueOf(vendorId.toInt(), productId.toInt())
      )

    promise.resolve(result ?: "No device found")
  }

  @ReactMethod
  fun RawData(base64Data: String, promise: Promise) {
    adapter?.printRawData(base64Data, promise)
  }

  @ReactMethod
  fun printImageURL(imageUrl: String, imageWidth: Double, imageHeight: Double, promise: Promise) {
    adapter?.printImageData(imageUrl, imageWidth.toInt(), imageHeight.toInt(), promise)
  }

  @ReactMethod
  fun printImageBase64(base64: String, imageWidth: Double, imageHeight: Double, promise: Promise) {
    val decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    val decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    adapter?.printImageBase64(decodedByte, imageWidth.toInt(), imageHeight.toInt(), promise)
  }

  @ReactMethod
  fun printCut(tailingLine: Boolean, beep: Boolean, promise: Promise) {
    adapter?.printCut(tailingLine, beep, promise)
  }
}
