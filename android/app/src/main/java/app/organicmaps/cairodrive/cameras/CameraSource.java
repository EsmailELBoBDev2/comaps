package app.organicmaps.cairodrive.cameras;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.io.IOException;
import java.util.List;

/// A single provider of speed/enforcement cameras for the multi-source
/// {@link CameraAggregator}. Implementations may hit a remote API (e.g.
/// Overpass) or serve an in-memory curated dataset.
///
/// All sources yield {@link OverpassCamera} so the aggregator can merge and
/// dedupe them with one model regardless of origin.
public interface CameraSource
{
  /// Short human-readable source name (e.g. "Overpass", "Custom"), used for
  /// logging and tie-breaking provenance.
  @NonNull
  String name();

  /// True when this source can currently serve results (e.g. a required key is
  /// present). Unavailable sources are skipped by the aggregator.
  boolean isAvailable();

  /// Cameras within roughly {@code radiusMeters} of {@code center}. Returns an
  /// empty list (never null) when none are found. May throw on network/IO
  /// failure; the aggregator catches per-source so one failure never aborts the
  /// merge.
  @NonNull
  List<OverpassCamera> cameras(@NonNull GeoPoint center, double radiusMeters) throws IOException;
}
