package app.organicmaps.cairodrive.reports;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/// A single community report (Waze-style). Stored locally only -- the legal
/// substitute for Egypt mobile-radar/police data that no provider sells.
///
/// Mobile/transient kinds expire (ttlMinutes > 0); fixed hazards never do
/// (ttlMinutes == 0).
public final class CairoReport
{
  public enum Kind
  {
    CAMERA(0xFFD32F2F, "Camera", 0),          // fixed - never expires
    RADAR(0xFF1565C0, "Mobile radar", 60),    // ~1h
    POLICE(0xFF6A1B9A, "Police (كمين)", 60),
    BUMP(0xFFF9A825, "Speed bump", 0),
    POTHOLE(0xFF5D4037, "Pothole", 0),
    HAZARD(0xFFE65100, "Hazard", 30);

    public final int colorArgb;
    @NonNull public final String label;
    public final int ttlMinutes;  // 0 = permanent

    Kind(int colorArgb, @NonNull String label, int ttlMinutes)
    {
      this.colorArgb = colorArgb;
      this.label = label;
      this.ttlMinutes = ttlMinutes;
    }

    @NonNull
    static Kind fromName(@Nullable String name)
    {
      if (name != null)
      {
        for (Kind k : values())
        {
          if (k.name().equals(name))
            return k;
        }
      }
      return HAZARD;
    }
  }

  @NonNull public final Kind kind;
  public final double lat;
  public final double lon;
  public final long createdAtMs;

  public CairoReport(@NonNull Kind kind, double lat, double lon, long createdAtMs)
  {
    this.kind = kind;
    this.lat = lat;
    this.lon = lon;
    this.createdAtMs = createdAtMs;
  }

  /// Expired when a TTL is set and elapsed. Permanent kinds (ttl == 0) never expire.
  public boolean isExpired(long nowMs)
  {
    return kind.ttlMinutes > 0 && nowMs - createdAtMs > kind.ttlMinutes * 60_000L;
  }

  @NonNull
  JSONObject toJson() throws JSONException
  {
    final JSONObject o = new JSONObject();
    o.put("kind", kind.name());
    o.put("lat", lat);
    o.put("lon", lon);
    o.put("t", createdAtMs);
    return o;
  }

  @NonNull
  static CairoReport fromJson(@NonNull JSONObject o)
  {
    return new CairoReport(Kind.fromName(o.optString("kind", null)), o.optDouble("lat", 0), o.optDouble("lon", 0),
                           o.optLong("t", 0));
  }
}
