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

/// HERE Geocoding & Search ("Discover") provider.
///
/// Endpoint:
///   GET https://discover.search.hereapi.com/v1/discover
///         ?at=<lat>,<lon>&q=<text>&apiKey=<key>
///
/// Parses items[].title, items[].address.label and
/// items[].position.lat / .lng.
///
/// NOTE: response field names may need tuning against the live API. HERE's
/// Discover endpoint requires an {@code at} bias; when {@code near} is null we
/// omit it, which the API may reject.
public final class HereProvider implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "HERE";
  private static final String BASE = "https://discover.search.hereapi.com/v1/discover";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasHere();
  }

  @NonNull
  @Override
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final StringBuilder url = new StringBuilder(BASE).append('?');
    if (near != null)
      url.append("at=").append(near.lat).append(',').append(near.lon).append('&');
    url.append("q=").append(encode(query))
       .append("&apiKey=").append(CairoKeys.here());

    final JSONObject root = HttpJson.getObject(url.toString(), HttpJson.noHeaders());
    final JSONArray items = root.optJSONArray("items");
    final List<OnlinePlace> places = new ArrayList<>();
    if (items == null)
      return places;

    for (int i = 0; i < items.length(); i++)
    {
      final JSONObject it = items.optJSONObject(i);
      if (it == null)
        continue;
      final String name = it.optString("title", "");
      final JSONObject address = it.optJSONObject("address");
      final String label = address != null ? address.optString("label", "") : "";
      final JSONObject pos = it.optJSONObject("position");
      if (pos == null)
        continue;
      if (!pos.has("lat") || !pos.has("lng"))
        continue;
      final GeoPoint point = new GeoPoint(pos.optDouble("lat", 0.0), pos.optDouble("lng", 0.0));
      places.add(new OnlinePlace(name, label, point, NAME));
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
