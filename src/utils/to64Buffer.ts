import * as EPToolkit from './EPToolkit';
import type { PrinterOptions } from './types';

export const textTo64Buffer = (text: string, opts: PrinterOptions) => {
  const defaultOptions = {
    beep: false,
    cut: false,
    tailingLine: false,
    encoding: 'UTF8',
  };

  const options = {
    ...defaultOptions,
    ...opts,
  };

  const fixAndroid = '\n';
  const buffer = EPToolkit.exchange_text(text + fixAndroid, options);
  return buffer.toString('base64');
};

export const billTo64Buffer = (text: string, opts: PrinterOptions) => {
  const defaultOptions = {
    beep: true,
    cut: true,
    encoding: 'UTF8',
    tailingLine: true,
  };
  const options = {
    ...defaultOptions,
    ...opts,
  };
  const buffer = EPToolkit.exchange_text(text, options);
  return buffer.toString('base64');
};
