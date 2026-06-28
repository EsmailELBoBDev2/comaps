package app.organicmaps.cairodrive.routing;

import androidx.annotation.NonNull;
import app.organicmaps.cairodrive.model.GeoPoint;
import java.util.Collections;
import java.util.List;

/// A single route alternative returned by an online {@link RouteProvider}.
///
/// The geometry/metrics are immutable once parsed; only the ranking flags
/// ({@link #isFastest} / {@link #isShortest}) are mutable, because they are
/// assigned by {@link RouteCompareManager} after all providers' results have
/// been collected and compared against each other.
public final class OnlineRoute
{
  @NonNull public final String provider;
  public final double distanceMeters;
  public final double durationSeconds;
  @NonNull public final List<GeoPoint> polyline;

  /// Set by RouteCompareManager during ranking (Waze-style comparison).
  public boolean isFastest;
  public boolean isShortest;

  public OnlineRoute(@NonNull String provider, double distanceMeters, double durationSeconds,
                     @NonNull List<GeoPoint> polyline)
  {
    this.provider = provider;
    this.distanceMeters = distanceMeters;
    this.durationSeconds = durationSeconds;
    this.polyline = Collections.unmodifiableList(polyline);
  }

  @NonNull
  @Override
  public String toString()
  {
    return "OnlineRoute{provider=" + provider
        + ", distanceMeters=" + distanceMeters
        + ", durationSeconds=" + durationSeconds
        + ", points=" + polyline.size()
        + ", fastest=" + isFastest
        + ", shortest=" + isShortest
        + '}';
  }
}
