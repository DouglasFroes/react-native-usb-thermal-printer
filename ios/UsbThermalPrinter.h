
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNUsbThermalPrinterSpec.h"

@interface UsbThermalPrinter : NSObject <NativeUsbThermalPrinterSpec>
#else
#import <React/RCTBridgeModule.h>

@interface UsbThermalPrinter : NSObject <RCTBridgeModule>
#endif

@end
