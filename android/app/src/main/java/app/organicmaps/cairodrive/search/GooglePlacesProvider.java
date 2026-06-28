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

/// Google Places "Text Search" provider.
///
/// Endpoint:
///   GET https://maps.googleapis.com/maps/api/place/textsearch/json
///         ?query=<text>&location=<lat>,<lon>&key=<key>
///
/// Parses results[].name, results[].formatted_address and
/// results[].geometry.location.lat / .lng.
///
/// NOTE: response field names may need tuning against the live API.
public final class GooglePlacesProvider implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "Google";
  private static final String BASE = "https://maps.googleapis.com/maps/api/place/textsearch/json";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasGooglePlaces();
  }

  @NonNull
  @Override
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final StringBuilder url = new StringBuilder(BASE)
        .append("?query=").append(encode(query));
    if (near != null)
      url.append("&location=").append(near.lat).append(',').append(near.lon);
    url.append("&key=").append(CairoKeys.googlePlaces());

    final JSONObject root = HttpJson.getObject(url.toString(), HttpJson.noHeaders());
    final JSONArray results = root.optJSONArray("results");
    final List<OnlinePlace> places = new ArrayList<>();
    if (results == null)
      return places;

    for (int i = 0; i < results.length(); i++)
    {
      final JSONObject r = results.optJSONObject(i);
      if (r == null)
        continue;
      final String name = r.optString("name", "");
      final String address = r.optString("formatted_address", "");
      final JSONObject geometry = r.optJSONObject("geometry");
      final JSONObject loc = geometry != null ? geometry.optJSONObject("location") : null;
      if (loc == null)
        continue;
      if (!loc.has("lat") || !loc.has("lng"))
        continue;
      final GeoPoint point = new GeoPoint(loc.optDouble("lat", 0.0), loc.optDouble("lng", 0.0));
      places.add(new OnlinePlace(name, address, point, NAME));
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
      // UTF-8 is always supported; fall back to the raw string.
      return s;
    }
  }
}
