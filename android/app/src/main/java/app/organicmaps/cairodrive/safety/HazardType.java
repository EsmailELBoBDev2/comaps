package app.organicmaps.cairodrive.safety;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

/// Classification of an OSM road safety hazard, with a distinct overlay color and
/// a human-readable label per type.
///
/// Classification is derived from OSM tags via {@link #fromTags(Map)}. The
/// relevant tagging schemes are:
///   - traffic_calming=bump|hump|table|cushion  -> speed bump / calming device
///   - amenity=school / amenity=kindergarten    -> school zone (slow / children)
///   - hazard=curve / hazard:type=curve         -> dangerous curve
///   - highway=turning_circle                   -> dangerous curve (tight turn)
///   - railway=level_crossing / railway=crossing-> railway crossing
///
/// Colors are 0xAARRGGBB ints so they can be handed straight to the renderer.
public enum HazardType
{
  SPEED_BUMP(0xFFFFB300, "Speed bump"),
  SCHOOL_ZONE(0xFF43A047, "School zone"),
  DANGEROUS_CURVE(0xFFE53935, "Dangerous curve"),
  RAILWAY_CROSSING(0xFF6D4C41, "Railway crossing"),
  UNKNOWN(0xFF9E9E9E, "Hazard");

  private final int mColorArgb;
  @NonNull
  private final String mLabel;

  HazardType(int colorArgb, @NonNull String label)
  {
    mColorArgb = colorArgb;
    mLabel = label;
  }

  /// ARGB (0xAARRGGBB) overlay color for this hazard type.
  public int colorArgb()
  {
    return mColorArgb;
  }

  @NonNull
  public String label()
  {
    return mLabel;
  }

  /// Classifies an OSM element's tags into a {@link HazardType}. Tolerant of a
  /// null/empty map (returns {@link #UNKNOWN}) and of missing individual tags.
  @NonNull
  public static HazardType fromTags(@Nullable Map<String, String> tags)
  {
    if (tags == null || tags.isEmpty())
      return UNKNOWN;

    final String trafficCalming = lower(tags.get("traffic_calming"));
    final String amenity = lower(tags.get("amenity"));
    final String hazard = lower(tags.get("hazard"));
    final String hazardType = lower(tags.get("hazard:type"));
    final String highway = lower(tags.get("highway"));
    final String railway = lower(tags.get("railway"));

    // Speed bumps / calming devices.
    if ("bump".equals(trafficCalming) || "hump".equals(trafficCalming)
        || "table".equals(trafficCalming) || "cushion".equals(trafficCalming))
      return SPEED_BUMP;

    // School zones (schools and kindergartens).
    if ("school".equals(amenity) || "kindergarten".equals(amenity))
      return SCHOOL_ZONE;

    // Dangerous curves: explicit hazard tags or a tight turning circle.
    if ("curve".equals(hazard) || "curve".equals(hazardType) || "turning_circle".equals(highway))
      return DANGEROUS_CURVE;

    // Railway level crossings.
    if ("level_crossing".equals(railway) || "crossing".equals(railway))
      return RAILWAY_CROSSING;

    return UNKNOWN;
  }

  @Nullable
  private static String lower(@Nullable String s)
  {
    return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
  }
}
