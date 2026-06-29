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

/// HERE Traffic API v7 incidents client.
///
/// Endpoint:
/// GET https://data.traffic.hereapi.com/v7/incidents
///       ?in=bbox:{minLon},{minLat},{maxLon},{maxLat}
///       &locationReferencing=shape&apiKey=...
///
/// HERE nests the incident geometry deeply
/// ({@code location.shape.links[0].points[0]} with {@code lat}/{@code lng}),
/// and the exact shape varies, so we walk the JSON defensively for the first
/// {@code lat}/{@code lng} pair we can find rather than assuming a fixed path.
/// Missing/odd fields degrade to sensible defaults rather than throwing.
public final class HereTrafficClient
{
  private static final String SUB = "traffic";
  private static final String BASE = "https://data.traffic.hereapi.com/v7/incidents";

  /// Whether this client can issue requests (HERE key present).
  public boolean isAvailable()
  {
    return CairoKeys.hasHere();
  }

  /// Fetches incidents within the given bounding box. Bbox order is
  /// {@code minLon,minLat,maxLon,maxLat}.
  @NonNull
  public List<TrafficIncident> fetchInBbox(double minLon, double minLat, double maxLon, double maxLat)
      throws IOException
  {
    final List<TrafficIncident> out = new ArrayList<>();
    if (!isAvailable())
    {
      CairoLog.w(SUB, "HERE traffic unavailable (no key); skipping fetch");
      return out;
    }

    final String url = BASE
        + "?in=bbox:" + minLon + "," + minLat + "," + maxLon + "," + maxLat
        + "&locationReferencing=shape"
        + "&apiKey=" + CairoKeys.here();

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    parse(root, out);
    CairoLog.i(SUB, "traffic[here]: incidents=" + out.size());
    return out;
  }

  private static void parse(@NonNull JSONObject root, @NonNull List<TrafficIncident> out)
  {
    final JSONArray results = root.optJSONArray("results");
    if (results == null)
    {
      CairoLog.w(SUB, "traffic[here]: no results array in response");
      return;
    }

    for (int i = 0; i < results.length(); i++)
    {
      final JSONObject result = results.optJSONObject(i);
      if (result == null)
        continue;

      final GeoPoint loc = firstLatLng(result.optJSONObject("location"));
      if (loc == null)
        continue;

      final JSONObject details = result.optJSONObject("incidentDetails");

      String typeStr = "";
      String criticality = "";
      String description = "";
      if (details != null)
      {
        typeStr = details.optString("type", "");
        criticality = details.optString("criticality", "");
        description = firstDescription(details);
      }

      // Prefer HERE's explicit criticality; fall back to type if absent.
      final String severityHint = !criticality.isEmpty() ? criticality : typeStr;
      out.add(new TrafficIncident(loc, fromHere(severityHint), description, typeStr));
    }
  }

  /// Maps HERE incident criticality (or type) onto our buckets.
  /// "minor"/"low" -> MINOR, "major" -> MAJOR, "critical" -> SEVERE, else MODERATE.
  /// Empty/null degrades to UNKNOWN.
  @NonNull
  static IncidentSeverity fromHere(@Nullable String criticality)
  {
    if (criticality == null || criticality.isEmpty())
      return IncidentSeverity.UNKNOWN;
    switch (criticality.toLowerCase())
    {
    case "minor":
    case "low":
      return IncidentSeverity.MINOR;
    case "major":
      return IncidentSeverity.MAJOR;
    case "critical":
      return IncidentSeverity.SEVERE;
    default:
      return IncidentSeverity.MODERATE;
    }
  }

  /// Pulls the first usable human-readable description out of an
  /// {@code incidentDetails} object, trying {@code description} then
  /// {@code summary}. Each may be a plain string or an object carrying a
  /// {@code value} field (HERE's localised-string shape).
  @NonNull
  private static String firstDescription(@NonNull JSONObject details)
  {
    final String desc = stringOrValue(details.opt("description"));
    if (!desc.isEmpty())
      return desc;
    return stringOrValue(details.opt("summary"));
  }

  /// Reads a field that is either a plain String or an object with a
  /// {@code value} (string) member, returning "" otherwise.
  @NonNull
  private static String stringOrValue(@Nullable Object node)
  {
    if (node instanceof String)
      return (String) node;
    if (node instanceof JSONObject)
      return ((JSONObject) node).optString("value", "");
    return "";
  }

  /// Walks the (possibly deeply nested) incident geometry and returns the first
  /// {@code lat}/{@code lng} pair encountered, as a GeoPoint(lat, lon).
  /// Defensive against HERE's varying nesting:
  /// {@code location.shape.links[].points[].{lat,lng}}.
  @Nullable
  private static GeoPoint firstLatLng(@Nullable Object node)
  {
    if (node instanceof JSONObject)
    {
      final JSONObject obj = (JSONObject) node;
      // Direct hit: an object carrying both a lat and a lng (or lon).
      if (obj.has("lat") && (obj.has("lng") || obj.has("lon")))
      {
        final double lat = obj.optDouble("lat", Double.NaN);
        final double lng = obj.has("lng")
            ? obj.optDouble("lng", Double.NaN)
            : obj.optDouble("lon", Double.NaN);
        if (!Double.isNaN(lat) && !Double.isNaN(lng))
          return new GeoPoint(lat, lng);
      }
      // Otherwise descend into every child value.
      for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); )
      {
        final GeoPoint p = firstLatLng(obj.opt(it.next()));
        if (p != null)
          return p;
      }
      return null;
    }

    if (node instanceof JSONArray)
    {
      final JSONArray arr = (JSONArray) node;
      for (int i = 0; i < arr.length(); i++)
      {
        final GeoPoint p = firstLatLng(arr.opt(i));
        if (p != null)
          return p;
      }
      return null;
    }

    return null;
  }
}
