package app.organicmaps.cairodrive.cameras;

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

/// OPT-IN online overlay that augments CoMaps' built-in offline cameras by
/// fetching speed/enforcement cameras from the Overpass API. Needs no API key.
///
/// Overpass is queried with a form-encoded POST whose body is
/// {@code data=<urlencoded Overpass QL>}. The query selects every node, way and
/// relation tagged as a speed camera or carrying a relevant enforcement tag
/// inside a bounding box around the map center, and asks for element centers so
/// ways/relations resolve to a single coordinate. The QL shape is:
///
///   [out:json][timeout:25];
///   (
///     node["highway"="speed_camera"](S,W,N,E);
///     way["highway"="speed_camera"](S,W,N,E);
///     relation["highway"="speed_camera"](S,W,N,E);
///     node["enforcement"~"maxspeed|average_speed|traffic_signals"](S,W,N,E);
///     way["enforcement"~"maxspeed|average_speed|traffic_signals"](S,W,N,E);
///     relation["enforcement"~"maxspeed|average_speed|traffic_signals"](S,W,N,E);
///   );
///   out center tags;
///
/// Multiple public mirrors are tried in order; a scrubbed failure is logged and
/// the next mirror is attempted, so a single overloaded endpoint never breaks
/// the overlay.
public final class OverpassCameraClient
{
  private static final String SUB = "cameras";

  private static final int CONNECT_TIMEOUT_MS = 10_000;
  private static final int READ_TIMEOUT_MS = 30_000;

  /// Public Overpass endpoints, tried in order until one succeeds.
  private static final String[] MIRRORS = {
      "https://overpass-api.de/api/interpreter",
      "https://overpass.kumi.systems/api/interpreter",
      "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
  };

  /// Approximate meters-per-degree of latitude (used to size the bbox). Longitude
  /// degrees are scaled by cos(lat) so the box stays roughly square on the ground.
  private static final double METERS_PER_DEG_LAT = 111_320.0;

  /// Fetches cameras within roughly {@code radiusMeters} of {@code center}. The
  /// radius defines the half-extent of the bounding box, so e.g. 10000m yields a
  /// ~20km wide / 20km tall box. Returns an empty list (never null) if all
  /// mirrors fail or no cameras are found.
  @NonNull
  public List<OverpassCamera> fetchAround(@NonNull GeoPoint center, double radiusMeters)
  {
    final String query = buildQuery(center, radiusMeters);
    final String body = "data=" + urlEncode(query);

    for (int i = 0; i < MIRRORS.length; i++)
    {
      final String mirror = MIRRORS[i];
      try
      {
        final String raw = postForm(mirror, body);
        final List<OverpassCamera> cameras = parse(raw);
        CairoLog.i(SUB, "overpass: mirror=" + mirror + " cameras=" + cameras.size());
        return cameras;
      }
      catch (IOException | JSONException e)
      {
        CairoLog.w(SUB, "overpass: mirror=" + mirror + " failed: " + e.getMessage());
      }
    }

    CairoLog.w(SUB, "overpass: all mirrors failed; returning no cameras");
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
    final String enforce = "[\"enforcement\"~\"maxspeed|average_speed|traffic_signals\"]";

    return "[out:json][timeout:25];"
        + "("
        + "node[\"highway\"=\"speed_camera\"]" + bbox + ";"
        + "way[\"highway\"=\"speed_camera\"]" + bbox + ";"
        + "relation[\"highway\"=\"speed_camera\"]" + bbox + ";"
        + "node" + enforce + bbox + ";"
        + "way" + enforce + bbox + ";"
        + "relation" + enforce + bbox + ";"
        + ");"
        + "out center tags;";
  }

  /// Parses an Overpass JSON response into cameras. Defensive throughout: any
  /// element missing usable coordinates is skipped rather than aborting.
  @NonNull
  private static List<OverpassCamera> parse(@NonNull String raw) throws JSONException
  {
    final List<OverpassCamera> out = new ArrayList<>();
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
      final long id = el.optLong("id", 0L);
      final CameraType type = CameraType.fromTags(tags);
      final int maxspeed = parseMaxspeed(tags.get("maxspeed"));
      String name = tags.get("name");
      if (name == null)
        name = "";

      out.add(new OverpassCamera(id, new GeoPoint(lat, lon), type, maxspeed, name));
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

  /// Parses an OSM maxspeed value (e.g. "80", "80 km/h", "50 mph") into a plain
  /// km/h integer by stripping non-digits. Returns 0 when absent/unparseable.
  /// Note: "mph" values are kept as the raw number (not converted), matching the
  /// requested "strip non-digits" behavior.
  private static int parseMaxspeed(String value)
  {
    if (value == null)
      return 0;
    final StringBuilder digits = new StringBuilder();
    for (int i = 0; i < value.length(); i++)
    {
      final char ch = value.charAt(i);
      if (ch >= '0' && ch <= '9')
        digits.append(ch);
      else if (digits.length() > 0)
        break; // first contiguous run of digits only
    }
    if (digits.length() == 0)
      return 0;
    try
    {
      return Integer.parseInt(digits.toString());
    }
    catch (NumberFormatException e)
    {
      return 0;
    }
  }

  /// Sends an {@code application/x-www-form-urlencoded} POST and returns the body.
  /// Overpass takes its query as form field {@code data=...}, which HttpJson's
  /// JSON-bodied POST cannot express, so we drive HttpURLConnection directly here.
  /// Still logs (scrubbed mirror URL) through CairoLog.
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
  /// carry no secrets, but we scrub defensively to match HttpJson's behavior.
  @NonNull
  private static String scrubUrl(@NonNull String url)
  {
    return url.replaceAll("(?i)([?&])(key|api_key|apikey|access_token|token|apiKey)=[^&]*", "$1$2=***");
  }
}
