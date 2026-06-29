package app.organicmaps.cairodrive.speed;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.util.log.CairoLog;

/// User's custom over-speed threshold: an absolute km/h alarm, independent of
/// the posted road limit. Stored in a private SharedPreferences file so it
/// survives restarts. A threshold of 0 means the feature is disabled.
public final class OverspeedConfig
{
  private static final String SUB = "OverspeedConfig";
  private static final String PREFS = "cairodrive_speed";
  private static final String KEY_THRESHOLD = "overspeed_threshold_kmh";
  private static final int DEFAULT_THRESHOLD = 0;

  private OverspeedConfig() {}

  @NonNull
  private static SharedPreferences prefs(@NonNull Context ctx)
  {
    return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /// Current threshold in km/h. 0 (the default) means disabled.
  public static int getThresholdKmh(@NonNull Context ctx)
  {
    return prefs(ctx).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD);
  }

  /// Sets the threshold in km/h. Negative values are clamped to 0 (disabled).
  public static void setThresholdKmh(@NonNull Context ctx, int kmh)
  {
    final int clamped = Math.max(0, kmh);
    prefs(ctx).edit().putInt(KEY_THRESHOLD, clamped).apply();
    CairoLog.i(SUB, "threshold set to " + clamped + " km/h");
  }

  /// True when a positive threshold is configured.
  public static boolean isEnabled(@NonNull Context ctx)
  {
    return getThresholdKmh(ctx) > 0;
  }
}
