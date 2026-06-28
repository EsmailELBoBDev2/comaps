package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import app.organicmaps.sdk.util.log.CairoLog;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/// Serializes fetched cameras to a GeoJSON {@code FeatureCollection} for the
/// dev-tools camera export. Each camera becomes a Point feature:
///
///   {
///     "type": "FeatureCollection",
///     "features": [
///       {
///         "type": "Feature",
///         "geometry": { "type": "Point", "coordinates": [lon, lat] },
///         "properties": { "id": 123, "type": "Fixed speed camera",
///                         "maxspeed": 80 }
///       }
///     ]
///   }
///
/// Note GeoJSON coordinate order is [lon, lat].
public final class CameraGeoJsonExporter
{
  private static final String SUB = "cameras";

  private CameraGeoJsonExporter() {}

  @NonNull
  public static String toGeoJson(@NonNull List<OverpassCamera> cams)
  {
    final JSONObject root = new JSONObject();
    final JSONArray features = new JSONArray();
    try
    {
      root.put("type", "FeatureCollection");
      for (final OverpassCamera cam : cams)
      {
        if (cam == null)
          continue;

        final JSONArray coordinates = new JSONArray();
        coordinates.put(cam.location.lon);
        coordinates.put(cam.location.lat);

        final JSONObject geometry = new JSONObject();
        geometry.put("type", "Point");
        geometry.put("coordinates", coordinates);

        final JSONObject properties = new JSONObject();
        properties.put("id", cam.id);
        properties.put("type", cam.type.label());
        properties.put("maxspeed", cam.maxspeedKmh);
        if (!cam.name.isEmpty())
          properties.put("name", cam.name);

        final JSONObject feature = new JSONObject();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);

        features.put(feature);
      }
      root.put("features", features);
    }
    catch (JSONException e)
    {
      // org.json only throws on null keys / NaN numbers; should not happen with
      // validated cameras, but never let the dev export crash the caller.
      CairoLog.e(SUB, "geojson export failed", e);
    }
    return root.toString();
  }
}
