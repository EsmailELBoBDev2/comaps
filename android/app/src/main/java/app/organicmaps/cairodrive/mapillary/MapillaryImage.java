package app.organicmaps.cairodrive.mapillary;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;

/// Immutable description of a single Mapillary street-level image (a
/// Google-Street-View-style photo captured by community contributors).
///
/// Sourced from the Mapillary Graph API:
///   GET https://graph.mapillary.com/images
/// Fields: id, thumb_1024_url, computed_geometry, captured_at.
///
/// Contains no secrets (the access token lives only in request URLs, never
/// here), so {@link #toString()} is safe to log.
public final class MapillaryImage
{
  /// Mapillary image id (stable string identifier).
  @NonNull
  public final String id;
  /// Where the image was captured (from computed_geometry).
  @NonNull
  public final GeoPoint location;
  /// URL of a ~1024px JPEG thumbnail. May be empty when absent in the response.
  @NonNull
  public final String thumbUrl;
  /// Capture time as Unix epoch milliseconds, or 0 when unknown.
  public final long capturedAtMs;

  public MapillaryImage(@NonNull String id, @NonNull GeoPoint location, @NonNull String thumbUrl,
                        long capturedAtMs)
  {
    this.id = id;
    this.location = location;
    this.thumbUrl = thumbUrl;
    this.capturedAtMs = capturedAtMs;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "MapillaryImage{id=" + id + ", at=" + location + ", capturedAtMs=" + capturedAtMs
        + ", hasThumb=" + !thumbUrl.isEmpty() + "}";
  }
}
