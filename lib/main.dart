import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_barcode_scanner/flutter_barcode_scanner.dart';

class PlatformChannel extends StatefulWidget {
  const PlatformChannel({Key? key}) : super(key: key);

  @override
  State<PlatformChannel> createState() => _PlatformChannelState();
}

class _PlatformChannelState extends State<PlatformChannel> {
  static const MethodChannel methodChannel =
      MethodChannel('inspeasy.flutter.io/writetag');
  static const MethodChannel readMethodChannel =
      MethodChannel('inspeasy.flutter.io/readBarcode');

  String _tag = '';
  String? _barcode = '';

  Future<void> _writeTag() async {
    // Uint8List tag = (Uint8List.fromList([
    //   0x53,
    //   0x57,
    //   0x00,
    //   0x16,
    //   //Address
    //   0xFF,
    //   //Password
    //   0x00,
    //   0x00,
    //   0x00,
    //   0x00,
    //   // Tag
    //   0x00,
    //   0x00,
    //   0xFF,
    //   0xFF,
    //   0x00,
    //   0x00,
    //   0xFF,
    //   0xFF,
    //   0x00,
    //   0x00,
    //   0xFF,
    //   0xFF,
    //   //Length
    //   0x60,
    // ]));

    String tag = '53570016FF030102060000000000112233776665558899AABBD3';
    String escrita;
    print(tag.length);
    try {
      final String? result = await methodChannel.invokeMethod('writeTag', tag);
      escrita = 'Escrita: $result.';
    } on PlatformException {
      escrita = 'Failed to get tag.';
    }
    setState(() {
      _tag = escrita;
    });
  }

  Future<void> _scanBarcode() async {
    // _barcode = await FlutterBarcodeScanner.scanBarcode(
    //     '#004297', "Cancel", true, ScanMode.BARCODE);
    String? result = await readMethodChannel.invokeMethod('readCode');

    if (result == null) {
      setState(() => _barcode = "Não foi possível ler o código");
    }

    BigInt.parse(result!);
    setState(() {
      _barcode = result;
    });

    result = '';
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: <Widget>[
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Text(_barcode.toString(), key: const Key('Código de barras:')),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: ElevatedButton(
                  onPressed: _scanBarcode,
                  child: const Text('Scan Barcode'),
                ),
              ),
              Text(_tag, key: const Key('Tag:')),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: ElevatedButton(
                  onPressed: _writeTag,
                  child: const Text('Write tag'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

void main() {
  runApp(const MaterialApp(home: PlatformChannel()));
}
