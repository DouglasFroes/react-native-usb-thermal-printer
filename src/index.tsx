import { NativeModules, Platform } from 'react-native';
import type { IPrinter, PrinterImageOptions, PrinterOptions } from './utils';
import { textTo64Buffer } from './utils/to64Buffer';

const LINKING_ERROR =
  `The package 'react-native-usb-thermal-printer' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// eslint-disable-next-line prettier/prettier
const UsbThermalPrinter = NativeModules.UsbThermalPrinter ? NativeModules.UsbThermalPrinter : new Proxy({}, { get() { throw new Error(LINKING_ERROR); }, });


export function onPrintDeviceList(): Promise<IPrinter[]> {
  return UsbThermalPrinter.getDeviceList();
}

export function onPrintText(
  id: number,
  text: string,
  opts: PrinterOptions = {}
): Promise<string> {
  return UsbThermalPrinter.RawData(textTo64Buffer(text, opts), id);
}

export async function onPrintImageURL(
  id: number,
  imageUrl: string,
  opts: PrinterImageOptions = {}
): Promise<string> {
  const result = await UsbThermalPrinter.printImageURL(
    imageUrl,
    opts.imageWidth ?? 0,
    opts.imageHeight ?? 0,
    id
  );

  if (opts.cut) {
    await UsbThermalPrinter.printCut(!!opts.tailingLine, !!opts.beep, id);
  }

  return result;
}

export async function onPrintImageBase64(
  id: number,
  base64: string,
  opts: PrinterImageOptions = {}
): Promise<string> {
  const result = await UsbThermalPrinter.printImageBase64(
    base64,
    opts.imageWidth ?? 0,
    opts.imageHeight ?? 0,
    id
  );

  if (opts.cut) {
    await UsbThermalPrinter.printCut(!!opts.tailingLine, !!opts.beep, id);
  }

  return result;
}

export async function onPrintCut(
  id: number,
  line: boolean,
  beep: boolean
): Promise<string> {
  return UsbThermalPrinter.printCut(line, beep, id);
}

export async function onPrintClear(id: number): Promise<string> {
  return UsbThermalPrinter.clean(id);
}
