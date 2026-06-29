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

/// OpenRouteService (Pelias) geocoding provider.
///
/// Endpoint:
///   GET https://api.openrouteservice.org/geocode/search
///         ?api_key=<key>&text=<text>
///         &focus.point.lat=<lat>&focus.point.lon=<lon>&size=10
///
/// The response is GeoJSON: parses features[].properties.label (address,
/// falling back to features[].properties.name) for the title, and the location
/// from features[].geometry.coordinates [lon,lat] (GeoJSON order).
///
/// NOTE: response field names / schema may need live tuning against the API.
public final class OpenRouteServiceGeocoder implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "OpenRouteService";
  private static final String BASE = "https://api.openrouteservice.org/geocode/search";
  private static final int SIZE = 10;

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
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final StringBuilder url = new StringBuilder(BASE)
        .append("?api_key=").append(CairoKeys.openRouteService())
        .append("&text=").append(encode(query))
        .append("&size=").append(SIZE);
    if (near != null)
      url.append("&focus.point.lat=").append(near.lat)
         .append("&focus.point.lon=").append(near.lon);

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
      final JSONObject props = f.optJSONObject("properties");
      final String label = props != null ? props.optString("label", "") : "";
      final String shortName = props != null ? props.optString("name", "") : "";
      // Prefer the short "name" as the title, else the full "label".
      final String name = !shortName.isEmpty() ? shortName : label;

      final GeoPoint point = extractLocation(f);
      if (point == null)
        continue;

      places.add(new OnlinePlace(name, label, point, NAME));
    }
    CairoLog.d(SUB, NAME + ": parsed " + places.size() + " places");
    return places;
  }

  /// Reads a GeoJSON [lon,lat] coordinate from geometry.coordinates[].
  @Nullable
  private static GeoPoint extractLocation(@NonNull JSONObject feature)
  {
    final JSONObject geometry = feature.optJSONObject("geometry");
    if (geometry == null)
      return null;
    final JSONArray coords = geometry.optJSONArray("coordinates");
    if (coords == null || coords.length() < 2)
      return null;
    final double lon = coords.optDouble(0, Double.NaN);
    final double lat = coords.optDouble(1, Double.NaN);
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
