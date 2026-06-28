package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Immutable description of a single speed/enforcement camera fetched from the
/// Overpass API. Contains no secrets; safe to log via {@link #toString()}.
public final class OverpassCamera
{
  /// OSM element id (node/way/relation id). Not globally unique across element
  /// types, but stable enough for de-duplication within a single fetch.
  public final long id;
  @NonNull
  public final GeoPoint location;
  @NonNull
  public final CameraType type;
  /// Posted speed limit in km/h, or 0 when unknown/unparseable.
  public final int maxspeedKmh;
  @NonNull
  public final String name;

  public OverpassCamera(long id, @NonNull GeoPoint location, @NonNull CameraType type, int maxspeedKmh,
                        @NonNull String name)
  {
    this.id = id;
    this.location = location;
    this.type = type;
    this.maxspeedKmh = maxspeedKmh;
    this.name = name;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "OverpassCamera{id=" + id + ", type=" + type + ", at=" + location + ", maxspeed="
        + (maxspeedKmh > 0 ? maxspeedKmh + "km/h" : "?") + ", name='" + name + "'}";
  }
}
