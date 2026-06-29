package app.organicmaps.cairodrive.mapillary;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Immutable description of a Mapillary CV-detected map feature, specifically a
/// traffic sign (speed limits, signals, etc.).
///
/// Sourced from the Mapillary Graph API:
///   GET https://graph.mapillary.com/map_features
/// Fields: id, object_value, geometry.
///
/// {@code objectValue} is Mapillary's taxonomy key for the detection, e.g.
/// "regulatory--maximum-speed-limit--g1". Contains no secrets; safe to log.
public final class MapillarySign
{
  /// Mapillary map-feature id (stable string identifier).
  @NonNull
  public final String id;
  /// Mapillary taxonomy value for the detected sign, e.g.
  /// "regulatory--maximum-speed-limit--g1". May be empty when absent.
  @NonNull
  public final String objectValue;
  /// Where the sign was detected (from geometry.coordinates [lon,lat]).
  @NonNull
  public final GeoPoint location;

  public MapillarySign(@NonNull String id, @NonNull String objectValue, @NonNull GeoPoint location)
  {
    this.id = id;
    this.objectValue = objectValue;
    this.location = location;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "MapillarySign{id=" + id + ", objectValue='" + objectValue + "', at=" + location + "}";
  }
}
