package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.util.Geo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// {@link CameraSource} backed by an in-memory list of cameras. This is where a
/// curated dataset (e.g. hand-verified Egypt speed cameras) is injected at
/// runtime, complementing the crowd-sourced Overpass data.
///
/// No network access: {@link #cameras(GeoPoint, double)} simply filters the
/// supplied list by great-circle distance from the query center. Always
/// available.
public final class CustomDatasetCameraSource implements CameraSource
{
  private static final String NAME = "Custom";

  @NonNull
  private final List<OverpassCamera> mCameras;

  /// @param cameras the curated dataset; a defensive copy is taken, so later
  ///                mutations of the caller's list do not affect this source. A
  ///                null list is treated as empty.
  public CustomDatasetCameraSource(List<OverpassCamera> cameras)
  {
    mCameras = cameras == null ? Collections.emptyList()
                               : Collections.unmodifiableList(new ArrayList<>(cameras));
  }

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
  public List<OverpassCamera> cameras(@NonNull GeoPoint center, double radiusMeters)
  {
    final List<OverpassCamera> out = new ArrayList<>();
    for (final OverpassCamera cam : mCameras)
    {
      if (cam == null)
        continue;
      if (Geo.haversineMeters(center, cam.location) <= radiusMeters)
        out.add(cam);
    }
    return out;
  }
}
