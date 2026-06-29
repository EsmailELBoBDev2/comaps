package app.organicmaps.cairodrive.cameras;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.model.GeoPoint;
import app.organicmaps.cairodrive.reports.CairoReport;
import app.organicmaps.cairodrive.reports.CairoReportStore;
import app.organicmaps.cairodrive.util.Geo;
import java.util.ArrayList;
import java.util.List;

/// {@link CameraSource} backed by the user's own community reports
/// ({@link CairoReportStore}). Surfaces user-reported CAMERA and mobile RADAR
/// points so they FILL GAPS the crowd-sourced OSM/Overpass data is missing.
/// Always available, fully offline.
///
/// The aggregator dedupes these against the other sources (same spot within
/// {@code DEDUPE_RADIUS_M} => hidden), so a camera you reported that OSM already
/// has won't show twice; Overpass (listed first) wins the identity tie.
public final class CommunityCameraSource implements CameraSource
{
  private static final String NAME = "Community";

  @NonNull
  private final Context mCtx;

  public CommunityCameraSource(@NonNull Context ctx)
  {
    mCtx = ctx.getApplicationContext();
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
    final long now = System.currentTimeMillis();
    for (final CairoReport r : CairoReportStore.active(mCtx, now))
    {
      final CameraType type = cameraTypeOf(r.kind);
      if (type == null)
        continue;  // not a camera-like report (police, bump, pothole, hazard)
      final GeoPoint at = new GeoPoint(r.lat, r.lon);
      if (Geo.haversineMeters(center, at) > radiusMeters)
        continue;
      // Negative synthetic id keeps these from colliding with OSM element ids.
      out.add(new OverpassCamera(-r.createdAtMs, at, type, 0, r.kind.label));
    }
    return out;
  }

  /// Maps a report kind to a camera type, or null when the report is not a
  /// camera (so it is ignored by this source).
  @Nullable
  private static CameraType cameraTypeOf(@NonNull CairoReport.Kind kind)
  {
    switch (kind)
    {
      case CAMERA:
        return CameraType.FIXED;
      case RADAR:
        return CameraType.MOBILE;
      default:
        return null;
    }
  }
}
