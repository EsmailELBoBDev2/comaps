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

/// LocationIQ "autocomplete" provider.
///
/// Endpoint:
///   GET https://api.locationiq.com/v1/autocomplete
///         ?key=<key>&q=<text>&limit=10
///
/// The response is a JSON ARRAY; parses [].display_name, [].lat and [].lon.
/// LocationIQ returns lat/lon as strings, so they are parsed defensively.
///
/// NOTE: response field names may need tuning against the live API.
public final class LocationIqProvider implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "LocationIQ";
  private static final String BASE = "https://api.locationiq.com/v1/autocomplete";
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
    return CairoKeys.hasLocationIq();
  }

  @NonNull
  @Override
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final String url = BASE
        + "?key=" + CairoKeys.locationIq()
        + "&q=" + encode(query)
        + "&limit=" + LIMIT;

    final JSONArray results = HttpJson.getArray(url, HttpJson.noHeaders());
    final List<OnlinePlace> places = new ArrayList<>();

    for (int i = 0; i < results.length(); i++)
    {
      final JSONObject r = results.optJSONObject(i);
      if (r == null)
        continue;
      final String display = r.optString("display_name", "");
      final Double lat = parseCoord(r.opt("lat"));
      final Double lon = parseCoord(r.opt("lon"));
      if (lat == null || lon == null)
        continue;
      final GeoPoint point = new GeoPoint(lat, lon);
      places.add(new OnlinePlace(display, display, point, NAME));
    }
    CairoLog.d(SUB, NAME + ": parsed " + places.size() + " places");
    return places;
  }

  /// LocationIQ encodes coordinates as strings; tolerate both string and number.
  @Nullable
  private static Double parseCoord(@Nullable Object value)
  {
    if (value == null)
      return null;
    if (value instanceof Number)
      return ((Number) value).doubleValue();
    try
    {
      return Double.parseDouble(value.toString());
    }
    catch (NumberFormatException e)
    {
      return null;
    }
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
