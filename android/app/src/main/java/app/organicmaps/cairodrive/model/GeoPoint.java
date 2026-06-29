package app.organicmaps.cairodrive.model;

import androidx.annotation.NonNull;

/// Immutable lat/lon pair shared across CairoDrive online modules.
public final class GeoPoint
{
  public final double lat;
  public final double lon;

  public GeoPoint(double lat, double lon)
  {
    this.lat = lat;
    this.lon = lon;
  }

  @NonNull
  @Override
  public String toString()
  {
    return lat + "," + lon;
  }
}
