package app.organicmaps.cairodrive.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.io.IOException;
import java.util.List;

/// A pluggable online place-search backend (Google Places, HERE, Geoapify,
/// LocationIQ, TomTom, ...).
///
/// Implementations are OPT-IN: {@link #isAvailable()} returns false when the
/// provider's API key is absent, letting {@link OnlineSearchManager} skip it so
/// the offline-first app degrades gracefully instead of firing keyless requests.
public interface SearchProvider
{
  /// Short human-readable provider name, also used as {@link OnlinePlace#provider}.
  @NonNull
  String name();

  /// Whether this provider has the credentials needed to be queried.
  boolean isAvailable();

  /// Searches for places matching {@code query}, biased towards {@code near}
  /// when supplied. May return an empty list if the provider yielded no usable
  /// results.
  @NonNull
  List<OnlinePlace> search(@NonNull String query, @Nullable GeoPoint near) throws IOException;
}
