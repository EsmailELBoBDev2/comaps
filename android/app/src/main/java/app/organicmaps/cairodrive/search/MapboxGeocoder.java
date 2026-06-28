package app.organicmaps.cairodrive.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/// Mapbox Geocoding provider.
///
/// Endpoint (v5 forward geocoding):
///   GET https://api.mapbox.com/geocoding/v5/mapbox.places/<query>.json
///         ?access_token=<key>&proximity=<lon>,<lat>&limit=10
///
/// The query is part of the URL path, so it is URL-encoded. Parses
/// features[].text (name), features[].place_name (address) and the location
/// from features[].center [lon,lat], falling back to
/// features[].geometry.coordinates [lon,lat]. Note Mapbox orders coordinates
/// lon,lat (GeoJSON order).
///
/// NOTE: response field names / schema may need live tuning against the API.
public final class MapboxGeocoder implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "Mapbox";
  private static final String BASE = "https://api.mapbox.com/geocoding/v5/mapbox.places/";
  private static final int LIMIT = 10;

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
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final StringBuilder url = new StringBuilder(BASE)
        .append(encode(query)).append(".json")
        .append("?access_token=").append(CairoKeys.mapbox())
        .append("&limit=").append(LIMIT);
    // Mapbox proximity bias is lon,lat (GeoJSON order).
    if (near != null)
      url.append("&proximity=").append(near.lon).append(',').append(near.lat);

    final JSONObject root = HttpJson.getObject(url.toString(), HttpJson.noHeaders());
    final JSONArray features = root.optJSONArray("features");
    final List<OnlinePlace> places = new ArrayList<>();
    if (features == null)
      return places;

    for (int i = 0; i < features.length(); i++)
    {
      final JSONObject f = features.optJSONObject(i);
      if (f == null)
        continue;
      final String placeName = f.optString("place_name", "");
      // Prefer the short "text" as the title, else the full place_name.
      final String name = f.optString("text", placeName);

      final GeoPoint point = extractLocation(f);
      if (point == null)
        continue;

      places.add(new OnlinePlace(name, placeName, point, NAME));
    }
    CairoLog.d(SUB, NAME + ": parsed " + places.size() + " places");
    return places;
  }

  /// Reads a [lon,lat] coordinate from center[], falling back to
  /// geometry.coordinates[]. Returns null when neither is usable.
  @Nullable
  private static GeoPoint extractLocation(@NonNull JSONObject feature)
  {
    final JSONArray center = feature.optJSONArray("center");
    final GeoPoint fromCenter = fromLonLat(center);
    if (fromCenter != null)
      return fromCenter;

    final JSONObject geometry = feature.optJSONObject("geometry");
    if (geometry != null)
      return fromLonLat(geometry.optJSONArray("coordinates"));
    return null;
  }

  /// Builds a GeoPoint from a GeoJSON [lon,lat] array; null when malformed.
  @Nullable
  private static GeoPoint fromLonLat(@Nullable JSONArray lonLat)
  {
    if (lonLat == null || lonLat.length() < 2)
      return null;
    final double lon = lonLat.optDouble(0, Double.NaN);
    final double lat = lonLat.optDouble(1, Double.NaN);
    if (Double.isNaN(lon) || Double.isNaN(lat))
      return null;
    return new GeoPoint(lat, lon);
  }

  @NonNull
  private static String encode(@NonNull String s)
  {
    try
    {
      return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    }
    catch (UnsupportedEncodingException e)
    {
      // UTF-8 is always supported; fall back to the raw string.
      return s;
    }
  }
}
