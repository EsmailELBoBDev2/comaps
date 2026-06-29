package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// Reachable-area (isochrone) helper, Google/Waze-style "how far can I get".
///
/// NOT a {@link RouteProvider} -- it computes drive-time reachability polygons
/// rather than point-to-point routes.
///
/// Endpoint: POST https://api.openrouteservice.org/v2/isochrones/driving-car
///   header "Authorization: <key>"
///   body {"locations":[[lon,lat]],"range":[range_seconds]}
///
/// Response is GeoJSON: each {@code features[]} feature carries a Polygon under
/// {@code geometry.coordinates[0]} (an array of [lon,lat] rings). We return one
/// {@code List<GeoPoint>} ring per feature.
///
/// Parsing is defensive (optDouble/optJSONArray, GeoJSON [lon,lat] order); the
/// schema may need live tuning against the API.
public final class IsochroneClient
{
  private static final String SUB = "route";
  private static final String NAME = "OpenRouteService isochrones";
  private static final String ENDPOINT =
      "https://api.openrouteservice.org/v2/isochrones/driving-car";

  public boolean isAvailable()
  {
    return CairoKeys.hasOpenRouteService();
  }

  /// Computes the area reachable from {@code center} within {@code seconds} of
  /// driving, as a list of polygon rings (each a list of boundary points).
  @NonNull
  public List<List<GeoPoint>> reachableArea(@NonNull GeoPoint center, int seconds) throws IOException
  {
    final Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", CairoKeys.openRouteService());

    final String body = buildBody(center, seconds);
    final JSONObject root = HttpJson.postObject(ENDPOINT, headers, body);
    return parse(root);
  }

  @NonNull
  private static String buildBody(@NonNull GeoPoint center, int seconds) throws IOException
  {
    try
    {
      final JSONObject req = new JSONObject();

      final JSONArray locations = new JSONArray();
      final JSONArray loc = new JSONArray();
      // GeoJSON coordinate order is [lon, lat].
      loc.put(center.lon);
      loc.put(center.lat);
      locations.put(loc);
      req.put("locations", locations);

      final JSONArray range = new JSONArray();
      range.put(seconds);
      req.put("range", range);

      return req.toString();
    }
    catch (JSONException e)
    {
      throw new IOException("Failed to build isochrone request", e);
    }
  }

  @NonNull
  private static List<List<GeoPoint>> parse(@NonNull JSONObject root)
  {
    final List<List<GeoPoint>> polygons = new ArrayList<>();
    final JSONArray features = root.optJSONArray("features");
    if (features == null)
    {
      CairoLog.w(SUB, NAME + ": no features array in response");
      return polygons;
    }

    for (int i = 0; i < features.length(); i++)
    {
      final JSONObject feature = features.optJSONObject(i);
      if (feature == null)
        continue;

      final JSONObject geometry = feature.optJSONObject("geometry");
      if (geometry == null)
        continue;

      // Polygon geometry: coordinates is an array of rings; take the outer ring.
      final JSONArray coords = geometry.optJSONArray("coordinates");
      if (coords == null)
        continue;
      final JSONArray ring = coords.optJSONArray(0);
      if (ring == null)
        continue;

      final List<GeoPoint> points = new ArrayList<>();
      for (int j = 0; j < ring.length(); j++)
      {
        final JSONArray pair = ring.optJSONArray(j);
        if (pair != null && pair.length() >= 2)
          points.add(new GeoPoint(pair.optDouble(1), pair.optDouble(0)));
      }
      if (!points.isEmpty())
        polygons.add(points);
    }
    return polygons;
  }
}
