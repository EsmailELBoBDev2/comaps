package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/// Classification of an OSM speed/enforcement camera, with a distinct overlay
/// color and a human-readable label per type.
///
/// Classification is derived from OSM tags via {@link #fromTags(Map)}. The
/// relevant tagging schemes are:
///   - highway=speed_camera                  -> fixed speed camera node
///   - enforcement=maxspeed                  -> speed enforcement (fixed)
///   - enforcement=average_speed             -> section/average-speed control
///   - highway=traffic_signals + enforcement -> red-light camera
///   - enforcement=traffic_signals|red_light -> red-light camera
///   - speed_camera=mobile / man_made=mobile -> mobile/temporary camera
///
/// Colors are 0xAARRGGBB ints so they can be handed straight to the renderer.
public enum CameraType
{
  FIXED(0xFFE53935, "Fixed speed camera"),
  RED_LIGHT(0xFFFB8C00, "Red-light camera"),
  AVERAGE_SPEED(0xFF8E24AA, "Average-speed camera"),
  MOBILE(0xFF1E88E5, "Mobile speed camera"),
  UNKNOWN(0xFF9E9E9E, "Speed camera");

  private final int mColorArgb;
  @NonNull
  private final String mLabel;

  CameraType(int colorArgb, @NonNull String label)
  {
    mColorArgb = colorArgb;
    mLabel = label;
  }

  /// ARGB (0xAARRGGBB) overlay color for this camera type.
  public int colorArgb()
  {
    return mColorArgb;
  }

  @NonNull
  public String label()
  {
    return mLabel;
  }

  /// Classifies an OSM element's tags into a {@link CameraType}. Tolerant of a
  /// null/empty map (returns {@link #UNKNOWN}) and of missing individual tags.
  @NonNull
  public static CameraType fromTags(@Nullable Map<String, String> tags)
  {
    if (tags == null || tags.isEmpty())
      return UNKNOWN;

    final String highway = lower(tags.get("highway"));
    final String enforcement = lower(tags.get("enforcement"));
    final String speedCamera = lower(tags.get("speed_camera"));
    final String manMade = lower(tags.get("man_made"));
    final String role = lower(tags.get("role"));

    // Average / section speed control. A node may either carry
    // enforcement=average_speed directly, or play a device role inside an
    // enforcement section relation.
    if ("average_speed".equals(enforcement) || "average_speed".equals(role) || "section".equals(role))
      return AVERAGE_SPEED;

    // Red-light cameras: signals with enforcement, or an explicit
    // enforcement=traffic_signals / red_light tag.
    if ("traffic_signals".equals(highway) && enforcement != null)
      return RED_LIGHT;
    if ("traffic_signals".equals(enforcement) || "red_light".equals(enforcement) || "red_lights".equals(enforcement))
      return RED_LIGHT;

    // Mobile / temporary cameras.
    if ("mobile".equals(speedCamera) || "mobile".equals(manMade) || "mobile".equals(enforcement))
      return MOBILE;

    // Plain fixed speed cameras.
    if ("speed_camera".equals(highway) || "maxspeed".equals(enforcement))
      return FIXED;

    return UNKNOWN;
  }

  @Nullable
  private static String lower(@Nullable String s)
  {
    return s == null ? null : s.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
