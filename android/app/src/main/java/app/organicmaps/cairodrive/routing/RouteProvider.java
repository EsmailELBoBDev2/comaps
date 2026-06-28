package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.io.IOException;
import java.util.List;

/// A pluggable online routing backend (Magic Lane, TomTom, Mapbox, ...).
///
/// Implementations are OPT-IN: {@link #isAvailable()} returns false when the
/// provider's API key is absent, letting {@link RouteCompareManager} skip it so
/// the offline-first app degrades gracefully instead of firing keyless requests.
public interface RouteProvider
{
  /// Short human-readable provider name, also used as {@link OnlineRoute#provider}.
  @NonNull
  String name();

  /// Whether this provider has the credentials needed to be queried.
  boolean isAvailable();

  /// Computes one or more route alternatives between {@code from} and {@code to}.
  /// May return an empty list if the provider yielded no usable routes.
  @NonNull
  List<OnlineRoute> route(@NonNull GeoPoint from, @NonNull GeoPoint to) throws IOException;
}
