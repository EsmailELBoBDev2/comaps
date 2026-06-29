package app.organicmaps.cairodrive.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// Google Places API (NEW) "Text Search" provider.
///
/// Endpoint:
///   POST https://places.googleapis.com/v1/places:searchText
///   Headers: X-Goog-Api-Key: <key>
///            X-Goog-FieldMask: places.displayName,places.formattedAddress,places.location
///   Body: {"textQuery":"...","locationBias":{"circle":{"center":{...},"radius":...}}}
///
/// We deliberately use the NEW API because keys provisioned today enable
/// "Places API (New)"; the legacy /maps/api/place/textsearch/json endpoint
/// returns REQUEST_DENIED for them. Response: places[].displayName.text,
/// places[].formattedAddress, places[].location.latitude/longitude.
public final class GooglePlacesProvider implements SearchProvider
{
  private static final String SUB = "search";
  private static final String NAME = "Google";
  private static final String ENDPOINT = "https://places.googleapis.com/v1/places:searchText";
  private static final double BIAS_RADIUS_M = 30_000.0;

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
    final Map<String, String> headers = new HashMap<>();
    headers.put("X-Goog-Api-Key", CairoKeys.googlePlaces());
    headers.put("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location");

    final JSONObject root = HttpJson.postObject(ENDPOINT, headers, buildBody(query, near));

    final List<OnlinePlace> places = new ArrayList<>();
    final JSONArray results = root.optJSONArray("places");
    if (results == null)
      return places;

    for (int i = 0; i < results.length(); i++)
    {
      final JSONObject r = results.optJSONObject(i);
      if (r == null)
        continue;
      final JSONObject displayName = r.optJSONObject("displayName");
      final String name = displayName != null ? displayName.optString("text", "") : "";
      final String address = r.optString("formattedAddress", "");
      final JSONObject loc = r.optJSONObject("location");
      if (loc == null || !loc.has("latitude") || !loc.has("longitude"))
        continue;
      final GeoPoint point = new GeoPoint(loc.optDouble("latitude", 0.0), loc.optDouble("longitude", 0.0));
      places.add(new OnlinePlace(name, address, point, NAME));
    }
    CairoLog.d(SUB, NAME + ": parsed " + places.size() + " places");
    return places;
  }

  @NonNull
  private static String buildBody(@NonNull String query, @Nullable GeoPoint near) throws IOException
  {
    try
    {
      final JSONObject body = new JSONObject();
      body.put("textQuery", query);
      if (near != null)
      {
        final JSONObject center = new JSONObject().put("latitude", near.lat).put("longitude", near.lon);
        final JSONObject circle = new JSONObject().put("center", center).put("radius", BIAS_RADIUS_M);
        body.put("locationBias", new JSONObject().put("circle", circle));
      }
      return body.toString();
    }
    catch (JSONException e)
    {
      throw new IOException("Failed to build Google Places request", e);
    }
  }
}
