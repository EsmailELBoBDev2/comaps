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

/// Geoapify Geocoding "autocomplete" provider.
///
/// Endpoint:
///   GET https://api.geoapify.com/v1/geocode/autocomplete
///         ?text=<text>&bias=proximity:<lon>,<lat>&apiKey=<key>
///
/// Parses features[].properties.formatted (and .name when present) plus
/// features[].properties.lat / .lon. Note Geoapify orders the proximity bias as
/// lon,lat.
///
/// NOTE: response field names may need tuning against the live API.
public final class GeoapifyProvider implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "Geoapify";
  private static final String BASE = "https://api.geoapify.com/v1/geocode/autocomplete";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasGeoapify();
  }

  @NonNull
  @Override
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final StringBuilder url = new StringBuilder(BASE)
        .append("?text=").append(encode(query));
    if (near != null)
      url.append("&bias=").append(encode("proximity:" + near.lon + "," + near.lat));
    url.append("&apiKey=").append(CairoKeys.geoapify());

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
      if (props == null)
        continue;
      if (!props.has("lat") || !props.has("lon"))
        continue;
      final String formatted = props.optString("formatted", "");
      // Prefer an explicit name when present, else fall back to the formatted address.
      final String name = props.optString("name", formatted);
      final GeoPoint point = new GeoPoint(props.optDouble("lat", 0.0), props.optDouble("lon", 0.0));
      places.add(new OnlinePlace(name, formatted, point, NAME));
    }
    CairoLog.d(SUB, NAME + ": parsed " + places.size() + " places");
    return places;
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
      return s;
    }
  }
}
