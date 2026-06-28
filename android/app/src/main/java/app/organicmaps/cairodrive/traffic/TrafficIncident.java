package app.organicmaps.cairodrive.traffic;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Immutable description of a single traffic incident returned by a traffic
/// provider. Holds no secrets; {@link #toString()} is safe to log.
public final class TrafficIncident
{
  @NonNull public final GeoPoint location;
  @NonNull public final IncidentSeverity severity;
  @NonNull public final String description;
  @NonNull public final String category;

  public TrafficIncident(@NonNull GeoPoint location, @NonNull IncidentSeverity severity,
                         @NonNull String description, @NonNull String category)
  {
    this.location = location;
    this.severity = severity;
    this.description = description;
    this.category = category;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "TrafficIncident{" + location + ", " + severity + ", category=" + category
        + ", desc=" + description + "}";
  }
}
