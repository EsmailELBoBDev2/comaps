package app.organicmaps.cairodrive.safety;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Immutable description of a single road safety hazard fetched from the Overpass
/// API. Contains no secrets; safe to log via {@link #toString()}.
public final class Hazard
{
  /// OSM element id (node/way/relation id). Not globally unique across element
  /// types, but stable enough for de-duplication within a single fetch.
  public final long id;
  @NonNull
  public final GeoPoint location;
  @NonNull
  public final HazardType type;
  @NonNull
  public final String name;

  public Hazard(long id, @NonNull GeoPoint location, @NonNull HazardType type, @NonNull String name)
  {
    this.id = id;
    this.location = location;
    this.type = type;
    this.name = name;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "Hazard{id=" + id + ", type=" + type + ", at=" + location + ", name='" + name + "'}";
  }
}
