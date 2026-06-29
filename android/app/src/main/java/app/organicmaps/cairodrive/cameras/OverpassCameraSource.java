package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.io.IOException;
import java.util.List;

/// {@link CameraSource} backed by the public Overpass API, delegating to
/// {@link OverpassCameraClient}. Needs no API key, so it is always available.
///
/// Underlying endpoint (see OverpassCameraClient for the full QL): a
/// form-encoded POST to a public Overpass mirror, e.g.
///   https://overpass-api.de/api/interpreter
public final class OverpassCameraSource implements CameraSource
{
  private static final String NAME = "Overpass";

  @NonNull
  private final OverpassCameraClient mClient = new OverpassCameraClient();

  @NonNull
  @Override
  public String name()
  {
    return NAME;
  }

  @Override
  public boolean isAvailable()
  {
    return true;
  }

  @NonNull
  @Override
  public List<OverpassCamera> cameras(@NonNull GeoPoint center, double radiusMeters) throws IOException
  {
    // OverpassCameraClient is itself defensive (returns empty on mirror
    // failures), but the interface allows IOException for future sources.
    return mClient.fetchAround(center, radiusMeters);
  }
}
