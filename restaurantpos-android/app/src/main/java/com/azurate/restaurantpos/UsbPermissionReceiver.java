package com.azurate.restaurantpos;
import android.content.*;
import android.hardware.usb.UsbManager;
import android.widget.Toast;
public final class UsbPermissionReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context,Intent intent){
    if(PrinterHub.USB_ACTION.equals(intent.getAction())){
      boolean ok=intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
      Toast.makeText(context,ok?"USB access granted. Retry printing.":"USB access denied.",Toast.LENGTH_LONG).show();
    }
  }
}
