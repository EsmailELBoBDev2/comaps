package app.organicmaps.cairodrive.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Shared geography helpers for CairoDrive's multi-source aggregation: distance
/// for proximity-based dedupe, and a name normaliser for fuzzy "same place"
/// matching across providers.
public final class Geo
{
  private static final double EARTH_RADIUS_M = 6_371_000.0;

  private Geo() {}

  /// Great-circle distance in metres.
  public static double haversineMeters(double lat1, double lon1, double lat2, double lon2)
  {
    final double dLat = Math.toRadians(lat2 - lat1);
    final double dLon = Math.toRadians(lon2 - lon1);
    final double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  public static double haversineMeters(@NonNull GeoPoint a, @NonNull GeoPoint b)
  {
    return haversineMeters(a.lat, a.lon, b.lat, b.lon);
  }

  /// Lower-cased, punctuation-stripped, whitespace-collapsed name for comparing
  /// place titles coming from different providers.
  @NonNull
  public static String normalizeName(@Nullable String name)
  {
    if (name == null)
      return "";
    return name.toLowerCase()
        .replaceAll("[\\p{Punct}]", " ")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /// True when two names are "the same place" for dedupe purposes: equal after
  /// normalisation, or one contains the other (handles "KFC" vs "KFC Nasr City").
  public static boolean namesMatch(@Nullable String a, @Nullable String b)
  {
    final String na = normalizeName(a);
    final String nb = normalizeName(b);
    if (na.isEmpty() || nb.isEmpty())
      return false;
    return na.equals(nb) || na.contains(nb) || nb.contains(na);
  }
}
