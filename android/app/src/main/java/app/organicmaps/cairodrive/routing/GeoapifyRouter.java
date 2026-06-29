package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/// Geoapify online routing.
///
/// Endpoint: GET https://api.geoapify.com/v1/routing
///   ?waypoints={lat},{lon}|{lat},{lon}&mode=drive&apiKey=...
///
/// Response is GeoJSON: a {@code features[]} array. Geometry lives under
/// {@code geometry.coordinates}, which Geoapify returns as a MultiLineString
/// (an array of line strings, each an array of [lon,lat] pairs) -- we flatten
/// it; a plain LineString is tolerated too. Metrics come from
/// {@code properties.distance} (m) / {@code properties.time} (s).
///
/// Parsing is defensive (optDouble/optJSONArray, GeoJSON [lon,lat] order); the
/// schema may need live tuning against the API.
public final class GeoapifyRouter implements RouteProvider
{
  private static final String SUB = "route";
  private static final String NAME = "Geoapify";
  private static final String BASE = "https://api.geoapify.com/v1/routing";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasGeoapify();
  }

  @NonNull
  @Override
  public List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    // Geoapify waypoints are lat,lon pairs separated by '|'.
    final String waypoints = String.format(Locale.US, "%f,%f|%f,%f", from.lat, from.lon, to.lat, to.lon);
    final String url = BASE
        + "?waypoints=" + waypoints
        + "&mode=drive"
        + "&apiKey=" + CairoKeys.geoapify();

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    return parse(root);
  }

  @NonNull
  private static List<OnlineRoute> parse(@NonNull JSONObject root)
  {
    final List<OnlineRoute> routes = new ArrayList<>();
    final JSONArray features = root.optJSONArray("features");
    if (features == null)
    {
      CairoLog.w(SUB, NAME + ": no features array in response");
      return routes;
    }

    for (int i = 0; i < features.length(); i++)
    {
      final JSONObject feature = features.optJSONObject(i);
      if (feature == null)
        continue;

      double distance = 0;
      double duration = 0;
      final JSONObject properties = feature.optJSONObject("properties");
      if (properties != null)
      {
        distance = properties.optDouble("distance", 0);
        duration = properties.optDouble("time", 0);
      }

      final List<GeoPoint> poly = extractPolyline(feature);
      if (poly.isEmpty())
        continue;

      routes.add(new OnlineRoute(NAME, distance, duration, poly));
    }
    return routes;
  }

  @NonNull
  private static List<GeoPoint> extractPolyline(@NonNull JSONObject feature)
  {
    final List<GeoPoint> points = new ArrayList<>();
    final JSONObject geometry = feature.optJSONObject("geometry");
    if (geometry == null)
      return points;

    final JSONArray coords = geometry.optJSONArray("coordinates");
    if (coords == null)
      return points;

    // coordinates may be a MultiLineString ([[ [lon,lat], ... ], ... ]) or a
    // plain LineString ([ [lon,lat], ... ]). Detect by inspecting nesting.
    for (int i = 0; i < coords.length(); i++)
    {
      final JSONArray entry = coords.optJSONArray(i);
      if (entry == null)
        continue;

      if (isPair(entry))
      {
        // LineString: entry is a [lon,lat] pair.
        points.add(new GeoPoint(entry.optDouble(1), entry.optDouble(0)));
      }
      else
      {
        // MultiLineString: entry is itself an array of [lon,lat] pairs.
        for (int j = 0; j < entry.length(); j++)
        {
          final JSONArray pair = entry.optJSONArray(j);
          if (pair != null && pair.length() >= 2)
            points.add(new GeoPoint(pair.optDouble(1), pair.optDouble(0)));
        }
      }
    }
    return points;
  }

  /// True when the array looks like a scalar [lon,lat] coordinate pair rather
  /// than a nested array of pairs.
  private static boolean isPair(@NonNull JSONArray a)
  {
    return a.length() >= 2 && a.optJSONArray(0) == null && a.optJSONArray(1) == null;
  }
}
