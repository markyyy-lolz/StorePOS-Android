package com.azurate.restaurantpos;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.usb.*;
import android.os.Build;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class PrinterHub {
  static final String USB_ACTION="com.azurate.restaurantpos.USB_PERMISSION";
  static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  static final byte[] CUT=new byte[]{0x1d,0x56,0x00};
  static byte[] bytes(String text,boolean cut)throws Exception {
    ByteArrayOutputStream b=new ByteArrayOutputStream();
    b.write(new byte[]{0x1b,0x40}); // ESC/POS initialize
    for(String line:text.split("\n",-1)){
      String ascii=line.replace("₱","PHP ").replaceAll("[^\\x20-\\x7E]","?");
      if(ascii.length()>48)ascii=ascii.substring(0,48);
      b.write(ascii.getBytes(StandardCharsets.US_ASCII));b.write(10);
    }
    b.write(new byte[]{10,10,10});
    if(cut)b.write(CUT);
    return b.toByteArray();
  }
  static void send(Context c,org.json.JSONObject p,byte[] data)throws Exception {
    switch(p.getString("transport")){
      case "lan":case "wifi":{
        String host=p.getString("host").trim();int port=p.getInt("port");
        if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
        InetAddress addr=InetAddress.getByName(host);
        if(addr.isLoopbackAddress()||addr.isAnyLocalAddress()||addr.isMulticastAddress())throw new IOException("Invalid printer host");
        try(Socket socket=new Socket()){
          socket.connect(new InetSocketAddress(addr,port),3000);
          socket.setSoTimeout(3500);socket.getOutputStream().write(data);
          socket.getOutputStream().flush();
        }return;
      }
      case "bluetooth":{
        if(Build.VERSION.SDK_INT>=31 && c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
          throw new SecurityException("Grant Nearby Devices (Bluetooth Connect) permission.");
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(a==null||!a.isEnabled())throw new IOException("Bluetooth is disabled");
        String mac=p.getString("mac");
        BluetoothDevice device=a.getRemoteDevice(mac);
        if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IOException("Pair printer in Android Bluetooth settings first.");
        a.cancelDiscovery();
        try(BluetoothSocket s=device.createRfcommSocketToServiceRecord(SPP)){
          s.connect();s.getOutputStream().write(data);s.getOutputStream().flush();
        }return;
      }
      case "usb":{
        UsbManager manager=(UsbManager)c.getSystemService(Context.USB_SERVICE);
        if(manager==null)throw new IOException("USB host unsupported");
        UsbDevice target=null;
        for(UsbDevice d:manager.getDeviceList().values()){
          if(d.getVendorId()==p.getInt("vid")&&d.getProductId()==p.getInt("pid")){target=d;break;}
        }
        if(target==null)throw new IOException("USB printer disconnected; check OTG adapter.");
        if(!manager.hasPermission(target))throw new SecurityException("Grant USB access in Printers page then retry manually.");
        UsbInterface iface=null;UsbEndpoint out=null;
        for(int i=0;i<target.getInterfaceCount()&&out==null;i++){
          UsbInterface f=target.getInterface(i);
          for(int j=0;j<f.getEndpointCount();j++){
            UsbEndpoint e=f.getEndpoint(j);
            if(e.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK&&e.getDirection()==UsbConstants.USB_DIR_OUT){iface=f;out=e;break;}
          }
        }
        if(iface==null)throw new IOException("USB device has no bulk-out ESC/POS endpoint.");
        UsbDeviceConnection connection=manager.openDevice(target);
        if(connection==null)throw new IOException("USB open failed");
        try{
          if(!connection.claimInterface(iface,true))throw new IOException("USB printer interface busy");
          int n=0;
          while(n<data.length){
            int len=Math.min(256,data.length-n);
            byte[] part=Arrays.copyOfRange(data,n,n+len);
            int written=connection.bulkTransfer(out,part,len,4500);
            if(written<=0)throw new IOException("USB write failed: "+written);
            n+=written;
          }
        }finally{connection.releaseInterface(iface);connection.close();}
        return;
      }
      default: throw new IOException("Unknown printer transport");
    }
  }
  static void requestUsb(Activity a,int vid,int pid){
    UsbManager manager=(UsbManager)a.getSystemService(Context.USB_SERVICE);
    if(manager==null)return;
    for(UsbDevice d:manager.getDeviceList().values())if(d.getVendorId()==vid&&d.getProductId()==pid){
      Intent intent=new Intent(a,UsbPermissionReceiver.class).setAction(USB_ACTION);
      PendingIntent pending=PendingIntent.getBroadcast(a,vid*65536+pid,intent,
        PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
      manager.requestPermission(d,pending);return;
    }
    Toast.makeText(a,"USB printer not detected",Toast.LENGTH_LONG).show();
  }
}
