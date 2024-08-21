import { useState, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import {
  connect,
  getDeviceList,
  init,
  onBill,
  onText,
  printCut,
  printImageBase64,
  printImageURL,
} from 'react-native-usb-thermal-printer';

import { height, width } from './dimensions';
import { img64 } from './img64';
import { COMMANDS, type IPrinter } from '../../src/utils';

const BOLD_ON = COMMANDS.TEXT_FORMAT.TXT_BOLD_ON;
const BOLD_OFF = COMMANDS.TEXT_FORMAT.TXT_BOLD_OFF;
const CENTER = COMMANDS.TEXT_FORMAT.TXT_ALIGN_CT;
const FONT_A = COMMANDS.TEXT_FORMAT.TXT_FONT_A;
const FONT3 = COMMANDS.TEXT_FORMAT.TXT_CUSTOM_SIZE(4, 3);
const FONT1 = COMMANDS.TEXT_FORMAT.TXT_4SQUARE;
const FONT2 = COMMANDS.TEXT_FORMAT.TXT_2HEIGHT;

const textPrint = `${CENTER}${FONT1}${BOLD_ON}DSF${BOLD_OFF}\n
${CENTER}${FONT2}${BOLD_ON}TEST\n${BOLD_OFF}
${CENTER}${FONT3}26\n${FONT_A}\n
${CENTER}${FONT_A}Dodo${FONT_A}\n
${CENTER}${FONT_A}TEST\n${FONT_A}\n
`;

export default function App() {
  const [text, setText] = useState('');
  const [printerList, setPrinterList] = useState<IPrinter[]>([]);
  const [printer, setPrinter] = useState<IPrinter>();

  useEffect(() => {
    init();
  }, []);

  async function get() {
    const deviceList = await getDeviceList();
    setPrinterList(deviceList);
    setText('Device List, total: ' + deviceList.length);
  }

  async function connectPrinter() {
    if (printer) {
      const { vendor_id, product_id } = printer;
      const response = await connect(Number(vendor_id), Number(product_id));
      setText(response);
    } else {
      setText('Select a printer first');
    }
  }

  async function printText() {
    const aa = await onText(textPrint, {
      encoding: 'CP860',
      beep: true,
      cut: true,
      tailingLine: true,
    });

    setText(aa);
  }

  async function printBill() {
    const aa = await onBill('\n Hello World \n', { beep: true });

    setText(aa);
  }

  async function printImage() {
    const img = 'https://avatars.githubusercontent.com/u/123817130';

    const result = await printImageURL(img, {
      imageWidth: 150,
      imageHeight: 150,
      beep: true,
      cut: true,
      tailingLine: true,
    });

    setText(result);
  }

  async function printImage64() {
    const result = await printImageBase64(img64, {
      imageWidth: 150,
      imageHeight: 150,
      beep: true,
      cut: true,
    });

    setText(result);
  }

  async function onPrintCut() {
    const result = await printCut(true, true);
    setText(result);
  }

  return (
    <ScrollView>
      <View style={styles.container}>
        <TouchableOpacity onPress={() => init()} style={styles.bt}>
          <Text>Init</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={get} style={styles.bt}>
          <Text>Get Device List</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={connectPrinter} style={styles.bt}>
          <Text>Connect Printer</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={printText} style={styles.bt}>
          <Text>Print Text</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={printBill} style={styles.bt}>
          <Text>Print Bill</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={printImage} style={styles.bt}>
          <Text>Print Image</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={printImage64} style={styles.bt}>
          <Text>Print Image 64</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={onPrintCut} style={styles.bt}>
          <Text>Print Cut</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={() => setText('')} style={styles.bt}>
          <Text>Clear</Text>
        </TouchableOpacity>

        <Text style={styles.tex}>{text}</Text>
        <View>
          {printerList.map((i, index) => (
            <TouchableOpacity
              key={index}
              onPress={() => setPrinter(i)}
              style={
                printer?.device_id === i.device_id
                  ? styles.btPrintSelected
                  : styles.btPrint
              }
            >
              <Text>{i.device_name}</Text>
              <Text>{i.manufacturer_name}</Text>
              <Text>{i.product_name}</Text>
              <Text>{i.vendor_id}</Text>
              <Text>{i.product_id}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: height,
    width: width,
    backgroundColor: 'white',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
    marginBottom: 200,
  },
  bt: {
    marginTop: 10,
    width: 200,
    height: 50,
    backgroundColor: 'green',
    justifyContent: 'center',
    alignItems: 'center',
  },
  tex: {
    margin: 20,
  },
  btPrint: {
    width: 200,
    paddingVertical: 10,
    backgroundColor: 'blue',
    justifyContent: 'center',
    marginTop: 13,
    alignItems: 'center',
  },
  btPrintSelected: {
    marginTop: 13,
    paddingVertical: 10,
    width: 200,
    backgroundColor: 'orange',
    justifyContent: 'center',
    alignItems: 'center',
  },
});
