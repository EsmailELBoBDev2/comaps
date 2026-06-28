package app.organicmaps.cairodrive;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/// Runtime configuration for CairoDrive's net-new behaviour.
///
/// Online features default to OFF so the app stays offline-first: routing,
/// search, traffic and the Overpass camera overlay only reach the network once
/// the user opts in. Pro features are unlocked and the default map start is
/// Cairo, per the CairoDrive spec.
public final class CairoConfig
{
  private static final String PREFS = "cairodrive";
  private static final String KEY_ONLINE = "online_features_enabled";
  private static final String KEY_DEV_OVERLAY = "dev_log_overlay_enabled";
  private static final String KEY_PREFERRED_ROUTER = "preferred_router";

  /// Preferred online navigation engine. AUTO = let the route-compare manager
  /// pick the fastest across all providers; otherwise the named engine's route
  /// is the default active one when available.
  public enum Router { AUTO, MAGIC_LANE, TOMTOM, MAPBOX, OPENROUTESERVICE, HERE, GEOAPIFY }

  // Cairo, Egypt - default start location and camera fetch centre fallback.
  public static final double CAIRO_LAT = 30.0444;
  public static final double CAIRO_LON = 31.2357;
  public static final int CAIRO_ZOOM = 13;

  private CairoConfig() {}

  @NonNull
  private static SharedPreferences prefs(@NonNull Context ctx)
  {
    return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /// Master switch for every network-backed feature. Default false = offline-first.
  public static boolean isOnlineEnabled(@NonNull Context ctx)
  {
    return prefs(ctx).getBoolean(KEY_ONLINE, false);
  }

  public static void setOnlineEnabled(@NonNull Context ctx, boolean enabled)
  {
    prefs(ctx).edit().putBoolean(KEY_ONLINE, enabled).apply();
  }

  public static boolean isDevOverlayEnabled(@NonNull Context ctx)
  {
    return prefs(ctx).getBoolean(KEY_DEV_OVERLAY, false);
  }

  public static void setDevOverlayEnabled(@NonNull Context ctx, boolean enabled)
  {
    prefs(ctx).edit().putBoolean(KEY_DEV_OVERLAY, enabled).apply();
  }

  /// Preferred online nav engine (default AUTO = fastest across all providers).
  @NonNull
  public static Router getPreferredRouter(@NonNull Context ctx)
  {
    final String name = prefs(ctx).getString(KEY_PREFERRED_ROUTER, Router.AUTO.name());
    try
    {
      return Router.valueOf(name);
    }
    catch (IllegalArgumentException e)
    {
      return Router.AUTO;
    }
  }

  public static void setPreferredRouter(@NonNull Context ctx, @NonNull Router router)
  {
    prefs(ctx).edit().putString(KEY_PREFERRED_ROUTER, router.name()).apply();
  }

  /// CairoDrive ships with pro features unlocked.
  public static boolean isProUnlocked()
  {
    return true;
  }
}
