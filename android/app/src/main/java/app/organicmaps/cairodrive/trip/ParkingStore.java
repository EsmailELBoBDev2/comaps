package app.organicmaps.cairodrive.trip;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.util.log.CairoLog;

/// Remembers where the car was last parked. Local-only, backed by a private
/// SharedPreferences. A parking fix is just a lat/lon plus the wall-clock time
/// it was saved, so the UI can show "parked 25 min ago".
public final class ParkingStore
{
  private static final String SUB = "parking";
  private static final String PREFS = "cairodrive_parking";
  private static final String KEY_LAT = "lat";
  private static final String KEY_LON = "lon";
  private static final String KEY_TIME = "time";

  private ParkingStore() {}

  @NonNull
  private static SharedPreferences prefs(@NonNull Context ctx)
  {
    return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /// Store the current parking location plus the current wall-clock time.
  public static void saveParking(@NonNull Context ctx, double lat, double lon)
  {
    prefs(ctx).edit()
        .putString(KEY_LAT, Double.toString(lat))
        .putString(KEY_LON, Double.toString(lon))
        .putLong(KEY_TIME, System.currentTimeMillis())
        .apply();
    CairoLog.i(SUB, "saved parking " + lat + "," + lon);
  }

  /// True when a parking location is on record.
  public static boolean hasParking(@NonNull Context ctx)
  {
    final SharedPreferences p = prefs(ctx);
    return p.contains(KEY_LAT) && p.contains(KEY_LON);
  }

  /// Saved latitude, or 0 when none stored / unparsable.
  public static double getParkingLat(@NonNull Context ctx)
  {
    return readDouble(ctx, KEY_LAT);
  }

  /// Saved longitude, or 0 when none stored / unparsable.
  public static double getParkingLon(@NonNull Context ctx)
  {
    return readDouble(ctx, KEY_LON);
  }

  /// Epoch millis the parking fix was saved, or 0 when none stored.
  public static long getParkingTimeMs(@NonNull Context ctx)
  {
    return prefs(ctx).getLong(KEY_TIME, 0L);
  }

  /// Forget the parking location.
  public static void clearParking(@NonNull Context ctx)
  {
    prefs(ctx).edit()
        .remove(KEY_LAT)
        .remove(KEY_LON)
        .remove(KEY_TIME)
        .apply();
    CairoLog.i(SUB, "cleared parking");
  }

  private static double readDouble(@NonNull Context ctx, @NonNull String key)
  {
    final String raw = prefs(ctx).getString(key, null);
    if (raw == null)
      return 0.0;
    try
    {
      return Double.parseDouble(raw);
    }
    catch (NumberFormatException e)
    {
      CairoLog.w(SUB, "bad " + key + ": " + raw);
      return 0.0;
    }
  }
}
