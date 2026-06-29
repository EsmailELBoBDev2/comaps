package app.organicmaps.cairodrive.safety;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// OPT-IN online overlay that fetches road safety hazards (speed bumps, school
/// zones, dangerous curves and railway crossings) from the Overpass API. Needs
/// no API key.
///
/// Overpass is queried with a form-encoded POST whose body is
/// {@code data=<urlencoded Overpass QL>}. The query selects every node, way and
/// relation carrying a relevant hazard tag inside a bounding box around the map
/// center, and asks for element centers so ways/relations resolve to a single
/// coordinate. The QL shape is:
///
///   [out:json][timeout:25];
///   (
///     node["traffic_calming"](S,W,N,E);
///     ... ways/relations ...
///     node["amenity"="school"](S,W,N,E);
///     node["amenity"="kindergarten"](S,W,N,E);
///     node["hazard"](S,W,N,E);
///     node["railway"="level_crossing"](S,W,N,E);
///   );
///   out center tags;
///
/// Multiple public mirrors are tried in order; a scrubbed failure is logged and
/// the next mirror is attempted, so a single overloaded endpoint never breaks
/// the overlay.
public final class OverpassHazardClient
{
  private static final String SUB = "hazards";

  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 30_000;

  /// Public Overpass endpoints, tried in order until one succeeds.
  private static final String[] MIRRORS = {
      "https://overpass-api.de/api/interpreter",
      "https://overpass.kumi.systems/api/interpreter",
      "https://overpass.private.coffee/api/interpreter",
      "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
      "https://overpass.osm.ch/api/interpreter",
  };

  /// Approximate meters-per-degree of latitude (used to size the bbox). Longitude
  /// degrees are scaled by cos(lat) so the box stays roughly square on the ground.
  private static final double METERS_PER_DEG_LAT = 111_320.0;

  /// Fetches hazards within roughly {@code radiusMeters} of {@code center}. The
  /// radius defines the half-extent of the bounding box, so e.g. 10000m yields a
  /// ~20km wide / 20km tall box. Returns an empty list (never null) if all
  /// mirrors fail or no hazards are found. UNKNOWN-typed elements are skipped.
  @NonNull
  public List<Hazard> fetchAround(@NonNull GeoPoint center, double radiusMeters)
  {
    final String query = buildQuery(center, radiusMeters);
    final String body = "data=" + urlEncode(query);

    for (int i = 0; i < MIRRORS.length; i++)
    {
      final String mirror = MIRRORS[i];
      try
      {
        final String raw = postForm(mirror, body);
        final List<Hazard> hazards = parse(raw);
        CairoLog.i(SUB, "hazards: mirror=" + mirror + " count=" + hazards.size());
        return hazards;
      }
      catch (IOException | JSONException e)
      {
        CairoLog.w(SUB, "overpass: mirror=" + mirror + " failed: " + e.getMessage());
      }
    }

    CairoLog.w(SUB, "overpass: all mirrors failed; returning no hazards");
    return new ArrayList<>();
  }

  /// Builds the Overpass QL query for a bbox of half-extent {@code radiusMeters}
  /// centered on {@code center}.
  @NonNull
  private static String buildQuery(@NonNull GeoPoint center, double radiusMeters)
  {
    final double dLat = radiusMeters / METERS_PER_DEG_LAT;
    final double cosLat = Math.max(0.01, Math.cos(Math.toRadians(center.lat)));
    final double dLon = radiusMeters / (METERS_PER_DEG_LAT * cosLat);

    final double south = clampLat(center.lat - dLat);
    final double north = clampLat(center.lat + dLat);
    final double west = center.lon - dLon;
    final double east = center.lon + dLon;

    // bbox order for Overpass is (south, west, north, east).
    final String bbox = String.format(Locale.US, "(%f,%f,%f,%f)", south, west, north, east);

    // One block of node/way/relation per hazard selector, so we catch hazards
    // mapped as points (bumps, crossings) and as areas (school grounds).
    final StringBuilder qb = new StringBuilder("[out:json][timeout:25];(");
    appendSelector(qb, "[\"traffic_calming\"]", bbox);
    appendSelector(qb, "[\"amenity\"=\"school\"]", bbox);
    appendSelector(qb, "[\"amenity\"=\"kindergarten\"]", bbox);
    appendSelector(qb, "[\"hazard\"]", bbox);
    appendSelector(qb, "[\"railway\"=\"level_crossing\"]", bbox);
    qb.append(");out center tags;");
    return qb.toString();
  }

