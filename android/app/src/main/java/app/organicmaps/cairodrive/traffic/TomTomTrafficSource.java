package app.organicmaps.cairodrive.traffic;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.List;

/// {@link TrafficSource} adapter that delegates to {@link TomTomTrafficClient}.
public final class TomTomTrafficSource implements TrafficSource
{
  @NonNull
  private final TomTomTrafficClient mClient = new TomTomTrafficClient();

  @NonNull
  @Override
  public String name()
  {
    return "TomTom";
  }

  @Override
  public boolean isAvailable()
  {
    return mClient.isAvailable();
  }

  @NonNull
  @Override
  public List<TrafficIncident> fetchInBbox(double minLon, double minLat, double maxLon, double maxLat)
      throws IOException
  {
    return mClient.fetchInBbox(minLon, minLat, maxLon, maxLat);
  }
}
