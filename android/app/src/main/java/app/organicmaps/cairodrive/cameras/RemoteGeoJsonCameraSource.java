package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.CairoKeys;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.net.HttpJson;
import app.organicmaps.cairodrive.util.Geo;
import app.organicmaps.sdk.util.log.CairoLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/// {@link CameraSource} backed by a remote GeoJSON FeatureCollection whose URL
/// comes from the gitignored secrets (BuildConfig.CAMERA_DATASET_URL). Lets a
/// curated Egypt camera dataset (community-maintained or your own server)
/// SUPPLEMENT Overpass. Inert until a URL is configured ({@link #isAvailable()}
/// is false), so the app stays OSM-only out of the box.
///
/// Expected GeoJSON: Point features with [lon, lat] coordinates and optional
/// properties: {@code camera_type} (fixed|red_light|average|mobile),
/// {@code maxspeed} (int km/h), {@code name}. Merged + deduped with the other
/// sources by the aggregator (same spot => hidden).
public final class RemoteGeoJsonCameraSource implements CameraSource
{
  private static final String NAME = "Dataset";
  private static final String SUB = "cameras";

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return CairoKeys.hasCameraDataset();
  }

  @NonNull
  @Override
  public List<OverpassCamera> cameras(@NonNull GeoPoint center, double radiusMeters) throws IOException
  {
    final List<OverpassCamera> out = new ArrayList<>();
    final String url = CairoKeys.cameraDatasetUrl();
    if (url.isEmpty())
      return out;

    final JSONObject root = HttpJson.getObject(url, HttpJson.noHeaders());
    final JSONArray features = root.optJSONArray("features");
    if (features == null)
      return out;

    long synthetic = 0;
    for (int i = 0; i < features.length(); i++)
    {
      final JSONObject f = features.optJSONObject(i);
      if (f == null)
        continue;
      final JSONObject geom = f.optJSONObject("geometry");
      if (geom == null || !"Point".equals(geom.optString("type")))
        continue;
      final JSONArray coords = geom.optJSONArray("coordinates");
      if (coords == null || coords.length() < 2)
        continue;
      // GeoJSON coordinate order is [lon, lat].
      final double lon = coords.optDouble(0, Double.NaN);
      final double lat = coords.optDouble(1, Double.NaN);
      if (Double.isNaN(lat) || Double.isNaN(lon))
        continue;
      final GeoPoint at = new GeoPoint(lat, lon);
      if (Geo.haversineMeters(center, at) > radiusMeters)
        continue;

      final JSONObject props = f.optJSONObject("properties");
      final CameraType type = typeOf(props);
      final int maxspeed = props != null ? props.optInt("maxspeed", 0) : 0;
      final String name = props != null ? props.optString("name", "") : "";
      // Negative synthetic id keeps these from colliding with OSM element ids.
      out.add(new OverpassCamera(-(1_000_000L + synthetic++), at, type, Math.max(0, maxspeed), name));
    }
    CairoLog.i(SUB, "dataset cameras=" + out.size());
    return out;
  }

  @NonNull
  private static CameraType typeOf(@Nullable JSONObject props)
  {
    if (props == null)
      return CameraType.UNKNOWN;
    final String t = props.optString("camera_type", "").trim().toLowerCase(Locale.ROOT);
    switch (t)
    {
      case "fixed":
        return CameraType.FIXED;
      case "red_light":
      case "redlight":
        return CameraType.RED_LIGHT;
      case "average":
      case "average_speed":
        return CameraType.AVERAGE_SPEED;
      case "mobile":
        return CameraType.MOBILE;
      default:
        return CameraType.UNKNOWN;
    }
  }
}
