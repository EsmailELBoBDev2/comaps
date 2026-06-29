package app.organicmaps.cairodrive.trip;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.util.log.CairoLog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/// Builds an Android share sheet so a driver can text someone a live ETA: a
/// human arrival time, the destination name, and a map link the recipient can
/// tap to open in any maps app (geo: + an OpenStreetMap https fallback).
public final class ShareEta
{
  private static final String SUB = "shareEta";

  private ShareEta() {}

  /// Fire an ACTION_SEND chooser with a plain-text ETA message. Safe to call
  /// with the application context; all failures are swallowed and logged.
  public static void shareEta(@NonNull Context ctx, double destLat, double destLon,
                              long etaEpochMs, @Nullable String destName)
  {
    try
    {
      final String message = buildMessage(destLat, destLon, etaEpochMs, destName);

      final Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("text/plain");
      send.putExtra(Intent.EXTRA_TEXT, message);

      final Intent chooser = Intent.createChooser(send, "Share ETA");
      // ctx may be the application context, which requires a new task.
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      ctx.startActivity(chooser);
      CairoLog.i(SUB, "shared eta");
    }
    catch (Throwable t)
    {
      CairoLog.e(SUB, "share failed: " + t.getMessage(), t);
    }
  }

  /// Compose the shareable text. Package-visible for readability/testing.
  @NonNull
  static String buildMessage(double destLat, double destLon, long etaEpochMs, @Nullable String destName)
  {
    final StringBuilder sb = new StringBuilder();

    final String name = (destName == null || destName.trim().isEmpty()) ? null : destName.trim();
    if (name != null)
      sb.append("Heading to ").append(name).append('.');
    else
      sb.append("On my way.");

    if (etaEpochMs > 0L)
    {
      final String hhmm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(etaEpochMs));
      sb.append(" ETA ").append(hhmm).append('.');
    }

    // geo: opens a maps app directly; OSM https link works everywhere else.
    final String geo = "geo:" + destLat + "," + destLon
        + "?q=" + destLat + "," + destLon + (name != null ? "(" + name + ")" : "");
    final String osm = "https://www.openstreetmap.org/?mlat=" + destLat + "&mlon=" + destLon;

    sb.append('\n').append(geo);
    sb.append('\n').append(osm);

    return sb.toString();
  }
}
