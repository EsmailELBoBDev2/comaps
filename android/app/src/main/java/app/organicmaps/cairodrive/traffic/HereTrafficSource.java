package app.organicmaps.cairodrive.traffic;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.List;

/// {@link TrafficSource} adapter that delegates to {@link HereTrafficClient}.
public final class HereTrafficSource implements TrafficSource
{
  @NonNull
  private final HereTrafficClient mClient = new HereTrafficClient();

  @NonNull
  @Override
  public String name()
  {
    return "HERE";
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
