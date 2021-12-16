package com.example.usb_test;

import androidx.annotation.NonNull;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugins.GeneratedPluginRegistrant;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants;
import android.os.Handler;

import android.content.Context;

import android.app.PendingIntent;
import android.content.Intent;

import android.util.Log;

import java.util.HashMap;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

// import com.example.usb_test.Capture;
import com.journeyapps.barcodescanner.CaptureActivity;


public class MainActivity extends FlutterActivity {

    private static final String TAG = "USB_HOST";
    private UsbManager myUsbManager;
    private UsbDevice myUsbDevice;
    private UsbInterface myInterface;
    private UsbDeviceConnection myDeviceConnection;
    private final int VendorID = 6790;
    private final int ProductID = 57360;

    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    Handler handler;
    Runnable runnable;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    UsbDeviceConnection conn = null;

    private static final String READ_CHANNEL = "inspeasy.flutter.io/readBarcode";
    private static final String WRITE_CHANNEL = "inspeasy.flutter.io/writetag";

    private byte[] tag;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        new MethodChannel(flutterEngine.getDartExecutor(), WRITE_CHANNEL).setMethodCallHandler(
        new MethodCallHandler() {
            @Override
            public void onMethodCall(MethodCall call, Result result) {
                if (call.method.equals("writeTag")) {
                    // tag = (byte[]) call.arguments;     
                    String tagString = (String) call.arguments;     
                    // String completeTag = CRC16(tagString);
                    tag = hexString2Bytes(tagString);         //Recebe tag do flutter (em bytes ja)   
                    Log.d(TAG, tag.toString());
                    
                    //tag = new byte[] {0x53, 0x57, 0x00, 0x05, (byte) 0xFF, 0x24, 0x02, 0x00, 0x2C};
                    handler=new Handler();
                    runnable=new Runnable(){
                        @Override
                        public void run() {
                            assignEndpoint();   //Cria o runnable
                            handler.postDelayed(this, 50);
                        }
                    };
            
                    String writeResult = writeTag();    //Chama essa função

                    if (writeResult != "") {
                        result.success(writeResult);
                    } else {
                    result.error("UNAVAILABLE", "Couldn't write tag.", null);
                    }
                } else {
                    result.notImplemented();
                }
            }
        }
        );


