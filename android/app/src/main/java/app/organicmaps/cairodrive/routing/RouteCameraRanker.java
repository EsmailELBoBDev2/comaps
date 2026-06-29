package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.cameras.OverpassCamera;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.util.Geo;
import java.util.List;

/// Ranks route alternatives by how many speed cameras they pass -- the
/// "route with fewer cameras" option. A camera counts for a route when it lies
/// within a threshold of any of the route's polyline points.
public final class RouteCameraRanker
{
  private static final double NEAR_METERS = 60.0;

  private RouteCameraRanker() {}

  public static int countCamerasNear(@NonNull OnlineRoute route, @NonNull List<OverpassCamera> cameras,
                                     double thresholdMeters)
  {
    int count = 0;
    for (OverpassCamera cam : cameras)
    {
      for (GeoPoint p : route.polyline)
      {
        if (Geo.haversineMeters(p.lat, p.lon, cam.location.lat, cam.location.lon) <= thresholdMeters)
        {
          count++;
          break;  // this camera already counted for this route
        }
      }
    }
    return count;
  }

  /// Flags the route with the fewest nearby cameras as isFewestCameras.
  public static void annotateFewest(@NonNull List<OnlineRoute> routes, @NonNull List<OverpassCamera> cameras)
  {
    OnlineRoute best = null;
    int min = Integer.MAX_VALUE;
    for (OnlineRoute r : routes)
    {
      final int n = countCamerasNear(r, cameras, NEAR_METERS);
      if (n < min)
      {
        min = n;
        best = r;
      }
    }
    for (OnlineRoute r : routes)
      r.isFewestCameras = (r == best);
  }
}
