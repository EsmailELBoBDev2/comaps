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

/// Mapbox Directions online routing.
///
/// Endpoint: GET https://api.mapbox.com/directions/v5/mapbox/driving/
///   {lon,lat};{lon,lat}?alternatives=true&geometries=polyline6&overview=full&access_token=...
///
/// Note Mapbox uses lon,lat ordering in the path. Metrics come from
/// {@code routes[].distance} (meters) / {@code routes[].duration} (seconds);
/// geometry is the encoded {@code routes[].geometry} string decoded with
/// {@link PolylineCodec} at precision 6 (because of {@code geometries=polyline6}).
/// Exact field names may need tuning against the live API.
public final class MapboxRouter implements RouteProvider
{
  private static final String SUB = "route";
  private static final String NAME = "Mapbox";
  private static final String BASE = "https://api.mapbox.com/directions/v5/mapbox/driving/";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasMapbox();
  }

  @NonNull
  @Override
  public List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    // Mapbox path coordinates are lon,lat;lon,lat.
    final String coords = String.format(Locale.US, "%f,%f;%f,%f", from.lon, from.lat, to.lon, to.lat);
    final String url = BASE + coords
        + "?alternatives=true"
        + "&geometries=polyline6"
        + "&overview=full"
        + "&access_token=" + CairoKeys.mapbox();

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

      final double distance = r.optDouble("distance", 0);
      final double duration = r.optDouble("duration", 0);

      List<GeoPoint> poly = decodeGeometry(r);
      if (poly.isEmpty())
        continue;

      routes.add(new OnlineRoute(NAME, distance, duration, poly));
    }
    return routes;
  }

  @NonNull
  private static List<GeoPoint> decodeGeometry(@NonNull JSONObject route)
  {
    // With geometries=polyline6 the geometry is an encoded string. Only treat
    // it as a string when it really is one -- optString stringifies arrays.
    if (route.opt("geometry") instanceof String)
    {
      final String encoded = route.optString("geometry", null);
      if (encoded != null && !encoded.isEmpty())
        return PolylineCodec.decode(encoded, 6);
    }

    // Defensive fallback: if geometries=geojson was used instead.
    final JSONObject geo = route.optJSONObject("geometry");
    final List<GeoPoint> points = new ArrayList<>();
    if (geo != null)
    {
      final JSONArray coords = geo.optJSONArray("coordinates");
      if (coords != null)
      {
        for (int i = 0; i < coords.length(); i++)
        {
          final JSONArray pair = coords.optJSONArray(i);
          if (pair != null && pair.length() >= 2)
            points.add(new GeoPoint(pair.optDouble(1), pair.optDouble(0)));
        }
      }
    }
    return points;
  }
}
