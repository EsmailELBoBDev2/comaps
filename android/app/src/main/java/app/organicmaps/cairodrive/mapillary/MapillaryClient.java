package app.organicmaps.cairodrive.mapillary;

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

/// CairoDrive client for Mapillary street-level imagery and CV-detected traffic
/// signs (a Google-Street-View-style overlay, an idea borrowed from Google
/// Maps). Uses only HttpURLConnection + org.json via {@link HttpJson}; adds no
/// dependencies.
///
/// Auth is by query parameter ({@code access_token=...}); no header is needed.
/// The token is never logged: HttpJson scrubs it from request URLs, and this
/// client only logs result counts.
///
/// Endpoints (Mapillary Graph API):
///   GET https://graph.mapillary.com/images
///         ?access_token=<token>
///         &fields=id,thumb_1024_url,computed_geometry,captured_at
///         &bbox=<minLon>,<minLat>,<maxLon>,<maxLat>
///         &limit=30
///   GET https://graph.mapillary.com/map_features
///         ?access_token=<token>
///         &fields=id,object_value,geometry
///         &bbox=<minLon>,<minLat>,<maxLon>,<maxLat>
///         &limit=50
///
/// Both responses carry results under a top-level {@code data} array. Parsing is
/// defensive throughout: any malformed/incomplete entry is skipped rather than
/// aborting the whole fetch.
public final class MapillaryClient
{
  private static final String SUB = "mapillary";

  private static final String IMAGES_BASE = "https://graph.mapillary.com/images";
  private static final String FEATURES_BASE = "https://graph.mapillary.com/map_features";

  private static final int IMAGES_LIMIT = 30;
  private static final int FEATURES_LIMIT = 50;

  /// Approximate meters-per-degree of latitude (used to size the bbox). Longitude
  /// degrees are scaled by cos(lat) so the box stays roughly square on the ground.
  private static final double METERS_PER_DEG_LAT = 111_320.0;

  /// True when a Mapillary access token is configured. Callers should skip this
  /// client entirely when false so the app degrades gracefully.
  public boolean isAvailable()
  {
    return CairoKeys.hasMapillary();
  }

  /// Fetches street-level images within roughly {@code radiusMeters} of
  /// {@code center}. {@code radiusMeters} is the bbox half-extent. Returns an
  /// empty list (never null) when unavailable or on any error.
  @NonNull
  public List<MapillaryImage> imagesNear(@NonNull GeoPoint center, double radiusMeters) throws IOException
  {
    final List<MapillaryImage> out = new ArrayList<>();
    if (!isAvailable())
    {
      CairoLog.w(SUB, "mapillary: no access token; skipping images");
      return out;
    }

    final String url = IMAGES_BASE
        + "?access_token=" + CairoKeys.mapillary()
        + "&fields=id,thumb_1024_url,computed_geometry,captured_at"
        + "&bbox=" + bbox(center, radiusMeters)
        + "&limit=" + IMAGES_LIMIT;

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    final JSONArray data = root.optJSONArray("data");
    if (data != null)
    {
      for (int i = 0; i < data.length(); i++)
      {
        final MapillaryImage img = parseImage(data.optJSONObject(i));
        if (img != null)
          out.add(img);
      }
    }
    CairoLog.i(SUB, "mapillary: images=" + out.size());
    return out;
  }

  /// Fetches CV-detected traffic signs (speed limits, signals, etc.) within
  /// roughly {@code radiusMeters} of {@code center}. Returns an empty list
  /// (never null) when unavailable or on any error.
  @NonNull
  public List<MapillarySign> trafficSignsNear(@NonNull GeoPoint center, double radiusMeters) throws IOException
  {
    final List<MapillarySign> out = new ArrayList<>();
    if (!isAvailable())
    {
      CairoLog.w(SUB, "mapillary: no access token; skipping signs");
      return out;
    }

    final String url = FEATURES_BASE
        + "?access_token=" + CairoKeys.mapillary()
        + "&fields=id,object_value,geometry"
        + "&bbox=" + bbox(center, radiusMeters)
        + "&limit=" + FEATURES_LIMIT;

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    final JSONArray data = root.optJSONArray("data");
    if (data != null)
    {
      for (int i = 0; i < data.length(); i++)
      {
        final MapillarySign sign = parseSign(data.optJSONObject(i));
        if (sign != null)
          out.add(sign);
      }
    }
    CairoLog.i(SUB, "mapillary: signs=" + out.size());
    return out;
  }

  /// Parses one image entry. Returns null when the id or coordinates are missing
  /// or unparseable.
  private static MapillaryImage parseImage(JSONObject el)
  {
    if (el == null)
      return null;
    final String id = el.optString("id", "");
    if (id.isEmpty())
      return null;

    final GeoPoint location = parseGeometry(el.optJSONObject("computed_geometry"));
    if (location == null)
      return null;

    final String thumb = el.optString("thumb_1024_url", "");
    final long capturedAt = el.optLong("captured_at", 0L);
    return new MapillaryImage(id, location, thumb, capturedAt);
  }

  /// Parses one map-feature (traffic sign) entry. Returns null when the id or
  /// coordinates are missing or unparseable.
  private static MapillarySign parseSign(JSONObject el)
  {
    if (el == null)
      return null;
    final String id = el.optString("id", "");
    if (id.isEmpty())
      return null;

    final GeoPoint location = parseGeometry(el.optJSONObject("geometry"));
    if (location == null)
      return null;

    final String objectValue = el.optString("object_value", "");
    return new MapillarySign(id, objectValue, location);
  }

  /// Reads a GeoJSON-style {@code {"coordinates":[lon,lat], ...}} object into a
  /// GeoPoint. Returns null when coordinates are absent or malformed.
  private static GeoPoint parseGeometry(JSONObject geometry)
  {
    if (geometry == null)
      return null;
    final JSONArray coords = geometry.optJSONArray("coordinates");
    if (coords == null || coords.length() < 2)
      return null;
    final double lon = coords.optDouble(0, Double.NaN);
    final double lat = coords.optDouble(1, Double.NaN);
    if (Double.isNaN(lat) || Double.isNaN(lon))
      return null;
    return new GeoPoint(lat, lon);
  }

  /// Builds a Mapillary bbox string {@code minLon,minLat,maxLon,maxLat} of
  /// half-extent {@code radiusMeters} around {@code center}. Longitude is scaled
  /// by cos(lat) so the box stays roughly square on the ground.
  @NonNull
  private static String bbox(@NonNull GeoPoint center, double radiusMeters)
  {
    final double dLat = radiusMeters / METERS_PER_DEG_LAT;
    final double cosLat = Math.max(0.01, Math.cos(Math.toRadians(center.lat)));
    final double dLon = radiusMeters / (METERS_PER_DEG_LAT * cosLat);

    final double minLat = clampLat(center.lat - dLat);
    final double maxLat = clampLat(center.lat + dLat);
    final double minLon = center.lon - dLon;
    final double maxLon = center.lon + dLon;

    return String.format(Locale.US, "%f,%f,%f,%f", minLon, minLat, maxLon, maxLat);
  }

  private static double clampLat(double lat)
  {
    return Math.max(-90.0, Math.min(90.0, lat));
  }
}
