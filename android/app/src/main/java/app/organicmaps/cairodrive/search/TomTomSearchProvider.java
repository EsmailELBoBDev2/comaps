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

/// TomTom "Fuzzy Search" provider.
///
/// Endpoint:
///   GET https://api.tomtom.com/search/2/search/<query>.json
///         ?key=<key>&lat=<lat>&lon=<lon>
///
/// The query is part of the URL path, so it is URL-encoded. Parses
/// results[].poi.name (falling back to results[].address.freeformAddress),
/// results[].address.freeformAddress and results[].position.lat / .lon.
///
/// NOTE: response field names may need tuning against the live API.
public final class TomTomSearchProvider implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "TomTom";
  private static final String BASE = "https://api.tomtom.com/search/2/search/";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasTomTom();
  }

  @NonNull
  @Override
  public List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    final StringBuilder url = new StringBuilder(BASE)
        .append(encode(query)).append(".json")
        .append("?key=").append(CairoKeys.tomTom());
    if (near != null)
      url.append("&lat=").append(near.lat).append("&lon=").append(near.lon);

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
      final JSONObject address = r.optJSONObject("address");
      final String freeform = address != null ? address.optString("freeformAddress", "") : "";
      final JSONObject poi = r.optJSONObject("poi");
      final String poiName = poi != null ? poi.optString("name", "") : "";
      final String name = !poiName.isEmpty() ? poiName : freeform;
      final JSONObject pos = r.optJSONObject("position");
      if (pos == null)
        continue;
      if (!pos.has("lat") || !pos.has("lon"))
        continue;
      final GeoPoint point = new GeoPoint(pos.optDouble("lat", 0.0), pos.optDouble("lon", 0.0));
      places.add(new OnlinePlace(name, freeform, point, NAME));
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
