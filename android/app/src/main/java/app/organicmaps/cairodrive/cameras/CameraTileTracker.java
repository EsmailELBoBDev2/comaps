package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Pan-to-load helper for the online camera overlay. Remembers the center of
/// the last successful fetch and decides when a new fetch is warranted, so we
/// don't hammer the Overpass mirrors on every tiny map movement.
///
/// A refetch is requested when there is no previous center, or when the map has
/// panned more than {@link #REFETCH_DISTANCE_METERS} from the last fetch center
/// (which is comfortably inside the ~10km box {@code fetchAround} loads, leaving
/// margin so the overlay never shows an edge of empty data).
public final class CameraTileTracker
{
  /// Pan threshold that triggers a new fetch.
  public static final double REFETCH_DISTANCE_METERS = 2000.0;

  private static final double EARTH_RADIUS_METERS = 6_371_000.0;

  @Nullable
  private GeoPoint mLastCenter;

  /// Whether a fetch should be triggered for {@code newCenter}.
  public boolean needsRefetch(@NonNull GeoPoint newCenter)
  {
    if (mLastCenter == null)
      return true;
    return haversineMeters(newCenter, mLastCenter) > REFETCH_DISTANCE_METERS;
  }

  /// Records {@code center} as the center of the most recent successful fetch.
  public void markFetched(@NonNull GeoPoint center)
  {
    mLastCenter = center;
  }

  @Nullable
  public GeoPoint lastCenter()
  {
    return mLastCenter;
  }

  /// Great-circle distance between two points, in meters.
  public static double haversineMeters(@NonNull GeoPoint a, @NonNull GeoPoint b)
  {
    final double lat1 = Math.toRadians(a.lat);
    final double lat2 = Math.toRadians(b.lat);
    final double dLat = Math.toRadians(b.lat - a.lat);
    final double dLon = Math.toRadians(b.lon - a.lon);

    final double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    final double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    return EARTH_RADIUS_METERS * c;
  }
}
