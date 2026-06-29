package app.organicmaps.cairodrive.speed;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.util.log.CairoLog;

/// Decides whether the current speed is over the user's absolute threshold
/// (see {@link OverspeedConfig}). Keeps the latest reading and fires an
/// optional listener only on a false->true transition, so the alarm does not
/// re-trigger on every GPS sample while still over the limit.
public final class OverspeedMonitor
{
  private static final String SUB = "OverspeedMonitor";
  private static final double MPS_TO_KMH = 3.6;

  /// Notified once each time the driver crosses from under to over the limit.
  public interface Listener
  {
    void onOverspeed(int kmh, int thresholdKmh);
  }

  private int mCurrentKmh = 0;
  private boolean mOver = false;
  @Nullable private Listener mListener;
  // Cached config so the GPS hot path (~1 Hz) never reads SharedPreferences.
  private boolean mEnabled = false;
  private int mThresholdKmh = 0;

  public void setListener(@Nullable Listener listener)
  {
    mListener = listener;
  }

  /// Refresh the cached threshold from settings. Call from onResume, not per fix.
  public void configure(@NonNull Context ctx)
  {
    mEnabled = OverspeedConfig.isEnabled(ctx);
    mThresholdKmh = OverspeedConfig.getThresholdKmh(ctx);
  }

  /// Feeds a new speed sample (metres per second) using the cached config.
  /// Negative or NaN speeds are treated as zero so a bad GPS fix can't alarm.
  public void onSpeed(double speedMps)
  {
    if (Double.isNaN(speedMps) || speedMps < 0.0)
      speedMps = 0.0;

    final int kmh = (int) Math.round(speedMps * MPS_TO_KMH);
    final boolean enabled = mEnabled;
    final int threshold = mThresholdKmh;
    final boolean over = enabled && kmh > threshold;

    final boolean transition = over && !mOver;

    mCurrentKmh = kmh;
    mOver = over;

    if (transition)
    {
      CairoLog.w(SUB, "overspeed: " + kmh + " > " + threshold + " km/h");
      final Listener l = mListener;
      if (l != null)
        l.onOverspeed(kmh, threshold);
    }
  }

  public int currentKmh()
  {
    return mCurrentKmh;
  }

  public boolean isOver()
  {
    return mOver;
  }
}
