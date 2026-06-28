package app.organicmaps.cairodrive.search;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;

/// A single place result returned by an online {@link SearchProvider}.
///
/// Immutable once parsed. {@link #provider} carries the short name of the
/// backend that produced this result, so the UI can attribute results without
/// the user having to pick a provider up front.
public final class OnlinePlace
{
  @NonNull public final String name;
  @NonNull public final String address;
  @NonNull public final GeoPoint location;
  @NonNull public final String provider;

  public OnlinePlace(@NonNull String name, @NonNull String address,
                     @NonNull GeoPoint location, @NonNull String provider)
  {
    this.name = name;
    this.address = address;
    this.location = location;
    this.provider = provider;
  }

  @NonNull
  @Override
  public String toString()
  {
    return "OnlinePlace{provider=" + provider
        + ", name=" + name
        + ", address=" + address
        + ", location=" + location
        + '}';
  }
}
