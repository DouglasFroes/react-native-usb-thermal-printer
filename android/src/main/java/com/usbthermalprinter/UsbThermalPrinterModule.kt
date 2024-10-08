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

  override fun getName(): String {
    return NAME
  }

  companion object {
    const val NAME = "UsbThermalPrinter"
  }

  @ReactMethod
  fun getDeviceList(promise: Promise) {
    try{
        val pairedDeviceList:WritableArray = Arguments.createArray()

        val adapter: PrinterAdapter = USBPrinterAdapter()
        adapter.init(0, context)

        val printerDevices = adapter.getDeviceList()

        if (printerDevices?.size!! > 0) {
          for (printerDevice in printerDevices) {
            pairedDeviceList.pushMap(printerDevice.toRNWritableMap())
          }
        }

        promise.resolve(pairedDeviceList)
    } catch (e: Exception) {
        promise.reject("Erro ao buscar impressoras", e)
    }
  }

  @ReactMethod
  fun RawData(base64Data: String, id: Double,  promise: Promise) {
    try {
        val adapter: PrinterAdapter = USBPrinterAdapter()
        adapter.init(id.toInt(), context)

        val connect: Boolean=  adapter.open()

        if(!connect){
           promise.reject("Não foi possível se conectar com a impressora!")
           adapter.close()
           return
        }

        adapter.printRawData(base64Data, promise)
    } catch (e: Exception) {
      promise.reject("Erro ao imprimir", e)
    }
  }

  @ReactMethod
  fun printImageURL(imageUrl: String, imageWidth: Double, imageHeight: Double, id:Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      val connect: Boolean=  adapter.open()

      if(!connect){
          promise.reject("Não foi possível se conectar com a impressora!")
          adapter.close()
          return
      }

      adapter.printImageData(imageUrl, imageWidth.toInt(), imageHeight.toInt(), promise)
      adapter.close()
    } catch (e: Exception) {
       promise.reject("Erro ao imprimir", e)
    }
  }

  @ReactMethod
  fun printImageBase64(base64: String, imageWidth: Double, imageHeight: Double, id:Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      val decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
      val decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

      val connect: Boolean=  adapter.open()

      if(!connect){
          promise.reject("Não foi possível se conectar com a impressora!")
          adapter.close()
          return
      }

      adapter.printImageBase64(decodedByte, imageWidth.toInt(), imageHeight.toInt(), promise)
      adapter.close()
    } catch (e: Exception) {
      promise.reject("Erro ao imprimir", e)
    }
  }

  @ReactMethod
  fun printCut(tailingLine: Boolean, beep: Boolean, id:Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      val connect: Boolean=  adapter.open()

      if(!connect){
          promise.reject("Não foi possível se conectar com a impressora!")
          adapter.close()
          return
      }

      adapter.printCut(tailingLine, beep, promise)
      adapter.close()
    } catch (e: Exception) {
      promise.reject("Erro ao imprimir", e)
    }
  }

  @ReactMethod
  fun barCode(text: String,  width: Double, height: Double, id: Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      val connect: Boolean=  adapter.open()

      if(!connect){
          promise.reject("Não foi possível se conectar com a impressora!")
          adapter.close()
          return
      }

      adapter.barCode(text, width.toInt(), height.toInt(), promise)
      adapter.close()
    } catch (e: Exception) {
       promise.reject("Erro ao imprimir", e)
    }
  }

  @ReactMethod
  fun qrCode(text: String, size: Double, id:Double, promise: Promise) {
    try {
      val adapter: PrinterAdapter = USBPrinterAdapter()
      adapter.init(id.toInt(), context)

      val connect: Boolean=  adapter.open()

      if(!connect){
          promise.reject("Não foi possível se conectar com a impressora!")
          adapter.close()
          return
      }

      adapter.qrCode(text, size.toInt(), promise)
      adapter.close()
    } catch (e: Exception) {
       promise.reject("Erro ao imprimir", e)
    }
  }

  @ReactMethod
  fun clean(id: Double,  promise: Promise) {
    try {
        val adapter: PrinterAdapter = USBPrinterAdapter()
        adapter.init(id.toInt(), context)

        adapter.open()

        adapter.clean(promise)
    } catch (e: Exception) {
      promise.reject("Erro ao imprimir", e)
    }
  }
}
