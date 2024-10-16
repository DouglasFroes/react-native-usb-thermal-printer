
# React Native USB Thermal Printer

`react-native-usb-thermal-printer` é uma biblioteca para React Native que permite a interface com impressoras térmicas USB. Esta biblioteca é compatível apenas com dispositivos Android. Ela oferece funcionalidades para imprimir textos, imagens, códigos de barras e QR codes.

Esta biblioteca foi desenvolvida usando como referência a [react-native-thermal-receipt-printer](https://github.com/HeligPfleigh/react-native-thermal-receipt-printer).

## Instalação

Certifique-se de que o pacote esteja adicionado ao seu projeto:

```bash
npm install react-native-usb-thermal-printer
```

### Requisitos

- Este package é compatível apenas com Android. Não é compatível com Expo Go, pois não suporta módulos nativos que não estão incluídos no SDK do Expo.

## Uso

### Importando a Biblioteca

```javascript
import {
  onPrintDeviceList,
  onPrintText,
  onPrintImageURL,
  onPrintImageBase64,
  onPrintCut,
  onPrintClear,
  onPrintBarCode,
  onPrintQRCode,
  IPrinter,
  PrinterOptions,
  PrinterImageOptions
} from 'react-native-usb-thermal-printer';
```

### Referência da API

#### `onPrintDeviceList()`

Recupera uma lista de impressoras USB disponíveis.

**Retorna:**

- `Promise<IPrinter[]>`: Uma promessa que resolve com um array de objetos impressora.

#### `onPrintText(id, text, opts)`

Imprime texto na impressora especificada.

- `id: number`: Identificador da impressora (product_id).
- `text: string`: Texto a ser impresso.
- `opts: PrinterOptions`: Configuração opcional para impressão.

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso na impressão.

#### `onPrintImageURL(id, imageUrl, opts)`

Imprime uma imagem a partir de uma URL.

- `id: number`: Identificador da impressora (product_id).
- `imageUrl: string`: URL da imagem a ser impressa.
- `opts: PrinterImageOptions`: Configuração opcional para impressão.

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso na impressão.

#### `onPrintImageBase64(id, base64, opts)`

Imprime uma imagem a partir de uma string codificada em Base64.

- `id: number`: Identificador da impressora (product_id).
- `base64: string`: Dados da imagem codificados em Base64.
- `opts: PrinterImageOptions`: Configuração opcional para impressão.

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso na impressão.

#### `onPrintCut(id, line, beep)`

Corta o papel na impressora especificada.

- `id: number`: Identificador da impressora (product_id).
- `line: boolean`: Indica se deve incluir uma linha final.
- `beep: boolean`: Determina se um som de beep é emitido durante o corte.

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso no corte.

#### `onPrintClear(id)`

Limpa a fila de impressão.

- `id: number`: Identificador da impressora (product_id).

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso na limpeza.

#### `onPrintBarCode(id, w, h, text)`

Imprime um código de barras.

- `id: number`: Identificador da impressora (product_id).
- `w: number`: Largura do código de barras.
- `h: number`: Altura do código de barras.
- `text: string`: Dados para o código de barras.

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso na impressão.

#### `onPrintQRCode(id, text, size)`

Imprime um QR code.

- `id: number`: Identificador da impressora (product_id).
- `text: string`: Dados para o QR code.
- `size: number`: Tamanho do QR code.

**Retorna:**

- `Promise<string>`: Uma promessa que resolve em caso de sucesso na impressão.

## Exemplo

```javascript
(async () => {
  try {
    const printers = await onPrintDeviceList();
    console.log('Impressoras Disponíveis:', printers);

    if (printers.length > 0) {
      const printerId = printers[0].device_id;
      await onPrintText(printerId, 'Olá, Impressora Térmica!');
      console.log('Texto impresso com sucesso!');
    }
  } catch (error) {
    console.error('Erro na impressão:', error);
  }
})();
```

## Licença

Este projeto é licenciado sob a Licença MIT.
