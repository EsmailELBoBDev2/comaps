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

/// OpenRouteService online routing.
///
/// Endpoint: POST https://api.openrouteservice.org/v2/directions/driving-car/geojson
///   header "Authorization: <key>"
///   body {"coordinates":[[lon,lat],[lon,lat]],
///         "alternative_routes":{"target_count":2,"share_factor":0.6,"weight_factor":1.5}}
///
/// Response is GeoJSON: a {@code features[]} array where each feature carries
/// {@code geometry.coordinates} ([lon,lat] pairs -> GeoPoint) and
/// {@code properties.summary.distance} (m) / {@code .duration} (s).
///
/// Parsing is defensive (optDouble/optJSONArray, GeoJSON [lon,lat] order); the
/// schema may need live tuning against the API.
public final class OpenRouteServiceRouter implements RouteProvider
{
  private static final String SUB = "route";
  private static final String NAME = "OpenRouteService";
  private static final String ENDPOINT =
      "https://api.openrouteservice.org/v2/directions/driving-car/geojson";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasOpenRouteService();
  }

  @NonNull
  @Override
  public List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    final Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", CairoKeys.openRouteService());

    final String body = buildBody(from, to);
    final JSONObject root = HttpJson.postObject(ENDPOINT, headers, body);
    return parse(root);
  }

  @NonNull
  private static String buildBody(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    try
    {
      final JSONObject req = new JSONObject();

      final JSONArray coordinates = new JSONArray();
      coordinates.put(coord(from));
      coordinates.put(coord(to));
      req.put("coordinates", coordinates);

      final JSONObject alt = new JSONObject();
      alt.put("target_count", 2);
      alt.put("share_factor", 0.6);
      alt.put("weight_factor", 1.5);
      req.put("alternative_routes", alt);

      return req.toString();
    }
    catch (JSONException e)
    {
      throw new IOException("Failed to build OpenRouteService request", e);
    }
  }

  /// GeoJSON coordinate order is [lon, lat].
  @NonNull
  private static JSONArray coord(@NonNull GeoPoint p)
  {
    final JSONArray a = new JSONArray();
    a.put(p.lon);
    a.put(p.lat);
    return a;
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
        final JSONObject summary = properties.optJSONObject("summary");
        if (summary != null)
        {
          distance = summary.optDouble("distance", 0);
          duration = summary.optDouble("duration", 0);
        }
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

    for (int i = 0; i < coords.length(); i++)
    {
      final JSONArray pair = coords.optJSONArray(i);
      if (pair != null && pair.length() >= 2)
        points.add(new GeoPoint(pair.optDouble(1), pair.optDouble(0)));
    }
    return points;
  }
}
