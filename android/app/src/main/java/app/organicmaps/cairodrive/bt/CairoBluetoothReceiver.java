package app.organicmaps.cairodrive.bt;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import app.organicmaps.cairodrive.CairoConfig;
import app.organicmaps.sdk.util.log.CairoLog;

/// Launches the app when a Bluetooth device (e.g. the car's head unit) connects,
/// if the user enabled it in CairoDrive settings. Best-effort: modern Android
/// restricts background activity launch, so this may only work while the app was
/// recently foregrounded. Never throws.
public final class CairoBluetoothReceiver extends BroadcastReceiver
{
  private static final String SUB = "bt";

  @Override
  public void onReceive(Context ctx, Intent intent)
  {
    try
    {
      if (intent == null || !BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction()))
        return;
      if (!CairoConfig.isBluetoothAutoStart(ctx))
        return;

      final Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
      if (launch != null)
      {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(launch);
        CairoLog.i(SUB, "auto-start on Bluetooth connect");
      }
    }
    catch (Throwable t)
    {
      CairoLog.w(SUB, "auto-start failed: " + t.getMessage());
    }
  }
}
