export interface IPrinter {
  device_name: string;
  vendor_id: string;
  product_id: string;
  manufacturer_name: string;
  product_name: string;
  device_id: string;
}

export interface PrinterOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  encoding?: string;
}

export interface PrinterImageOptions {
  beep?: boolean;
  cut?: boolean;
  tailingLine?: boolean;
  imageWidth?: number;
  imageHeight?: number;
}
