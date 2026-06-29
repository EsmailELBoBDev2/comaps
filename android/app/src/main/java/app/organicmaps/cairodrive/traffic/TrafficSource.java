package app.organicmaps.cairodrive.traffic;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.List;

/// A pluggable traffic-incident backend (TomTom, HERE, ...).
///
/// Implementations are OPT-IN: {@link #isAvailable()} returns false when the
/// provider's API key is absent, letting {@link TrafficAggregator} skip it so
/// the offline-first app degrades gracefully instead of firing keyless requests.
public interface TrafficSource
{
  /// Short human-readable source name (e.g. "TomTom", "HERE").
  @NonNull
  String name();

  /// Whether this source has the credentials needed to be queried.
  boolean isAvailable();

  /// Fetches incidents within the given bounding box. Bbox order is
  /// {@code minLon,minLat,maxLon,maxLat}. May return an empty list.
  @NonNull
  List<TrafficIncident> fetchInBbox(double minLon, double minLat, double maxLon, double maxLat)
      throws IOException;
}
