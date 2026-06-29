package app.organicmaps.cairodrive.traffic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/// TomTom Traffic Incident Details API client.
///
/// Endpoint: GET https://api.tomtom.com/traffic/services/5/incidentDetails
///
/// Requests only the fields we render and parses the GeoJSON-ish response
/// defensively: geometry may be a {@code Point} ([lon,lat]) or a
/// {@code LineString} (nested [[lon,lat],...]) so we always take the first
/// lon/lat pair we can find. Missing/odd fields degrade to sensible defaults
/// rather than throwing.
public final class TomTomTrafficClient
{
  private static final String SUB = "traffic";
  private static final String BASE = "https://api.tomtom.com/traffic/services/5/incidentDetails";

  // Compact field selector limiting the payload to what we render.
  private static final String FIELDS =
      "{incidents{type,geometry{type,coordinates},"
      + "properties{iconCategory,magnitudeOfDelay,events{description}}}}";

  /// Whether this client can issue requests (TomTom key present).
  public boolean isAvailable()
  {
    return CairoKeys.hasTomTom();
  }

  /// Fetches incidents within the given bounding box. Bbox order matches the
  /// TomTom API: {@code minLon,minLat,maxLon,maxLat}.
  @NonNull
  public List<TrafficIncident> fetchInBbox(double minLon, double minLat, double maxLon, double maxLat)
      throws IOException
  {
    final List<TrafficIncident> out = new ArrayList<>();
    if (!isAvailable())
    {
      CairoLog.w(SUB, "TomTom traffic unavailable (no key); skipping fetch");
      return out;
    }

    final String url = BASE
        + "?key=" + CairoKeys.tomTom()
        + "&bbox=" + minLon + "," + minLat + "," + maxLon + "," + maxLat
        + "&fields=" + FIELDS
        + "&language=en-GB";

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    parse(root, out);
    CairoLog.i(SUB, "incidents=" + out.size());
    return out;
  }

  private static void parse(@NonNull JSONObject root, @NonNull List<TrafficIncident> out)
  {
    final JSONArray incidents = root.optJSONArray("incidents");
    if (incidents == null)
    {
      CairoLog.w(SUB, "no incidents array in response");
      return;
    }

    for (int i = 0; i < incidents.length(); i++)
    {
      final JSONObject inc = incidents.optJSONObject(i);
      if (inc == null)
        continue;

      final GeoPoint loc = firstCoordinate(inc.optJSONObject("geometry"));
      if (loc == null)
        continue;

      final JSONObject props = inc.optJSONObject("properties");

      int magnitude = 0;
      String description = "";
      String category = "";
      if (props != null)
      {
        magnitude = props.optInt("magnitudeOfDelay", 0);
        category = props.optString("iconCategory", "");
        description = firstEventDescription(props.optJSONArray("events"));
      }

      out.add(new TrafficIncident(loc, IncidentSeverity.fromTomTom(magnitude), description, category));
    }
  }

  /// Extracts the first lon/lat pair from a GeoJSON geometry object, handling
  /// both {@code Point} ([lon,lat]) and {@code LineString}/{@code Polygon}
  /// (arbitrarily nested arrays) shapes.
  @Nullable
  private static GeoPoint firstCoordinate(@Nullable JSONObject geometry)
  {
    if (geometry == null)
      return null;
    return firstPair(geometry.optJSONArray("coordinates"));
  }

  /// Recursively walks possibly-nested coordinate arrays and returns the first
  /// numeric [lon, lat] pair encountered, as a GeoPoint(lat, lon).
  @Nullable
  private static GeoPoint firstPair(@Nullable JSONArray coords)
  {
    if (coords == null || coords.length() == 0)
      return null;

    // A flat [lon, lat] pair: both first two elements parse as numbers.
    if (coords.optJSONArray(0) == null)
    {
      if (coords.length() < 2)
        return null;
      final double lon = coords.optDouble(0, Double.NaN);
      final double lat = coords.optDouble(1, Double.NaN);
      if (Double.isNaN(lon) || Double.isNaN(lat))
        return null;
      return new GeoPoint(lat, lon);
    }

    // Nested: descend into the first child until we hit a flat pair.
    for (int i = 0; i < coords.length(); i++)
    {
      final GeoPoint p = firstPair(coords.optJSONArray(i));
      if (p != null)
        return p;
    }
    return null;
  }

  @NonNull
  private static String firstEventDescription(@Nullable JSONArray events)
  {
    if (events == null)
      return "";
    for (int i = 0; i < events.length(); i++)
    {
      final JSONObject ev = events.optJSONObject(i);
      if (ev == null)
        continue;
      final String desc = ev.optString("description", "");
      if (!desc.isEmpty())
        return desc;
    }
    return "";
  }
}
