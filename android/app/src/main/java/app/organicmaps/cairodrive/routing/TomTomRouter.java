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

/// TomTom online routing.
///
/// Endpoint: GET https://api.tomtom.com/routing/1/calculateRoute/
///   {lat,lon}:{lat,lon}/json?key=...&maxAlternatives=2&instructionsType=text
///
/// Response is parsed defensively from {@code routes[]}, reading
/// {@code summary.lengthInMeters} / {@code summary.travelTimeInSeconds} for the
/// metrics and {@code legs[].points[]} (objects with {@code latitude} /
/// {@code longitude}) for the geometry. Exact field names may need tuning
/// against the live API.
public final class TomTomRouter implements RouteProvider
{
  private static final String SUB = "route";
  private static final String NAME = "TomTom";
  private static final String BASE = "https://api.tomtom.com/routing/1/calculateRoute/";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasTomTom();
  }

  @NonNull
  @Override
  public List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    // Locations are lat,lon pairs separated by ':'.
    final String locations = String.format(Locale.US, "%f,%f:%f,%f", from.lat, from.lon, to.lat, to.lon);
    final String url = BASE + locations + "/json"
        + "?key=" + CairoKeys.tomTom()
        + "&maxAlternatives=2"
        + "&instructionsType=text";

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    return parse(root);
  }

  @NonNull
  private static List<OnlineRoute> parse(@NonNull JSONObject root)
  {
    final List<OnlineRoute> routes = new ArrayList<>();
    final JSONArray arr = root.optJSONArray("routes");
    if (arr == null)
    {
      CairoLog.w(SUB, NAME + ": no routes array in response");
      return routes;
    }

    for (int i = 0; i < arr.length(); i++)
    {
      final JSONObject r = arr.optJSONObject(i);
      if (r == null)
        continue;

      double distance = 0;
      double duration = 0;
      final JSONObject summary = r.optJSONObject("summary");
      if (summary != null)
      {
        distance = summary.optDouble("lengthInMeters", 0);
        duration = summary.optDouble("travelTimeInSeconds", 0);
      }

      final List<GeoPoint> poly = extractPolyline(r);
      if (poly.isEmpty())
        continue;

      routes.add(new OnlineRoute(NAME, distance, duration, poly));
    }
    return routes;
  }

  @NonNull
  private static List<GeoPoint> extractPolyline(@NonNull JSONObject route)
  {
    final List<GeoPoint> points = new ArrayList<>();
    final JSONArray legs = route.optJSONArray("legs");
    if (legs == null)
      return points;

    for (int i = 0; i < legs.length(); i++)
    {
      final JSONObject leg = legs.optJSONObject(i);
      if (leg == null)
        continue;
      final JSONArray pts = leg.optJSONArray("points");
      if (pts == null)
        continue;
      for (int j = 0; j < pts.length(); j++)
      {
        final JSONObject p = pts.optJSONObject(j);
        if (p == null)
          continue;
        // TomTom uses {latitude, longitude}; tolerate short aliases too.
        final double lat = p.has("latitude") ? p.optDouble("latitude") : p.optDouble("lat", Double.NaN);
        final double lon = p.has("longitude") ? p.optDouble("longitude") : p.optDouble("lon", Double.NaN);
        if (!Double.isNaN(lat) && !Double.isNaN(lon))
          points.add(new GeoPoint(lat, lon));
      }
    }
    return points;
  }
}
