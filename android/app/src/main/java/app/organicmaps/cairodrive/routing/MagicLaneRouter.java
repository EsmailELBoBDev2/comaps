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

/// Magic Lane online routing.
///
/// Endpoint: POST https://routing.magiclane.com/route
///
/// SCHEMA ASSUMPTION: Magic Lane's exact request/response schema is not
/// publicly fixed here, so this implementation makes documented choices that
/// may need field-name tuning against the live API:
///   - Authentication via an {@code Authorization: Bearer <key>} header (the
///     alternative would be an {@code apikey} query/body param). We chose the
///     bearer header since the key never appears in the URL that way.
///   - Request body asks for alternatives and an encoded polyline geometry.
///   - The response is parsed defensively: we look for a {@code routes} array
///     and, per route, read distance/duration from a handful of plausible
///     field names and decode either an encoded {@code geometry}/{@code
///     polyline} string (precision 5) or an inline coordinate array.
public final class MagicLaneRouter implements RouteProvider
{
  private static final String SUB = "route";
  private static final String NAME = "Magic Lane";
  private static final String ENDPOINT = "https://routing.magiclane.com/route";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasMagicLane();
  }

  @NonNull
  @Override
  public List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException
  {
    final Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + CairoKeys.magicLane());

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

      final JSONArray waypoints = new JSONArray();
      waypoints.put(coord(from));
      waypoints.put(coord(to));
      req.put("waypoints", waypoints);

      req.put("transportMode", "car");
      req.put("alternativeRoutes", 2);
      req.put("alternatives", true);
      req.put("geometry", true);
      return req.toString();
    }
    catch (JSONException e)
    {
      throw new IOException("Failed to build Magic Lane request", e);
    }
  }

  @NonNull
  private static JSONObject coord(@NonNull GeoPoint p) throws JSONException
  {
    final JSONObject o = new JSONObject();
    o.put("lat", p.lat);
    o.put("lon", p.lon);
    return o;
  }

  @NonNull
  private static List<OnlineRoute> parse(@NonNull JSONObject root)
  {
    final List<OnlineRoute> routes = new ArrayList<>();

    JSONArray arr = root.optJSONArray("routes");
    if (arr == null)
      arr = root.optJSONArray("route");
    if (arr == null)
    {
      // Some APIs wrap the list under a "result"/"data" object.
      final JSONObject data = root.optJSONObject("result");
      if (data != null)
        arr = data.optJSONArray("routes");
    }
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

      final double distance = firstDouble(r, "distance", "distanceMeters", "lengthInMeters", "length");
      final double duration = firstDouble(r, "duration", "durationSeconds", "travelTimeInSeconds", "time");
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
    // Case 1: an encoded polyline string (assume precision 5). Only treat the
    // field as a string when it really is one -- optString stringifies arrays.
    String encoded = null;
    if (route.opt("geometry") instanceof String)
      encoded = route.optString("geometry", null);
    if (encoded == null && route.opt("polyline") instanceof String)
      encoded = route.optString("polyline", null);
    if (encoded != null && !encoded.isEmpty())
      return PolylineCodec.decode(encoded, 5);

    // Case 2: an inline coordinate array under various field names.
    JSONArray coords = route.optJSONArray("geometry");
    if (coords == null)
      coords = route.optJSONArray("coordinates");
    if (coords == null)
      coords = route.optJSONArray("points");
    if (coords == null)
    {
      final JSONObject geo = route.optJSONObject("geometry");
      if (geo != null)
        coords = geo.optJSONArray("coordinates");
    }
    return parseCoordArray(coords);
  }

  @NonNull
  private static List<GeoPoint> parseCoordArray(JSONArray coords)
  {
    final List<GeoPoint> points = new ArrayList<>();
    if (coords == null)
      return points;
    for (int i = 0; i < coords.length(); i++)
    {
      // Each entry may be {lat,lon} or [lon,lat] (GeoJSON order).
      final JSONObject o = coords.optJSONObject(i);
      if (o != null)
      {
        if (o.has("lat") && (o.has("lon") || o.has("lng")))
          points.add(new GeoPoint(o.optDouble("lat"), o.optDouble("lon", o.optDouble("lng"))));
        continue;
      }
      final JSONArray pair = coords.optJSONArray(i);
      if (pair != null && pair.length() >= 2)
        points.add(new GeoPoint(pair.optDouble(1), pair.optDouble(0)));
    }
    return points;
  }

  private static double firstDouble(@NonNull JSONObject o, @NonNull String... keys)
  {
    for (final String k : keys)
    {
      final double v = o.optDouble(k, Double.NaN);
      if (!Double.isNaN(v))
        return v;
    }
    return 0;
  }
}