  /// Appends node/way/relation lines for a single tag selector + bbox.
  private static void appendSelector(@NonNull StringBuilder qb, @NonNull String selector,
                                     @NonNull String bbox)
  {
    qb.append("node").append(selector).append(bbox).append(";");
    qb.append("way").append(selector).append(bbox).append(";");
    qb.append("relation").append(selector).append(bbox).append(";");
  }

  /// Parses an Overpass JSON response into hazards. Defensive throughout: any
  /// element missing usable coordinates is skipped rather than aborting, and
  /// {@link HazardType#UNKNOWN} elements are dropped.
  @NonNull
  private static List<Hazard> parse(@NonNull String raw) throws JSONException
  {
    final List<Hazard> out = new ArrayList<>();
    final JSONObject root = new JSONObject(raw);
    final JSONArray elements = root.optJSONArray("elements");
    if (elements == null)
      return out;

    for (int i = 0; i < elements.length(); i++)
    {
      final JSONObject el = elements.optJSONObject(i);
      if (el == null)
        continue;

      // Nodes carry lat/lon directly; ways/relations carry a "center" object
      // (requested via "out center").
      double lat;
      double lon;
      if (el.has("lat") && el.has("lon"))
      {
        lat = el.optDouble("lat", Double.NaN);
        lon = el.optDouble("lon", Double.NaN);
      }
      else
      {
        final JSONObject c = el.optJSONObject("center");
        if (c == null)
          continue;
        lat = c.optDouble("lat", Double.NaN);
        lon = c.optDouble("lon", Double.NaN);
      }
      if (Double.isNaN(lat) || Double.isNaN(lon))
        continue;

      final Map<String, String> tags = readTags(el.optJSONObject("tags"));
      final HazardType type = HazardType.fromTags(tags);
      if (type == HazardType.UNKNOWN)
        continue;

      final long id = el.optLong("id", 0L);
      String name = tags.get("name");
      if (name == null)
        name = "";

      out.add(new Hazard(id, new GeoPoint(lat, lon), type, name));
    }
    return out;
  }

  @NonNull
  private static Map<String, String> readTags(JSONObject tagsObj)
  {
    final Map<String, String> tags = new HashMap<>();
    if (tagsObj == null)
      return tags;
    for (final Iterator<String> it = tagsObj.keys(); it.hasNext();)
    {
      final String key = it.next();
      final String value = tagsObj.optString(key, null);
      if (value != null)
        tags.put(key, value);
    }
    return tags;
  }

  /// Sends an {@code application/x-www-form-urlencoded} POST and returns the body.
  /// Overpass takes its query as form field {@code data=...}, so we drive
  /// HttpURLConnection directly here. Still logs (scrubbed mirror URL) through
  /// CairoLog.
  @NonNull
  private static String postForm(@NonNull String url, @NonNull String body) throws IOException
  {
    final long t0 = System.nanoTime();
    HttpURLConnection conn = null;
    try
    {
      conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setRequestMethod("POST");
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setDoOutput(true);
      try (OutputStream os = conn.getOutputStream())
      {
        os.write(body.getBytes(StandardCharsets.UTF_8));
      }

      final int code = conn.getResponseCode();
      final boolean ok = code >= 200 && code < 300;
      final String payload = readAll(ok ? conn.getInputStream() : conn.getErrorStream());
      final long ms = (System.nanoTime() - t0) / 1_000_000;
      CairoLog.d(SUB, "POST " + scrubUrl(url) + " -> " + code + " (" + ms + "ms)");
      if (!ok)
        throw new IOException("HTTP " + code + " for " + scrubUrl(url));
      return payload;
    }
    finally
    {
      if (conn != null)
        conn.disconnect();
    }
  }

  @NonNull
  private static String readAll(InputStream in) throws IOException
  {
    if (in == null)
      return "";
    final StringBuilder sb = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
    {
      String line;
      while ((line = r.readLine()) != null)
        sb.append(line);
    }
    return sb.toString();
  }

  @NonNull
  private static String urlEncode(@NonNull String s)
  {
    try
    {
      return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      // UTF-8 is always supported on Android; unreachable in practice.
      return s;
    }
  }

  /// Strips key/token query params so mirror URLs are safe to log. Overpass URLs
  /// carry no secrets, but we scrub defensively.
  @NonNull
  private static String scrubUrl(@NonNull String url)
  {
    return url.replaceAll("(?i)([?&])(key|api_key|apikey|access_token|token|apiKey)=[^&]*", "$1$2=***");
  }

  /// Clamps a latitude into the valid [-90, 90] range so a large radius near the
  /// poles never produces an out-of-range bbox.
  private static double clampLat(double lat)
  {
    return Math.max(-90.0, Math.min(90.0, lat));
  }
}