        new MethodChannel(flutterEngine.getDartExecutor(), READ_CHANNEL).setMethodCallHandler(
        new MethodCallHandler() {
            @Override
            public void onMethodCall(MethodCall call, Result result) {
                if (call.method.equals("readCode")) {
                    IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
                    intentIntegrator.setCaptureActivity(CaptureActivity.class);

                            intentIntegrator.initiateScan();

                    
                    if (readResult != "") {
                    result.success(readResult);
                    } else {
                    result.error("UNAVAILABLE", "Couldn't read Barcode.", null);
                    }
                } else {
                    result.notImplemented();
                }
            }
        }
        );
    }

    String readResult = "";
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(
            requestCode, resultCode, data
        );
        if(intentResult.getContents() != null) {
            readResult = intentResult.getContents();
        }
    }

    private int writeResult;
    private String stringResult = "";
    private String writeTag() {
        
        myUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        enumerateDevice();
        
        if(openDevice()){   //Abre o dispositivo
            handler.postDelayed(runnable, 50);  //Executa o assign endpoint
            //assignEndpoint();
            // if (writeResult==tag.length) return "Tag written successfully " + stringResult;
            // else return "Something went wrong";
            return "Result of bulk " + writeResult + " " +stringResult; 
        }
        return "Could not open device";        
    }

    private void enumerateDevice() {
        if (myUsbManager == null) return;
        HashMap<String, UsbDevice> deviceList = myUsbManager.getDeviceList();
        if (!deviceList.isEmpty()) { // deviceList can not null
            StringBuffer sb = new StringBuffer();
            for (UsbDevice device : deviceList.values()) {
                // Log.d(TAG, device.toString());
                // Log.d(TAG, "DeviceInfo: " + device.getVendorId() + " , "
                        // + device.getProductId());
                if (device.getVendorId() == VendorID
                        && device.getProductId() == ProductID) {
                    myUsbDevice = device;
                    // Log.d(TAG, "enumerate Success");
                    findInterface();
                } else {
                    // Log.d(TAG, "Not Found VID and PID");
                }
            }
        } else {
            // Log.d(TAG,"No USB HID device Connect");
        }
    }

    private void findInterface() {
        if (myUsbDevice == null) return;
        int iRet = 0;
        // Log.d(TAG, "interfaceCounts : " + myUsbDevice.getInterfaceCount());
        for (int i = 0; i < myUsbDevice.getInterfaceCount(); i++) {
            UsbInterface intf = myUsbDevice.getInterface(i);
            if (intf.getInterfaceSubclass() == 0 && intf.getInterfaceProtocol() == 0) {
                myInterface = intf;
                // Log.d(TAG, "Find Reader");
                // mShow.setText("Find Reader");
                iRet = 1;
                break;
            }
        }
        // if(iRet == 0) mShow.setText("No Reader");
    }

    public void assignEndpoint() {
        if (myInterface.getEndpoint(1) != null) {
            // stringResult += "Endpoint 1 encontrado ";
            epOut = myInterface.getEndpoint(1);
        }
        
        if (myInterface.getEndpoint(0) != null) {
            // stringResult += "Endpoint 0 encontrado ";
            epIn = myInterface.getEndpoint(0);
        }
        
        stringResult+=("\nInterface name: " + myInterface.getName());
        stringResult+=("\nInterface Subclass: " + myInterface.getInterfaceSubclass());
        stringResult+=("\nInterface Count: " + myUsbDevice.getInterfaceCount());

        // UsbInterface tempInterface = null;
        // UsbEndpoint endpointIN = null;
        // UsbEndpoint endpointOUT = null;
        // for (int i = 0; i < myUsbDevice.getInterfaceCount(); i++){
        //     tempInterface = myUsbDevice.getInterface(i);
        //     if (tempInterface.getEndpointCount() >= 2) {

        //         for (int j = 0; j < tempInterface.getEndpointCount(); j++) {
        //             UsbEndpoint usbEndpointTemp = tempInterface.getEndpoint(j);
        //             if (usbEndpointTemp.getDirection() == UsbConstants.USB_DIR_IN) {
        //                 endpointIN = usbEndpointTemp;
        //                 stringResult += "\tInterface " + i + " possui endpoint de entrada em " + j;
        //             } else if (usbEndpointTemp.getDirection() == UsbConstants.USB_DIR_OUT) {
        //                 endpointOUT = usbEndpointTemp;
        //                 stringResult += "\tInterface " + i + " possui endpoint de saida em " + j;
        //             } else {
        //                 stringResult += "\tInterface " + i + " possui endpoint outro endpoint em " + j;
        //             }
        //         }
        //     }

        // }
        
        

        //byte[] changeMode = new byte[] {(byte)0xFF, (byte)0x02, (byte)0x00};
        // int re = myDeviceConnection.bulkTransfer(epOut, changeMode, changeMode.length, 10);
    
        
        int re2 = myDeviceConnection.bulkTransfer(epOut, tag, tag.length, 200);
        // int re2 = myDeviceConnection.controlTransfer (UsbConstants.USB_DIR_OUT, 
        //         1, 1, 0, tag, tag.length, 50);
        
        stringResult += bytesToHexString(tag);        
        byte[] tag = new byte[] {0x53, 0x57, 0x00, 0x05, (byte) 0xFF, 0x24, 0x02, 0x00, 0x2C};
        
        // re2 = myDeviceConnection.bulkTransfer(epOut, tag, tag.length, 200);
        // stringResult += bytesToHexString(tag);
        
        writeResult = re2;
           
        // if(reByte[0] != 0) {
        //     //Log.i("reByte", re2 + "\n" + bytesToHexString(reByte));
        //     String str1 = "",str2 = "";
        //     int bPackLength = 0;
        //     bPackLength = (int)reByte[16] - 3;
        //     for (int i = 0; i < bPackLength; i++) {
        //         str1 = String.format("%02X", reByte[19 + i]);
        //         str2 = str2 + str1;
        //     }
            
        // }
        // return "Could not assign endpoint";
    }


    private boolean openDevice() {
        if (myInterface != null) {

            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            if (!myUsbManager.hasPermission(myUsbDevice)) {
                Log.d(TAG, "Get Permission");
                myUsbManager.requestPermission(myUsbDevice, pi);
            }
            if (myUsbManager.hasPermission(myUsbDevice)) {
                conn = myUsbManager.openDevice(myUsbDevice);
            }
            else {
                Log.d(TAG, "No Permission");
            }
            if (conn == null) {
                Log.d(TAG, "Open Error1");
                return false;
            }
            if (conn.claimInterface(myInterface, true)) {
                myDeviceConnection = conn;
                Log.d(TAG, "Open Success");
                return true;
            }
            else {
                Log.d(TAG, "Open Error2");
                conn.close();
                return false;
            }
        }
        return false;
    }

    public static String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    public String CRC16(String strInput) {
        int crc = 0xFFFF;
        //Vai converter o parâmetro para Bytes
        byte[] bytes = hexString2Bytes(strInput);
        int lsb, msb;
        String finalString;

        // Log.d("CRC", "String Entrada: " + strInput);

        for (int j = 0; j < bytes.length; j++) {
            //crc = (crc ^ bytes[j]);
            crc = (crc ^ bytes[j] & 0xFF);

            for (int i = 0; i < 8; i++) {

                if ((crc & 0x0001) == 1)
                    crc = (crc >> 1) ^ 0x8408;
                else
                    crc = (crc >> 1);
            }
        }

        lsb = (crc & 0xff);           //LSB 1° Dado
        msb = ((crc >> 8) & 0xff);    //MSB 2° Dado

        // Log.d("CRC", "LSB: " + Integer.toHexString(lsb) + " MSB: " + Integer.toHexString(msb));

        //Recebe a string e concatena com o CRC para retorno
        finalString = strInput + Integer.toHexString(lsb) + Integer.toHexString(msb);

        Log.d("CRC", "Final: " + finalString);

        return finalString;
    }

    public byte[] hexString2Bytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}
